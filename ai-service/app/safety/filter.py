"""규칙 기반 안전성 필터 (표준 라이브러리만 사용, 외부 호출 없음).

사내 sentence-pipeline(2026-07-27)에서 106케이스로 튜닝된 매칭 로직을 그대로 옮겼다.
이 저장소에서는 정책 §12.4 "공동 책 생성 완료 전" 검수에만 쓴다.

[핵심 설계 — 왜 이렇게 만들었나]
입력을 한 가지로 정규화해 한 번 비교하지 않는다. 우회 기법마다 무력화되는 표현이 다르므로
입력을 세 가지 변형으로 만들어 각 변형에 맞는 사전과 대조한다.

  변형          방어하는 우회                            매칭에 사용
  ────────────────────────────────────────────────────────────────
  norm (표면)   공백/특수문자/숫자삽입/문자반복           금지어 표면
  jamo (자모)   키보드오타(tlqkf)/자모분리/필러모음/오타   금지어 자모 시퀀스
  cons (초성)   초성체(ㅅㅂ)                              자모우회 사전
  raw  (원문)   -                                        PII 정규식·인젝션·spam

[오탐 최소화 원칙 — 창작 서비스의 생명]
1) 초성 우회는 원문에 홀로 찍힌 자음에만 대조 → '사방'이 ㅅㅂ로 오인되지 않는다.
   생성 결과에서는 서로 붙어 있는 자음 런만 보아 긴 본문의 떨어진 자음이 합쳐지지 않게 한다.
2) allowlist로 벤인 복합어·연결형을 억제 → '시발점', '보지 못했다', '걸레로' 등.
3) 문맥 애매어는 block이 아니라 flag로 두고 최종 판정을 상위로 넘긴다.

[이 저장소에서 달라진 점]
적발된 금지어 원문과 정규화된 입력 문자열을 결과에 담지 않는다. 원본 구현은 매칭된 단어를
그대로 실어 보냈지만, 그러면 호출자가 로그에 남기는 순간 금지어 사전과 사용자 문장이 함께
유출된다. 호출자에게는 판정과 계층·범주 라벨만 돌려준다.
"""

from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Literal


DENYLIST_PATH = Path(__file__).with_name("denylist.json")

SafetyAction = Literal["block", "flag", "pass"]

# ---- 한글 자모 분해 테이블 ----
CHO = list("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")
JUNG = list("ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ")
JONG = [
    "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ",
    "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ",
]
CONSONANTS = set("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㄳㄵㄶㄺㄻㄼㄽㄾㄿㅀㅄ")

# QWERTY -> 두벌식 (키보드 오타 우회: tlqkf -> 시발)
KEYBOARD = {
    "q": "ㅂ", "w": "ㅈ", "e": "ㄷ", "r": "ㄱ", "t": "ㅅ", "y": "ㅛ", "u": "ㅕ",
    "i": "ㅑ", "o": "ㅐ", "p": "ㅔ", "a": "ㅁ", "s": "ㄴ", "d": "ㅇ", "f": "ㄹ",
    "g": "ㅎ", "h": "ㅗ", "j": "ㅓ", "k": "ㅏ", "l": "ㅣ", "z": "ㅋ", "x": "ㅌ",
    "c": "ㅊ", "v": "ㅍ", "b": "ㅠ", "n": "ㅜ", "m": "ㅡ",
}
KEYBOARD_SHIFT = {"Q": "ㅃ", "W": "ㅉ", "E": "ㄸ", "R": "ㄲ", "T": "ㅆ", "O": "ㅒ", "P": "ㅖ"}

_SEPARATORS = re.compile(r"[\s\.\-_~*/·ㆍ|+^,'\"`!?()\[\]{}<>@#$%&:;\\]")
_NONSPACE_SEPARATORS = re.compile(r"[\.\-_~*/·ㆍ|+^,'\"`!?()\[\]{}<>@#$%&:;\\]")


@dataclass(frozen=True)
class SafetyVerdict:
    """안전성 판정 결과.

    적발된 표현 자체는 담지 않는다. `labels`는 "deny:profanity"처럼 계층과 범주만 담은
    라벨이라 로그에 남겨도 사용자 문장이나 금지어 사전이 드러나지 않는다.
    """

    action: SafetyAction
    labels: tuple[str, ...] = ()

    @property
    def blocked(self) -> bool:
        return self.action == "block"


def _decompose_syllable(character: str) -> str:
    """한글 음절 하나를 초성+중성+종성 자모로 분해한다.

    한글은 유니코드에 규칙적으로 배열된다: code = 0xAC00 + (초*21 + 중)*28 + 종.
    역산하면 초=code//588, 중=(code%588)//28, 종=code%28이다. (예: 시 → ㅅㅣ, 발 → ㅂㅏㄹ)
    """
    code = ord(character) - 0xAC00
    if 0 <= code < 11172:
        cho, remainder = divmod(code, 588)
        jung, jong = divmod(remainder, 28)
        return CHO[cho] + JUNG[jung] + JONG[jong]
    return character


def strip_separators(value: str, *, keep_spaces: bool = False) -> str:
    """[변형 norm] 표면 문자열 매칭용. 구분자와 숫자를 지워 삽입형 우회를 무력화한다.

    '시 발' / '시-발' / '씨★발' → '시발'  (공백·특수문자)
    '시1발'                     → '시발'  (숫자 삽입)
    '씨이이이발'                 → '씨이발' (3연속 이상 반복 축약)

    keep_spaces=True면 공백만 남긴다('야, 설마' → '야 설마'). 생성 본문 검수는 이 변형을
    block 판정의 기준으로 쓴다. 긴 창작 본문에서 공백까지 지우면 '야, 설마'가 '야설'로
    결합되는 식의 어절 경계 붕괴 오탐이 흔하기 때문이다. 삽입형 우회는 모델에 입력되기
    전의 사용자 문장을 위한 방어라, 모델이 쓴 본문에는 공백 붕괴 매칭을 block 근거로
    쓰지 않아도 실제 금지어 표기는 어절 안에서 그대로 드러난다.
    """
    normalized = unicodedata.normalize("NFKC", value).lower()
    if keep_spaces:
        normalized = _NONSPACE_SEPARATORS.sub("", normalized)
        normalized = re.sub(r"\s+", " ", normalized).strip()
    else:
        normalized = _SEPARATORS.sub("", normalized)
    normalized = re.sub(r"\d", "", normalized)
    return re.sub(r"(.)\1{2,}", r"\1", normalized)


def to_jamo(value: str) -> str:
    """[변형 jamo] 자모 시퀀스 매칭용. 세 가지 우회를 한 번에 흡수한다.

    (a) 완성형 한글을 자모로 분해해 부분 치환·오타를 자모 겹침으로 포착한다.
    (b) 영타(QWERTY)를 두벌식으로 remap 한다. (tlqkf → ㅅㅣㅂㅏㄹ)
    (c) 필러 모음 에코를 제거한다. (씨이발 → ㅆㅣㅂㅏㄹ)
    """
    converted = []
    for character in value:
        if "가" <= character <= "힣":
            converted.append(_decompose_syllable(character))
        elif character in KEYBOARD_SHIFT:
            converted.append(KEYBOARD_SHIFT[character])
        elif character.lower() in KEYBOARD:
            converted.append(KEYBOARD[character.lower()])
        else:
            converted.append(character)
    # '모음 + (ㅇ + 같은 모음)+' → 모음. '아이'처럼 모음이 다른 경우는 건드리지 않는다.
    return re.sub(r"([ㅏ-ㅣ])(?:ㅇ\1)+", r"\1", "".join(converted))


def consonant_run(value: str) -> str:
    """[변형 cons] 원문에서 홀로 찍힌 자음만 이어 붙인다.

    초성체 우회(ㅅㅂ)는 여기에만 대조한다. 완성형 음절의 초성은 뽑지 않으므로
    '사방'이나 '수반' 같은 정상어가 오인되지 않는다.
    """
    return "".join(character for character in value if character in CONSONANTS)


def contiguous_consonant_runs(value: str) -> tuple[str, ...]:
    """서로 붙어 있는 독립 자음만 런으로 묶는다.

    긴 생성 본문에서 멀리 떨어진 자음들이 우연히 하나의 금지 시퀀스로 합쳐지는 것을 막는다.
    """
    runs: list[str] = []
    current: list[str] = []
    for character in value:
        if character in CONSONANTS:
            current.append(character)
            continue
        if current:
            runs.append("".join(current))
            current.clear()
    if current:
        runs.append("".join(current))
    return tuple(runs)


def _mask_allowlisted(value: str, allowlist: list[str]) -> str:
    """허용어가 차지하는 범위만 마스킹한다.

    문장에 허용어가 하나 있다는 이유로 같은 문장의 별도 금지어까지 허용하지 않도록
    금지어 표면·자모 매칭에 사용할 문자열에서 허용어 위치만 제거한다.
    """
    masked = list(value)
    for allowed in allowlist:
        start = 0
        while True:
            index = value.find(allowed, start)
            if index < 0:
                break
            masked[index:index + len(allowed)] = ["\0"] * len(allowed)
            start = index + len(allowed)
    return "".join(masked)


class SafetyFilter:
    """금지어·PII·인젝션·도배를 규칙으로 판정한다."""

    def __init__(self, denylist_path: Path = DENYLIST_PATH):
        denylist = json.loads(denylist_path.read_text(encoding="utf-8"))
        self._entries = [
            {
                "term": entry["term"],
                "category": entry["category"],
                "severity": entry["severity"],
                "jamo": to_jamo(strip_separators(entry["term"])),
                "syllable_count": sum(
                    1 for character in entry["term"] if "가" <= character <= "힣"
                ),
            }
            for entry in denylist["entries"]
        ]
        self._consonant = denylist["consonant_evasion"]
        self._injection = denylist["injection_patterns"]
        self._allowlist = [strip_separators(term) for term in denylist["allowlist"]]
        self._allowlist_spaced = [
            strip_separators(term, keep_spaces=True) for term in denylist["allowlist"]
        ]
        # (이름, 입력 severity, 생성 결과 severity, 패턴). 생성 결과의 연락처·카드·이메일은
        # 허구의 서술일 수 있어 flag로 낮추고, 오탐 가능성이 낮은 주민등록번호만 block을 유지한다.
        self._pii = [
            ("주민등록번호", "block", "block", re.compile(r"\d{6}[-\s]?[1-4]\d{6}")),
            ("휴대폰번호", "block", "flag", re.compile(r"01[016789][-\s]?\d{3,4}[-\s]?\d{4}")),
            ("카드번호", "block", "flag", re.compile(r"(?:\d{4}[-\s]?){3}\d{4}")),
            ("이메일", "block", "flag", re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")),
            ("유선전화", "flag", "flag", re.compile(r"0(?:2|[3-6]\d)[-\s]?\d{3,4}[-\s]?\d{4}")),
            ("계좌번호", "flag", "flag", re.compile(r"\d{2,6}[-\s]\d{2,6}[-\s]\d{2,8}")),
        ]

    def check(self, text: str) -> SafetyVerdict:
        """다섯 계층을 각자 알맞은 변형에 대조하고 가장 센 severity로 판정한다."""
        return self._check(
            text,
            check_injection=True,
            check_spam=True,
            generated_content=False,
        )

    def check_generated_content(self, text: str) -> SafetyVerdict:
        """생성된 책 본문에 적합한 계층만 검사한다.

        프롬프트 인젝션과 도배 규칙은 모델에 전달하기 전 사용자 입력을 위한 규칙이다.
        모델이 만든 문학 문장에는 금지어·자모 우회·PII만 적용해 정상적인 서술을
        인젝션 표현으로 오인하지 않는다. PII는 주민등록번호만 차단하고 연락처·카드·이메일처럼
        허구의 서술일 수 있는 표현은 flag로 남긴다.
        """
        return self._check(
            text,
            check_injection=False,
            check_spam=False,
            generated_content=True,
        )

    def _check(
        self,
        text: str,
        *,
        check_injection: bool,
        check_spam: bool,
        generated_content: bool,
    ) -> SafetyVerdict:
        raw = text or ""
        norm = strip_separators(raw)
        matchable_norm = _mask_allowlisted(norm, self._allowlist)
        if generated_content:
            # 생성 본문의 block 기준은 공백을 보존한 변형이다. 공백까지 지운 변형이나
            # 자모 시퀀스에서만 걸린 매칭은 어절·음절 경계를 넘은 우연 결합일 확률이
            # 높으므로('야, 설마'→야설, '강가는'→강간) block으로 승격하지 않고 flag로
            # 남긴다. 자모 대조도 어절 단위로 잘라 어절 경계를 넘는 결합을 막는다.
            matchable_spaced = _mask_allowlisted(
                strip_separators(raw, keep_spaces=True), self._allowlist_spaced
            )
            jamo_candidates = tuple(to_jamo(token) for token in matchable_spaced.split(" "))
        else:
            matchable_spaced = None
            jamo_candidates = (to_jamo(matchable_norm),)
        consonant_candidates = (
            contiguous_consonant_runs(raw)
            if generated_content
            else (consonant_run(raw),)
        )

        hits: list[tuple[str, str]] = []

        # 1) PII — 정규화 전 원문에서 검사한다.
        for name, input_severity, generated_severity, pattern in self._pii:
            if pattern.search(raw):
                severity = generated_severity if generated_content else input_severity
                hits.append((severity, f"pii:{name}"))

        # 2) 금지어 — 표면(삽입형 우회) 또는 자모(키보드·자모 우회) 어느 쪽에 걸려도 적발한다.
        for entry in self._entries:
            in_surface = entry["term"] in matchable_norm
            # 한 음절 금지어의 자모는 정상적인 다음 음절과 경계를 구분할 수 없다.
            # 예: '좆'(ㅈㅗㅈ)이 '조작'(ㅈㅗㅈㅏㄱ)에 걸리므로 표면 일치만 허용한다.
            in_jamo = (
                entry["syllable_count"] >= 2
                and len(entry["jamo"]) >= 3
                and any(entry["jamo"] in candidate for candidate in jamo_candidates)
            )
            if not (in_surface or in_jamo):
                continue
            severity = entry["severity"]
            if generated_content and entry["term"] not in matchable_spaced:
                # 어절 안에서 표면으로 드러나지 않았다면(공백 붕괴 결합 또는 자모 단독
                # 적발) 이미 과금이 끝난 책 전체를 버릴 근거로는 약하다.
                severity = "flag"
            hits.append((severity, f"deny:{entry['category']}"))

        # 3) 자모 우회 — 독립 자음 런에서만 본다.
        for evasion in self._consonant:
            if any(evasion["seq"] in run for run in consonant_candidates):
                hits.append((evasion["severity"], "consonant:evasion"))

        # 4) 프롬프트 인젝션
        if check_injection:
            lowered = unicodedata.normalize("NFKC", raw).lower()
            for pattern in self._injection:
                if pattern["pat"].lower() in lowered:
                    hits.append((pattern["severity"], "injection:injection"))

        # 5) 도배·무의미
        if check_spam:
            hits.extend(self._spam(raw))

        # 안전측 판정(worst-wins).
        action: SafetyAction = "pass"
        if any(severity == "block" for severity, _ in hits):
            action = "block"
        elif any(severity == "flag" for severity, _ in hits):
            action = "flag"
        return SafetyVerdict(action=action, labels=tuple(sorted({label for _, label in hits})))

    def _spam(self, raw: str) -> list[tuple[str, str]]:
        found: list[tuple[str, str]] = []
        if re.search(r"https?://|www\.|\.com|\.net|\.kr", raw, re.IGNORECASE):
            found.append(("flag", "spam:url"))
        tokens = raw.split()
        if tokens and max((tokens.count(token) for token in set(tokens)), default=0) >= 4:
            found.append(("flag", "spam:repeat_token"))
        letters = [character for character in raw if not character.isspace()]
        if len(letters) >= 8:
            hangul = sum(1 for character in letters if "가" <= character <= "힣")
            if hangul / len(letters) < 0.3:
                found.append(("flag", "spam:low_hangul_ratio"))
        return found
