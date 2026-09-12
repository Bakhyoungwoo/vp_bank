# 비동기 크롤링 결과 통지 구조

## 결론

크롤링 작업은 Kafka로 비동기 처리하고, 프론트엔드의 완료 확인은 `jobId` 기반 Polling을 기본으로 사용한다. 기존 SSE는 뉴스 알림을 즉시 전달하는 보조 채널로 유지한다. WebSocket은 현재 요구사항에 필요하지 않다.

## 처리 흐름

```text
Frontend
  │ POST /api/internal/news/crawl/async?category=it
  │ ← 202 { jobId, status: ACCEPTED }
  ▼
Spring Boot
  │ Redis: crawl:job:{jobId} = QUEUED
  │ Kafka: crawl-news (jobId, category)
  ▼
NewsCrawlConsumer
  │ PROCESSING → Python crawler → cache/DB 갱신 → COMPLETED
  │ 실패 시 FAILED 및 message 저장
  ▼
Frontend
  │ GET /api/internal/news/crawl/jobs/{jobId} (2초 간격)
  │ COMPLETED/FAILED까지 조회
```

## 상태와 책임

| 상태 | 의미 |
|---|---|
| `QUEUED` | 작업 상태를 저장했고 Kafka 발행을 기다리는 중 |
| `PROCESSING` | Kafka Consumer가 작업을 처리 중 |
| `COMPLETED` | 크롤러 호출과 후속 캐시 처리가 끝남 |
| `FAILED` | 발행 또는 처리 실패. `message`로 원인을 제공 |

작업 상태는 Redis에 1시간 TTL로 보관한다. 따라서 새로고침이나 SSE 연결 끊김이 발생해도 프론트엔드는 `jobId`로 작업 결과를 다시 확인할 수 있다. SSE가 끊겨도 Polling이 결과 확인을 보장한다.

## SSE의 역할

`GET /api/notifications/subscribe?userId=...`는 `news-alert` Kafka 이벤트를 연결된 브라우저에 전달하는 알림 스트림이다. 현재는 특정 크롤링 작업의 완료 보장 채널이 아니므로, 작업 완료 판정은 반드시 job status API를 기준으로 한다.

## 운영 시 보완점

- 내부 전용 경로는 운영 환경에서 서비스 인증 또는 관리자 권한으로 제한한다.
- 다중 서버 배포 시 SSE emitter를 로컬 메모리에 두지 말고 Redis Pub/Sub 또는 별도 알림 게이트웨이를 사용한다.
- 장기 작업은 Redis 대신 영속 DB에 작업 이력을 저장하고, 결과 식별자를 함께 반환한다.
