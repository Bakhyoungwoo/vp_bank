from __future__ import annotations

import os
from typing import Any

_client = None

SYSTEM_PROMPT = (
    "당신은 금융 데이터 분석 보조원입니다. 아래에 주어진 점수와 근거만 사용해 "
    "한국어로 3~5문장의 간단한 종목 분석 요약을 작성하세요. "
    "주어지지 않은 가격, 수치, 사실을 새로 만들어내지 마세요. "
    "투자 추천이나 매수/매도 의견을 제시하지 말고, 참고용 분석이라는 점과 "
    "데이터의 한계를 함께 언급하세요."
)

COMPARISON_SYSTEM_PROMPT = (
    "당신은 금융 데이터 분석 보조원입니다. 아래 종목별 지표만 근거로 "
    "한국어로 4~6문장의 비교 설명을 작성하세요. 어느 종목이 어떤 지표에서 "
    "우위인지 사실 기반으로 설명하되, 투자 추천이나 매수/매도 의견은 제시하지 마세요. "
    "제공되지 않은 수치나 사실을 새로 만들어내지 마세요. 데이터가 없는 지표는 "
    "비교할 수 없다고 명시하세요."
)


def is_configured() -> bool:
    return bool(os.getenv("OPENAI_API_KEY"))


def _get_client():
    global _client
    if _client is None:
        from openai import OpenAI

        _client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
    return _client


def _complete(system_prompt: str, user_prompt: str, max_tokens: int, error_label: str) -> str | None:
    """공통 Chat Completions 호출. 실패해도 예외를 던지지 않고 None을 반환해
    호출부가 결정론적 수치만으로 응답을 내려줄 수 있게 한다."""
    if not is_configured():
        return None
    try:
        client = _get_client()
        model = os.getenv("OPENAI_MODEL", "gpt-4.1-mini")
        response = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.3,
            max_tokens=max_tokens,
        )
        content = response.choices[0].message.content
        return content.strip() if content else None
    except Exception as exc:
        print(f"[LLM ERROR] {error_label} failed:", exc)
        return None


def _build_prompt(analysis: dict[str, Any]) -> str:
    """이미 계산된 점수·근거만 서술용 프롬프트로 옮긴다.

    모델에는 원본 provider 데이터를 넘기지 않고 계산도 맡기지 않는다.
    이미 계산된 수치만 설명하게 해서, 실제 데이터에서 나오지 않은
    가격이나 지표를 스스로 만들어내지 못하게 하기 위함이다.
    """
    lines = [f"종목: {analysis.get('symbol')}", f"기준 시각: {analysis.get('asOf')}"]
    for key in ("growth", "profitability", "valuation", "momentum"):
        section = analysis.get(key) or {}
        score = section.get("score")
        evidence = ", ".join(section.get("evidence") or []) or "근거 없음"
        lines.append(f"- {key}: 점수 {score if score is not None else '평가 불가'} ({evidence})")
    positive = analysis.get("positiveFactors") or []
    risk = analysis.get("riskFactors") or []
    lines.append(f"긍정 요인: {', '.join(positive) if positive else '없음'}")
    lines.append(f"위험 요인: {', '.join(risk) if risk else '없음'}")
    return "\n".join(lines)


def generate_narrative(analysis: dict[str, Any]) -> str | None:
    """설정된 LLM에게 이미 계산된 분석 초안의 서술을 맡긴다."""
    return _complete(SYSTEM_PROMPT, _build_prompt(analysis), max_tokens=400, error_label="narrative generation")


def _format_metric(value: Any, label: str, unit: str = "") -> str:
    return f"{label} {value}{unit}" if value is not None else f"{label} 데이터없음"


def _build_comparison_prompt(comparison: dict[str, Any]) -> str:
    """이미 계산된 비교 지표만 서술용 프롬프트로 옮긴다. 원본 시계열이나
    재무제표 원문은 넘기지 않는다."""
    lines = [
        f"비교 종목: {', '.join(comparison.get('symbols') or [])}",
        f"기준 시각: {comparison.get('asOf')}",
        f"동일 업종 여부: {'예' if comparison.get('sameSector') else '아니오'}",
    ]
    for item in comparison.get("items", []):
        parts = [
            _format_metric(item.get("sector"), "업종"),
            _format_metric(item.get("revenueGrowthPercent"), "매출성장률", "%"),
            _format_metric(item.get("operatingMarginPercent"), "영업이익률", "%"),
            _format_metric(item.get("per"), "PER"),
            _format_metric(item.get("pbr"), "PBR"),
            _format_metric(item.get("roe"), "ROE", "%"),
            _format_metric(item.get("debtRatio"), "부채비율", "%"),
            _format_metric(item.get("periodReturnPercent"), "최근 기간 수익률", "%"),
            _format_metric(item.get("volatilityPercent"), "변동성", "%"),
        ]
        lines.append(f"- {item.get('symbol')} ({item.get('name')}): " + ", ".join(parts))
    return "\n".join(lines)


def generate_comparison_narrative(comparison: dict[str, Any]) -> str | None:
    """설정된 LLM에게 이미 계산된 종목 비교표의 차이 설명을 맡긴다."""
    return _complete(
        COMPARISON_SYSTEM_PROMPT,
        _build_comparison_prompt(comparison),
        max_tokens=500,
        error_label="comparison narrative generation",
    )
