from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from analysis.llm import generate_briefing_narrative
from analysis.metrics import price_metrics
from analysis.news_impact import classify_headline, dedupe_articles, published_at
from market.service import get_stock_detail, get_stock_news

MAX_SYMBOLS = 20
NEWS_LIMIT_PER_SYMBOL = 5


class InvalidBriefingError(ValueError):
    """요청한 관심종목 목록이 검증을 통과하지 못했을 때 발생한다."""


def _validate_symbols(symbols: list[str]) -> list[str]:
    cleaned = [s.strip().upper() for s in symbols if s and s.strip()]
    seen: set[str] = set()
    unique = []
    for symbol in cleaned:
        if symbol not in seen:  # 입력 순서를 유지하며 중복만 제거한다.
            seen.add(symbol)
            unique.append(symbol)
    if not unique:
        raise InvalidBriefingError("관심종목이 없습니다.")
    if len(unique) > MAX_SYMBOLS:
        raise InvalidBriefingError(f"관심종목은 최대 {MAX_SYMBOLS}개까지 지원합니다.")
    return unique


def _build_symbol_item(symbol: str, days: int) -> dict[str, Any]:
    detail = get_stock_detail(symbol, days)
    metrics = price_metrics(detail.get("history", []))

    news = get_stock_news(symbol, NEWS_LIMIT_PER_SYMBOL)
    raw_items = dedupe_articles(news.get("items", []))
    notable_news = [
        {
            "title": item.get("title"),
            "publisher": item.get("publisher"),
            "url": item.get("url"),
            "publishedAt": published_at(item.get("publishedAt")),
            **classify_headline(item.get("title")),
        }
        for item in raw_items
    ]

    return {
        "symbol": symbol,
        "status": "ready" if metrics.get("available") else "insufficient_data",
        "periodReturnPercent": metrics.get("periodReturnPercent"),
        "volumeRatio": metrics.get("volumeRatio"),
        "notableNews": notable_news,
    }


def build_briefing(symbols: list[str], days: int = 7) -> dict[str, Any]:
    """관심종목별 가격·거래량·뉴스 변화를 수집해, 결정론적 수치만으로 브리핑을
    구성하고 마지막에 LLM 서술을 덧붙인다. 마지막 브리핑 시점 이후 경과일은
    호출자(Spring Backend)가 계산해 `days`로 전달한다."""
    cleaned = _validate_symbols(symbols)

    items = [_build_symbol_item(symbol, days) for symbol in cleaned]
    # 변동폭이 큰 종목을 먼저 보여준다.
    items.sort(key=lambda item: abs(item.get("periodReturnPercent") or 0), reverse=True)

    result: dict[str, Any] = {
        "asOf": datetime.now(timezone.utc).isoformat(),
        "sinceDays": days,
        "items": items,
        "riskFactors": [
            "브리핑은 관측된 가격·거래량·뉴스 헤드라인 변화 요약이며 투자 판단 근거가 아닙니다.",
        ],
        "sources": [
            {"provider": "openbb/yfinance", "type": "market_data"},
            {"provider": "yahoo-finance", "type": "news"},
        ],
        "llmNarrative": None,
    }
    result["status"] = "ready" if any(item["status"] == "ready" for item in items) else "insufficient_data"
    if result["status"] == "ready":
        result["llmNarrative"] = generate_briefing_narrative(result)
    return result
