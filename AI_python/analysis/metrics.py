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
