# 성능 측정 테스트

`PerformanceBenchmarkTest`는 실행 중인 Spring Boot, Kafka, Redis를 대상으로 다음 지표를 측정합니다.

- 동기 크롤링 API와 Kafka publish API의 평균/p50/p95 응답시간
- 동시 크롤링 요청 N건의 성공 수, timeout 수, HTTP 5xx 수, 평균/p95
- Kafka consumer 1개/2개일 때 broker 메시지 처리량
- DB 조회와 캐시 조회의 평균/p95 응답시간
- 지정한 Redis key의 cache hit ratio

테스트는 외부 인프라를 사용하므로 기본 `test` 실행에서는 비활성화되어 있습니다.

## 실행 예시

Docker Compose 인프라를 먼저 실행합니다.

```powershell
docker compose up -d mysql redis kafka
$env:RUN_PERFORMANCE_TESTS="true"
$env:SYNC_API_URL="http://localhost:8080/api/internal/news/crawl?category=it"
$env:READ_DB_URL="http://localhost:8080/api/news/it"
$env:READ_CACHE_URL="http://localhost:8080/api/news/keywords/it"
$env:REDIS_CACHE_KEY="trend:it:articles"
.\gradlew.bat test --tests "*PerformanceBenchmarkTest"
```

결과 CSV는 `build/performance-results/`에 생성됩니다.

## 주요 환경변수

| 변수 | 기본값 | 설명 |
|---|---:|---|
| `RUN_PERFORMANCE_TESTS` | 없음 | `true`일 때만 테스트 활성화 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka 접속 주소 |
| `SYNC_API_URL` | `/api/internal/news/crawl?category=it` | 동기 API 주소 |
| `ASYNC_API_URL` | `/api/internal/news/crawl/async?category=it` | Kafka publish ack API 주소 |
| `READ_DB_URL`, `READ_CACHE_URL` | 없음 | DB/캐시 비교용 GET 주소. 둘 다 설정해야 실행 |
| `REDIS_CACHE_KEY` | 없음 | 설정 시 Redis key 존재율을 hit ratio로 기록 |
| `BENCHMARK_SAMPLES` | `30` | HTTP 샘플 수 |
| `CRAWL_CONCURRENCY` | `10` | 동시 크롤링 요청 수 |
| `REQUEST_TIMEOUT_MS` | `10000` | 요청 timeout |
| `KAFKA_MESSAGES` | `1000` | consumer throughput 메시지 수 |

Kafka throughput 테스트는 애플리케이션의 크롤링 저장 로직까지 포함하지 않고, broker에서 consumer가 메시지를 성공적으로 poll하는 처리량을 측정합니다. 실제 업무 처리 성공률/처리 지연까지 측정하려면 별도의 benchmark topic consumer 또는 애플리케이션의 처리 완료 이벤트를 연결해야 합니다.

동기 API의 실제 크롤링 완료시간을 측정하려면 `AI_python/main.py`를 실행해야 합니다. 해당 서버는 `/crawl?category=it` endpoint를 제공하며, Spring의 동기 API가 이 endpoint의 완료 응답을 기다립니다. Kafka publish API는 `/api/internal/news/crawl/async`이며 Kafka broker의 publish ack를 받은 뒤 `202 Accepted`를 반환합니다.
