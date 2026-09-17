import unittest
from unittest.mock import patch

import redis

from market import cache, service


class GetHistoryTest(unittest.TestCase):
    """Force the fail-open path so results don't depend on a real Redis or its leftover state."""

    def setUp(self):
        patcher = patch.object(cache._redis, "get", side_effect=redis.RedisError("no redis in tests"))
        self.addCleanup(patcher.stop)
        patcher.start()
        patcher = patch.object(cache._redis, "set")
        self.addCleanup(patcher.stop)
        patcher.start()
        patcher = patch.object(cache._redis, "incr", side_effect=redis.RedisError("no redis in tests"))
        self.addCleanup(patcher.stop)
        patcher.start()

    def test_korean_symbol_is_marked_delayed(self):
        with patch.object(service._yfinance, "get_history", return_value={"symbol": "005930.KS", "items": []}):
            result = service.get_history("005930", 30)
        self.assertEqual(result["provider"], "yfinance")
        self.assertTrue(result["delayed"])

    def test_overseas_symbol_is_not_delayed(self):
        with patch.object(service._yfinance, "get_history", return_value={"items": [1]}):
            result = service.get_history("AAPL", 30)
        self.assertEqual(result["provider"], "yfinance")
        self.assertFalse(result["delayed"])

    def test_bare_korean_code_is_suffixed_before_hitting_yfinance(self):
        """Regression test: yfinance/OpenBB only resolves Korean tickers with a
        .KS/.KQ suffix, so a bare 6-digit code must be translated before the
        call, while the response still echoes back the original input."""
        with patch.object(service._yfinance, "get_history", return_value={"items": [1]}) as get_history:
            result = service.get_history("005930", 30)
        get_history.assert_called_once_with("005930.KS", 30)
        self.assertEqual(result["symbol"], "005930")

    def test_overseas_symbol_is_not_translated(self):
        with patch.object(service._yfinance, "get_history", return_value={"items": [1]}) as get_history:
            service.get_history("AAPL", 30)
        get_history.assert_called_once_with("AAPL", 30)


if __name__ == "__main__":
    unittest.main()
