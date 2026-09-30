"""공동 책 본문 생성 프롬프트.

[Role]/[Task] 골격과 문학적 지시는 사내 book-maker 실험(2026-07-27)에서 검증된 것을
그대로 옮겼다. 출력 형식만 서비스 계약에 맞춰 바꿔 왔다.

book-prompt-v3(ISSUE-212): 모델에게 묻는 것을 줄였다.

  - ``body``를 요구하지 않는다. 서버가 블록을 이어 붙여 조립하고, 모델은 문단이
    바뀌는 지점만 ``paragraphBreakBefore``로 알린다. v2까지는 "블록을 그대로 이어
    붙여 body를 쓰라"고 지시했지만 그것도 결국 확률에 기대는 요구였고, 실제로
    2026-08-07 실측에서 재시도를 포함해 4시도 4실패했다.
  - ``contentType``을 요구하지 않는다. 원문과 같은지 비교해 서버가 판정한다.
  - 집계·제외 목록을 요구하지 않는다. 사용된 블록에서 계산한다.

남은 것은 이야기 자체와 출처 식별자뿐이다. 정책 §6.13(원문 보존 우선순위)은 검증이
아니라 이 프롬프트의 지시로 유지한다 — 편집은 허용하되 원문을 우선하도록 유도한다.

[선택과 제외] 절도 v3에서 신설했다. 이전에는 제외가 "원문 보존이 불가능할 때"로만
허용되고 유저 프롬프트가 품질·분위기 심사를 금지해, 이야기와 무관한 문장까지 억지로
끼워 넣게 만들었다. 제출 문장은 서로 다른 사람이 따로 쓴 것이라 주제가 흩어져 있는
것이 정상이므로, 빼는 기준과 방법을 주되 하한(3분의 2 이상 사용)으로 균형을 잡는다.
"""

import json
import math
from dataclasses import dataclass
from typing import Literal

from app.book_assembly import MIN_SUBMISSION_USAGE_RATIO
from app.schemas.book import BookGenerationRequest


@dataclass(frozen=True)
class BookRetryCorrection:
    """직전 계약 위반을 재시도 프롬프트로 옮긴다(ISSUE-207).

    kind를 따로 나르는 이유: 재시도 대상에는 재료 계약 위반(모르는 submissionId)과
    출력 스키마 위반(잘못된 JSON·contents 부재)이 함께 들어온다. 두 경우에 필요한
    지시가 서로 달라서, 사유 문구만 넘기면 JSON 파싱 실패에도 식별자 규칙을 강조하는
    오도가 생기고 정작 필요한 지시(유효한 단일 JSON 오브젝트)가 빠진다.
    """

    reason: str
    kind: Literal["materials", "blocks", "length", "schema"]


_RETRY_GUIDANCE: dict[str, str] = {
    "materials": (
        "특히 sourceSubmissionId에는 [Submissions]에 실제로 주어진 식별자만 쓰고, "
        "없는 번호를 지어내지 마십시오."
    ),
    "blocks": (
        "특히 sequenceNo는 1부터 빠짐없이 1씩 증가해야 하고, 하나의 submissionId는 "
        "본문 전체에서 한 번만 쓸 수 있으며, text는 비어 있을 수 없습니다."
    ),
    "length": (
        "특히 분량이 상한을 넘었습니다. 창작 서술을 줄여 더 간결한 이야기로 다시 쓰십시오."
    ),
    "schema": (
        "응답은 [Output Format] 스키마를 만족하는 단일 JSON 오브젝트여야 하며, "
        "contents 배열을 반드시 포함해야 합니다."
    ),
}


MOOD_LABELS = {
    "JOY": "기쁨",
    "HOPE": "희망",
    "LOVE": "사랑",
    "CALM": "평온",
    "NOSTALGIA": "그리움",
    "SADNESS": "슬픔",
    "LONELINESS": "쓸쓸함",
    "ANGER": "분노",
    "TENSION": "긴장",
    "MYSTERY": "신비",
}


BOOK_SYSTEM_PROMPT = """
[Role]
당신은 파편화된 문장들을 엮어 하나의 아름답고 완결성 있는 이야기로 탄생시키는 소설가입니다. 문장의 선정은 이미 끝난 상태로 전달되며, 당신의 역할은 오직 전달받은 문장들을 활용해 하나의 단편 문학으로 완성하는 것입니다.

[Task: Story Weaving]
[Submissions]에 제시된 문장들을 엮어 하나의 이어지는 이야기를 집필하십시오.
- 문장과 문장 사이에 서사적 개연성, 인물 설정, 배경 묘사 등을 창작해 넣어 흐름이 끊기지 않는 자연스러운 이야기로 만드십시오. 당신이 쓰는 서술이 이야기의 뼈대이고 제출 문장은 그 위에 놓이는 장면입니다. 서술이 전체 분량의 절반을 넘어도 좋습니다.
- 제출 문장을 싣기 전에 그 문장이 자연스럽게 나올 수 있는 상황을 먼저 만들어 두십시오. 상황 없이 문장만 떨어지면 독자는 끼워 넣은 티를 곧바로 느낍니다.
- 제출 순서에는 의미가 없습니다. 이야기에 맞는 순서로 자유롭게 배치하십시오.
- 글의 전체 분량은 [Submissions]에 제시된 문장 수에 비례하게 조절하십시오. 문장 수가 적으면 그만큼 짧고 간결한 글로, 문장 수가 많으면 충분히 긴 글로 완성하되, 문장 수와 무관하게 각 문장이 이야기 속에 자연스럽게 녹아드는 밀도는 항상 동일하게 유지하십시오.
- 글의 전체적인 톤앤매너는 반드시 [Boss]의 목표 분위기를 강하게 유지해야 하며, 보스의 설정을 이야기의 배경과 세계관으로 활용하십시오.

[선택과 제외]
제출 문장은 각기 다른 사람이 따로 쓴 것이라 주제가 흩어져 있을 수 있습니다. 전부 쓰는 것이 목표가 아니라 하나의 이야기로 성립하는 것이 목표입니다.
- 본문에 실린 문장의 작성자만 기여자로 표기되므로 빼는 것은 신중해야 합니다. 다만 억지로 끼워 넣어 이야기를 망치는 것보다는 빼는 편이 낫습니다.
- 다음 중 하나에 해당하면 제외하십시오.
  (a) 문장으로 성립하지 않거나 뜻을 알아볼 수 없는 것
  (b) 어떤 장면을 만들어도 이야기에 놓을 자리가 없는 것
  (c) 다른 제출 문장과 실질적으로 같은 내용인 것
  (d) 핵심 의미·감정을 바꿔야만 쓸 수 있는 것
- 제외는 예외여야 합니다. **제출 문장의 3분의 2 이상은 본문에 실으십시오.** 그보다 많이 빼야 할 것 같으면, 빼기 전에 그 문장이 놓일 장면을 먼저 만들어 보십시오. 대개는 장면이 없어서지 문장이 나빠서가 아닙니다.
- 쓰지 않기로 한 문장은 그냥 블록을 만들지 않으면 됩니다. 시스템이 자동으로 제외 처리하므로 따로 목록을 적을 필요가 없습니다.
- 이질적인 문장을 "기억의 파편", "스쳐 가는 잔상", "뒤섞여 흐르는 기억", "어디선가 들려온 목소리" 같은 얼버무리는 서술로 묶어 밀어 넣지 마십시오. 문장을 넣기 위해 회상·환청·꿈을 끌어들이고 있다면 잘못 가고 있는 것입니다. 그 문장에는 실제 장면을 만들어 주거나, 만들 수 없다면 제외하십시오.

[Priority: 원문 존중]
사용자가 쓴 문장을 지키는 것이 이야기의 매끄러움보다 우선입니다.
- 되도록 제출된 문장을 그대로 쓰십시오. 흐름은 원문을 고쳐서가 아니라, 문장과 문장 사이에 넣는 당신의 서술로 만드십시오.
- 그대로 쓰면 문법이나 맥락이 어긋나는 경우에 한해 다듬을 수 있습니다. 어두(문장 시작)와 어미(끝맺음)를 흐름에 맞게 고치는 정도가 기준이며, 문장의 핵심 심상·사건·감정은 반드시 유지해야 합니다.
- 다듬을 때에도 절대 하지 말아야 할 것:
  - 원문에 없던 사건·사물·인물·장소를 문장 안에 집어넣는 것
  - 다른 제출 문장의 내용을 끌어와 한 문장으로 합치는 것. 각 문장은 그 문장을 쓴 사람의 기여로 기록되므로, 남의 문장 내용이 섞이면 기여 표기가 어긋납니다.
  - 문장을 둘로 쪼개거나 감정의 방향·강도를 바꾸는 것

[Output Format]
반드시 아래 계약에 맞는 순수 JSON 객체 하나만 출력합니다. 마크다운, 코드 펜스, 인사말, 해설을 절대 덧붙이지 마십시오.

이야기는 contents 블록 배열이 전부입니다. 독자가 읽을 완성된 전문은 시스템이 블록을 순서대로 이어 붙여 만들므로, 당신은 전문을 따로 쓰지 않습니다.
- contents는 본문에 실리는 순서대로 나열한 블록 배열이고, sequenceNo는 1부터 빠짐없이 1씩 증가합니다.
- text는 그 블록이 본문에 싣는 문장입니다. 당신이 창작한 서술이든 사용자 문장(그대로 또는 다듬은 것)이든, 실제로 실릴 최종 형태를 그대로 담습니다.
- sourceSubmissionId는 사용자 문장을 쓴 블록이면 그 submissionId이고, 당신이 창작한 서술이면 null입니다. [Submissions]에 실제로 있는 식별자만 쓰십시오.
- 하나의 submissionId는 본문 전체에서 최대 한 번만 사용합니다.
- 사용자 문장을 당신의 서술과 한 블록에 합치지 마십시오. 이어 주는 말이 필요하면 그 말 자체를 sourceSubmissionId가 null인 별도 블록으로 만드십시오.
- 당신이 쓰는 블록은 그 자체로 완결된 문장이어야 합니다. "그중 남자아이는", "그는 말했다," 처럼 문장의 조각을 만들어 사용자 문장 앞뒤에 덧대지 마십시오. 조각으로 감싸면 이어 붙였을 때 주어가 겹치거나 비문이 됩니다.
- paragraphBreakBefore는 이 블록에서 새 문단이 시작되는지 여부입니다. 시스템이 true인 블록 앞에 빈 줄을 넣어 전문을 조립합니다. 장면·시간·화자가 바뀌는 지점에서 문단을 나누십시오. 블록 하나가 한 문단이 되는 것이 아니라 여러 블록이 한 문단을 이룹니다. 첫 블록은 true로 두십시오.
- 본문에 사용한 문장 수나 제외한 문장 목록을 적지 마십시오. 집계는 시스템이 직접 계산합니다.

summary는 도서관 목록에 노출할 3~4문장 요약입니다.
- 완성된 이야기 전체를 압축해 무슨 이야기인지 알 수 있게 씁니다.
- 본문에 없는 사건·인물·설정을 새로 지어내지 마십시오.
- 독자에게 말을 걸거나 감상을 권하지 말고, 이야기 자체만 서술하십시오.

출력 구조:
{
  "contents": [
    {"sequenceNo": 1, "text": "string", "sourceSubmissionId": null, "paragraphBreakBefore": true},
    {"sequenceNo": 2, "text": "string", "sourceSubmissionId": 101, "paragraphBreakBefore": false}
  ],
  "summary": "3~4문장 요약."
}
""".strip()


def _mood_block(mood_codes: list[str]) -> str:
    if not mood_codes:
        return "(지정되지 않음 — 문장들의 정서를 스스로 판단해 일관된 톤을 유지할 것)"
    return "\n".join(f"- {code} ({MOOD_LABELS.get(code, code)})" for code in mood_codes)


class BookPromptBuilder:
    """책 생성 요청 하나를 system/user 프롬프트 쌍으로 옮긴다."""

    def build(
        self,
        request: BookGenerationRequest,
        correction: BookRetryCorrection | None = None,
    ) -> tuple[str, str]:
        submissions = json.dumps(
            [
                {"submissionId": submission.submissionId, "text": submission.text}
                for submission in request.submissions
            ],
            ensure_ascii=False,
            indent=2,
        )
        user_prompt = (
            "[Boss]\n"
            f"이름: {request.boss.name}\n"
            f"설정: {request.boss.description}\n"
            "이 책을 관통해야 하는 핵심 분위기:\n"
            f"{_mood_block(request.boss.moods)}\n\n"
            "[Submissions]\n"
            "아래 문장들은 보스전 기여도를 기준으로 추려진 것이며, 서로 이어지는 이야기인지는 "
            "검토되지 않았습니다. 각기 다른 사람이 따로 쓴 문장이므로 주제가 흩어져 있을 수 있습니다.\n"
            # 하한을 비율로만 주면 모델이 직접 환산해야 해서 잘 지켜지지 않았다(실측 17~26/30).
            # 목표 개수를 세어 알려 주고 지시를 여기서 한 번 더 반복한다.
            f"총 {len(request.submissions)}개이며, 이 중 최소 "
            f"{math.ceil(len(request.submissions) * MIN_SUBMISSION_USAGE_RATIO)}개는 "
            "본문에 실어야 합니다.\n"
            f"{submissions}\n\n"
            "위 [Boss]와 [Submissions]를 바탕으로, 시스템 지시의 [Output Format]에 맞춰 "
            "하나의 이어지는 이야기를 작성하십시오."
        )
        if correction is not None:
            # 계약 위반 재시도(ISSUE-207). 직전 위반 사유를 그대로 보여 주는 것이
            # 일반적인 규칙 강조보다 재발을 잘 막는다. 사유 문구는 검증기의 영어 메시지다.
            # 사유가 비어 있어도 빈 불릿만 남기지 않고 유형별 지시는 그대로 싣는다.
            reason = correction.reason.strip()
            user_prompt += (
                "\n\n[Retry Notice]\n"
                + (
                    f"직전 생성 결과는 다음 계약 위반으로 반려되었습니다:\n- {reason}\n"
                    if reason
                    else "직전 생성 결과는 계약 위반으로 반려되었습니다.\n"
                )
                + "같은 위반을 반복하지 마십시오. "
                + _RETRY_GUIDANCE[correction.kind]
                + " 창의적 표현보다 계약 준수를 우선하십시오."
            )
        return BOOK_SYSTEM_PROMPT, user_prompt
