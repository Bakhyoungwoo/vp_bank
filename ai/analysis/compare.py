from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from analysis.llm import generate_comparison_narrative
from analysis.metrics import compute_valuation_ratios, normalize_balance, normalize_financials, price_metrics
from market.service import get_stock_balance, get_stock_detail

MIN_SYMBOLS = 2
MAX_SYMBOLS = 5


class InvalidComparisonError(ValueError):
    """요청한 종목 목록이 검증을 통과하지 못했을 때 발생한다."""


def _validate_symbols(symbols: list[str]) -> list[str]:
    cleaned = [s.strip().upper() for s in symbols if s and s.strip()]
    seen: set[str] = set()
    unique = []
    for symbol in cleaned:
        if symbol not in seen:  # 입력 순서를 유지하며 중복만 제거한다.
            seen.add(symbol)
            unique.append(symbol)
    if not (MIN_SYMBOLS <= len(unique) <= MAX_SYMBOLS):
        raise InvalidComparisonError(f"종목은 {MIN_SYMBOLS}~{MAX_SYMBOLS}개를 입력해야 합니다.")
    return unique


def _build_row(symbol: str, days: int) -> dict[str, Any]:
    detail = get_stock_detail(symbol, days)
    balance = get_stock_balance(symbol, limit=1)

    metrics = price_metrics(detail.get("history", []))
    financials = normalize_financials(detail.get("financials", []))
    balance_norm = normalize_balance(balance.get("items", []))

    profile = detail.get("profile") or {}
    quote = detail.get("quote") or {}
    latest_financials = financials.get("latest", {})
    market_cap = profile.get("market_cap")
    net_income = latest_financials.get("netIncome")
    ratios = compute_valuation_ratios(market_cap, net_income, balance_norm.get("latest", {}))
    operating_margin = latest_financials.get("operatingMargin")

    return {
        "symbol": detail.get("symbol", symbol),
        "name": profile.get("name") or quote.get("name") or symbol,
        "sector": profile.get("sector"),
        "available": bool(metrics.get("available")),
        "price": metrics.get("latestPrice"),
        "currency": quote.get("currency"),
        "periodReturnPercent": metrics.get("periodReturnPercent"),
        "volatilityPercent": metrics.get("volatilityPercent"),
        "revenueGrowthPercent": financials.get("growthPercent", {}).get("revenue"),
        "operatingMarginPercent": operating_margin * 100 if operating_margin is not None else None,
        "eps": latest_financials.get("eps"),
        "per": ratios["per"],
        "pbr": ratios["pbr"],
        "roe": ratios["roe"],
        "debtRatio": ratios["debtRatio"],
        "evidence": metrics.get("evidence", []) + financials.get("evidence", []) + balance_norm.get("evidence", []),
    }


def compare_stocks(symbols: list[str], days: int = 90) -> dict[str, Any]:
    unique_symbols = _validate_symbols(symbols)
    items = [_build_row(symbol, days) for symbol in unique_symbols]

    sectors = [item.get("sector") for item in items]
    same_sector = bool(sectors) and all(s is not None and s == sectors[0] for s in sectors)

    result = {
        "symbols": unique_symbols,
        "asOf": datetime.now(timezone.utc).isoformat(),
        "sameSector": same_sector,
        "items": items,
        "sources": [{"provider": "openbb/yfinance", "type": "market_data"}],
        "llmNarrative": None,
    }
    # LLM은 위 표에서 이미 계산한 지표만 서술한다. 하나라도 데이터가 있을 때만 호출한다.
    if any(item["available"] for item in items):
        result["llmNarrative"] = generate_comparison_narrative(result)
    return result
