from __future__ import annotations

import os
from typing import Any

_client = None

SYSTEM_PROMPT = """
## 역할

당신은 금융 데이터 분석 보조원입니다.

이미 계산된 성장성·수익성·밸류에이션·모멘텀 점수와 그 근거를 바탕으로,
종목 분석 결과를 이해하기 쉬운 한국어 설명으로 변환합니다.

당신의 역할은 새로운 점수를 계산하거나 투자 판단을 제공하는 것이 아니라,
입력으로 제공된 분석 결과를 설명하는 것입니다.

## 분석 지침

1. 반드시 제공된 점수와 근거만 사용하세요 (성장성, 수익성, 밸류에이션, 모멘텀 점수 및 근거, 긍정 요인, 위험 요인).
2. 평가 불가로 표시된 항목은 데이터 부족을 그대로 언급하고, 임의로 값을 채우지 마세요.
3. 참고용 분석이라는 점과 데이터의 한계를 함께 언급하세요.

## 금지 사항

- 제공되지 않은 가격, 수치, 사실을 새로 만들어내지 마세요.
- 투자 추천이나 매수/매도 의견을 제시하지 마세요.

## 출력 형식

- 한국어
- 3~5문장의 간단한 종목 분석 요약
""".strip()

COMPARISON_SYSTEM_PROMPT = """
## 역할

당신은 금융 데이터 분석 보조원입니다.

이미 계산된 종목별 비교 지표(매출성장률, 영업이익률, PER, PBR, ROE, 부채비율,
기간 수익률, 변동성 등)를 바탕으로, 종목 간 차이를 이해하기 쉬운 한국어
설명으로 변환합니다.

당신의 역할은 새로운 지표를 계산하거나 투자 판단을 제공하는 것이 아니라,
입력으로 제공된 비교 결과를 설명하는 것입니다.

## 분석 지침

1. 반드시 제공된 종목별 지표만 근거로 사용하세요.
2. 어느 종목이 어떤 지표에서 우위인지 사실 기반으로 설명하세요.
3. 데이터가 없는 지표는 "비교할 수 없다"고 명시하세요.

## 금지 사항

- 제공되지 않은 수치나 사실을 새로 만들어내지 마세요.
- 투자 추천이나 매수/매도 의견을 제시하지 마세요.

## 출력 형식

- 한국어
- 4~6문장의 비교 설명
""".strip()

NEWS_IMPACT_SYSTEM_PROMPT = """
## 역할

당신은 금융 뉴스 영향 분석 보조원입니다.

이미 계산된 뉴스 헤드라인의 긍정·부정·중립 분류와 최근 주가 모멘텀을 바탕으로,
현재 관찰 가능한 뉴스 분위기와 주가 움직임의 관계를 설명합니다.

당신의 역할은 새로운 분석 결과를 계산하거나 투자 판단을 제공하는 것이 아니라,
입력으로 제공된 분석 결과를 이해하기 쉬운 한국어 설명으로 변환하는 것입니다.

## 분석 지침

1. 반드시 사용자 메시지에 제공된 정보만 사용하세요.

사용 가능한 정보는 다음과 같습니다.
- 종목
- 기준 시각
- 뉴스 헤드라인
- 헤드라인별 긍정·부정·중립 분류
- 분류의 근거가 된 키워드
- 뉴스 분류 집계
- 최근 기간 수익률
- 거래량/평균 거래량 비율

2. 제공되지 않은 정보를 추론하거나 추가하지 마세요.

다음 내용을 임의로 생성해서는 안 됩니다.
- 뉴스 본문의 내용
- 기업의 실적이나 재무 상태
- 산업 또는 시장 상황
- 사건의 구체적인 원인
- 추가적인 가격 또는 거래량 수치
- 뉴스 발표 이후 실제 투자자 반응

3. 뉴스와 주가 움직임의 인과관계를 확정하지 마세요.

예:
- "이 뉴스 때문에 주가가 상승했습니다."
- "악재로 인해 주가가 하락했습니다."

대신 다음과 같이 표현하세요.
- "~와 같은 방향을 보이고 있습니다."
- "~와 연관되었을 가능성은 있습니다."
- "~로 해석될 수 있습니다."
- "다만 직접적인 인과관계는 확인할 수 없습니다."

4. 뉴스 분류 결과를 절대적인 사실로 취급하지 마세요.

긍정·부정·중립 분류는 키워드 기반 1차 분류 결과이며,
실제 시장 영향과 반드시 일치하는 것은 아닙니다.

5. 뉴스 방향과 주가 모멘텀이 일치하는 경우에도
두 정보가 같은 방향을 나타낸다는 수준까지만 설명하세요.

6. 뉴스 방향과 주가 모멘텀이 서로 다르면
이를 모순이라고 단정하지 말고 불확실성을 설명하세요.

7. 근거가 부족하거나 의미 있는 관계를 확인하기 어려우면
"확인 가능한 영향 없음"이라고 명시하세요.

## 금지 사항

- 향후 주가 상승 또는 하락 예측
- 목표 주가 제시
- 매수·매도·보유 의견
- 투자 추천
- 제공되지 않은 사실 생성
- 뉴스와 주가의 인과관계 단정

## 출력 형식

- 한국어
- 4~6문장
- 뉴스 분류의 전반적인 방향을 먼저 설명
- 주요 헤드라인 또는 키워드를 필요한 범위에서 언급
- 최근 주가 모멘텀과 뉴스 방향을 비교
- 마지막에는 불확실성 또는 인과관계를 확인할 수 없다는 점을 표현
- 근거가 부족하면 반드시 "확인 가능한 영향 없음"을 포함
""".strip()


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
    """이미 계산된 점수·근거를 LLM 입력 형식으로 변환한다.

    모델에는 원본 provider 데이터를 넘기지 않고 계산도 맡기지 않는다.
    이미 계산된 수치만 설명하게 해서, 실제 데이터에서 나오지 않은
    가격이나 지표를 스스로 만들어내지 못하게 하기 위함이다.
    """
    lines = [
        "다음은 금융 종목 분석 설명에 사용할 분석 데이터입니다.",
        "아래 데이터만 근거로 설명을 작성하세요.",
        "",
        "[종목 정보]",
        f"종목: {analysis.get('symbol')}",
        f"기준 시각: {analysis.get('asOf')}",
        "",
        "[점수 및 근거]",
    ]
    for key in ("growth", "profitability", "valuation", "momentum"):
        section = analysis.get(key) or {}
        score = section.get("score")
        evidence = ", ".join(section.get("evidence") or []) or "근거 없음"
        lines.append(f"- {key}: 점수 {score if score is not None else '평가 불가'} ({evidence})")

    positive = analysis.get("positiveFactors") or []
    risk = analysis.get("riskFactors") or []
    lines.extend([
        "",
        "[긍정·위험 요인]",
        f"긍정 요인: {', '.join(positive) if positive else '없음'}",
        f"위험 요인: {', '.join(risk) if risk else '없음'}",
    ])
    return "\n".join(lines)


def generate_narrative(analysis: dict[str, Any]) -> str | None:
    """설정된 LLM에게 이미 계산된 분석 초안의 서술을 맡긴다."""
    return _complete(SYSTEM_PROMPT, _build_prompt(analysis), max_tokens=400, error_label="narrative generation")


def _format_metric(value: Any, label: str, unit: str = "") -> str:
    return f"{label} {value}{unit}" if value is not None else f"{label} 데이터없음"


def _build_comparison_prompt(comparison: dict[str, Any]) -> str:
    """이미 계산된 비교 지표를 LLM 입력 형식으로 변환한다. 원본 시계열이나
    재무제표 원문은 넘기지 않는다."""
    lines = [
        "다음은 금융 종목 비교 설명에 사용할 분석 데이터입니다.",
        "아래 데이터만 근거로 설명을 작성하세요.",
        "",
        "[비교 정보]",
        f"비교 종목: {', '.join(comparison.get('symbols') or [])}",
        f"기준 시각: {comparison.get('asOf')}",
        f"동일 업종 여부: {'예' if comparison.get('sameSector') else '아니오'}",
        "",
        "[종목별 지표]",
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


def _build_news_impact_prompt(analysis: dict[str, Any]) -> str:
    """이미 계산된 뉴스 분류 결과와 주가 모멘텀을 LLM 입력 형식으로 변환한다."""

    lines = [
        "다음은 금융 뉴스 영향 설명에 사용할 분석 데이터입니다.",
        "아래 데이터만 근거로 설명을 작성하세요.",
        "",
        "[종목 정보]",
        f"종목: {analysis.get('symbol')}",
        f"기준 시각: {analysis.get('asOf')}",
        "",
        "[뉴스 분류 요약]",
    ]

    summary = analysis.get("summary") or {}
    lines.append(
        f"긍정: {summary.get('positiveCount', 0)}건 / "
        f"부정: {summary.get('negativeCount', 0)}건 / "
        f"중립: {summary.get('neutralCount', 0)}건"
    )

    lines.extend([
        "",
        "[최근 주가 모멘텀]",
    ])

    momentum = analysis.get("momentum")

    if momentum:
        lines.append(
            f"기간 수익률: "
            f"{_format_metric(momentum.get('periodReturnPercent'), '', '%').strip()}"
        )
        lines.append(
            f"거래량/평균 거래량 비율: "
            f"{_format_metric(momentum.get('volumeRatio'), '', '배').strip()}"
        )
    else:
        lines.append("데이터 없음")

    lines.extend([
        "",
        "[뉴스 헤드라인]",
    ])

    for article in analysis.get("articles", []):
        keywords = article.get("matchedKeywords") or {}

        matched = ", ".join(
            keywords.get("positive", [])
            + keywords.get("negative", [])
        ) or "매칭 키워드 없음"

        lines.append(
            f"- 분류: {article.get('impactLabel')}\n"
            f"  제목: {article.get('title')}\n"
            f"  출처: {article.get('publisher') or '알 수 없음'}\n"
            f"  근거 키워드: {matched}"
        )

    return "\n".join(lines)


def generate_news_impact_narrative(analysis: dict[str, Any]) -> str | None:
    """설정된 LLM에게 이미 계산된 뉴스 헤드라인 분류·모멘텀의 서술을 맡긴다."""
    return _complete(
        NEWS_IMPACT_SYSTEM_PROMPT,
        _build_news_impact_prompt(analysis),
        max_tokens=500,
        error_label="news impact narrative generation",
    )
