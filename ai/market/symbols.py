from __future__ import annotations

from typing import Any

KOREA_SUFFIXES = {"KS": "KOSPI", "KQ": "KOSDAQ"}


def classify_symbol(symbol: str) -> dict[str, Any]:
    """Standardize a raw symbol into market/currency metadata shared by every provider.

    Bare 6-digit codes are Korean but their market (KOSPI vs KOSDAQ) cannot be
    determined without a lookup table, so callers must treat `marketUncertain`
    as a signal to confirm the market before relying on it.
    """
    raw = symbol.strip().upper()
    if "." in raw:
        code, _, suffix = raw.partition(".")
        market = KOREA_SUFFIXES.get(suffix)
        if market:
            return {
                "input": symbol,
                "code": code,
                "yahooSymbol": raw,
                "market": market,
                "currency": "KRW",
                "isKorea": True,
                "marketUncertain": False,
            }
    if raw.isdigit() and len(raw) == 6:
        return {
            "input": symbol,
            "code": raw,
            "yahooSymbol": f"{raw}.KS",
            "market": "KOSPI",
            "currency": "KRW",
            "isKorea": True,
            "marketUncertain": True,
        }
    return {
        "input": symbol,
        "code": raw,
        "yahooSymbol": raw,
        "market": "OVERSEAS",
        "currency": "USD",
        "isKorea": False,
        "marketUncertain": False,
    }
