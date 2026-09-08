from fastapi import FastAPI, HTTPException
import asyncio
import json
import os
import requests
import redis
from datetime import datetime
from contextlib import asynccontextmanager
from apscheduler.schedulers.asyncio import AsyncIOScheduler

from crawler.naver_crawler import crawl_category, CATEGORIES
from market.routes import router as market_router, stocks_router

# ==================================================
# Spring API
# ==================================================
SPRING_HOST = os.getenv("SPRING_HOST", "localhost")
REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")

SPRING_NEWS_API = f"http://{SPRING_HOST}:8080/api/internal/news"

rd = redis.Redis(
    host=REDIS_HOST,
    port=6379,
    db=0,
    decode_responses=True,
    protocol=2
)
# ==================================================
# Paths
# ==================================================
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "Data", "raw", "naver")

# ==================================================
# Utils
# ==================================================
def normalize_time(raw_time):
    if not raw_time or raw_time in ["시간 미상", "", "null"]:
        return None
    try:
        return datetime.fromisoformat(raw_time).isoformat()
    except Exception:
        return None


def normalize_keywords(keywords):
    cleaned = []
    for item in keywords:
        if not isinstance(item, (list, tuple)) or len(item) != 2:
            continue
        word, score = item
        try:
            cleaned.append((str(word), float(score)))
        except Exception:
            continue
    return cleaned


def send_news_to_spring(article, category):
    # [수정] DB에도 키워드가 저장되어야 나중에 캐싱할 때 사용할 수 있음
    payload = {
        "category": category,
        "title": article.get("title"),
        "content": article.get("content"),
        "url": article.get("url"),
        "press": article.get("press"),
        "published_at": normalize_time(article.get("published_at")),
        "keywords": article.get("keywords")  # 🔥 [중요] 키워드 필드 추가
    }

    try:
        res = requests.post(SPRING_NEWS_API, json=payload, timeout=3)
        if res.status_code != 200:
            print("[SPRING ERROR]", res.status_code, res.text)
    except Exception as e:
        print("[SPRING CONNECT ERROR]", e)


# ==================================================
# Crawler Job
# ==================================================
def scheduled_crawl_all_categories():
    from AI.keyword_extractor import extract_keywords
    print("\n🔥 [CRAWLER] START")

    for category, code in CATEGORIES.items():
        category = category.lower()

        try:
            print(f"\n[CRAWLER] crawling {category}")

            # 1️⃣ 크롤링
            crawl_category(category, code, max_pages=1)

            file_path = os.path.join(DATA_DIR, f"{category}.json")
            if not os.path.exists(file_path):
                print("[SKIP] no file", file_path)
                continue

            with open(file_path, "r", encoding="utf-8") as f:
                articles = json.load(f)

            if not articles:
                print("[SKIP] no articles")
                continue

            # 2️⃣ Spring으로 뉴스 저장 (MySQL 저장)
            for article in articles:
                send_news_to_spring(article, category)

            # 3️⃣ [삭제됨] Redis 기사 캐시 (Cache-Aside 패턴 적용)
            # Python은 이제 Redis에 기사 본문을 저장하지 않습니다.
            # Java(Spring)가 조회 시점에 DB에서 가져와 Redis에 넣습니다.
            
            # 기존 기사 캐시 삭제 (혹시 남아있을 데이터 정리용)
            article_key = f"trend:{category}:articles"
            rd.delete(article_key) 
            
            print(f"[CACHE-ASIDE] Skipped Redis write for {category}. Data sent to DB.")


            # 4️⃣ 키워드 추출 & 점수 저장 (랭킹 시스템은 유지)
            # 기사 본문 캐시와 달리, '실시간 키워드 랭킹'은 Redis가 원본이므로 유지합니다.
            score_key = f"trend:{category}:scores"
            rd.delete(score_key)

            raw_keywords = extract_keywords(category)
            keywords = normalize_keywords(raw_keywords)

            print(f"[KEYWORDS] {category} -> {keywords}")

            # 점수 누적
            for word, score in keywords:
                rd.zincrby(score_key, int(score * 100), word)

            # 🔥 상위 10개만 남기기
            top_keywords = rd.zrevrange(score_key, 0, 9, withscores=True)

            rd.delete(score_key)
            for word, score in top_keywords:
                rd.zadd(score_key, {word: score})

            rd.expire(score_key, 86400)

            print(
                f"[REDIS SAVED] {category} scores ->",
                rd.zrange(score_key, 0, -1, withscores=True)
            )

        except Exception as e:
            print(f"[CRAWLER ERROR] {category}", e)

    print("\n🔥 [CRAWLER] END\n")


# ==================================================
# FastAPI Lifespan
# ==================================================
@asynccontextmanager
async def lifespan(app: FastAPI):
    scheduler = AsyncIOScheduler()

    # 🔥 서버 시작 시 1회 즉시 실행
    initial_crawl = asyncio.create_task(asyncio.to_thread(scheduled_crawl_all_categories))

    scheduler.add_job(
        scheduled_crawl_all_categories,
        trigger="interval",
        minutes=10,
        id="news_crawler"
    )

    scheduler.start()
    print("[SCHEDULER] started")

    yield

    if not initial_crawl.done():
        initial_crawl.cancel()
    scheduler.shutdown()
    print("[SCHEDULER] stopped")


# ==================================================
# App
# ==================================================
app = FastAPI(lifespan=lifespan)
app.include_router(market_router)
app.include_router(stocks_router)


@app.post("/crawl")
def crawl(category: str):
    """Run one category synchronously. Used by the before-Kafka comparison API."""
    category = category.strip().lower()
    if category not in CATEGORIES:
        raise HTTPException(status_code=400, detail=f"unsupported category: {category}")

    code = CATEGORIES[category]
    try:
        crawl_category(category, code, max_pages=1)
        file_path = os.path.join(DATA_DIR, f"{category}.json")
        if not os.path.exists(file_path):
            return {"category": category, "articles": 0, "status": "completed"}

        with open(file_path, "r", encoding="utf-8") as f:
            articles = json.load(f)
        for article in articles or []:
            send_news_to_spring(article, category)

        rd.delete(f"trend:{category}:articles")
        return {"category": category, "articles": len(articles or []), "status": "completed"}
    except Exception as exc:
        print(f"[CRAWL ERROR] {category}", exc)
        raise HTTPException(status_code=500, detail="crawl failed") from exc


if __name__ == "__main__":
    import uvicorn

    print("REDIS PING:", rd.ping())
    uvicorn.run(app, host="0.0.0.0", port=8000)
