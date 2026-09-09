import unittest

from analysis.metrics import normalize_financials, price_metrics, score_financials


class MetricsTest(unittest.TestCase):
    def test_price_metrics_calculates_return_and_volume_ratio(self):
        result = price_metrics([
            {"close": 100, "volume": 10},
            {"close": 110, "volume": 20},
            {"close": 121, "volume": 40},
        ])
        self.assertAlmostEqual(result["periodReturnPercent"], 21.0)
        self.assertAlmostEqual(result["volumeRatio"], 40 / 15)

    def test_financials_normalize_growth_and_margin(self):
        result = normalize_financials([
            {"date": "2025", "revenue": 120, "operating_income": 24, "net_income": 12, "eps": 2},
            {"date": "2024", "revenue": 100, "operating_income": 20, "net_income": 10, "eps": 1.5},
        ])
        self.assertTrue(result["available"])
        self.assertAlmostEqual(result["growthPercent"]["revenue"], 20.0)
        self.assertAlmostEqual(result["latest"]["operatingMargin"], 0.2)
        self.assertIsNotNone(score_financials(result)["growth"]["score"])

    def test_missing_financials_do_not_create_scores(self):
        result = score_financials(normalize_financials([]))
        self.assertIsNone(result["growth"]["score"])
        self.assertIsNone(result["profitability"]["score"])


if __name__ == "__main__":
    unittest.main()
