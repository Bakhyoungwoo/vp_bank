from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException, Query

from analysis.llm import generate_narrative
from analysis.metrics import normalize_financials, price_metrics, score_financials, score_momentum
from market.service import get_stock_detail

router = APIRouter(prefix="/ai", tags=["ai-analysis"])


@router.post("/stocks/{symbol}/analysis")
def analyze_stock(symbol: str, days: int = Query(default=30, ge=5, le=3650)):
    """Fetch OpenBB data and return a deterministic analysis draft for later narration."""
    try:
        detail = get_stock_detail(symbol, days)
    except ImportError as exc:
        raise HTTPException(status_code=503, detail="OpenBB unavailable") from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=429, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Stock data unavailable") from exc

    # Keep raw provider data out of the scoring logic by normalizing each domain first.
    metrics = price_metrics(detail.get("history", []))
    momentum_score, momentum_evidence = score_momentum(metrics)
    financials = normalize_financials(detail.get("financials", []))
    financial_scores = score_financials(financials)
    available = bool(metrics.get("available"))
    result = {
        "symbol": detail.get("symbol", symbol.upper()),
        "asOf": datetime.now(timezone.utc).isoformat(),
        "status": "ready" if available else "insufficient_data",
        "summary": "가격·거래량 데이터를 기반으로 생성한 분석 초안입니다." if available else "분석에 필요한 가격 데이터가 부족합니다.",
        "growth": financial_scores["growth"],
        "profitability": financial_scores["profitability"],
        "valuation": financial_scores["valuation"],
        "momentum": {"score": momentum_score, "evidence": momentum_evidence},
        "financials": financials,
        "quantitative": metrics,
        "positiveFactors": momentum_evidence if momentum_score is not None and momentum_score >= 60 else [],
        "riskFactors": ["분석은 관측된 가격·거래량 데이터에 한정되며 원인을 확정하지 않습니다."],
        "sources": [{"provider": detail.get("provider", "openbb/yfinance"), "type": "market_data"}],
        "llmNarrative": None,
    }
    # LLM은 위에서 이미 계산한 점수·근거만 서술한다. 원본 provider 데이터를
    # 보거나 수치를 직접 만들어내지 않는다. OPENAI_API_KEY가 없으면 에러가
    # 아니라 None으로 남긴다.
    if available:
        result["llmNarrative"] = generate_narrative(result)
    return result
