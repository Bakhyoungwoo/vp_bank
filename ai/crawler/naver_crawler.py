import requests
from bs4 import BeautifulSoup
import json
import time
import os
import re
from collections import Counter

# 네이버 뉴스 카테고리 페이지 기본 URL
BASE_URL = "https://news.naver.com/section"

# 네이버 뉴스 카테고리 코드
CATEGORIES = {
    "politics": "100",
    "economy": "101",
    "society": "102",
    "it": "105",
    "world": "104",
    "culture": "103"
}

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    )
}

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SAVE_DIR = os.path.join(BASE_DIR, "..", "Data", "raw", "naver")
os.makedirs(SAVE_DIR, exist_ok=True)


def extract_keywords(title, content, top_n=5):
    text = f"{title} {content}"
    words = re.findall(r"[가-힣A-Z]{2,}", text)

    stopwords = {
        "뉴스", "기자", "기사", "연합뉴스", "무단", "전재", "배포", "금지",
        "오전", "오후", "이번", "오늘", "때문", "대한"
    }

    filtered = [w for w in words if w not in stopwords]
    counts = Counter(filtered)

    return [word for word, _ in counts.most_common(top_n)]


def crawl_category(name, code, max_pages=1):
    articles = []
    visited = set()

    for page in range(1, max_pages + 1):
        url = f"{BASE_URL}/{code}?page={page}"
        res = requests.get(url, headers=HEADERS)
        res.encoding = "utf-8"
        soup = BeautifulSoup(res.text, "html.parser")

        links = soup.select("a.sa_text_title")
        print(f"  → [{name}] page {page}, found links: {len(links)}")

        for link in links:
            article_url = link.get("href")
            if not article_url or article_url in visited:
                continue

            visited.add(article_url)
            article = crawl_article(article_url)

            if article:
                articles.append(article)

        time.sleep(0.5)

    save_path = os.path.join(SAVE_DIR, f"{name}.json")
    with open(save_path, "w", encoding="utf-8") as f:
        json.dump(articles, f, ensure_ascii=False, indent=2)

    print(f" ✅ Saved {len(articles)} articles → {save_path}")


def crawl_article(url):
    try:
        res = requests.get(url, headers=HEADERS)
        res.encoding = "utf-8"
        soup = BeautifulSoup(res.text, "html.parser")

        title_tag = soup.select_one("h2#title_area")
        content_tag = soup.select_one("article#dic_area")
        press_tag = soup.select_one("a.media_end_head_top_logo img")
        time_tag = soup.select_one("span.media_end_head_info_datestamp_time")

        if not title_tag or not content_tag:
            return None

        title = title_tag.get_text(strip=True)
        content = content_tag.get_text(" ", strip=True)
        press = press_tag.get("alt") if press_tag else "언론사 미상"

        # ✅ 정확한 시간 추출 (data-date-time)
        publish_time = (
            time_tag["data-date-time"]
            if time_tag and time_tag.has_attr("data-date-time")
            else "시간 미상"
        )

        keywords = extract_keywords(title, content)

        return {
            "title": title,
            "content": content,
            "url": url,
            "press": press,
            "published_at": publish_time,
            "keywords": keywords
        }

    except Exception as e:
        print(f" ❌ Error crawling {url}: {e}")
        return None


if __name__ == "__main__":
    for name, code in CATEGORIES.items():
        print(f" 📰 Crawling {name}...")
        crawl_category(name, code)
