# AI 뉴스 트렌드 분석 & 추천 시스템

## 프로젝트 소개
VAP는 실시간 뉴스 데이터를 수집·분석하여  
사용자 관심사 기반 뉴스 추천과  
AI 키워드 추출을 통한 뉴스 트렌드 분석을 제공하는 시스템입니다.

---

## 주요 기능
- JWT 기반 사용자 인증
- 사용자 관심사 기반 뉴스 추천
- 뉴스 클릭 이벤트 수집 및 분석
- AI 키워드 추출을 통한 트렌드 분석
- Redis + WebSocket 기반 실시간 키워드 제공

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
- **Redis 7** – In-memory data store for caching and fast access

### Infrastructure & DevOps
- **Apache Kafka** – Asynchronous event streaming and message processing
- **Zookeeper** – Kafka cluster coordination and metadata management

- **Docker (Compose)** – Containerization of application and infrastructure and Multi-container orchestration for local development
- **GitHub Actions** – CI pipeline for build and Docker image automation

---

### 데이터 수집

| 구분 | 기술 |
|---|---|
| Crawling | Python |
| Static Page | Requests / BeautifulSoup |
| Dynamic Page | Selenium |

---

## 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    Web Client (HTML/JS)                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   News Feed UI  │  │   Login/Auth    │  │  Click Logs  │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP (REST API :8080)
┌─────────────────────────▼───────────────────────────────────┐
│                  Spring Boot Main Server                    │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   User/News     │  │   Kafka Prod.   │  │   Kafka Cons.│ │
│  │   Service       │  │   (Event Pub)   │  │   (Log Save) │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────┬───────────────────────┬───────────────────────────┘
          │ JPA / Redis Ops       │ Async Events (Topics)
┌─────────▼───────────────────────▼───────────────────────────┐
│                   Infrastructure Layer                      │
│  ┌──────────────┐   ┌──────────────┐    ┌────────────────┐  │
│  │    MySQL     │   │    Redis     │    │  Apache Kafka  │  │
│  │ (User/News)  │   │ (Cache/Rank) │◀──▶│ (Msg Broker)   │  │
│  └──────────────┘   └──────────────┘    └────────┬───────┘  │
└──────────────────────────────────────────────────│──────────┘
                                                   │ Crawl Req
                                     ┌─────────────▼──────────┐
                                     │  FastAPI AI Server     │
                                     │ ┌─────────┐ ┌────────┐ │
                                     │ │ Crawler │ │KeyBERT │ │
                                     │ └─────────┘ └────────┘ │
                                     └────────────────────────┘


