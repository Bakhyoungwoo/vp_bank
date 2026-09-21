import os
import unittest
from unittest.mock import MagicMock, patch

from analysis import llm


class IsConfiguredTest(unittest.TestCase):
    def test_false_when_key_missing(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertFalse(llm.is_configured())

    def test_true_when_key_present(self):
        with patch.dict(os.environ, {"OPENAI_API_KEY": "sk-test"}):
            self.assertTrue(llm.is_configured())


class GenerateNarrativeTest(unittest.TestCase):
    SAMPLE_ANALYSIS = {
        "symbol": "AAPL",
        "asOf": "2026-09-15T00:00:00Z",
        "growth": {"score": 60, "evidence": ["매출 +5.20%"]},
        "profitability": {"score": 55, "evidence": ["영업이익률 30.00%"]},
        "valuation": {"score": None, "evidence": ["PER/PBR 데이터 없음"]},
        "momentum": {"score": 70, "evidence": ["최근 기간 수익률 8.10%"]},
        "positiveFactors": ["최근 기간 수익률 8.10%"],
        "riskFactors": ["원인을 확정하지 않습니다."],
    }

    def test_returns_none_when_not_configured(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertIsNone(llm.generate_narrative(self.SAMPLE_ANALYSIS))

    def test_returns_narrative_text_on_success(self):
        fake_response = MagicMock()
        fake_response.choices[0].message.content = "  요약 문장입니다.  "
        fake_client = MagicMock()
        fake_client.chat.completions.create.return_value = fake_response
        with patch.dict(os.environ, {"OPENAI_API_KEY": "sk-test"}), \
                patch.object(llm, "_get_client", return_value=fake_client):
            result = llm.generate_narrative(self.SAMPLE_ANALYSIS)
        self.assertEqual(result, "요약 문장입니다.")
        fake_client.chat.completions.create.assert_called_once()

    def test_prompt_never_includes_raw_provider_fields(self):
        """LLM에는 이미 계산된 점수·근거만 전달되고 원본 provider 데이터는 전달되지 않는다."""
        analysis_with_raw_data = {
            **self.SAMPLE_ANALYSIS,
            "quantitative": {"latestPrice": 999.99},
            "financials": {"items": [{"revenue": 123}]},
        }
        prompt = llm._build_prompt(analysis_with_raw_data)
        self.assertNotIn("999.99", prompt)
        self.assertNotIn("123", prompt)

    def test_returns_none_and_does_not_raise_when_call_fails(self):
        fake_client = MagicMock()
        fake_client.chat.completions.create.side_effect = RuntimeError("network error")
        with patch.dict(os.environ, {"OPENAI_API_KEY": "sk-test"}), \
                patch.object(llm, "_get_client", return_value=fake_client):
            result = llm.generate_narrative(self.SAMPLE_ANALYSIS)
        self.assertIsNone(result)


class GenerateComparisonNarrativeTest(unittest.TestCase):
    SAMPLE_COMPARISON = {
        "symbols": ["AAPL", "MSFT"],
        "asOf": "2026-09-15T00:00:00Z",
        "sameSector": True,
        "items": [
            {"symbol": "AAPL", "name": "Apple", "sector": "Technology", "revenueGrowthPercent": 6.4,
             "operatingMarginPercent": 32.0, "per": 10.0, "pbr": 2.0, "roe": 20.0, "debtRatio": 40.0,
             "periodReturnPercent": 5.0, "volatilityPercent": 1.5},
            {"symbol": "MSFT", "name": "Microsoft", "sector": "Technology", "revenueGrowthPercent": 12.0,
             "operatingMarginPercent": 40.0, "per": 15.0, "pbr": 3.0, "roe": 25.0, "debtRatio": 30.0,
             "periodReturnPercent": 8.0, "volatilityPercent": 1.2},
        ],
    }

    def test_returns_none_when_not_configured(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertIsNone(llm.generate_comparison_narrative(self.SAMPLE_COMPARISON))

    def test_returns_narrative_text_on_success(self):
        fake_response = MagicMock()
        fake_response.choices[0].message.content = "  비교 요약입니다.  "
        fake_client = MagicMock()
        fake_client.chat.completions.create.return_value = fake_response
        with patch.dict(os.environ, {"OPENAI_API_KEY": "sk-test"}), \
                patch.object(llm, "_get_client", return_value=fake_client):
            result = llm.generate_comparison_narrative(self.SAMPLE_COMPARISON)
        self.assertEqual(result, "비교 요약입니다.")

    def test_prompt_covers_every_symbol_with_only_computed_metrics(self):
        prompt = llm._build_comparison_prompt(self.SAMPLE_COMPARISON)
        self.assertIn("AAPL", prompt)
        self.assertIn("MSFT", prompt)
        self.assertIn("PER 10.0", prompt)


class GeneratePriceMoveNarrativeTest(unittest.TestCase):
    SAMPLE_ANALYSIS = {
        "symbol": "AAPL",
        "asOf": "2026-09-15T00:00:00Z",
        "detection": {
            "dailyChangePercent": 6.0,
            "volumeRatio": 2.5,
            "volatilityPercent": 3.0,
        },
        "marketComparison": {"name": "S&P500", "changePercent": 1.5},
        "candidates": [
            {"type": "volume_spike", "label": "거래량 급증", "score": 60, "evidence": ["최근 거래량이 평균 대비 2.50배"]},
        ],
    }

    def test_returns_none_when_not_configured(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertIsNone(llm.generate_price_move_narrative(self.SAMPLE_ANALYSIS))

    def test_returns_narrative_text_on_success(self):
        fake_response = MagicMock()
        fake_response.choices[0].message.content = "  급등락 요약입니다.  "
        fake_client = MagicMock()
        fake_client.chat.completions.create.return_value = fake_response
        with patch.dict(os.environ, {"OPENAI_API_KEY": "sk-test"}), \
                patch.object(llm, "_get_client", return_value=fake_client):
            result = llm.generate_price_move_narrative(self.SAMPLE_ANALYSIS)
        self.assertEqual(result, "급등락 요약입니다.")

    def test_prompt_includes_detection_and_candidates(self):
        prompt = llm._build_price_move_prompt(self.SAMPLE_ANALYSIS)
        self.assertIn("6.0", prompt)
        self.assertIn("거래량 급증", prompt)
        self.assertIn("S&P500", prompt)

    def test_prompt_notes_no_candidates_when_empty(self):
        analysis = {**self.SAMPLE_ANALYSIS, "candidates": []}
        prompt = llm._build_price_move_prompt(analysis)
        self.assertIn("근거가 확인된 후보 없음", prompt)


if __name__ == "__main__":
    unittest.main()
