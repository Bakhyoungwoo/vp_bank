import json
import unittest
from pathlib import Path


RESULT_FILE = Path(__file__).parents[1] / "keyword_experiment_result.json"
SUMMARY_FILE = Path(__file__).parents[1] / "keyword_experiment_test_summary.json"


def build_summary(result):
    categories = list(result["categories"].values())
    old_seconds = sum(item["old"]["seconds"] for item in categories) / len(categories)
    new_seconds = sum(item["new"]["seconds"] for item in categories) / len(categories)
    old_keywords = sum(len(item["old"]["keywords"]) for item in categories)
    new_keywords = sum(len(item["new"]["keywords"]) for item in categories)
    old_particles = sum(item["old"]["particle_count"] for item in categories)
    new_particles = sum(item["new"]["particle_count"] for item in categories)
    old_pairs = sum(item["old"]["similar_pairs"] for item in categories)
    new_pairs = sum(item["new"]["similar_pairs"] for item in categories)
    return {
        "article_count": sum(item["article_count"] for item in categories),
        "category_count": len(categories),
        "keyword_count": {"before": old_keywords, "after": new_keywords},
        "particle_count": {"before": old_particles, "after": new_particles},
        "particle_ratio_percent": {
            "before": old_particles / old_keywords * 100,
            "after": new_particles / new_keywords * 100,
        },
        "similar_pair_count": {"before": old_pairs, "after": new_pairs},
        "average_seconds": {"before": old_seconds, "after": new_seconds},
        "average_category_stddev": {
            "before": sum(item["old"]["seconds_stddev"] for item in categories) / len(categories),
            "after": sum(item["new"]["seconds_stddev"] for item in categories) / len(categories),
        },
        "speed_improvement_percent": (old_seconds - new_seconds) / old_seconds * 100,
        "information_density": {"status": "REVIEW", "reason": "사람 검토 기준 미확정"},
        "validity_index": {"status": "REVIEW", "reason": "검토자·배점 기준 미확정"},
        "data_volume": {"status": "REVIEW", "reason": "집계 단위 미확정"},
    }


class RealKeywordExtractorComparisonTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not RESULT_FILE.exists():
            raise unittest.SkipTest("실제 측정 결과 파일이 없습니다. scripts/compare_keyword_extractors.py를 먼저 실행하세요.")
        cls.result = json.loads(RESULT_FILE.read_text(encoding="utf-8"))
        cls.categories = list(cls.result["categories"].values())
        cls.summary = build_summary(cls.result)
        SUMMARY_FILE.write_text(json.dumps(cls.summary, ensure_ascii=False, indent=2), encoding="utf-8")

    def test_same_crawled_articles_are_compared(self):
        self.assertEqual(self.result["conditions"]["pages"], 1)
        self.assertEqual(self.result["conditions"]["repeats"], 3)
        self.assertEqual(len(self.categories), 6)
        self.assertEqual(sum(item["article_count"] for item in self.categories), 270)

    def test_refined_keywords_have_no_particles(self):
        self.assertEqual(sum(item["old"]["particle_count"] for item in self.categories), 26)
        self.assertEqual(sum(item["new"]["particle_count"] for item in self.categories), 0)

    def test_duplicate_pairs_are_reduced_to_zero(self):
        self.assertEqual(sum(item["old"]["similar_pairs"] for item in self.categories), 37)
        self.assertEqual(sum(item["new"]["similar_pairs"] for item in self.categories), 0)
        self.assertEqual(self.result["conditions"]["similarity_threshold"], 0.8)

    def test_real_processing_speed_improves(self):
        self.assertAlmostEqual(self.summary["average_seconds"]["before"], 48.35, delta=0.2)
        self.assertAlmostEqual(self.summary["average_seconds"]["after"], 1.47, delta=0.1)
        self.assertAlmostEqual(self.summary["speed_improvement_percent"], 97.0, delta=1.0)

    def test_prints_computed_before_after_values(self):
        print(json.dumps(self.summary, ensure_ascii=False, indent=2))
        self.assertEqual(self.summary["particle_ratio_percent"]["after"], 0.0)
        self.assertEqual(self.summary["similar_pair_count"]["after"], 0)


if __name__ == "__main__":
    unittest.main()
