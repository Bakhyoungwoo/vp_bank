import unittest

from analysis.metrics import (
    compute_valuation_ratios,
    normalize_balance,
    normalize_financials,
    price_metrics,
    score_financials,
)


class MetricsTest(unittest.TestCase):
    """Regression tests for deterministic analysis calculations."""

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

    def test_normalize_balance_reads_known_field_variants(self):
        result = normalize_balance([
            {
                "period_ending": "2025",
                "total_liabilities_net_minority_interest": 200,
                "common_stock_equity": 100,
                "total_debt": 150,
            }
        ])
        self.assertTrue(result["available"])
        self.assertEqual(result["latest"]["totalLiabilities"], 200)
        self.assertEqual(result["latest"]["totalEquity"], 100)

    def test_compute_valuation_ratios_derives_per_pbr_roe_debt_ratio(self):
        ratios = compute_valuation_ratios(
            market_cap=1000, net_income=100, balance_latest={"totalEquity": 500, "totalLiabilities": 250}
        )
        self.assertAlmostEqual(ratios["per"], 10.0)
        self.assertAlmostEqual(ratios["pbr"], 2.0)
        self.assertAlmostEqual(ratios["roe"], 20.0)
        self.assertAlmostEqual(ratios["debtRatio"], 50.0)

    def test_compute_valuation_ratios_returns_none_when_inputs_missing(self):
        ratios = compute_valuation_ratios(market_cap=None, net_income=100, balance_latest={})
        self.assertIsNone(ratios["per"])
        self.assertIsNone(ratios["pbr"])
        self.assertIsNone(ratios["roe"])
        self.assertIsNone(ratios["debtRatio"])


if __name__ == "__main__":
    unittest.main()
