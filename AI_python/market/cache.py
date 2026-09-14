from __future__ import annotations

import json
import os
import time
from functools import wraps
from typing import Any, Callable

import redis
from fastapi.encoders import jsonable_encoder

REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
_redis = redis.Redis(host=REDIS_HOST, port=6379, db=0, decode_responses=True)


def _cache_key(prefix: str, args: tuple, kwargs: dict) -> str:
    parts = [str(value) for value in args] + [f"{key}={value}" for key, value in sorted(kwargs.items())]
    return f"market:cache:{prefix}:" + ":".join(parts)


def cached(prefix: str, ttl_seconds: int) -> Callable:
    """Cache a JSON-serializable market response in Redis. Fails open if Redis is unreachable."""

    def decorator(fn: Callable[..., dict[str, Any]]) -> Callable[..., dict[str, Any]]:
        @wraps(fn)
        def wrapper(*args, **kwargs):
            key = _cache_key(prefix, args, kwargs)
            try:
                hit = _redis.get(key)
                if hit is not None:
                    return json.loads(hit)
            except redis.RedisError:
                pass
            result = fn(*args, **kwargs)
            try:
                # jsonable_encoder mirrors what FastAPI would do when serializing the
                # response, so provider values pandas returns (e.g. Timestamp) don't
                # break caching even though plain json.dumps can't handle them.
                _redis.set(key, json.dumps(jsonable_encoder(result)), ex=ttl_seconds)
            except redis.RedisError:
                pass
            return result

        return wrapper

    return decorator


def enforce_rate_limit(bucket: str, max_calls: int, window_seconds: int) -> None:
    """Raise RuntimeError once a provider bucket exceeds max_calls within window_seconds.

    Fails open if Redis is unreachable, since a cache/limiter outage must not
    take down market data entirely.
    """
    window = int(time.time() // window_seconds)
    key = f"market:ratelimit:{bucket}:{window}"
    try:
        count = _redis.incr(key)
        if count == 1:
            _redis.expire(key, window_seconds)
    except redis.RedisError:
        return
    if count > max_calls:
        raise RuntimeError(f"{bucket} 조회 요청이 제한을 초과했습니다. 잠시 후 다시 시도하세요.")


def rate_limited(bucket: str, max_calls: int, window_seconds: int) -> Callable:
    """Decorator form of enforce_rate_limit for a call site with a fixed bucket."""

    def decorator(fn: Callable[..., Any]) -> Callable[..., Any]:
        @wraps(fn)
        def wrapper(*args, **kwargs):
            enforce_rate_limit(bucket, max_calls, window_seconds)
            return fn(*args, **kwargs)

        return wrapper

    return decorator
