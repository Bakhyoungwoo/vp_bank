from fastapi import APIRouter, HTTPException, Query

from market.openbb_provider import get_history, get_overview


router = APIRouter(prefix="/market", tags=["market"])


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
