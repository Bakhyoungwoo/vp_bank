from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from market.cache import cached, enforce_rate_limit, rate_limited
from market.providers.base import ProviderUnavailableError
from market.providers.korea_investment_provider import KoreaInvestmentProvider
from market.providers.yfinance_provider import YFinanceProvider
from market.symbols import classify_symbol

_yfinance = YFinanceProvider()
_korea = KoreaInvestmentProvider()


def _envelope(result: dict[str, Any], provider: str, delayed: bool, symbol: str | None = None) -> dict[str, Any]:
    envelope = {
        **result,
        "provider": result.get("provider", provider),
        "asOf": datetime.now(timezone.utc).isoformat(),
        "delayed": delayed,
    }
    if symbol is not None:
        # Report back the symbol the caller asked for, not the
        # provider-specific form (e.g. "005930.KS") used internally.
        envelope["symbol"] = symbol.strip().upper()
    return envelope


def _yfinance_symbol(symbol: str) -> str:
    """yfinance/OpenBB only resolves Korean tickers with a .KS/.KQ suffix."""
    info = classify_symbol(symbol)
    return info["yahooSymbol"] if info["isKorea"] else symbol


def _resolve_history_provider(symbol: str) -> tuple[Any, bool]:
    """Use Korea Investment for KR symbols when configured; otherwise fall back to yfinance (marked delayed)."""
    info = classify_symbol(symbol)
    if info["isKorea"] and _korea.is_configured():
        return _korea, False
    return _yfinance, info["isKorea"]


@cached("overview", ttl_seconds=30)
@rate_limited("yfinance", max_calls=30, window_seconds=60)
def get_overview() -> dict[str, Any]:
    return _envelope(_yfinance.get_overview(), _yfinance.name, delayed=False)


@cached("history", ttl_seconds=300)
def get_history(symbol: str, days: int = 30) -> dict[str, Any]:
    provider, delayed = _resolve_history_provider(symbol)
    query_symbol = symbol if provider is _korea else _yfinance_symbol(symbol)
    enforce_rate_limit(provider.name, max_calls=30, window_seconds=60)
    try:
        result = provider.get_history(query_symbol, days)
    except ProviderUnavailableError:
        provider, delayed = _yfinance, True
        query_symbol = _yfinance_symbol(symbol)
        enforce_rate_limit(provider.name, max_calls=30, window_seconds=60)
        result = provider.get_history(query_symbol, days)
    return _envelope(result, provider.name, delayed, symbol=symbol)


@cached("search", ttl_seconds=60)
@rate_limited("yfinance", max_calls=30, window_seconds=60)
def search_stocks(query: str, limit: int = 10) -> dict[str, Any]:
    return _envelope(_yfinance.search(query, limit), _yfinance.name, delayed=False)


@cached("detail", ttl_seconds=60)
@rate_limited("yfinance", max_calls=30, window_seconds=60)
def get_stock_detail(symbol: str, days: int = 30) -> dict[str, Any]:
    # Quote/profile/financials are only wired for yfinance today; Korea
    # Investment is limited to get_history/get_quote until step 2/3 add a
    # standalone fundamentals source for KR names.
    info = classify_symbol(symbol)
    result = _yfinance.get_detail(_yfinance_symbol(symbol), days)
    return _envelope(result, _yfinance.name, delayed=info["isKorea"], symbol=symbol)


@cached("financials", ttl_seconds=3600)
@rate_limited("yfinance", max_calls=30, window_seconds=60)
def get_stock_financials(symbol: str, limit: int = 5) -> dict[str, Any]:
    info = classify_symbol(symbol)
    result = _yfinance.get_financials(_yfinance_symbol(symbol), limit)
    return _envelope(result, _yfinance.name, delayed=info["isKorea"], symbol=symbol)


@cached("balance", ttl_seconds=3600)
@rate_limited("yfinance", max_calls=30, window_seconds=60)
def get_stock_balance(symbol: str, limit: int = 4) -> dict[str, Any]:
    info = classify_symbol(symbol)
    result = _yfinance.get_balance(_yfinance_symbol(symbol), limit)
    return _envelope(result, _yfinance.name, delayed=info["isKorea"], symbol=symbol)


@cached("news", ttl_seconds=120)
@rate_limited("yfinance", max_calls=30, window_seconds=60)
def get_stock_news(symbol: str, limit: int = 10) -> dict[str, Any]:
    result = _yfinance.get_news(_yfinance_symbol(symbol), limit)
    return _envelope(result, _yfinance.name, delayed=False, symbol=symbol)
