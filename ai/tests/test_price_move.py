import unittest
from unittest.mock import patch

from analysis import price_move
from analysis.price_move import analyze_price_move


def _detail(history=None, provider="openbb/yfinance"):
    return {"provider": provider, "history": history or []}


def _news(items):
    return {"provider": "yahoo-finance", "items": items}


def _overview(items):
    return {"items": items}


class IsSignificantMoveTest(unittest.TestCase):
    def test_false_when_metrics_unavailable(self):
        self.assertFalse(price_move._is_significant_move({"available": False}))

    def test_true_when_daily_change_exceeds_threshold(self):
        metrics = {"available": True, "dailyChangePercent": -6.0, "volumeRatio": 1.0}
        self.assertTrue(price_move._is_significant_move(metrics))

    def test_true_when_volume_ratio_exceeds_threshold(self):
        metrics = {"available": True, "dailyChangePercent": 0.5, "volumeRatio": 2.5}
        self.assertTrue(price_move._is_significant_move(metrics))

    def test_false_when_below_both_thresholds(self):
        metrics = {"available": True, "dailyChangePercent": 1.0, "volumeRatio": 1.2}
        self.assertFalse(price_move._is_significant_move(metrics))


class VolumeSpikeCandidateTest(unittest.TestCase):
    def test_none_when_ratio_below_candidate_threshold(self):
        self.assertIsNone(price_move._volume_spike_candidate({"volumeRatio": 1.2}))

    def test_returns_candidate_scaled_by_ratio(self):
        candidate = price_move._volume_spike_candidate({"volumeRatio": 3.5})
        self.assertEqual(candidate["type"], "volume_spike")
        self.assertEqual(candidate["score"], 80)


class MarketWideCandidateTest(unittest.TestCase):
    def test_none_when_index_missing(self):
        self.assertIsNone(price_move._market_wide_candidate(6.0, None))

    def test_none_when_opposite_direction(self):
        index = {"name": "코스피", "changePercent": -1.0}
        self.assertIsNone(price_move._market_wide_candidate(6.0, index))

    def test_none_when_index_change_too_small(self):
        index = {"name": "코스피", "changePercent": 0.1}
        self.assertIsNone(price_move._market_wide_candidate(6.0, index))

    def test_returns_candidate_when_same_direction_and_significant(self):
        index = {"name": "코스피", "changePercent": 1.5}
        candidate = price_move._market_wide_candidate(6.0, index)
        self.assertEqual(candidate["type"], "market_wide_move")
        self.assertGreater(candidate["score"], 0)


class NewsDrivenCandidateTest(unittest.TestCase):
    ARTICLES = [
        {"title": "A사 최대 실적 흑자", "impactLabel": "positive"},
        {"title": "B사 신제품 발표", "impactLabel": "neutral"},
    ]

    def test_none_when_no_matching_direction(self):
        self.assertIsNone(price_move._news_driven_candidate(-4.0, self.ARTICLES))

    def test_returns_candidate_for_matching_direction(self):
        candidate = price_move._news_driven_candidate(4.0, self.ARTICLES)
        self.assertEqual(candidate["type"], "news_driven")
        self.assertEqual(len(candidate["articles"]), 1)


class AnalyzePriceMoveTest(unittest.TestCase):
    def test_insufficient_data_when_no_price_history(self):
        with patch("analysis.price_move.get_stock_detail", return_value=_detail()), \
                patch("analysis.price_move.generate_price_move_narrative") as narrative:
            result = analyze_price_move("AAPL")
        self.assertEqual(result["status"], "insufficient_data")
        narrative.assert_not_called()

    def test_no_significant_move_when_below_thresholds(self):
        history = [{"close": 100, "volume": 10}, {"close": 101, "volume": 10}]
        with patch("analysis.price_move.get_stock_detail", return_value=_detail(history)), \
                patch("analysis.price_move.generate_price_move_narrative") as narrative:
            result = analyze_price_move("AAPL")
        self.assertEqual(result["status"], "no_significant_move")
        self.assertEqual(result["candidates"], [])
        narrative.assert_not_called()

    def test_ready_with_volume_spike_candidate_when_no_market_or_news_match(self):
        history = [{"close": 100, "volume": 10}, {"close": 101, "volume": 25}]
        with patch("analysis.price_move.get_stock_detail", return_value=_detail(history)), \
                patch("analysis.price_move.get_overview", return_value=_overview([])), \
                patch("analysis.price_move.get_stock_news", return_value=_news([])), \
                patch("analysis.price_move.generate_price_move_narrative", return_value="요약") as narrative:
            result = analyze_price_move("AAPL", days=30, limit=10)

        self.assertEqual(result["status"], "ready")
        self.assertEqual(len(result["candidates"]), 1)
        self.assertEqual(result["candidates"][0]["type"], "volume_spike")
        self.assertEqual(result["llmNarrative"], "요약")
        narrative.assert_called_once()

    def test_ready_with_no_candidates_notes_unconfirmed_cause(self):
        history = [{"close": 100, "volume": 10}, {"close": 106, "volume": 10}]
        with patch("analysis.price_move.get_stock_detail", return_value=_detail(history)), \
                patch("analysis.price_move.get_overview", return_value=_overview([])), \
                patch("analysis.price_move.get_stock_news", return_value=_news([])), \
                patch("analysis.price_move.generate_price_move_narrative", return_value=None) as narrative:
            result = analyze_price_move("AAPL")

        self.assertEqual(result["status"], "ready")
        self.assertEqual(result["candidates"], [])
        self.assertIn("확인 가능한 직접 원인 없음", result["riskFactors"])
        narrative.assert_called_once()

    def test_ready_with_market_and_news_candidates_sorted_by_score(self):
        history = [{"close": 100, "volume": 10}, {"close": 108, "volume": 10}]
        overview = _overview([
            {"code": "SP500", "name": "S&P500", "changePercent": 2.0, "available": True},
        ])
        news = _news([
            {"title": "A사 최대 실적 흑자", "publisher": "P1", "url": "https://a"},
        ])
        with patch("analysis.price_move.get_stock_detail", return_value=_detail(history)), \
                patch("analysis.price_move.get_overview", return_value=overview), \
                patch("analysis.price_move.get_stock_news", return_value=news), \
                patch("analysis.price_move.generate_price_move_narrative", return_value="요약"):
            result = analyze_price_move("AAPL")

        self.assertEqual(result["status"], "ready")
        types = [c["type"] for c in result["candidates"]]
        self.assertIn("market_wide_move", types)
        self.assertIn("news_driven", types)
        scores = [c["score"] for c in result["candidates"]]
        self.assertEqual(scores, sorted(scores, reverse=True))
        self.assertIsNotNone(result["marketComparison"])


if __name__ == "__main__":
    unittest.main()
