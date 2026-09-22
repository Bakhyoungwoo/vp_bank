from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from analysis.llm import generate_price_move_narrative
from analysis.metrics import price_metrics
from analysis.news_impact import classify_headline, dedupe_articles, published_at
from market.service import get_overview, get_stock_detail, get_stock_news
from market.symbols import classify_symbol

# 감지 조건(개발계획 7단계): 전일 대비 일정 비율 이상 변동, 또는 거래량 급증.
DAILY_CHANGE_THRESHOLD_PERCENT = 5.0
VOLUME_RATIO_THRESHOLD = 2.0
# 위 조건에는 못 미치지만 원인 후보로는 참고할 만한 거래량 급증 기준.
VOLUME_CANDIDATE_THRESHOLD = 1.5
MARKET_INDEX_CODE_BY_MARKET = {"KOSPI": "KOSPI", "KOSDAQ": "KOSDAQ", "OVERSEAS": "SP500"}


def _is_significant_move(metrics: dict[str, Any]) -> bool:
    if not metrics.get("available"):
        return False
    daily_change = metrics.get("dailyChangePercent")
    volume_ratio = metrics.get("volumeRatio")
    return (
        (daily_change is not None and abs(daily_change) >= DAILY_CHANGE_THRESHOLD_PERCENT)
        or (volume_ratio is not None and volume_ratio >= VOLUME_RATIO_THRESHOLD)
    )


def _market_index_change(symbol: str) -> dict[str, Any] | None:
    """종목이 속한 시장(KOSPI/KOSDAQ/해외)의 대표 지수 변동률을 조회한다."""
    info = classify_symbol(symbol)
    code = MARKET_INDEX_CODE_BY_MARKET.get(info["market"])
    if not code:
        return None
    overview = get_overview()
    for item in overview.get("items", []):
        if item.get("code") == code and item.get("available"):
            return {"code": code, "name": item.get("name"), "changePercent": item.get("changePercent")}
    return None


def _volume_spike_candidate(metrics: dict[str, Any]) -> dict[str, Any] | None:
    ratio = metrics.get("volumeRatio")
    if ratio is None or ratio < VOLUME_CANDIDATE_THRESHOLD:
        return None
    score = 80 if ratio >= 3 else 60 if ratio >= 2 else 40
    return {
        "type": "volume_spike",
        "label": "거래량 급증",
        "score": score,
        "evidence": [f"최근 거래량이 평균 대비 {ratio:.2f}배"],
    }


def _market_wide_candidate(
    daily_change: float | None, index: dict[str, Any] | None
) -> dict[str, Any] | None:
    """종목 변동과 같은 방향의 유의미한 시장 지수 변동이 있는지 확인한다."""
    if daily_change is None or index is None:
        return None
    index_change = index.get("changePercent")
    if index_change is None or abs(index_change) < 0.5:
        return None
    if (daily_change > 0) != (index_change > 0):
        return None
    ratio = min(1.0, abs(index_change) / abs(daily_change)) if daily_change else 0.0
    score = round(30 + ratio * 50)
    return {
        "type": "market_wide_move",
        "label": "시장 전반 동반 변동 가능성",
        "score": score,
        "evidence": [f"{index.get('name')} 지수 {index_change:+.2f}% (종목 {daily_change:+.2f}%와 같은 방향)"],
    }


def _news_driven_candidate(
    daily_change: float | None, articles: list[dict[str, Any]]
) -> dict[str, Any] | None:
    """가격 변동 방향과 일치하는 뉴스 헤드라인이 있는지 확인한다."""
    if daily_change is None or not articles:
        return None
    direction = "positive" if daily_change > 0 else "negative"
    matched = [article for article in articles if article["impactLabel"] == direction]
    if not matched:
        return None
    score = min(80, 30 + len(matched) * 15)
    return {
        "type": "news_driven",
        "label": "뉴스 방향과 일치",
        "score": score,
        "evidence": [f"'{article['title']}'" for article in matched[:3]],
        "articles": matched,
    }


def analyze_price_move(symbol: str, days: int = 30, limit: int = 10) -> dict[str, Any]:
    """전일 대비 급변·거래량 급증을 감지하고, 시장 지수·뉴스를 근거로 원인 후보를
    점수화한다. 근거가 부족하면 원인을 확정하지 않고 그대로 남긴다."""
    symbol = symbol.strip().upper()
    detail = get_stock_detail(symbol, days)
    metrics = price_metrics(detail.get("history", []))

    result: dict[str, Any] = {
        "symbol": symbol,
        "asOf": datetime.now(timezone.utc).isoformat(),
        "status": "insufficient_data",
        "detection": {
            "dailyChangePercent": metrics.get("dailyChangePercent"),
            "volumeRatio": metrics.get("volumeRatio"),
            "volatilityPercent": metrics.get("volatilityPercent"),
            "thresholds": {
                "dailyChangePercent": DAILY_CHANGE_THRESHOLD_PERCENT,
                "volumeRatio": VOLUME_RATIO_THRESHOLD,
            },
        },
        "marketComparison": None,
        "candidates": [],
        "riskFactors": [
            "급등락 감지·후보 분석은 관측된 가격·거래량·뉴스 헤드라인에 한정됩니다.",
            "후보는 가능성이 있는 요인이며, 원인을 확정하지 않습니다.",
        ],
        "sources": [{"provider": detail.get("provider", "openbb/yfinance"), "type": "market_data"}],
        "llmNarrative": None,
    }

    if not metrics.get("available"):
        return result

    if not _is_significant_move(metrics):
        result["status"] = "no_significant_move"
        return result

    result["status"] = "ready"
    daily_change = metrics.get("dailyChangePercent")

    index = _market_index_change(symbol)
    if index:
        result["marketComparison"] = index
        result["sources"].append({"provider": "openbb/yfinance", "type": "market_index"})

    news = get_stock_news(symbol, limit)
    raw_items = dedupe_articles(news.get("items", []))
    articles = [
        {
            "title": item.get("title"),
            "publisher": item.get("publisher"),
            "url": item.get("url"),
            "publishedAt": published_at(item.get("publishedAt")),
            **classify_headline(item.get("title")),
        }
        for item in raw_items
    ]
    if articles:
        result["sources"].append({"provider": news.get("provider", "yahoo-finance"), "type": "news"})

    candidates = [
        _volume_spike_candidate(metrics),
        _market_wide_candidate(daily_change, index),
        _news_driven_candidate(daily_change, articles),
    ]
    result["candidates"] = sorted(
        (candidate for candidate in candidates if candidate),
        key=lambda candidate: candidate["score"],
        reverse=True,
    )
    if not result["candidates"]:
        result["riskFactors"].append("확인 가능한 직접 원인 없음")

    result["llmNarrative"] = generate_price_move_narrative(result)
    return result
