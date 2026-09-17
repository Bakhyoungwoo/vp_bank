import unittest

from market.symbols import classify_symbol


class ClassifySymbolTest(unittest.TestCase):
    def test_bare_korean_code_is_marked_uncertain(self):
        result = classify_symbol("005930")
        self.assertTrue(result["isKorea"])
        self.assertTrue(result["marketUncertain"])
        self.assertEqual(result["yahooSymbol"], "005930.KS")
        self.assertEqual(result["currency"], "KRW")

    def test_kosdaq_suffix_is_recognized(self):
        result = classify_symbol("091990.KQ")
        self.assertTrue(result["isKorea"])
        self.assertFalse(result["marketUncertain"])
        self.assertEqual(result["market"], "KOSDAQ")

    def test_overseas_symbol_defaults_to_usd(self):
        result = classify_symbol("aapl")
        self.assertFalse(result["isKorea"])
        self.assertEqual(result["market"], "OVERSEAS")
        self.assertEqual(result["currency"], "USD")
        self.assertEqual(result["code"], "AAPL")


if __name__ == "__main__":
    unittest.main()
