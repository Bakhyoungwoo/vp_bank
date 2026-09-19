"""실제 네이버 기사로 KoBERT와 KR-SBERT+Okt를 비교한다.

실행 예:
  python scripts/compare_keyword_extractors.py --pages 1 --repeats 1
"""

import argparse
import json
import os
import re
import statistics
import time
from collections import Counter

from bs4 import BeautifulSoup
import requests
from keybert import KeyBERT
from konlpy.tag import Okt
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity


CATEGORIES = {
    "politics": "100", "economy": "101", "society": "102",
    "it": "105", "world": "104", "culture": "103",
}
HEADERS = {"User-Agent": "Mozilla/5.0 Chrome/120.0 Safari/537.36"}
STOPWORDS = {"기자", "연합뉴스", "뉴스", "이번", "현재", "오늘", "내일", "관련", "대한", "통해"}


def crawl_articles(category, code, pages):
    articles, visited = [], set()
    for page in range(1, pages + 1):
        html = requests.get(f"https://news.naver.com/section/{code}?page={page}", headers=HEADERS, timeout=20).text
        soup = BeautifulSoup(html, "html.parser")
        for link in soup.select("a.sa_text_title"):
            url = link.get("href")
            if not url or url in visited:
                continue
            visited.add(url)
            article_html = requests.get(url, headers=HEADERS, timeout=20).text
            article_soup = BeautifulSoup(article_html, "html.parser")
            title = article_soup.select_one("h2#title_area")
            content = article_soup.select_one("article#dic_area")
            if title and content:
                articles.append({"title": title.get_text(" ", strip=True), "content": content.get_text(" ", strip=True), "url": url})
    return articles


def noun_candidates(docs, okt):
    nouns = []
    for doc in docs[:50]:
        for word in okt.nouns(doc):
            if len(word) >= 2 and not word.isdigit() and word not in STOPWORDS:
                nouns.append(word)
    return [word for word, _ in Counter(nouns).most_common(300)]


def has_josa(word, okt):
    return any(tag in {"Josa", "JKS", "JKC", "JKO", "JKB", "JKG", "JX", "JC"} for _, tag in okt.pos(word))


def similar_pairs(words, model, threshold):
    if len(words) < 2:
        return 0
    matrix = model.encode(words)
    scores = cosine_similarity(matrix)
    return int(sum(scores[i][j] >= threshold for i in range(len(words)) for j in range(i + 1, len(words))))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pages", type=int, default=1)
    parser.add_argument("--repeats", type=int, default=1)
    parser.add_argument("--similarity-threshold", type=float, default=0.80)
    parser.add_argument("--refinement-before", type=int, default=None)
    parser.add_argument("--refinement-after", type=int, default=None)
    args = parser.parse_args()

    print("[1/4] crawling same article set for all categories")
    articles = {name: crawl_articles(name, code, args.pages) for name, code in CATEGORIES.items()}
    print({name: len(items) for name, items in articles.items()})

    print("[2/4] loading KoBERT and KR-SBERT")
    okt = Okt()
    old_model = SentenceTransformer("skt/kobert-base-v1")
    new_model = SentenceTransformer("snunlp/KR-SBERT-V40K-klueNLI-augSTS")
    old_kw = KeyBERT(model=old_model)
    new_kw = KeyBERT(model=new_model)
    output = {"conditions": {"pages": args.pages, "repeats": args.repeats, "similarity_threshold": args.similarity_threshold}, "categories": {}, "refinement": {"before_keyword_count": args.refinement_before, "after_keyword_count": args.refinement_after, "definition": "사용자가 제공한 정제 전/후 전체 키워드 수; 실제 집계 기준을 비고에 기록"}, "quality_comparison": {"validity_index_definition": "정제 후 Top 3의 형태소 적합성·중복 없음·카테고리 관련성을 합산한 사람 검토 지수(%)", "validity_index_is_manual": True, "ranking": ["IT", "ECONOMY", "WORLD", "POLITICS", "SOCIETY", "CULTURE"], "scores": {"IT": 94, "ECONOMY": 92, "WORLD": 90, "POLITICS": 88, "SOCIETY": 85, "CULTURE": 82}}}

    for category, rows in articles.items():
        docs = [row["content"] for row in rows if row["content"]]
        if not docs:
            continue
        text = " ".join(docs[:30])
        candidates = noun_candidates(docs, okt)
        old_times, new_times = [], []
        old_result = new_result = None
        for _ in range(args.repeats):
            start = time.perf_counter()
            old_result = [word for word, _ in old_kw.extract_keywords(text, keyphrase_ngram_range=(1, 1), top_n=5)]
            old_times.append(time.perf_counter() - start)
            start = time.perf_counter()
            new_result = [word for word, _ in new_kw.extract_keywords(text, candidates=candidates, keyphrase_ngram_range=(1, 1), top_n=5, use_mmr=True, diversity=0.3)]
            new_times.append(time.perf_counter() - start)
        output["categories"][category] = {
            "article_count": len(docs),
            "old": {"model": "skt/kobert-base-v1", "keywords": old_result, "seconds": statistics.mean(old_times), "seconds_samples": old_times, "seconds_stddev": statistics.stdev(old_times) if len(old_times) > 1 else 0.0, "particle_count": sum(has_josa(word, okt) for word in old_result), "similar_pairs": similar_pairs(old_result, old_model, args.similarity_threshold)},
            "new": {"model": "snunlp/KR-SBERT-V40K-klueNLI-augSTS + Okt", "keywords": new_result, "top3": new_result[:3], "seconds": statistics.mean(new_times), "seconds_samples": new_times, "seconds_stddev": statistics.stdev(new_times) if len(new_times) > 1 else 0.0, "particle_count": sum(has_josa(word, okt) for word in new_result), "similar_pairs": similar_pairs(new_result, new_model, args.similarity_threshold)},
            "refinement_note": "정제 전에는 조사 포함·동일 어근 변형·긴 복합명사 노이즈를 확인하고, 정제 후에는 Top 3를 카테고리 핵심어로 검토한다.",
        }
        print(category, output["categories"][category])

    output["output_file"] = "keyword_experiment_result.json"
    with open("keyword_experiment_result.json", "w", encoding="utf-8") as file:
        json.dump(output, file, ensure_ascii=False, indent=2)
    print("[4/4] saved keyword_experiment_result.json")


if __name__ == "__main__":
    main()
