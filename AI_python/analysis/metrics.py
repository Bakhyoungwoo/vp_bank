from __future__ import annotations

from math import sqrt
from statistics import mean
from typing import Any


def _number(value: Any) -> float | None:
    try:
        return None if value is None else float(value)
    except (TypeError, ValueError):
        return None


def _field(row: dict[str, Any], *names: str) -> float | None:
    lowered = {str(key).lower(): value for key, value in row.items()}
    for name in names:
        value = _number(lowered.get(name.lower()))
        if value is not None:
            return value
    return None


def price_metrics(history: list[dict[str, Any]]) -> dict[str, Any]:
    closes = [_field(row, "close", "adj_close") for row in history]
    closes = [value for value in closes if value is not None]
    volumes = [_field(row, "volume") for row in history]
    volumes = [value for value in volumes if value is not None]
    if not closes:
        return {"available": False, "evidence": []}

    first, last = closes[0], closes[-1]
    return_pct = ((last / first) - 1) * 100 if first else None
    previous = closes[-2] if len(closes) > 1 else None
    daily_pct = ((last / previous) - 1) * 100 if previous else None
    average_volume = mean(volumes[:-1]) if len(volumes) > 1 else None
    latest_volume = volumes[-1] if volumes else None
    volume_ratio = latest_volume / average_volume if average_volume else None
    returns = [((b / a) - 1) for a, b in zip(closes, closes[1:]) if a]
    volatility = (sqrt(mean([(item - mean(returns)) ** 2 for item in returns])) * 100
                  if len(returns) > 1 else None)
    return {
        "available": True,
        "latestPrice": last,
        "periodReturnPercent": return_pct,
        "dailyChangePercent": daily_pct,
        "averageVolume": average_volume,
        "latestVolume": latest_volume,
        "volumeRatio": volume_ratio,
        "volatilityPercent": volatility,
        "observations": len(closes),
        "evidence": ["OpenBB historical price data"],
    }


def score_momentum(metrics: dict[str, Any]) -> tuple[int | None, list[str]]:
    if not metrics.get("available"):
        return None, ["가격 데이터 없음"]
    score = 50
    evidence = []
    period_return = metrics.get("periodReturnPercent")
    volume_ratio = metrics.get("volumeRatio")
    if period_return is not None:
        score += 25 if period_return > 5 else -25 if period_return < -5 else 0
        evidence.append(f"최근 기간 수익률 {period_return:.2f}%")
    if volume_ratio is not None:
        score += 15 if volume_ratio > 1.5 else 0
        evidence.append(f"최근 거래량/평균 거래량 {volume_ratio:.2f}배")
    return max(0, min(100, score)), evidence


def normalize_financials(rows: list[dict[str, Any]]) -> dict[str, Any]:
    """Normalize common OpenBB/yfinance financial fields without inventing values."""
    normalized = []
    for row in rows:
        revenue = _field(row, "revenue", "total_revenue", "totalRevenue")
        operating_income = _field(row, "operating_income", "operatingIncome")
        net_income = _field(row, "net_income", "netIncome", "net_income_common_stockholders")
        eps = _field(row, "eps", "diluted_eps", "dilutedEPS", "basic_eps")
        normalized.append({
            "period": row.get("fiscal_period") or row.get("period") or row.get("date"),
            "revenue": revenue,
            "operatingIncome": operating_income,
            "netIncome": net_income,
            "eps": eps,
            "operatingMargin": operating_income / revenue if operating_income is not None and revenue else None,
        })
    usable = [item for item in normalized if any(value is not None for value in item.values())]
    latest = usable[0] if usable else {}
    previous = usable[1] if len(usable) > 1 else {}

    def growth(field: str) -> float | None:
        current, prior = latest.get(field), previous.get(field)
        return ((current / prior) - 1) * 100 if current is not None and prior not in (None, 0) else None

    return {
        "available": bool(usable),
        "latest": latest,
        "previous": previous,
        "growthPercent": {
            "revenue": growth("revenue"),
            "operatingIncome": growth("operatingIncome"),
            "netIncome": growth("netIncome"),
            "eps": growth("eps"),
        },
        "items": usable,
        "evidence": ["OpenBB fundamental income data"] if usable else [],
    }


def score_financials(financials: dict[str, Any]) -> dict[str, dict[str, Any]]:
    if not financials.get("available"):
        unavailable = {"score": None, "evidence": ["재무 데이터 없음"]}
        return {"growth": unavailable, "profitability": unavailable, "valuation": unavailable}

    growth = financials.get("growthPercent", {})
    growth_values = [value for value in growth.values() if value is not None]
    growth_score = round(max(0, min(100, 50 + mean(growth_values) / 2))) if growth_values else None
    margin = financials.get("latest", {}).get("operatingMargin")
    profitability_score = round(max(0, min(100, 50 + margin * 100))) if margin is not None else None
    return {
        "growth": {"score": growth_score, "evidence": [f"재무 성장률 {growth}"]},
        "profitability": {
            "score": profitability_score,
            "evidence": [f"최근 영업이익률 {margin:.2%}"] if margin is not None else ["영업이익률 없음"],
        },
        "valuation": {"score": None, "evidence": ["PER/PBR 데이터가 확인되지 않아 계산하지 않음"]},
    }
