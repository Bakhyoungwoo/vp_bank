import unittest
from unittest.mock import patch

from analysis import news_impact
from analysis.news_impact import analyze_news_impact


def _news(items):
    return {"provider": "yahoo-finance", "items": items}


def _detail(history=None):
    return {"provider": "openbb/yfinance", "history": history or []}


class ClassifyHeadlineTest(unittest.TestCase):
    def test_positive_keyword_only(self):
        result = news_impact.classify_headline("A사, 3분기 최대 실적 흑자 전환")
        self.assertEqual(result["impactLabel"], "positive")
        self.assertIn("흑자", result["matchedKeywords"]["positive"])

    def test_negative_keyword_only(self):
        result = news_impact.classify_headline("B사, 리콜 사태로 소송 제기")
        self.assertEqual(result["impactLabel"], "negative")

    def test_no_keyword_is_neutral(self):
        result = news_impact.classify_headline("C사, 신제품 발표회 개최")
        self.assertEqual(result["impactLabel"], "neutral")

    def test_mixed_keywords_fall_back_to_neutral(self):
        result = news_impact.classify_headline("D사, 흑자 전환했지만 소송 제기")
        self.assertEqual(result["impactLabel"], "neutral")


class DedupeArticlesTest(unittest.TestCase):
    def test_deduplicates_by_url(self):
        items = [
            {"url": "https://a", "title": "A"},
            {"url": "https://a", "title": "A duplicate"},
            {"url": "https://b", "title": "B"},
        ]
        result = news_impact.dedupe_articles(items)
        self.assertEqual(len(result), 2)


class AnalyzeNewsImpactTest(unittest.TestCase):
    def test_builds_summary_and_calls_llm_when_articles_present(self):
        news = _news([
            {"title": "A사 최대 실적 흑자", "publisher": "P1", "url": "https://a",
             "publishedAt": 1_700_000_000, "relatedSymbols": ["AAPL"]},
            {"title": "A사 소송 제기", "publisher": "P2", "url": "https://b",
             "publishedAt": 1_700_000_100, "relatedSymbols": ["AAPL"]},
        ])
        detail = _detail([{"close": 100, "volume": 10}, {"close": 110, "volume": 20}])
        with patch("analysis.news_impact.get_stock_news", return_value=news), \
                patch("analysis.news_impact.get_stock_detail", return_value=detail), \
                patch("analysis.news_impact.generate_news_impact_narrative", return_value="요약") as narrative:
            result = analyze_news_impact("aapl", limit=10, days=30)

        self.assertEqual(result["symbol"], "AAPL")
        self.assertEqual(result["status"], "ready")
        self.assertEqual(len(result["articles"]), 2)
        self.assertEqual(result["summary"]["positiveCount"], 1)
        self.assertEqual(result["summary"]["negativeCount"], 1)
        self.assertEqual(result["summary"]["overallLabel"], "mixed")
        self.assertIsNotNone(result["momentum"])
        self.assertEqual(result["llmNarrative"], "요약")
        narrative.assert_called_once()

    def test_insufficient_data_when_no_articles_and_llm_not_called(self):
        with patch("analysis.news_impact.get_stock_news", return_value=_news([])), \
                patch("analysis.news_impact.get_stock_detail", return_value=_detail()), \
                patch("analysis.news_impact.generate_news_impact_narrative") as narrative:
            result = analyze_news_impact("AAPL")

        self.assertEqual(result["status"], "insufficient_data")
        self.assertEqual(result["articles"], [])
        self.assertIsNone(result["llmNarrative"])
        narrative.assert_not_called()

    def test_momentum_is_none_when_price_history_unavailable(self):
        news = _news([{"title": "A사 신제품 발표", "publisher": "P1", "url": "https://a"}])
        with patch("analysis.news_impact.get_stock_news", return_value=news), \
                patch("analysis.news_impact.get_stock_detail", return_value=_detail()), \
                patch("analysis.news_impact.generate_news_impact_narrative", return_value=None):
            result = analyze_news_impact("AAPL")
        self.assertIsNone(result["momentum"])


if __name__ == "__main__":
    unittest.main()
