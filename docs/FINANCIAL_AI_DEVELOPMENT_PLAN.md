# 금융 AI 증시 서비스 개발계획

## 1. 문서 목적

기존 프로젝트의 증시 기능을 OpenBB와 FinRobot을 기반으로 확장한다. 일반적인 시장 조회 기능은 실제 금융 데이터를 사용하고, AI 기능은 조회된 데이터와 뉴스를 근거로 분석하도록 구성한다.

핵심 방향은 다음과 같다.

```text
OpenBB     = 금융 데이터 수집·정규화
FinRobot   = 금융 분석 Agent·분석 workflow
LLM        = 자연어 해석·설명·리포트 생성
직접 구현  = 뉴스 영향 분석·급등락 원인·비교·개인화 기능
기존 백엔드 = 인증·사용자 요청·DB·API 중계·결과 전달
```

## 2. 현재 구현 상태

### 완료 또는 기반 구현

- Python AI 서비스에 OpenBB 의존성 및 금융 데이터 모듈 추가
- OpenBB 기반 시장 개요 조회 API 추가
- OpenBB 기반 기간별 차트 데이터 API 추가
- 기존 Spring Backend가 AI 서비스의 시장 API를 중계하도록 구성
- 프론트의 시장 개요 조회 연결
- 프론트의 시장 차트 조회 연결
- 시장 관련 뉴스 영역의 기존 연결 구조 확인
- Docker 환경에서 AI 서비스가 실행될 수 있도록 Java 런타임 및 OpenBB 빌드 단계 구성

### 현재 제한사항

- 현재 시장 데이터 Provider는 `yfinance` 기반 임시 연결이다.
- 한국 시장의 실시간·공식 데이터 제공을 위해서는 한국투자증권 Open API 또는 KRX 계열 API 연동이 필요하다.
- yfinance에서 KOSDAQ, KOSPI200 등 일부 한국 지수는 조회가 불안정할 수 있다.
- FinRobot은 아직 프로젝트에 직접 연결되지 않았다.
- AI 종목 분석, 종목 비교, 뉴스 영향 분석, 급등락 원인 분석, Tool Calling 챗봇은 후속 개발 대상이다.
- 프론트는 현재 별도 수정 중인 상태이므로 이 문서 작업에서 프론트 파일을 커밋하지 않는다.

## 3. 목표 아키텍처

```text
[Frontend]
    │
    ▼
[기존 Spring Backend]
    │
    ├── 시장·종목 일반 조회 ──► [Stock Service] ──► [OpenBB Adapter]
    │                                      │
    │                                      ├── 한국 API
    │                                      ├── 해외 Provider
    │                                      └── 뉴스 Provider
    │
    └── AI 요청 ──────────────► [AI Service]
                                      │
                                      ├── Tool Registry
                                      │      ├── price tool
                                      │      ├── financial tool
                                      │      ├── company tool
                                      │      ├── news tool
                                      │      └── market tool
                                      │
                                      ├── FinRobot Agent / Workflow
                                      ├── 직접 구현 분석 모듈
                                      └── LLM
```

금융 수치 계산은 코드와 데이터 계층에서 처리하고, LLM은 계산 결과의 설명과 요약을 담당하게 한다. 이를 통해 LLM이 기억에 의존해 가격이나 재무 수치를 생성하는 문제를 줄인다.

## 4. 단계별 개발계획

### 1단계. 금융 데이터 기반 안정화

목표는 시장 화면과 이후 AI 기능이 사용할 공통 데이터 계약을 확정하는 것이다.

#### 작업

- `MarketDataProvider` 인터페이스 정의
- OpenBB Provider를 사용하는 기본 Adapter 정리
- 한국 Provider Adapter 추가
- 한국투자증권 조회 API 또는 KRX 데이터 API 연동 검토 및 선정
- 조회 전용 API만 사용하고 주문 API는 구현하지 않음
- 종목 코드·시장 코드·통화·거래시간 표준화
- 데이터 출처, 조회 시각, 지연 여부를 응답에 포함
- Provider 장애 시 fallback 및 unavailable 응답 정의
- Redis 캐시와 요청 제한 적용

#### 기본 API

```text
GET /api/market/overview
GET /api/market/history/{symbol}
GET /api/stocks/search?query={query}
GET /api/stocks/{symbol}
GET /api/stocks/{symbol}/financials
GET /api/stocks/{symbol}/news
```

#### 완료 기준

- KOSPI, KOSDAQ, NASDAQ, S&P500을 동일한 응답 형식으로 조회
- 한국 장중·장외 상태와 데이터 지연 여부 표시
- Provider 오류가 화면 전체 오류로 이어지지 않음
- 실시간 데이터와 일별 데이터의 기준을 문서화

### 2단계. 종목 검색 및 상세 화면

#### 작업

- 국내·해외 종목 검색
- 종목 기본 정보와 거래소 표시
- OHLCV 차트 및 거래량 차트
- 기업 정보, 시가총액, 업종 표시
- 매출, 영업이익, 순이익, EPS 등 재무 정보 표시
- 관련 뉴스 목록과 발행 시각 표시
- 데이터 출처와 기준일 표시

#### 프론트 연결

현재 프론트에는 시장 개요와 차트 API 연결이 있으므로, 다음 단계에서 종목 검색과 상세 API를 같은 `api.js` 요청 계층으로 추가한다. 프론트에서 직접 외부 금융 API를 호출하지 않고 기존 백엔드를 통해 호출한다.

### 3단계. FinRobot 분석 기반 구성

FinRobot은 금융 데이터를 직접 보여주는 계층이 아니라, 수집된 데이터를 분석하는 Agent 계층으로 사용한다.

#### 작업

- FinRobot의 분석 구조와 라이선스·의존성 검토
- 프로젝트 AI 서비스 안에 분석 workflow 경계 정의
- OpenBB 데이터를 FinRobot이 사용할 수 있는 표준 입력 형식으로 변환
- 수치 계산 모듈과 LLM narrative 모듈 분리
- 분석 결과 스키마 정의
- 분석 근거 데이터와 기준 시각 저장
- 분석 실패·데이터 부족 상태 처리

#### 분석 결과 기본 형식

```json
{
  "symbol": "AAPL",
  "asOf": "2026-09-08T09:00:00Z",
  "summary": "...",
  "growth": {"score": 0, "evidence": []},
  "profitability": {"score": 0, "evidence": []},
  "valuation": {"score": 0, "evidence": []},
  "momentum": {"score": 0, "evidence": []},
  "positiveFactors": [],
  "riskFactors": [],
  "sources": []
}
```

### 4단계. AI 종목 분석

```text
종목 선택
  → 가격·재무·기업정보·뉴스 조회
  → 수치 지표 계산
  → FinRobot 분석 workflow
  → LLM 설명 생성
  → 근거와 함께 결과 반환
```

#### 분석 항목

- 성장성
- 수익성
- 재무 안정성
- 밸류에이션
- 최근 주가 모멘텀
- 긍정 요인
- 위험 요인
- 분석에 사용된 데이터와 뉴스

투자 추천이나 수익률 보장을 하지 않고, 데이터 기준일과 분석의 불확실성을 함께 표시한다.

### 5단계. AI 종목 비교

#### 작업

- 2~5개 종목 입력 검증
- 매출 성장률, 영업이익률, EPS, PER, PBR, ROE, 부채비율 계산
- 최근 기간 수익률과 변동성 계산
- 동일 업종 여부 표시
- 정량 비교표 생성
- FinRobot과 LLM을 이용한 차이 설명

```text
종목 A/B
  → 동일 기준 데이터 조회
  → 코드로 지표 계산
  → 비교 우위 판정
  → AI가 성장성·안정성·모멘텀 차이 설명
```

LLM이 비교 수치를 직접 계산하지 않도록 모든 핵심 숫자에는 원본 값과 계산식을 보존한다.

### 6단계. AI 뉴스 영향 분석

#### 작업

- 뉴스 수집 및 중복 제거
- 뉴스에서 기업·티커·산업·공급망 키워드 추출
- 관련 기업 후보 생성
- 기업별 주가·재무·업종 데이터 조회
- 긍정·중립·부정 영향 가능성 분류
- 영향 근거와 불확실성 설명

```text
뉴스
  → 관련 기업 식별
  → 기업·산업 데이터 조회
  → 영향 방향과 연결 근거 계산
  → FinRobot/LLM 설명
```

단순 감정분석으로 결론을 내리지 않고, 뉴스와 기업의 관계를 근거 중심으로 설명한다. 주가 상승·하락을 확정적으로 예측하지 않는다.

### 7단계. 급등락 원인 분석

#### 감지 조건 예시

- 전일 대비 일정 비율 이상 변동
- 최근 평균 대비 거래량 급증
- 장중 변동성 급증

#### 분석 흐름

```text
가격 급변 감지
  → 거래량·시장·업종 비교
  → 최근 뉴스와 공시 조회
  → 가능한 원인 후보 생성
  → 후보별 근거 점수화
  → AI가 원인과 한계 설명
```

원인이 확인되지 않는 경우에도 억지로 하나의 원인을 확정하지 않고 `확인 가능한 직접 원인 없음`으로 반환한다.

### 8단계. 개인화 시장 브리핑

#### 작업

- 사용자 관심종목 저장
- 관심종목별 가격·거래량·뉴스 변화 수집
- 마지막 브리핑 시점 이후 변화 계산
- 사용자별 시장 브리핑 생성
- 브리핑 생성 이력과 사용 데이터 저장
- 사용자가 관심종목을 삭제하거나 갱신할 수 있는 API 제공

```text
관심종목
  → 가격·재무·뉴스 변화 수집
  → 종목별 이벤트 분류
  → 사용자별 브리핑 생성
```

### 9단계. Tool Calling 기반 AI 증시 챗봇

#### Tool 예시

```text
get_market_overview
search_stock
get_stock_price
get_stock_history
get_financials
get_company_info
get_related_news
compare_stocks
analyze_news_impact
analyze_price_move
```

#### 처리 흐름

```text
사용자 질문
  → 의도·종목·기간 추출
  → 필요한 Tool 선택
  → 실제 금융 데이터 조회
  → 수치 검증 및 분석
  → FinRobot workflow
  → LLM 답변 생성
```

챗봇 답변에는 사용한 종목, 기간, 기준 시각, 데이터 출처를 포함한다. 데이터가 없는 경우 추측하지 않고 추가 정보를 요청하거나 조회 불가 사유를 반환한다.

## 5. Backend 모듈 계획

```text
backend
├── market
│   ├── controller
│   ├── service
│   └── dto
├── stock
│   ├── search
│   ├── detail
│   └── financial
├── news
├── ai
│   ├── tool
│   ├── agent
│   ├── analysis
│   └── prompt
└── personalization
```

Python AI 서비스는 다음 영역으로 나눈다.

```text
AI_python
├── market             # OpenBB 및 한국 Provider Adapter
├── stock              # 종목·재무·기업정보 조회
├── news               # 뉴스 조회·정규화
├── agents             # FinRobot 연계 Agent
├── analysis           # 비교·급등락·뉴스 영향 분석
├── tools              # LLM Tool Calling 도구
└── schemas            # 공통 입력·출력 모델
```

## 6. 데이터 및 보안 원칙

- API 키는 `.env`와 배포 환경변수로만 관리한다.
- API 키와 원문 응답을 로그에 남기지 않는다.
- 주문·매매 API 권한은 사용하지 않는다.
- 금융 데이터의 기준 시각과 Provider를 응답에 표시한다.
- 외부 API 장애와 rate limit을 고려해 캐시와 재시도 정책을 둔다.
- AI 결과는 투자 권유가 아닌 참고용 분석으로 표시한다.
- 뉴스 원문을 저장하거나 노출할 때 각 제공자의 이용약관과 저작권을 준수한다.
- 사용자 관심종목과 브리핑 이력은 사용자별로 접근을 제한한다.

## 7. 테스트 및 검증 계획

### 단위 테스트

- Provider 응답을 공통 스키마로 변환하는지 검증
- 수익률·PER·ROE·변동성 계산 검증
- 종목 코드와 기간 입력 검증
- 데이터 없음·장 휴장·Provider 오류 처리 검증

### 통합 테스트

- Frontend → Spring Backend → AI Service → OpenBB 흐름 검증
- 한국 Provider와 해외 Provider 응답 비교
- 뉴스 조회 후 관련 기업 분석 흐름 검증
- Tool Calling이 허용된 도구만 호출하는지 검증

### AI 품질 테스트

- 숫자와 원본 데이터 일치 여부
- 근거 없는 종목·가격 생성 여부
- 분석 기준일 누락 여부
- 뉴스와 기업 연결 근거의 정확성
- 원인 불명 급등락을 억지로 설명하지 않는지 검증

## 8. 권장 개발 순서

```text
1. 한국 조회 Provider 확정
2. 공통 금융 데이터 스키마 확정
3. 시장·종목·재무·뉴스 API 완성
4. 프론트 시장·종목 상세 연결
5. FinRobot 분석 workflow 연결
6. AI 종목 분석
7. 종목 비교
8. 뉴스 영향 분석
9. 급등락 원인 분석
10. 개인화 브리핑
11. Tool Calling 챗봇
12. 캐시·모니터링·품질평가·배포 안정화
```

## 9. 1차 릴리스 범위

첫 번째 사용자 확인이 가능한 릴리스는 다음으로 제한한다.

- KOSPI, KOSDAQ, NASDAQ, S&P500 시장 카드
- 한국·해외 종목 검색
- 종목 가격 차트
- 기업 기본 정보와 핵심 재무 지표
- 관련 뉴스
- AI 종목 분석 초안
- 분석 기준일·데이터 출처 표시

이후 비교, 뉴스 영향, 급등락 원인, 개인화 브리핑, 챗봇을 순차적으로 추가한다. 이 순서를 지키면 금융 데이터 연결이 불안정한 상태에서 AI 기능부터 개발하는 위험을 줄일 수 있다.

## 10. 성공 기준

- 시장 및 종목 데이터가 동일한 공통 스키마로 제공된다.
- 프론트가 기존 백엔드를 통해 시장·종목 데이터를 조회한다.
- 한국 시장 데이터는 공식 Provider 연결 상태와 지연 여부를 표시한다.
- AI 분석 결과의 주요 수치가 원본 데이터와 일치한다.
- 모든 AI 분석 결과가 근거 데이터와 기준 시각을 가진다.
- 데이터가 없거나 API가 실패할 때 추측 대신 명확한 상태를 반환한다.
- 주문 기능 없이 조회·분석 서비스로 동작한다.

