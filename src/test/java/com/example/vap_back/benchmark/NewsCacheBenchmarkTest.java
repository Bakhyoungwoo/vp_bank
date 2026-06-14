package com.example.vap_back.benchmark;

import com.example.vap_back.Entity.News;
import com.example.vap_back.repository.NewsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Redis Cache Hit vs MySQL 직접 조회 성능 비교 벤치마크
 *
 * [측정 대상]
 *   - MySQL 경로: H2 인메모리 DB를 통한 JPA 쿼리 (JDBC 오버헤드 + SQL 실행 포함)
 *   - Cache 경로: 미리 직렬화된 JSON 문자열 역직렬화 (Redis가 반환한 데이터 처리 시뮬레이션)
 *
 * [주의 사항]
 *   - H2는 인메모리 DB라 실제 MySQL(디스크 I/O, 네트워크)보다 훨씬 빠름
 *   - 실제 프로덕션에서 MySQL은 수 ms ~ 수십 ms, Redis는 0.1ms 수준
 *   - 즉, 이 테스트는 실제 차이보다 보수적(Redis 이점이 더 작게) 측정됨
 */
@DataJpaTest
@TestPropertySource(properties = {
        // MEDIUMTEXT 등 MySQL 전용 타입 호환을 위해 H2 MySQL 모드 사용
        "spring.datasource.url=jdbc:h2:mem:benchmarkdb;MODE=MySQL;NON_KEYWORDS=VALUE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("[Benchmark] Redis Cache Hit vs MySQL 직접 조회 성능 비교")
class NewsCacheBenchmarkTest {

    @Autowired
    NewsRepository newsRepository;

    @Autowired
    TestEntityManager entityManager;

    // ObjectMapper를 직접 생성 (Spring context 의존성 없이 JSON 역직렬화)
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Redis에 캐시된 상태를 시뮬레이션: JSON 직렬화된 문자열 리스트
    // (실제 Redis의 LRANGE key 0 49 결과와 동일한 형태)
    private List<String> simulatedRedisJsons;

    private static final int DATA_SIZE = 50;
    private static final int WARMUP_ITERATIONS = 20;
    private static final int BENCHMARK_ITERATIONS = 200;

    @BeforeEach
    void setUp() throws Exception {
        // H2에 뉴스 데이터 50개 삽입 (MySQL의 news 테이블과 동일한 구조)
        for (int i = 0; i < DATA_SIZE; i++) {
            entityManager.persist(News.builder()
                    .category("it")
                    .title("테스트 기사 " + i)
                    .url("http://test.com/news/" + i)
                    .press("테스트 언론사")
                    .publishedAt(LocalDateTime.now().minusHours(i))
                    .keywords("[\"keyword" + i + "\",\"tech\",\"java\"]")
                    .build());
        }
        entityManager.flush();

        // Redis 캐시 히트 상태 시뮬레이션:
        // 실제 Redis는 JSON 문자열로 직렬화하여 저장 → LRANGE로 꺼낼 때 JSON 문자열 반환
        // NewsCacheServiceImpl.getLatestArticles()의 cache hit 경로와 동일하게 역직렬화 포함
        simulatedRedisJsons = new ArrayList<>();
        for (int i = 0; i < DATA_SIZE; i++) {
            Map<String, Object> article = Map.of(
                    "title", "테스트 기사 " + i,
                    "url", "http://test.com/news/" + i,
                    "press", "테스트 언론사",
                    "time", LocalDateTime.now().minusHours(i).toString(),
                    "keywords", List.of("keyword" + i, "tech", "java")
            );
            simulatedRedisJsons.add(objectMapper.writeValueAsString(article));
        }
    }

    @Test
    @DisplayName("MySQL(H2) 직접 조회 vs Redis 캐시 히트(JSON 역직렬화) 평균 응답 시간 비교")
    void compareMysqlVsRedisCacheHit() throws Exception {

        // ===== Warm-up: JIT 컴파일러 최적화 및 JPA 1차 캐시 워밍 =====
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("it");
            deserializeFromSimulatedRedis();
        }

        // ===== MySQL(H2) 직접 조회 측정 =====
        long mysqlTotalNs = 0;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            // JPA 1차 캐시 영향 제거: EntityManager clear
            entityManager.clear();

            long start = System.nanoTime();
            List<News> result = newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("it");
            mysqlTotalNs += System.nanoTime() - start;

            assertThat(result).hasSize(DATA_SIZE); // 데이터 일관성 확인
        }

        // ===== Redis 캐시 히트 시뮬레이션 측정 =====
        // 실제 Redis 캐시 히트 흐름: LRANGE → JSON 역직렬화 → Map 리스트 반환
        long cacheTotalNs = 0;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            List<Map<String, Object>> cached = deserializeFromSimulatedRedis();
            cacheTotalNs += System.nanoTime() - start;

            assertThat(cached).hasSize(DATA_SIZE);
        }

        // ===== 결과 계산 =====
        double mysqlAvgMs  = mysqlTotalNs  / (double) BENCHMARK_ITERATIONS / 1_000_000.0;
        double cacheAvgMs  = cacheTotalNs  / (double) BENCHMARK_ITERATIONS / 1_000_000.0;
        double mysqlAvgUs  = mysqlTotalNs  / (double) BENCHMARK_ITERATIONS / 1_000.0;
        double cacheAvgUs  = cacheTotalNs  / (double) BENCHMARK_ITERATIONS / 1_000.0;
        double speedupRatio = mysqlAvgMs / cacheAvgMs;

        // ===== Result Output =====
        System.out.println();
        System.out.println("=================================================");
        System.out.println("     [Benchmark] Cache vs MySQL Performance");
        System.out.println("=================================================");
        System.out.printf("  Iterations    : %d  (warmup: %d)%n", BENCHMARK_ITERATIONS, WARMUP_ITERATIONS);
        System.out.printf("  Dataset size  : %d records%n", DATA_SIZE);
        System.out.println("-------------------------------------------------");
        System.out.printf("  MySQL (H2)    : avg %.4f ms  (%.2f us)%n", mysqlAvgMs, mysqlAvgUs);
        System.out.printf("  Redis Cache   : avg %.4f ms  (%.2f us)%n", cacheAvgMs, cacheAvgUs);
        System.out.println("-------------------------------------------------");
        System.out.printf("  Speedup       : Redis is %.1fx faster than MySQL%n", speedupRatio);
        System.out.println("=================================================");
        System.out.println("  NOTE: H2 is in-memory -> real MySQL disk I/O excluded");
        System.out.println("  NOTE: Redis time = JSON deserialization only (no network)");
        System.out.println("  NOTE: In production, MySQL is much slower -> Redis gains more");
        System.out.println("=================================================");
        System.out.println();

        // Verify: cache must be faster than MySQL
        assertThat(cacheAvgMs)
                .as("Redis cache hit must be faster than MySQL direct query")
                .isLessThan(mysqlAvgMs);
    }

    /*
     * Redis 캐시 히트 시뮬레이션:
     * 실제 NewsCacheServiceImpl.getLatestArticles()의 cache hit 경로와 동일하게
     * JSON 문자열 리스트를 Map으로 역직렬화
     */
    private List<Map<String, Object>> deserializeFromSimulatedRedis() throws Exception {
        List<Map<String, Object>> result = new ArrayList<>(simulatedRedisJsons.size());
        for (String json : simulatedRedisJsons) {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            if (map != null) {
                result.add(map);
            }
        }
        return result;
    }
}
