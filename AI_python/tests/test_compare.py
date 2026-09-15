import unittest
from unittest.mock import patch

from analysis import compare
from analysis.compare import InvalidComparisonError, compare_stocks


def _detail(symbol, sector, name, market_cap, revenue, prev_revenue, net_income, operating_income):
    return {
        "symbol": symbol,
        "quote": {"name": name},
        "profile": {"name": name, "sector": sector, "market_cap": market_cap},
        "history": [{"close": 100, "volume": 10}, {"close": 110, "volume": 20}],
        "financials": [
            {"date": "2025", "revenue": revenue, "operating_income": operating_income, "net_income": net_income},
            {"date": "2024", "revenue": prev_revenue, "operating_income": operating_income * 0.9, "net_income": net_income * 0.9},
        ],
    }


class ValidateSymbolsTest(unittest.TestCase):
    def test_rejects_single_symbol(self):
        with self.assertRaises(InvalidComparisonError):
            compare_stocks(["AAPL"])

    def test_rejects_more_than_five_symbols(self):
        with self.assertRaises(InvalidComparisonError):
            compare_stocks(["A", "B", "C", "D", "E", "F"])

    def test_deduplicates_while_keeping_order(self):
        with patch.object(compare, "_build_row", side_effect=lambda symbol, days: {
            "symbol": symbol, "sector": None, "available": False,
        }) as build_row:
            result = compare_stocks(["AAPL", "aapl", "MSFT"])
        self.assertEqual(result["symbols"], ["AAPL", "MSFT"])
        self.assertEqual(build_row.call_count, 2)


class CompareStocksTest(unittest.TestCase):
    def test_builds_comparison_table_and_flags_same_sector(self):
        details = {
            "AAPL": _detail("AAPL", "Technology", "Apple", 1000, 120, 100, 100, 120),
            "MSFT": _detail("MSFT", "Technology", "Microsoft", 2000, 240, 200, 200, 240),
        }
        balances = {
            "AAPL": {"items": [{"period_ending": "2025", "total_liabilities_net_minority_interest": 200, "common_stock_equity": 500}]},
            "MSFT": {"items": [{"period_ending": "2025", "total_liabilities_net_minority_interest": 400, "common_stock_equity": 1000}]},
        }
        with patch("analysis.compare.get_stock_detail", side_effect=lambda symbol, days: details[symbol]), \
                patch("analysis.compare.get_stock_balance", side_effect=lambda symbol, limit: balances[symbol]), \
                patch("analysis.compare.generate_comparison_narrative", return_value="요약") as narrative:
            result = compare_stocks(["AAPL", "MSFT"], days=90)

        self.assertEqual(result["symbols"], ["AAPL", "MSFT"])
        self.assertTrue(result["sameSector"])
        self.assertEqual(len(result["items"]), 2)
        aapl = result["items"][0]
        self.assertTrue(aapl["available"])
        self.assertAlmostEqual(aapl["revenueGrowthPercent"], 20.0)
        self.assertAlmostEqual(aapl["per"], 10.0)  # market_cap 1000 / net_income 100
        self.assertAlmostEqual(aapl["pbr"], 2.0)  # market_cap 1000 / equity 500
        self.assertEqual(result["llmNarrative"], "요약")
        narrative.assert_called_once()

    def test_different_sectors_are_not_flagged_same(self):
        details = {
            "AAPL": _detail("AAPL", "Technology", "Apple", 1000, 120, 100, 100, 120),
            "XOM": _detail("XOM", "Energy", "Exxon", 2000, 240, 200, 200, 240),
        }
        with patch("analysis.compare.get_stock_detail", side_effect=lambda symbol, days: details[symbol]), \
                patch("analysis.compare.get_stock_balance", return_value={"items": []}), \
                patch("analysis.compare.generate_comparison_narrative", return_value=None):
            result = compare_stocks(["AAPL", "XOM"])
        self.assertFalse(result["sameSector"])

    def test_llm_not_called_when_no_symbol_has_data(self):
        unavailable = {"symbol": "AAPL", "quote": {}, "profile": {}, "history": [], "financials": []}
        with patch("analysis.compare.get_stock_detail", return_value=unavailable), \
                patch("analysis.compare.get_stock_balance", return_value={"items": []}), \
                patch("analysis.compare.generate_comparison_narrative") as narrative:
            result = compare_stocks(["AAPL", "MSFT"])
        self.assertIsNone(result["llmNarrative"])
        narrative.assert_not_called()


if __name__ == "__main__":
    unittest.main()
