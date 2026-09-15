# AI 뉴스 트렌드 분석 & 증시 정보 서비스 (VAP)

## 프로젝트 소개
VAP는 두 축으로 구성된 서비스입니다.

1. **뉴스** — 실시간 뉴스 데이터를 수집·분석해 사용자 관심사 기반 뉴스 추천과 AI 키워드 추출을 통한 뉴스 트렌드 분석을 제공합니다.
2. **증시** — OpenBB/yfinance 기반 실제 금융 데이터로 시장 지수·종목 검색/상세/비교를 제공하고, LLM(OpenAI)이 계산된 수치를 근거로 AI 종목 분석·비교 설명을 생성합니다.

프론트엔드는 별도 저장소(`vp_front`)의 순수 HTML/CSS/JS 정적 사이트이며, 이 저장소(Spring Boot)가 API를 제공하고 운영 환경에서는 정적 파일도 함께 서빙합니다.

---

## 주요 기능

### 뉴스
- JWT 기반 사용자 인증
- 사용자 관심사 기반 뉴스 추천
- 뉴스 클릭 이벤트 수집 및 분석
- AI 키워드 추출을 통한 트렌드 분석

### 증시 (Market)
- 코스피·코스닥·코스피200·나스닥·S&P500·원/달러 환율 등 주요 지수 조회
- 국내·해외 종목 검색 및 상세(차트·기업정보·재무·뉴스) 조회
- AI 종목 분석 — 가격·거래량·재무 데이터로 계산한 성장성/수익성/밸류에이션/모멘텀 점수와 LLM 서술
- 종목 비교 — 2~5개 종목의 매출성장률·영업이익률·PER·PBR·ROE·부채비율·기간수익률·변동성을 동일 기준으로 비교하고 AI가 차이를 설명
- 한국투자증권(KIS) Open API 어댑터 준비 (자격증명 미설정 시 yfinance로 자동 폴백, 지연 여부 표시)
- Provider 장애 시 자동 폴백, Redis 기반 캐시·요청 제한(Rate Limit)

---

## 기술 스택

### Backend (API & Security)
- **Java 17** – Core backend language (LTS)
- **Spring Boot 3.2.5** – Backend framework for RESTful APIs
- **Spring Web** – HTTP request handling and controller layer
- **Spring Data JPA (Hibernate)** – ORM-based database access
- **Spring Security** – Authentication and authorization framework
- **JWT (jjwt)** – Token-based authentication mechanism

### Database & Cache
- **MySQL 8.0** – Relational database for persistent data storage
- **Redis 7** – In-memory data store for caching, rate limiting, and rank storage

### Infrastructure & DevOps
- **Apache Kafka** – Asynchronous event streaming and message processing
- **KRaft mode** – Kafka metadata management and controller quorum without ZooKeeper
- **Docker (Compose)** – Containerization of application and infrastructure and Multi-container orchestration for local development
- **GitHub Actions** – CI pipeline for build and Docker image automation

---

### AI — 뉴스
- **Keyword Extraction** - KeyBERT
- **Embedding Model** - Sentence-BERT (KR-SBERT, KLUE-NLI)
- **NLP Preprocessing** - KoNLPy (Okt)
- **Crawling** - Python
  - **Static Page** - Requests / BeautifulSoup
  - **Dynamic Page** - Selenium

### AI — 증시
- **OpenBB** – 시장·종목·재무 데이터 수집 및 정규화
- **yfinance** – 기본 시세/재무 Provider (KIS 미설정 시 자동 폴백)
- **한국투자증권(KIS) Open API** – 국내 실시간 시세 어댑터 (선택, 자격증명 필요)
- **OpenAI (Chat Completions)** – 이미 계산된 수치·근거만으로 AI 종목분석/비교 서술 생성

### AI 서버 공통
- **FastAPI (Python)** – 뉴스/증시 AI 서비스

---

## 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                 Web Client (vp_front, 별도 저장소)             │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌──────────────┐ │
│  │ News Feed │ │ Login/Auth│ │  Market /  │ │ Stock Search/│ │
│  │    UI     │ │           │ │  Overview  │ │ Detail/Compare│ │
│  └───────────┘ └───────────┘ └───────────┘ └──────────────┘ │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP (REST API :8080)
┌─────────────────────────▼───────────────────────────────────┐
│                  Spring Boot Main Server                    │
│  ┌─────────────┐ ┌─────────────┐ ┌───────────┐ ┌──────────┐ │
│  │  User/News  │ │  Kafka Prod.│ │ Kafka Cons│ │ Market/AI│ │
│  │  Service    │ │ (Event Pub) │ │(Log Save) │ │  Proxy   │ │
│  └─────────────┘ └─────────────┘ └───────────┘ └────┬─────┘ │
└─────────┬───────────────────────┬────────────────────┼───────┘
          │ JPA / Redis Ops       │ Async Events (Topics)│
┌─────────▼───────────────────────▼────────────────────▼───────┐
│                   Infrastructure Layer                       │
│  ┌──────────────┐   ┌──────────────┐    ┌────────────────┐   │
│  │    MySQL     │   │    Redis     │    │  Apache Kafka  │   │
│  │ (User/News)  │   │(Cache/Rank/  │◀──▶│  (Msg Broker)  │   │
│  │              │   │ Rate Limit)  │    │                │   │
│  └──────────────┘   └──────────────┘    └────────┬───────┘   │
└───────────────────────────────────────────────────│───────────┘
                                                   │ Crawl Req / Market·AI Req
                                     ┌─────────────▼──────────────┐
                                     │     FastAPI AI Server      │
                                     │ ┌─────────┐ ┌─────────────┐│
                                     │ │ Crawler │ │   KeyBERT   ││
                                     │ └─────────┘ └─────────────┘│
                                     │ ┌─────────┐ ┌─────────────┐│
                                     │ │ OpenBB /│ │   OpenAI    ││
                                     │ │yfinance/│ │(종목분석·비교││
                                     │ │  KIS    │ │    서술)    ││
                                     │ └─────────┘ └─────────────┘│
                                     └─────────────────────────────┘
```
