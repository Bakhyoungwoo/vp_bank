import unittest
from unittest.mock import patch

from analysis.briefing import InvalidBriefingError, build_briefing


def _detail(history=None, provider="openbb/yfinance"):
    return {"provider": provider, "history": history or []}


def _news(items):
    return {"provider": "yahoo-finance", "items": items}


class ValidateSymbolsTest(unittest.TestCase):
    def test_raises_when_no_symbols(self):
        with self.assertRaises(InvalidBriefingError):
            build_briefing([])

    def test_raises_when_only_blank_symbols(self):
        with self.assertRaises(InvalidBriefingError):
            build_briefing(["  ", ""])

    def test_raises_when_too_many_symbols(self):
        symbols = [f"SYM{i}" for i in range(21)]
        with self.assertRaises(InvalidBriefingError):
            build_briefing(symbols)


class BuildBriefingTest(unittest.TestCase):
    def test_insufficient_data_when_no_history_for_any_symbol(self):
        with patch("analysis.briefing.get_stock_detail", return_value=_detail()), \
                patch("analysis.briefing.get_stock_news", return_value=_news([])), \
                patch("analysis.briefing.generate_briefing_narrative") as narrative:
            result = build_briefing(["AAPL", "MSFT"])

        self.assertEqual(result["status"], "insufficient_data")
        self.assertEqual(len(result["items"]), 2)
        self.assertTrue(all(item["status"] == "insufficient_data" for item in result["items"]))
        self.assertIsNone(result["llmNarrative"])
        narrative.assert_not_called()

    def test_ready_when_at_least_one_symbol_has_history(self):
        history = [{"close": 100, "volume": 10}, {"close": 110, "volume": 12}]
        news_items = [{"title": "A사 최대 실적 흑자", "publisher": "P1", "url": "https://a"}]

        def fake_detail(symbol, days):
            return _detail(history) if symbol == "AAPL" else _detail()

        with patch("analysis.briefing.get_stock_detail", side_effect=fake_detail), \
                patch("analysis.briefing.get_stock_news", return_value=_news(news_items)), \
                patch("analysis.briefing.generate_briefing_narrative", return_value="요약") as narrative:
            result = build_briefing(["MSFT", "AAPL"], days=7)

        self.assertEqual(result["status"], "ready")
        self.assertEqual(result["sinceDays"], 7)
        # 변동폭이 큰(데이터가 있는) 종목이 먼저 와야 한다.
        self.assertEqual(result["items"][0]["symbol"], "AAPL")
        self.assertEqual(result["items"][0]["status"], "ready")
        self.assertAlmostEqual(result["items"][0]["periodReturnPercent"], 10.0)
        self.assertEqual(result["items"][1]["symbol"], "MSFT")
        self.assertEqual(result["items"][1]["status"], "insufficient_data")
        self.assertEqual(result["llmNarrative"], "요약")
        narrative.assert_called_once()

    def test_dedupes_symbols_and_uppercases(self):
        with patch("analysis.briefing.get_stock_detail", return_value=_detail()), \
                patch("analysis.briefing.get_stock_news", return_value=_news([])), \
                patch("analysis.briefing.generate_briefing_narrative"):
            result = build_briefing(["aapl", "AAPL", " aapl "])

        self.assertEqual(len(result["items"]), 1)
        self.assertEqual(result["items"][0]["symbol"], "AAPL")


if __name__ == "__main__":
    unittest.main()
