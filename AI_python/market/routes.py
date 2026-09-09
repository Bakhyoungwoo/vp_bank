from fastapi import APIRouter, HTTPException, Query

from market.openbb_provider import get_history, get_overview, get_stock_detail, get_stock_financials, get_stock_news, search_stocks


router = APIRouter(prefix="/market", tags=["market"])
stocks_router = APIRouter(prefix="/stocks", tags=["stocks"])


@router.get("/overview")
def overview():
    try:
        return get_overview()
    except ImportError as exc:
        raise HTTPException(status_code=503, detail="OpenBB가 설치되지 않았습니다.") from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail="금융 데이터 Provider를 조회하지 못했습니다.") from exc


@router.get("/history/{symbol}")
def history(symbol: str, days: int = Query(default=30, ge=1, le=3650)):
    try:
        return get_history(symbol, days)
    except ImportError as exc:
        raise HTTPException(status_code=503, detail="OpenBB가 설치되지 않았습니다.") from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail="금융 데이터 Provider를 조회하지 못했습니다.") from exc


@stocks_router.get("/search")
def stock_search(query: str = Query(min_length=1, max_length=80), limit: int = Query(default=10, ge=1, le=25)):
    try:
        return search_stocks(query, limit)
    except Exception as exc:
        raise HTTPException(status_code=502, detail="종목 검색에 실패했습니다.") from exc


@stocks_router.get("/{symbol}/financials")
def stock_financials(symbol: str, limit: int = Query(default=5, ge=1, le=20)):
    try:
        return get_stock_financials(symbol, limit)
    except ImportError as exc:
        raise HTTPException(status_code=503, detail="OpenBB unavailable") from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Financial data unavailable") from exc


@stocks_router.get("/{symbol}/news")
def stock_news(symbol: str, limit: int = Query(default=10, ge=1, le=30)):
    try:
        return get_stock_news(symbol, limit)
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Related news unavailable") from exc


@stocks_router.get("/{symbol}")
def stock_detail(symbol: str, days: int = Query(default=30, ge=1, le=3650)):
    try:
        return get_stock_detail(symbol, days)
    except ImportError as exc:
        raise HTTPException(status_code=503, detail="OpenBB가 설치되지 않았습니다.") from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail="종목 상세 데이터를 조회하지 못했습니다.") from exc
