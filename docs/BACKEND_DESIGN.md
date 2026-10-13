# 02. Backend Design

이 문서는 VAP의 전체 Backend 구조와 서비스 간 책임을 설명합니다.
VAP는 뉴스와 금융 데이터를 제공하는 서비스이며, Spring Boot Backend를 중심으로 FastAPI AI Server, Frontend, Kafka, Redis가 연결된 구조로 구성되어 있습니다.

## 1. Backend의 역할

VAP는 Frontend, Spring Boot Backend, FastAPI AI Server가 분리된 구조로 구성되어 있습니다.

Spring Boot는 회원, 뉴스, 관심사, 북마크, 관심종목 등 서비스의 기준 데이터를 관리하고, Frontend와 AI Server 사이에서 뉴스·금융 데이터와 분석 결과를 연결합니다.

```text
Frontend
    │
    │ REST API / SSE
    ▼
Spring Boot Backend
    │
    ├── User / Authentication
    ├── News / Bookmark / Recommendation
    ├── Stock / Watchlist / Comparison
    ├── Crawl Job / Analysis Job
    ├── Redis Cache / Runtime State
    └── Kafka Event 연동
            │
            ▼
      FastAPI AI Server
      ├── News Crawler
      ├── Keyword Analysis
      ├── Market Data
      └── AI Briefing / Stock Analysis
```

Backend 전체에서는 다음 역할을 담당합니다.

- 회원 인증과 사용자별 리소스 접근 관리
- 뉴스 수집 결과, 북마크, 사용자 활동 데이터 관리
- 국내외 주식 및 금융 데이터 조회 API 제공
- 뉴스 크롤링과 AI 분석 작업의 비동기 실행 관리
- Redis 캐시와 사용자 관심사 기반 추천 처리
- AI Server에 필요한 서비스 데이터 제공
- AI Server와 Kafka를 통한 작업 및 결과 연동
- Frontend에 뉴스·증시·작업 상태·알림 데이터 제공

## 2. 서비스 간 책임 분리

하나의 서비스가 모든 수집, 저장, 분석, 화면 처리를 담당하지 않도록 Frontend, Backend, AI 영역의 책임을 나눴습니다.

| 영역 | 주요 책임 |
|---|---|
| Frontend | 사용자 입력, 뉴스·증시 화면 표시, 크롤링 작업 상태 조회, 실시간 알림 표시 |
| Spring Boot | 기준 데이터 관리, 인증·인가, REST API, 작업 상태 관리, Kafka 이벤트 발행·소비 |
| FastAPI AI Server | 뉴스 크롤링, 키워드 추출, 금융 데이터 조회, 종목 분석 및 AI 브리핑 |
| Kafka | 크롤링·분석과 같이 시간이 오래 걸리는 작업의 비동기 메시지 전달 |
| Redis | 캐시, 사용자 활동 점수, 작업 상태 등 빠른 조회가 필요한 데이터 관리 |

전체 서비스 흐름은 다음과 같습니다.

```text
사용자 요청
    ↓
Frontend
    ↓ REST API
Spring Boot
    ├─ 서비스 기준 데이터 조회
    ├─ 즉시 처리 가능한 요청 수행
    └─ 장기 작업은 Kafka 이벤트로 전달
            ↓
       FastAPI AI Server
       크롤링·분석·금융 데이터 처리
            ↓
       Spring Boot
       결과 저장 및 캐시 갱신
            ↓
Frontend
뉴스·증시 데이터 및 작업 결과 표시
```

AI Server가 크롤링과 분석을 담당하더라도, 사용자·뉴스·관심종목과 같은 서비스 기준 데이터 및 API 계약은 Spring Boot에서 관리하도록 역할을 구분했습니다.

## 3. 뉴스 크롤링 비동기 처리 구조

뉴스 크롤링은 외부 사이트 요청과 데이터 후처리로 인해 시간이 오래 걸릴 수 있습니다. 따라서 요청을 받은 HTTP 스레드가 모든 작업을 직접 처리하지 않고, Kafka 기반 비동기 작업으로 분리했습니다.

```text
Frontend
    │
    │ POST /api/internal/news/crawl/async?category=it
    ▼
AsyncCrawlController
    │
    ├── jobId 생성
    ├── Redis에 QUEUED 저장
    └── crawl-news 이벤트 발행
            │
            ▼
      NewsCrawlConsumer
            │
            ├── PROCESSING 상태 변경
            ├── FastAPI /crawl 호출
            ├── 뉴스 저장소·캐시 갱신
            └── COMPLETED 또는 FAILED 저장
```

크롤링 요청 API는 작업을 접수한 뒤 `202 Accepted`와 `jobId`를 반환합니다. Frontend는 해당 `jobId`로 다음 API를 호출해 작업 상태를 확인합니다.

```http
GET /api/internal/news/crawl/jobs/{jobId}
```

작업 상태는 다음과 같이 관리합니다.

| 상태 | 의미 |
|---|---|
| `QUEUED` | 작업 생성 후 Kafka 처리를 기다리는 상태 |
| `PROCESSING` | Consumer가 FastAPI 크롤러와 결과 저장을 처리하는 상태 |
| `COMPLETED` | 크롤링 결과 반영까지 완료된 상태 |
| `FAILED` | 이벤트 발행 또는 크롤링·저장 중 오류가 발생한 상태 |

## 4. 데이터 저장소 역할 분리

VAP에서는 데이터의 변경 빈도와 사용 목적에 따라 저장소의 역할을 구분했습니다.

| 저장소 | 역할 |
|---|---|
| MySQL | 사용자, 뉴스, 북마크, 관심종목 등 영속 데이터 |
| Redis | 뉴스 캐시, 사용자 키워드 점수, 작업 상태, 요청 제한 |
| Kafka | 뉴스 크롤링 및 분석 작업 이벤트 전달 |

```text
                 Spring Boot
                      │
        ┌─────────────┼─────────────┐
        │             │             │
        ▼             ▼             ▼
      MySQL         Redis         Kafka
   Persistent       Cache /       Async
     Data        Runtime State    Events
```

크롤링 작업 상태는 `crawl:job:{jobId}` 키로 Redis에 저장하고 1시간의 TTL을 적용합니다. 따라서 SSE 연결이 끊기거나 Frontend가 새로고침되어도 `jobId`를 이용해 상태를 다시 조회할 수 있습니다.

## 5. 사용자 관심사와 뉴스 데이터 흐름

뉴스 서비스는 사용자의 활동을 관심사 데이터로 변환하고, 이를 뉴스 추천에 활용합니다.

```text
뉴스 수집
    ↓
키워드 추출 및 카테고리별 랭킹
    ↓
뉴스 저장 / Redis 캐시
    ↓
사용자 검색·클릭·북마크 활동
    ↓
사용자별 키워드 점수
    ↓
관심사 기반 뉴스 추천
```

뉴스 원문과 분석 결과는 Backend를 통해 제공하고, 반복 조회가 많거나 실시간성이 필요한 키워드·추천 데이터는 Redis를 활용하도록 구성했습니다.

## 6. SSE 알림과 작업 상태 조회

VAP의 실시간 알림과 비동기 작업 완료 확인은 서로 다른 목적을 가집니다.

```text
뉴스 알림 전달       → SSE
크롤링 작업 완료 확인 → jobId 상태 조회 API
```

SSE 구독 API는 `news-alert` 이벤트를 연결된 브라우저에 전달합니다.

```http
GET /api/notifications/subscribe?userId={userId}
```

SSE는 사용자에게 새로운 뉴스나 알림을 빠르게 전달하기 위한 채널이고, 특정 크롤링 작업이 완료되었는지를 보장하는 채널은 아닙니다. 작업의 최종 상태는 Redis에 저장된 `jobId` 상태를 기준으로 확인합니다.

## 7. 주요 Backend Component

### 뉴스 및 비동기 작업 영역

| Component | 역할 |
|---|---|
| `AsyncCrawlController` | 크롤링 요청 접수 및 작업 상태 조회 API 제공 |
| `CrawlJobService` | Redis 기반 크롤링 작업 상태 생성·변경·조회 |
| `NewsCrawlProducer` | `crawl-news` Kafka 이벤트 발행 |
| `NewsCrawlConsumer` | Kafka 이벤트 소비, FastAPI 호출, 결과 저장 및 상태 변경 |
| `NewsCacheService` | 뉴스 크롤링 결과 저장 및 캐시 갱신 |
| `NotificationService` | SSE 연결 관리 및 실시간 알림 전달 |

### 뉴스·사용자 데이터 영역

| Component | 역할 |
|---|---|
| `NewsService` | 뉴스 저장 및 기본 조회 |
| `NewsTrendingService` | 카테고리별 트렌딩 키워드 조회 |
| `NewsRecommendationService` | 사용자 활동 기반 뉴스 추천 |
| `NewsUserActivityService` | 클릭·검색 등 사용자 활동 점수 관리 |
| `BookmarkService` | 사용자별 뉴스 북마크 관리 |

### 금융 데이터 영역

| Component | 역할 |
|---|---|
| `StockService` | 종목 시세·재무·관련 뉴스 조회 |
| `WatchlistService` | 사용자 관심종목 관리 |
| `AnalysisJob` 영역 | 관심종목 기반 AI 분석 및 브리핑 작업 관리 |

## 8. 설계에서 중요하게 본 부분

- 오래 걸리는 크롤링과 분석을 Kafka 이벤트로 분리해 API 응답 지연을 줄였습니다.
- `jobId`를 기준으로 작업 상태를 관리해 Frontend가 재조회할 수 있도록 했습니다.
- MySQL은 기준 데이터, Redis는 빠른 조회 데이터와 작업 상태, Kafka는 비동기 이벤트를 담당하도록 역할을 나눴습니다.
- SSE를 작업 완료 보장 수단으로 사용하지 않고, 알림 전달과 작업 상태 확인의 책임을 분리했습니다.
- Consumer에서 카테고리별 Lock을 사용해 동일 카테고리의 중복 크롤링을 방지했습니다.
- Kafka 처리 실패는 재시도와 Dead Letter Topic으로 분리해 장애 상황을 추적할 수 있도록 했습니다.

## 9. 전체 구조 요약

VAP Backend는 Spring Boot를 중심으로 서비스 기준 데이터를 관리하고, Kafka를 통해 시간이 오래 걸리는 크롤링·분석 작업을 분리합니다. FastAPI AI Server는 뉴스 크롤링과 금융 데이터 분석을 담당하며, 결과는 다시 Backend의 저장소와 캐시에 반영됩니다.

Frontend는 REST API로 서비스 데이터를 조회하고, `jobId` 기반 상태 조회로 비동기 작업 결과를 확인하며, SSE로 실시간 알림을 수신합니다. 이를 통해 데이터 관리, AI 처리, 작업 실행, 화면 표시의 책임을 각각 분리했습니다.
