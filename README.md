# 📰 VAP – AI 뉴스 트렌드 분석 & 추천 시스템

## 📚 프로젝트 소개
VAP는 실시간 뉴스 데이터를 수집·분석하여  
사용자 관심사 기반 뉴스 추천과  
AI 키워드 추출을 통한 뉴스 트렌드 분석을 제공하는 시스템입니다.

---

## 🔖 주요 기능
- JWT 기반 사용자 인증
- 사용자 관심사 기반 뉴스 추천
- 뉴스 클릭 이벤트 수집 및 분석
- AI 키워드 추출을 통한 트렌드 분석
- Redis + WebSocket 기반 실시간 키워드 제공

---

## ⚙️ 기술 스택

### 🧩 Backend / Infrastructure

| 구분 | 기술 |
|---|---|
| Backend | Spring Boot (Java 17) |
| Security | Spring Security + JWT |
| Database | MySQL 8.0 |
| Cache | Redis |
| Event Streaming | Apache Kafka |
| Realtime | WebSocket |
| Container | Docker / Docker Compose |

---

### 🧠 AI / NLP

| 구분 | 기술 |
|---|---|
| Keyword Extraction | KeyBERT |
| Embedding Model | Sentence-BERT (KR-SBERT, KLUE-NLI) |
| NLP Preprocessing | KoNLPy (Okt) |
| AI Server | FastAPI (Python) |

---

### 📰 데이터 수집

| 구분 | 기술 |
|---|---|
| Crawling | Python |
| Static Page | Requests / BeautifulSoup |
| Dynamic Page | Selenium |

---
## 🏛️ 시스템 아키텍처
<img width="1536" height="1024" alt="VAP System Architecture" src="https://github.com/user-attachments/assets/02038672-2a00-45bd-b6a2-4050549d5d7c" />

