import json
import os
import re
from collections import Counter
from keybert import KeyBERT
from sentence_transformers import SentenceTransformer
from konlpy.tag import Okt

# 설정 & 모델 로드
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# 경로
DATA_DIR = os.path.join(BASE_DIR, "..", "Data", "raw", "naver")

# SBERT 모델
EMBEDDING_MODEL_NAME = "snunlp/KR-SBERT-V40K-klueNLI-augSTS"
embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)
kw_model = KeyBERT(model=embedding_model)

okt = Okt()

# 불용어(Stopwords) 강화
STOPWORDS = {
    "기자", "이번", "다음", "지난", "당시", "현재", "오늘", "내일", "어제",
    "그러나", "하지만", "또한", "그리고", "때문", "통해", "위해", "관련",
    "대한", "통한", "의한", "있다", "없다", "했다", "한다", "됐다", "된다",
    "밝혔다", "밝힌", "인한", "새로운", "모든", "많은", "같은", "그것",
    "경우", "정도", "사실", "부분", "내용", "결과", "자신", "사람", "생각",
    "문제", "자체", "가장", "일부", "진행", "확인", "시작", "계속", "이후",
    "포함", "제공", "대상", "기준", "이상", "이하", "전망", "예정", "계획",
    "최근", "주요", "각종", "가지", "여러", "매우", "다시", "바로", "역시",
    "가장", "크게", "달라", "만큼", "동안", "사이", "곳곳", "대로", "대해",
    "것으로", "것이", "것은", "것을", "등을", "등이", "등의", "등에",
    "무단", "배포", "금지", "뉴스", "저작권", "연합뉴스", "일보", "신문"
}

# 전처리 함수
def clean_word(word: str) -> str | None:
    """
    명사 전처리: 길이 제한, 숫자 제외, 불용어 제거
    """
    # 길이 제한 (1글자는 의미가 모호한 경우가 많음)
    if len(word) < 2:
        return None

    # 불용어 제거
    if word in STOPWORDS:
        return None
    
    # 숫자만 있는 경우 제외 (예: "100", "2024")
    if word.isdigit():
        return None

    return word


def extract_nouns_from_docs(docs: list[str]) -> list[str]:
    """모든 문서에서 명사만 추출하여 리스트로 반환"""
    all_nouns = []

    for doc in docs[:50]:
        # Okt.nouns()는 자동으로 조사를 제거하고 명사만 반환함
        nouns = okt.nouns(doc)
        for n in nouns:
            cleaned = clean_word(n)
            if cleaned:
                all_nouns.append(cleaned)
    
    return all_nouns


def load_news(category: str) -> list[str]:
    path = os.path.join(DATA_DIR, f"{category}.json")
    
    if not os.path.exists(path):
        return []

    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    # 본문이 있는 기사만 추출
    return [item["content"] for item in data if "content" in item and item["content"]]


# 핵심 키워드 추출 함수
def extract_keywords(category: str, top_n: int = 10): # -> List[Tuple[str, float]]
    """
    특정 카테고리의 뉴스들을 모아 상위 키워드를 (단어, 점수) 형태로 반환
    """
    docs = load_news(category)

    if not docs:
        return []

    # 후보 명사 추출 (빈도수 기반)
    nouns = extract_nouns_from_docs(docs)
    candidate_words = [word for word, _ in Counter(nouns).most_common(300)]

    if not candidate_words:
        return []

    # 2. 전체 문서를 하나의 큰 텍스트로 병합 (문맥 반영)
    full_text = " ".join(docs[:30])  

    # 3. KeyBERT 실행
    keywords = kw_model.extract_keywords(
        full_text,
        candidates=candidate_words,
        keyphrase_ngram_range=(1, 1), 
        top_n=top_n,
        use_mmr=True, 
        diversity=0.3
    )

    # keywords 구조: [('반도체', 0.78), ('삼성', 0.65), ...]
    return keywords 


if __name__ == "__main__":
    categories = ["economy", "politics", "society", "it", "world", "culture"]

    for category in categories:
        print(f"\n [{category.upper()}] 키워드")
        result = extract_keywords(category)
        print(result)