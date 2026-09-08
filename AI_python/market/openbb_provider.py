from __future__ import annotations

from datetime import date, timedelta
from functools import lru_cache
from typing import Any


MARKET_TARGETS = (
    {"code": "KOSPI", "name": "코스피", "symbol": "^KS11", "unit": "지수"},
    {"code": "KOSDAQ", "name": "코스닥", "symbol": "^KQ11", "unit": "지수"},
    {"code": "KOSPI200", "name": "코스피200", "symbol": "^KS200", "unit": "지수"},
    {"code": "NASDAQ", "name": "나스닥", "symbol": "^IXIC", "unit": "지수"},
    {"code": "SP500", "name": "S&P500", "symbol": "^GSPC", "unit": "지수"},
    {"code": "USD/KRW", "name": "원/달러", "symbol": "USDKRW", "unit": "환율", "kind": "currency"},
)


@lru_cache(maxsize=1)
def _obb():
    """Import OpenBB only when a market request is made."""
    from openbb import obb

    return obb


def _records(result: Any) -> list[dict[str, Any]]:
    frame = result.to_df()
    if frame is None or frame.empty:
        return []
    frame = frame.reset_index()
    frame = frame.where(frame.notna(), None)
    return frame.to_dict(orient="records")


def _history(symbol: str, start: str, end: str, interval: str = "1d") -> list[dict[str, Any]]:
    obb = _obb()
    result = obb.currency.price.historical(
        symbol=symbol, start_date=start, end_date=end,
        interval=interval, provider="yfinance",
    ) if symbol == "USDKRW" else obb.index.price.historical(
        symbol=symbol, start_date=start, end_date=end,
        interval=interval, provider="yfinance",
    ) if symbol.startswith("^") else obb.equity.price.historical(
        symbol=symbol, start_date=start, end_date=end,
        interval=interval, provider="yfinance",
    )
    return _records(result)


def _target_history(target: dict[str, str]) -> list[dict[str, Any]]:
    end = date.today()
    start = end - timedelta(days=7)
    # yfinance supports intraday data only for a recent window. Fall back to
    # daily data when the market is closed or the provider returns no rows.
    rows = _history(target["symbol"], start.isoformat(), end.isoformat(), "1m")
    if not rows:
        rows = _history(target["symbol"], (end - timedelta(days=30)).isoformat(), end.isoformat())
    return rows


def get_overview() -> dict[str, Any]:
    items = []
    for target in MARKET_TARGETS:
        try:
            rows = _target_history(target)
            latest = rows[-1] if rows else {}
            previous = rows[-2] if len(rows) > 1 else latest
            price = latest.get("close")
            previous_close = previous.get("close")
            if price is None:
                raise ValueError("가격 데이터가 없습니다.")
            change = price - previous_close if previous_close is not None else 0
            items.append({
                **{key: target[key] for key in ("code", "name", "unit")},
                "symbol": target["symbol"],
                "price": price,
                "change": change,
                "changePercent": (change / previous_close * 100) if previous_close else 0,
                "marketTime": latest.get("date"),
                "provider": "yfinance",
                "available": True,
            })
        except Exception as exc:
            items.append({
                **{key: target[key] for key in ("code", "name", "unit")},
                "symbol": target["symbol"],
                "available": False,
                "provider": "yfinance",
                "error": str(exc),
            })
    return {"items": items, "provider": "openbb/yfinance", "updatedAt": date.today().isoformat()}


def get_history(symbol: str, days: int = 30) -> dict[str, Any]:
    if not 1 <= days <= 3650:
        raise ValueError("days는 1에서 3650 사이여야 합니다.")
    end = date.today()
    rows = _history(symbol.upper(), (end - timedelta(days=days)).isoformat(), end.isoformat())
    return {"symbol": symbol.upper(), "provider": "openbb/yfinance", "items": rows}
