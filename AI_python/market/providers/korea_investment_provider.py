from __future__ import annotations

import os
import time
from datetime import date, datetime, timedelta
from typing import Any

import requests

from market.providers.base import ProviderUnavailableError

# NOTE: this adapter follows the public Korea Investment & Securities (KIS)
# Open API spec (oauth2/tokenP + domestic-stock quotations). It has not been
# exercised against a live account because no APP_KEY/APP_SECRET are
# available in this environment yet. Set KIS_APP_KEY/KIS_APP_SECRET (and
# optionally KIS_BASE_URL for the paper-trading host) and verify each
# endpoint against a real account before relying on it in production.
DEFAULT_BASE_URL = "https://openapi.koreainvestment.com:9443"
TOKEN_PATH = "/oauth2/tokenP"
PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price"
DAILY_CHART_PATH = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"


class KoreaInvestmentProvider:
    """Real-time domestic (KOSPI/KOSDAQ) quote/history adapter for the KIS Open API."""

    name = "korea-investment"

    def __init__(self) -> None:
        self._base_url = os.getenv("KIS_BASE_URL", DEFAULT_BASE_URL)
        self._app_key = os.getenv("KIS_APP_KEY", "")
        self._app_secret = os.getenv("KIS_APP_SECRET", "")
        self._token: str | None = None
        self._token_expires_at: float = 0.0

    def is_configured(self) -> bool:
        return bool(self._app_key and self._app_secret)

    def _require_configured(self) -> None:
        if not self.is_configured():
            raise ProviderUnavailableError("KIS_APP_KEY/KIS_APP_SECRET이 설정되지 않았습니다.")

    def _access_token(self) -> str:
        """Fetch (and cache in-process) the OAuth token; KIS tokens are valid for ~24h."""
        if self._token and time.monotonic() < self._token_expires_at:
            return self._token
        response = requests.post(
            f"{self._base_url}{TOKEN_PATH}",
            json={
                "grant_type": "client_credentials",
                "appkey": self._app_key,
                "appsecret": self._app_secret,
            },
            timeout=10,
        )
        response.raise_for_status()
        payload = response.json()
        token = payload.get("access_token")
        if not token:
            raise ProviderUnavailableError("KIS 인증 토큰을 발급받지 못했습니다.")
        self._token = token
        self._token_expires_at = time.monotonic() + int(payload.get("expires_in", 86400)) - 60
        return token

    def _headers(self, tr_id: str) -> dict[str, str]:
        return {
            "content-type": "application/json; charset=utf-8",
            "authorization": f"Bearer {self._access_token()}",
            "appkey": self._app_key,
            "appsecret": self._app_secret,
            "tr_id": tr_id,
            "custtype": "P",
        }

    def get_quote(self, code: str) -> dict[str, Any]:
        """Domestic current-price quote (tr_id FHKST01010100)."""
        self._require_configured()
        try:
            response = requests.get(
                f"{self._base_url}{PRICE_PATH}",
                headers=self._headers("FHKST01010100"),
                params={"FID_COND_MRKT_DIV_CODE": "J", "FID_INPUT_ISCD": code},
                timeout=10,
            )
            response.raise_for_status()
            output = response.json().get("output", {})
        except requests.RequestException as exc:
            raise ProviderUnavailableError(f"KIS 시세 조회 실패: {exc}") from exc
        price = output.get("stck_prpr")
        return {
            "code": code,
            "price": float(price) if price not in (None, "") else None,
            "change": float(output.get("prdy_vrss") or 0),
            "changePercent": float(output.get("prdy_ctrt") or 0),
            "volume": int(output.get("acml_vol") or 0),
            "marketTime": datetime.now().isoformat(),
        }

    def get_history(self, symbol: str, days: int = 30) -> dict[str, Any]:
        """Domestic daily OHLCV chart (tr_id FHKST03010100)."""
        self._require_configured()
        code = symbol.strip().upper().split(".")[0]
        end = date.today()
        start = end - timedelta(days=days)
        try:
            response = requests.get(
                f"{self._base_url}{DAILY_CHART_PATH}",
                headers=self._headers("FHKST03010100"),
                params={
                    "FID_COND_MRKT_DIV_CODE": "J",
                    "FID_INPUT_ISCD": code,
                    "FID_INPUT_DATE_1": start.strftime("%Y%m%d"),
                    "FID_INPUT_DATE_2": end.strftime("%Y%m%d"),
                    "FID_PERIOD_DIV_CODE": "D",
                    "FID_ORG_ADJ_PRC": "0",
                },
                timeout=10,
            )
            response.raise_for_status()
            rows = response.json().get("output2", [])
        except requests.RequestException as exc:
            raise ProviderUnavailableError(f"KIS 차트 조회 실패: {exc}") from exc
        items = [
            {
                "date": row.get("stck_bsop_date"),
                "open": float(row["stck_oprc"]) if row.get("stck_oprc") else None,
                "high": float(row["stck_hgpr"]) if row.get("stck_hgpr") else None,
                "low": float(row["stck_lwpr"]) if row.get("stck_lwpr") else None,
                "close": float(row["stck_clpr"]) if row.get("stck_clpr") else None,
                "volume": int(row["acml_vol"]) if row.get("acml_vol") else None,
            }
            for row in rows
        ]
        items.reverse()  # KIS returns most-recent-first; downstream code expects chronological order.
        return {"symbol": code, "provider": self.name, "items": items}

    # Fundamentals/news/search are not yet wired for this provider; the market
    # service falls back to the yfinance adapter for these until a dedicated
    # KIS integration is built (see docs/FINANCIAL_AI_DEVELOPMENT_PLAN.md).
    def get_overview(self) -> dict[str, Any]:
        raise NotImplementedError

    def search(self, query: str, limit: int = 10) -> dict[str, Any]:
        raise NotImplementedError

    def get_detail(self, symbol: str, days: int = 30) -> dict[str, Any]:
        raise NotImplementedError

    def get_financials(self, symbol: str, limit: int = 5) -> dict[str, Any]:
        raise NotImplementedError

    def get_balance(self, symbol: str, limit: int = 4) -> dict[str, Any]:
        raise NotImplementedError

    def get_news(self, symbol: str, limit: int = 10) -> dict[str, Any]:
        raise NotImplementedError
