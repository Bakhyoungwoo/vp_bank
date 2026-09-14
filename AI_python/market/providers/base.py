from __future__ import annotations

from typing import Any, Protocol


class MarketDataProvider(Protocol):
    """Common contract every market data adapter (OpenBB/yfinance, Korea Investment, ...) must satisfy."""

    name: str

    def is_configured(self) -> bool: ...

    def get_overview(self) -> dict[str, Any]: ...

    def get_history(self, symbol: str, days: int) -> dict[str, Any]: ...

    def search(self, query: str, limit: int) -> dict[str, Any]: ...

    def get_detail(self, symbol: str, days: int) -> dict[str, Any]: ...

    def get_financials(self, symbol: str, limit: int) -> dict[str, Any]: ...

    def get_news(self, symbol: str, limit: int) -> dict[str, Any]: ...


class ProviderUnavailableError(RuntimeError):
    """Raised when a provider cannot serve a request (missing credentials or upstream failure)."""
