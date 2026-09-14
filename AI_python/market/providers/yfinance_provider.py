from __future__ import annotations

from typing import Any

from market import openbb_provider


class YFinanceProvider:
    """Adapter exposing the existing OpenBB/yfinance functions behind the MarketDataProvider contract."""

    name = "yfinance"

    def is_configured(self) -> bool:
        return True  # No credentials required; this is the default/fallback provider.

    def get_overview(self) -> dict[str, Any]:
        return openbb_provider.get_overview()

    def get_history(self, symbol: str, days: int = 30) -> dict[str, Any]:
        return openbb_provider.get_history(symbol, days)

    def search(self, query: str, limit: int = 10) -> dict[str, Any]:
        return openbb_provider.search_stocks(query, limit)

    def get_detail(self, symbol: str, days: int = 30) -> dict[str, Any]:
        return openbb_provider.get_stock_detail(symbol, days)

    def get_financials(self, symbol: str, limit: int = 5) -> dict[str, Any]:
        return openbb_provider.get_stock_financials(symbol, limit)

    def get_news(self, symbol: str, limit: int = 10) -> dict[str, Any]:
        return openbb_provider.get_stock_news(symbol, limit)
