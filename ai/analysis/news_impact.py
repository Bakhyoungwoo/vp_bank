from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from analysis.llm import generate_news_impact_narrative
from analysis.metrics import price_metrics
from market.service import get_stock_detail, get_stock_news

# 헤드라인만으로 판단하는 1차 필터라 과도한 확신을 주지 않도록 최소한의
# 사건성 표현만 담는다. 본문 대신 제목만 있는 데이터 특성상, 이 목록은
# 최종 판정이 아니라 LLM 서술의 근거(evidence) 역할만 한다.
POSITIVE_KEYWORDS = [
    "흑자", "호실적", "최대 실적", "역대 최고", "급등", "상승", "수주", "신기록",
    "목표가 상향", "인수", "투자 유치", "배당 확대", "특허", "승인", "파트너십",
    "협력", "출시", "성장", "호조",
]
NEGATIVE_KEYWORDS = [
    "적자", "급락", "하락", "소송", "리콜", "제재", "조사", "감원", "구조조정",
    "파산", "부도", "결함", "사고", "벌금", "목표가 하향", "해임", "횡령", "배임",
    "유출",
]


def dedupe_articles(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """URL을 우선 키로, 없으면 제목으로 중복 기사를 제거한다."""
    seen: set[str] = set()
    unique: list[dict[str, Any]] = []
    for item in items:
        key = (item.get("url") or item.get("title") or "").strip()
        if not key or key in seen:
            continue
        seen.add(key)
        unique.append(item)
    return unique


def published_at(raw: Any) -> str | None:
    """yfinance/Yahoo 뉴스의 unix timestamp(초)를 ISO 문자열로 정규화한다."""
    if raw is None:
        return None
    try:
        return datetime.fromtimestamp(float(raw), tz=timezone.utc).isoformat()
    except (TypeError, ValueError, OSError):
        return raw if isinstance(raw, str) else None


def classify_headline(title: str | None) -> dict[str, Any]:
    """제목에 포함된 키워드만으로 긍정/부정/중립 후보를 판정한다.

    본문이 아닌 제목만 보고 내리는 1차 후보 분류이므로, 근거가 없으면
    항상 '중립/불확실'로 남기고 임의로 방향을 확정하지 않는다.
    """
    text = title or ""
    matched_positive = [kw for kw in POSITIVE_KEYWORDS if kw in text]
    matched_negative = [kw for kw in NEGATIVE_KEYWORDS if kw in text]
    if matched_positive and not matched_negative:
        label = "positive"
    elif matched_negative and not matched_positive:
        label = "negative"
    else:
        label = "neutral"
    return {
        "impactLabel": label,
        "matchedKeywords": {"positive": matched_positive, "negative": matched_negative},
    }


def _summarize(articles: list[dict[str, Any]]) -> dict[str, Any]:
    counts = {"positive": 0, "negative": 0, "neutral": 0}
    for article in articles:
        counts[article["impactLabel"]] += 1
    if not articles:
        overall = "insufficient_data"
    elif counts["positive"] > counts["negative"] and counts["positive"] > counts["neutral"]:
        overall = "positive"
    elif counts["negative"] > counts["positive"] and counts["negative"] > counts["neutral"]:
        overall = "negative"
    elif counts["positive"] == counts["negative"] and counts["positive"] > 0:
        overall = "mixed"
    else:
        overall = "neutral"
    return {
        "positiveCount": counts["positive"],
        "negativeCount": counts["negative"],
        "neutralCount": counts["neutral"],
        "overallLabel": overall,
    }


def analyze_news_impact(symbol: str, limit: int = 10, days: int = 30) -> dict[str, Any]:
    """종목 관련 뉴스 헤드라인을 수집해 키워드 기반 영향 후보를 계산하고,
    이미 계산된 결과만 LLM이 서술하게 한다. 원인을 확정하지 않는다."""
    symbol = symbol.strip().upper()
    news = get_stock_news(symbol, limit)
    detail = get_stock_detail(symbol, days)

    raw_items = dedupe_articles(news.get("items", []))
    articles = []
    for item in raw_items:
        classification = classify_headline(item.get("title"))
        articles.append({
            "title": item.get("title"),
            "publisher": item.get("publisher"),
            "url": item.get("url"),
            "publishedAt": published_at(item.get("publishedAt")),
            "relatedSymbols": item.get("relatedSymbols", []),
            **classification,
        })

    metrics = price_metrics(detail.get("history", []))
    summary = _summarize(articles)

    result = {
        "symbol": symbol,
        "asOf": datetime.now(timezone.utc).isoformat(),
        "status": "ready" if articles else "insufficient_data",
        "articles": articles,
        "summary": summary,
        "momentum": {
            "periodReturnPercent": metrics.get("periodReturnPercent"),
            "volumeRatio": metrics.get("volumeRatio"),
        } if metrics.get("available") else None,
        "riskFactors": [
            "뉴스 제목만으로 판단한 1차 후보 분류이며 본문 내용은 반영하지 않습니다.",
            "뉴스와 주가 변동 사이의 인과관계를 확정하지 않습니다.",
        ],
        "sources": [
            {"provider": news.get("provider", "yahoo-finance"), "type": "news"},
            {"provider": detail.get("provider", "openbb/yfinance"), "type": "market_data"},
        ],
        "llmNarrative": None,
    }
    if articles:
        result["llmNarrative"] = generate_news_impact_narrative(result)
    return result
