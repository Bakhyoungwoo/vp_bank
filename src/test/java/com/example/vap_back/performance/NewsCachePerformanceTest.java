package com.example.vap_back.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시 요청 환경에서 MySQL 직접 조회와 Redis Cache Hit를 비교하는 수동 벤치마크입니다.
 *
 * RUN_PERF_TESTS=true .\\gradlew.bat test --tests '*NewsCachePerformanceTest'
 * PERF_REQUESTS_PER_WORKER, PERF_WARMUP_REQUESTS 환경변수로 요청 수를 조정할 수 있습니다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_PERF_TESTS", matches = "true")
class NewsCachePerformanceTest {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3307/vapdb?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul";
    private static final String CATEGORY = "it";
    private static final String REDIS_KEY = "benchmark:trend:it:articles";
    private static final int LIMIT = 10;
    private static final int[] CONCURRENCIES = {10, 50, 100};
    private static final int REQUESTS_PER_WORKER = envInt("PERF_REQUESTS_PER_WORKER", 100);
    private static final int WARMUP_REQUESTS = envInt("PERF_WARMUP_REQUESTS", 20);
    private static StringRedisTemplate redis;
    private static LettuceConnectionFactory redisFactory;
    private static ObjectMapper mapper;
    private static final ThreadLocal<DbSession> MYSQL_SESSION = new ThreadLocal<>();

    @BeforeAll
    static void setUp() throws Exception {
        mapper = new ObjectMapper();
        redisFactory = new LettuceConnectionFactory("localhost", 6379);
        redisFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(redisFactory);
        redis.afterPropertiesSet();
        try (Connection connection = openMysql()) { seedNews(connection); seedCache(connection); }
    }

    @AfterAll
    static void tearDown() { if (redis != null) redis.delete(REDIS_KEY); if (redisFactory != null) redisFactory.destroy(); }

    @Test
    void compareConcurrentMySqlAndRedis() throws Exception {
        System.out.printf("%n[NEWS CONCURRENT BENCHMARK] workers=10/50/100, requestsPerWorker=%d, warmup=%d%n", REQUESTS_PER_WORKER, WARMUP_REQUESTS);
        System.out.println("mode                  concurrency  requests  p95(ms)  dbQueries");
        for (int concurrency : CONCURRENCIES) {
            runWarmup(concurrency, NewsCachePerformanceTest::queryMySql);
            Result mysqlResult = measure(concurrency, NewsCachePerformanceTest::queryMySql, true);
            printResult("MySQL direct", concurrency, mysqlResult);
            runWarmup(concurrency, NewsCachePerformanceTest::queryRedis);
            Result redisResult = measure(concurrency, NewsCachePerformanceTest::queryRedis, false);
            printResult("Redis Cache Hit", concurrency, redisResult);
            assertThat(mysqlResult.successfulRequests()).isEqualTo(concurrency * REQUESTS_PER_WORKER);
            assertThat(redisResult.successfulRequests()).isEqualTo(concurrency * REQUESTS_PER_WORKER);
            assertThat(mysqlResult.dbQueries()).isEqualTo(mysqlResult.successfulRequests());
            assertThat(redisResult.dbQueries()).isZero();
        }
    }

    private static Result measure(int concurrency, Callable<List<Map<String, Object>>> operation, boolean mysqlMode) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong dbQueries = new AtomicLong();
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int worker = 0; worker < concurrency; worker++) futures.add(executor.submit(() -> {
            try {
                start.await(); List<Long> samples = new ArrayList<>(REQUESTS_PER_WORKER);
                for (int i = 0; i < REQUESTS_PER_WORKER; i++) {
                    long begin = System.nanoTime(); List<Map<String, Object>> result = operation.call();
                    if (result.size() != LIMIT) throw new AssertionError("unexpected result size: " + result.size());
                    samples.add(System.nanoTime() - begin); if (mysqlMode) dbQueries.incrementAndGet();
                }
                return samples;
            } finally {
                closeMysqlSession();
            }
        }));
        start.countDown();
        List<Long> samples = new ArrayList<>(concurrency * REQUESTS_PER_WORKER);
        try { for (Future<List<Long>> future : futures) samples.addAll(future.get(2, TimeUnit.MINUTES)); }
        finally { executor.shutdownNow(); }
        Collections.sort(samples);
        long p95 = samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
        return new Result(samples.size(), p95 / 1_000_000.0, dbQueries.get());
    }

    private static void runWarmup(int concurrency, Callable<List<Map<String, Object>>> operation) throws Exception {
        if (WARMUP_REQUESTS == 0) return;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int worker = 0; worker < concurrency; worker++) futures.add(executor.submit(() -> {
            try {
                start.await(); for (int i = 0; i < WARMUP_REQUESTS; i++) if (operation.call().size() != LIMIT) throw new AssertionError("unexpected result size"); return null;
            } finally {
                closeMysqlSession();
            }
        }));
        start.countDown();
        try { for (Future<?> future : futures) future.get(2, TimeUnit.MINUTES); } finally { executor.shutdownNow(); }
    }

    private static List<Map<String, Object>> queryMySql() throws Exception {
        DbSession session = MYSQL_SESSION.get();
        if (session == null) {
            Connection connection = openMysql();
            session = new DbSession(connection, connection.prepareStatement("SELECT title, url, press, published_at, keywords FROM news WHERE category = ? ORDER BY published_at DESC LIMIT ?"));
            MYSQL_SESSION.set(session);
        }
        PreparedStatement query = session.query();
        query.setString(1, CATEGORY); query.setInt(2, LIMIT); List<Map<String, Object>> result = new ArrayList<>();
        try (ResultSet rows = query.executeQuery()) { while (rows.next()) result.add(article(rows)); }
        return result;
    }

    private static Map<String, Object> article(ResultSet rows) throws SQLException {
        Map<String, Object> article = new HashMap<>(); article.put("title", rows.getString("title")); article.put("url", rows.getString("url"));
        article.put("press", rows.getString("press")); article.put("time", rows.getTimestamp("published_at")); article.put("keywords", rows.getString("keywords")); return article;
    }

    private static List<Map<String, Object>> queryRedis() throws Exception {
        List<String> values = redis.opsForList().range(REDIS_KEY, 0, LIMIT - 1); List<Map<String, Object>> result = new ArrayList<>();
        if (values != null) for (String value : values) result.add(mapper.readValue(value, new TypeReference<>() {})); return result;
    }

    private static Connection openMysql() throws SQLException { return DriverManager.getConnection(JDBC_URL, "vap", "vap123"); }

    private static void closeMysqlSession() {
        DbSession session = MYSQL_SESSION.get();
        MYSQL_SESSION.remove();
        if (session != null) {
            try { session.query().close(); } catch (SQLException ignored) { }
            try { session.connection().close(); } catch (SQLException ignored) { }
        }
    }

    private static void seedNews(Connection connection) throws Exception {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM news WHERE category = ? LIMIT 1")) {
            query.setString(1, CATEGORY); try (ResultSet rows = query.executeQuery()) { if (rows.next()) return; }
        }
        String sql = "INSERT INTO news (category, content, created_at, keywords, press, published_at, title, url) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) { for (int i = 1; i <= 50; i++) {
            LocalDateTime now = LocalDateTime.now(); insert.setString(1, CATEGORY); insert.setString(2, "Benchmark content " + i); insert.setTimestamp(3, Timestamp.valueOf(now)); insert.setString(4, "[]"); insert.setString(5, "benchmark"); insert.setTimestamp(6, Timestamp.valueOf(now.minusMinutes(i))); insert.setString(7, "Benchmark article " + i); insert.setString(8, "https://benchmark.local/articles/" + i); insert.addBatch();
        } insert.executeBatch(); }
    }

    private static void seedCache(Connection connection) throws Exception {
        redis.delete(REDIS_KEY); List<String> values = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("SELECT title, url, press, published_at, keywords FROM news WHERE category = ? ORDER BY published_at DESC LIMIT ?")) {
            query.setString(1, CATEGORY); query.setInt(2, LIMIT); try (ResultSet rows = query.executeQuery()) { while (rows.next()) values.add(mapper.writeValueAsString(article(rows))); }
        }
        redis.opsForList().rightPushAll(REDIS_KEY, values);
    }

    private static void printResult(String mode, int concurrency, Result result) { System.out.printf("%-20s %11d %9d %8.3f %10d%n", mode, concurrency, result.successfulRequests(), result.p95Ms(), result.dbQueries()); }
    private static int envInt(String name, int fallback) { String value = System.getenv(name); return value == null || value.isBlank() ? fallback : Integer.parseInt(value); }
    private record Result(int successfulRequests, double p95Ms, long dbQueries) {}
    private record DbSession(Connection connection, PreparedStatement query) {}
}
