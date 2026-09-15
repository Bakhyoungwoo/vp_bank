import unittest
from datetime import date
from unittest.mock import patch

from market import cache


class FakeRedis:
    """Minimal dict-backed stand-in so tests don't need a real Redis server."""

    def __init__(self):
        self.store: dict[str, str] = {}

    def get(self, key):
        return self.store.get(key)

    def set(self, key, value, ex=None):
        self.store[key] = value

    def incr(self, key):
        self.store[key] = str(int(self.store.get(key, "0")) + 1)
        return int(self.store[key])

    def expire(self, key, seconds):
        pass


class CachedDecoratorTest(unittest.TestCase):
    def test_caches_values_pandas_and_datetime_objects_that_json_cannot_handle_directly(self):
        """Regression test: openbb/pandas responses contain Timestamp objects that
        plain json.dumps rejects; the cache must serialize them the same way
        FastAPI would (jsonable_encoder) instead of raising TypeError."""
        with patch.object(cache, "_redis", FakeRedis()):
            calls = []

            @cache.cached("test", ttl_seconds=60)
            def fn():
                calls.append(1)
                return {"asOf": date(2026, 1, 1), "items": [1, 2]}

            first = fn()
            second = fn()  # served from cache

        self.assertEqual(first["items"], [1, 2])
        self.assertEqual(second["asOf"], "2026-01-01")
        self.assertEqual(len(calls), 1)  # second call was a cache hit


if __name__ == "__main__":
    unittest.main()
