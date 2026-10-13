# 03. AI Server Integration

VAP는 Spring Boot Backend와 FastAPI AI Server를 분리해 구성했습니다. Backend는 사용자와 서비스 기준 데이터를 관리하고, AI Server는 뉴스 크롤링·키워드 추출·금융 데이터 분석·AI 브리핑을 담당합니다.

## 1. 서비스 책임

```text
Frontend
    │ REST API
    ▼
Spring Boot Backend
    ├── 사용자 인증 및 서비스 데이터 관리
    ├── 뉴스·종목·관심종목 API 제공
    ├── AI 요청에 필요한 데이터 구성
    └── 작업 결과 저장 및 Frontend 반환
            │ REST / Kafka
            ▼
FastAPI AI Server
    ├── 뉴스 크롤링 및 키워드 추출
    ├── OpenBB·yfinance 금융 데이터 조회
    ├── 종목 분석·비교
    └── OpenAI 기반 서술형 브리핑
```

AI Server가 직접 사용자 세션이나 서비스 데이터를 관리하지 않도록 분리했습니다. AI Server는 Backend가 전달한 요청과 데이터를 처리하고 결과를 반환하는 역할에 집중합니다.

## 2. 뉴스 크롤링 연동

크롤링은 처리 시간이 길 수 있으므로 Kafka를 사용하는 비동기 흐름으로 구성했습니다.

```text
Frontend
    ↓ POST /api/internal/news/crawl/async
Spring Boot
    ├── jobId 생성 및 Redis 상태 저장
    └── crawl-news Kafka 이벤트 발행
            ↓
NewsCrawlConsumer
    └── FastAPI POST /crawl 호출
            ↓
뉴스 수집·키워드 추출
            ↓
Backend 내부 API를 통한 뉴스 저장 및 캐시 갱신
```

작업 상태는 `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`로 관리하며, Frontend는 `jobId`로 상태를 조회합니다. 이를 통해 크롤러가 느리거나 일시적으로 실패해도 사용자 요청과 백그라운드 처리를 분리할 수 있습니다.

## 3. 금융 AI 분석 연동

종목 분석은 먼저 금융 데이터를 정규화하고, 계산된 지표와 근거를 기반으로 AI 서술을 생성하는 순서로 처리합니다.

```text
종목 분석 요청
    ↓
FastAPI Analysis Route
    ↓
OpenBB / yfinance 데이터 조회
    ↓
가격·재무·뉴스 지표 정규화
    ↓
분석 점수 및 근거 생성
    ↓
OpenAI 서술형 결과 생성
    ↓
분석 결과·출처와 함께 응답
```

원본 Provider 데이터를 그대로 LLM에 전달하지 않고, Backend와 AI 분석 모듈에서 계산한 점수·지표·근거를 전달하도록 구성했습니다. 따라서 AI가 금융 수치를 직접 계산하기보다 이미 검증된 분석 결과를 설명하는 역할을 수행합니다.

## 4. 데이터 흐름

```text
Spring Boot
    │ 종목·관심종목·뉴스 요청
    ▼
FastAPI
    │ 외부 금융 데이터 및 AI 분석
    ▼
정규화된 분석 응답
    │ provider, sources, metrics, narrative
    ▼
Spring Boot / Frontend
```

분석 응답에는 가능한 경우 데이터 Provider와 출처 정보를 함께 포함해 결과가 어떤 데이터에 기반했는지 확인할 수 있도록 했습니다.

## 5. 주요 설계 포인트

- Spring Boot는 인증·인가와 서비스 기준 데이터의 단일 진입점입니다.
- FastAPI는 AI와 외부 데이터 처리에 집중해 Backend와 독립적으로 변경할 수 있습니다.
- 오래 걸리는 크롤링·브리핑 작업은 Kafka와 작업 상태로 분리합니다.
- 금융 데이터는 Provider별 응답을 공통 형태로 정규화한 뒤 분석합니다.
- AI 결과에는 분석 근거와 데이터 출처를 함께 포함합니다.

