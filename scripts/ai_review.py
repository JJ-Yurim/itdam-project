#!/usr/bin/env python3
"""
GitLab MR AI 코드리뷰 봇
- Jenkins 에이전트(노트북)에서 실행
- 리뷰 생성: Claude Code 헤드리스(`claude -p`) — 구독 로그인 기반, API 키 불필요
- 코멘트 작성: GitLab REST API (요약 노트 + 인라인 discussion)

필요 환경변수:
  GITLAB_URL      예: https://gitlab.example.com
  GITLAB_TOKEN    api scope PAT (Jenkins credential)
사용법:
  python3 ai_review.py --project-id 12345 --mr-iid 7
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request

# 출력은 UTF-8로 고정한다.
# 한국어 Windows에서 파이프로 리다이렉트된 stdout은 cp949로 잡히는데,
# (1) em dash 같은 문자를 담지 못해 print가 UnicodeEncodeError로 죽고
# (2) Jenkins 에이전트가 JDK 18+ (JEP 400)라 로그를 UTF-8로 읽어 한글이 깨진다.
# 둘 다 출력을 UTF-8로 맞추면 해결된다. errors는 남은 예외 상황용 안전망.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except AttributeError:  # 리다이렉트된 특수 스트림 등
        pass

# ----------------------------- 설정 -----------------------------

# 리뷰에서 제외할 파일 패턴
EXCLUDE_PATTERNS = [
    r"package-lock\.json$", r"yarn\.lock$", r"pnpm-lock\.yaml$",
    r"gradle\.lockfile$", r"Pipfile\.lock$", r"poetry\.lock$",
    r"\.min\.(js|css)$", r"\.map$", r"\.svg$", r"\.png$", r"\.jpe?g$",
    r"\.gif$", r"\.ico$", r"\.woff2?$", r"\.ttf$", r"\.pdf$",
    r"^dist/", r"^build/", r"/dist/", r"/build/", r"node_modules/",
    r"\.snap$",
]

MAX_FILE_DIFF_CHARS = 20_000    # 파일 하나당 diff 상한 (초과 시 해당 파일 스킵)
MAX_TOTAL_DIFF_CHARS = 120_000  # 전체 diff 상한 (초과 시 리뷰 생략하고 안내 코멘트만)
MAX_INLINE_COMMENTS = 15        # 인라인 코멘트 박스 최대 개수 (지적 건수가 아니라 박스 수)

# 프롬프트에 함께 넣을 팀 컨벤션 문서 (워크스페이스 루트 기준 상대경로, 없으면 건너뜀)
CONVENTION_FILES = ["backend/CONVENTIONS.md"]
MAX_CONVENTION_CHARS = 20_000   # 문서 하나당 상한

REVIEW_MARKER = "ai-review"     # 중복 방지 마커: <!-- ai-review:<head_sha> -->
# 출력 파싱 실패 시 모델 원본 응답을 남길 파일 (워크스페이스 루트, Jenkins가 아티팩트로 보관)
RAW_DUMP_PATH = "ai-review-raw-output.txt"
BOT_SIGNATURE = "🤖 *AI Code Review Bot*"

SYSTEM_PROMPT = """당신은 시니어 개발자로서 GitLab Merge Request를 리뷰합니다.

리뷰 관점 (중요도 순):
1. 버그, 논리 오류, 예외 처리 누락, null/undefined 위험
2. 보안 문제 (인젝션, 하드코딩된 시크릿, 인증/인가 누락)
3. 성능 문제 (N+1 쿼리, 불필요한 반복, 메모리 누수)
4. 가독성과 유지보수성 (네이밍, 중복 코드, 과도한 복잡도)

규칙:
- diff에 실제로 보이는 코드에 대해서만 코멘트합니다. 추측하지 않습니다.
- 사소한 스타일 지적은 하지 않습니다. 확실하고 가치 있는 지적만 합니다.
- 각 코멘트는 문제 설명 + 구체적인 개선 제안을 포함합니다.
- 코멘트는 한국어로 작성합니다.
- 아래 diff의 추가된 줄에는 [L<줄번호>] 형식으로 새 파일 기준 줄번호가 표시되어 있습니다.
  인라인 코멘트의 line 값은 반드시 이 줄번호를 사용합니다.

출력 형식 (다른 텍스트 없이 아래 JSON만 출력):
{
  "summary": "MR 전체에 대한 마크다운 요약. 이 MR이 무엇을 왜 바꾸는지 2~5문장.",
  "walkthrough": [
    {"files": "관련 파일들 (쉼표 구분, 공통 경로는 축약 가능)", "change": "해당 파일들의 변경 내용 한 줄 요약"}
  ],
  "diagram": ["Mermaid 코드를 한 줄씩 담은 문자열 배열 (```mermaid 펜스 없이 코드만).", "첫 줄은 다이어그램 종류: API 요청 흐름·컴포넌트 상호작용이면 sequenceDiagram, 분기 로직이면 flowchart TD.", "다이어그램으로 표현할 흐름이 없는 단순 변경(설정, 문구 수정 등)이면 배열 대신 null."],
  "comments": [
    {"file": "src/main/java/.../Foo.java", "line": 42, "severity": "major|minor|suggestion", "comment": "..."}
  ]
}

Mermaid 작성 규칙 (JSON 파싱·렌더링 오류 방지):
- 큰따옴표를 절대 쓰지 않습니다. JSON 문자열 안이라 이스케이프가 깨지기 쉽습니다.
- 그래서 노드 라벨과 participant 이름에는 괄호, 콜론, 세미콜론, 파이프 같은 특수문자를 넣지 않습니다.
  필요하면 지우거나 공백·하이픈으로 바꿉니다. 올바른 형태: A[요청 처리 POST]
  라벨을 따옴표로 감싸거나 라벨 안에 괄호를 넣는 형태는 금지합니다.
- 한글과 공백은 따옴표 없이 그대로 쓸 수 있습니다.
- diff에서 확인되는 흐름만 그립니다. 파일에 없는 컴포넌트를 상상해서 넣지 않습니다.
- 노드 15개 이하로 핵심 흐름만 간결하게.
인라인으로 달 만한 지적이 없으면 comments는 빈 배열로 둡니다."""


# ------------------------- GitLab API 헬퍼 -------------------------

class GitLab:
    def __init__(self, base_url: str, token: str, project_id: str):
        self.base = base_url.rstrip("/") + "/api/v4"
        self.token = token
        self.project = urllib.parse.quote(str(project_id), safe="")

    def _request(self, method: str, path: str, params=None, body=None):
        url = f"{self.base}{path}"
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("PRIVATE-TOKEN", self.token)
        if data:
            req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = resp.read().decode()
            headers = dict(resp.headers)
        return (json.loads(payload) if payload else None), headers

    def get(self, path, params=None):
        return self._request("GET", path, params=params)[0]

    def get_paginated(self, path, params=None):
        params = dict(params or {})
        params.setdefault("per_page", 100)
        page, results = 1, []
        while True:
            params["page"] = page
            data, headers = self._request("GET", path, params=params)
            results.extend(data)
            next_page = headers.get("X-Next-Page") or headers.get("x-next-page")
            if not next_page:
                break
            page = int(next_page)
        return results

    def post(self, path, body):
        return self._request("POST", path, body=body)[0]

    def put(self, path, body):
        return self._request("PUT", path, body=body)[0]

    # --- MR 관련 ---
    def mr(self, iid):
        return self.get(f"/projects/{self.project}/merge_requests/{iid}")

    def mr_diffs(self, iid):
        return self.get_paginated(f"/projects/{self.project}/merge_requests/{iid}/diffs")

    def mr_notes(self, iid):
        return self.get_paginated(f"/projects/{self.project}/merge_requests/{iid}/notes")

    def post_note(self, iid, body):
        return self.post(f"/projects/{self.project}/merge_requests/{iid}/notes", {"body": body})

    def update_note(self, iid, note_id, body):
        """이미 올린 노트를 수정. 게시 시각이 유지되므로 타임라인 위치가 그대로다."""
        return self.put(
            f"/projects/{self.project}/merge_requests/{iid}/notes/{note_id}", {"body": body}
        )

    def post_inline(self, iid, body, diff_refs, new_path, new_line):
        position = {
            "position_type": "text",
            "base_sha": diff_refs["base_sha"],
            "start_sha": diff_refs["start_sha"],
            "head_sha": diff_refs["head_sha"],
            "new_path": new_path,
            "new_line": new_line,
        }
        return self.post(
            f"/projects/{self.project}/merge_requests/{iid}/discussions",
            {"body": body, "position": position},
        )


# ------------------------- diff 전처리 -------------------------

def is_excluded(path: str) -> bool:
    return any(re.search(p, path) for p in EXCLUDE_PATTERNS)


def annotate_diff(diff_text: str) -> str:
    """추가된(+) 줄에 새 파일 기준 줄번호 [L{n}] 를 붙인다."""
    out, new_line = [], 0
    for line in diff_text.splitlines():
        if line.startswith("@@"):
            m = re.search(r"\+(\d+)", line)
            if m:
                new_line = int(m.group(1))
            out.append(line)
        elif line.startswith("+") and not line.startswith("+++"):
            out.append(f"+[L{new_line}]{line[1:]}")
            new_line += 1
        elif line.startswith("-") and not line.startswith("---"):
            out.append(line)
        else:
            out.append(line)
            if new_line:
                new_line += 1
    return "\n".join(out)


def added_lines_by_file(diffs) -> dict:
    """{path: set(추가된 새 줄번호)} — 인라인 코멘트 위치 검증용"""
    result = {}
    for d in diffs:
        lines, new_line = set(), 0
        for line in d.get("diff", "").splitlines():
            if line.startswith("@@"):
                m = re.search(r"\+(\d+)", line)
                if m:
                    new_line = int(m.group(1))
            elif line.startswith("+") and not line.startswith("+++"):
                lines.add(new_line)
                new_line += 1
            elif not line.startswith("-"):
                if new_line:
                    new_line += 1
        result[d["new_path"]] = lines
    return result


def hunk_anchors(diffs) -> dict:
    """{path: {추가된 줄: 그 헝크의 마지막 추가 줄}}

    지적을 원래 줄에 그대로 달면 diff 중간에 코멘트 박스가 끼어 아래 코드가 밀린다.
    그래서 같은 변경 블록(헝크)에 속한 지적은 블록의 마지막 추가 줄에 모아 단다.
    GitLab은 diff에 나타난 줄에만 코멘트를 허용하므로, 메서드/클래스의 실제
    끝줄이 diff 밖이면 그 위치는 쓸 수 없다. 헝크 끝이 실현 가능한 최선이다.
    """
    result = {}
    for d in diffs:
        path = d.get("new_path")
        if not path:
            continue
        runs, current, new_line = [], [], 0
        for line in d.get("diff", "").splitlines():
            if line.startswith("@@"):
                if current:
                    runs.append(current)
                    current = []
                m = re.search(r"\+(\d+)", line)
                if m:
                    new_line = int(m.group(1))
            elif line.startswith("+") and not line.startswith("+++"):
                current.append(new_line)
                new_line += 1
            elif line.startswith("-") and not line.startswith("---"):
                pass  # 삭제된 줄은 새 파일 줄번호를 소비하지 않는다
            else:
                if new_line:
                    new_line += 1
        if current:
            runs.append(current)

        mapping = {}
        for run in runs:
            last = run[-1]  # 추가 줄은 오름차순으로 쌓이므로 마지막이 최대
            for ln in run:
                mapping[ln] = last
        result[path] = mapping
    return result


def build_diff_payload(diffs):
    """리뷰 대상 diff 문자열 조립. (payload, skipped_files, oversized 여부) 반환"""
    parts, skipped, total = [], [], 0
    for d in diffs:
        path = d.get("new_path") or d.get("old_path") or ""
        raw = d.get("diff", "")
        if is_excluded(path) or d.get("deleted_file"):
            continue
        if not raw:
            continue
        if len(raw) > MAX_FILE_DIFF_CHARS:
            skipped.append(path)
            continue
        annotated = annotate_diff(raw)
        header = f"### 파일: {path}" + (" (신규 파일)" if d.get("new_file") else "")
        parts.append(f"{header}\n```diff\n{annotated}\n```")
        total += len(annotated)
        if total > MAX_TOTAL_DIFF_CHARS:
            return None, skipped, True
    return "\n\n".join(parts), skipped, False


# ------------------------- 리뷰 백엔드 -------------------------
# 나중에 API 방식으로 옮길 때 이 부분만 교체하면 됨.

class ReviewBackend:
    last_meta: dict | None = None  # 토큰 사용량 등 호출 메타데이터 (있으면 로그에 출력)

    def generate(self, prompt: str) -> str:
        raise NotImplementedError


class ClaudeCodeBackend(ReviewBackend):
    """Claude Code 헤드리스 모드. 노트북에서 `claude` 로그인이 되어 있어야 함."""

    def __init__(self, model: str | None = None):
        self.model = model or os.environ.get("CLAUDE_MODEL")  # 미지정 시 기본 모델

    def generate(self, prompt: str) -> str:
        # 윈도우에서 claude는 claude.cmd 셰임이라 PATHEXT 해석이 필요하다.
        # subprocess는 .exe만 붙여 찾으므로 which로 풀 경로를 넘긴다.
        exe = shutil.which("claude")
        if not exe:
            raise RuntimeError("claude 실행 파일을 PATH에서 찾지 못했습니다.")
        # json 형식은 리뷰 본문(result)과 함께 토큰 사용량·비용·소요시간을 돌려준다.
        cmd = [exe, "-p", "--output-format", "json"]
        if self.model:
            cmd += ["--model", self.model]
        proc = subprocess.run(
            cmd, input=prompt, capture_output=True, text=True, timeout=600,
            encoding="utf-8", errors="replace",
        )
        if proc.returncode != 0:
            raise RuntimeError(f"claude 실행 실패 (exit {proc.returncode}): {proc.stderr[:2000]}")
        try:
            envelope = json.loads(proc.stdout)
        except json.JSONDecodeError as e:
            raise RuntimeError(f"claude 출력을 JSON으로 읽지 못했습니다: {e}\n{proc.stdout[:500]}")
        if envelope.get("is_error"):
            raise RuntimeError(f"claude 오류 응답: {envelope.get('result', '')[:2000]}")
        self.last_meta = envelope
        return envelope.get("result", "")


class AnthropicAPIBackend(ReviewBackend):
    """나중에 API 크레딧으로 전환할 때 사용. ANTHROPIC_API_KEY 필요."""

    def __init__(self, model: str = "claude-sonnet-4-6"):
        self.model = model

    def generate(self, prompt: str) -> str:
        req = urllib.request.Request(
            "https://api.anthropic.com/v1/messages",
            data=json.dumps({
                "model": self.model,
                "max_tokens": 4096,
                "messages": [{"role": "user", "content": prompt}],
            }).encode(),
            method="POST",
        )
        req.add_header("Content-Type", "application/json")
        req.add_header("x-api-key", os.environ["ANTHROPIC_API_KEY"])
        req.add_header("anthropic-version", "2023-06-01")
        with urllib.request.urlopen(req, timeout=300) as resp:
            data = json.loads(resp.read().decode())
        return "".join(b.get("text", "") for b in data.get("content", []))


def parse_review(raw: str) -> dict:
    """모델 출력에서 JSON 추출 (코드펜스/앞뒤 텍스트 방어)"""
    text = raw.strip()
    m = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, re.DOTALL)
    if m:
        text = m.group(1)
    else:
        start, end = text.find("{"), text.rfind("}")
        if start != -1 and end > start:
            text = text[start:end + 1]
    review = json.loads(text)
    # 문법은 맞지만 최상위가 객체가 아닌 출력(배열·문자열 하나 등)도 파싱 실패로 다룬다.
    # 여기서 걸러내지 않으면 아래 setdefault에서 AttributeError가 나 호출부의
    # ValueError 처리를 빠져나가고, 원본 응답 덤프도 남지 않는다.
    if not isinstance(review, dict):
        raise ValueError(f"최상위 JSON이 객체가 아닙니다: {type(review).__name__}")
    review.setdefault("summary", "")
    review.setdefault("walkthrough", [])
    review.setdefault("comments", [])

    # diagram은 줄 배열로 받는다. Mermaid 코드를 JSON 문자열 하나에 담으면 개행
    # 이스케이프가 깨지기 쉽다. 예전 형식(문자열 하나)도 그대로 받아들인다.
    diagram = review.get("diagram")
    if isinstance(diagram, list):
        diagram = "\n".join(str(line) for line in diagram)
    review["diagram"] = diagram or None
    return review


def strip_mermaid_fence(code: str) -> str:
    """모델이 실수로 ```mermaid 펜스를 포함해도 안전하게 제거"""
    code = code.strip()
    code = re.sub(r"^```(?:mermaid)?\s*", "", code)
    code = re.sub(r"\s*```$", "", code)
    return code.strip()


def load_conventions() -> str:
    """팀 컨벤션 문서를 읽어 프롬프트 조각으로 만든다. 파일이 없으면 빈 문자열."""
    parts = []
    for path in CONVENTION_FILES:
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read(MAX_CONVENTION_CHARS)
        except OSError:
            continue  # 저장소 구조가 바뀌어도 리뷰 자체는 계속 돌아야 한다
        parts.append(f"### {path}\n{text}")
    if not parts:
        return ""
    return (
        "## 팀 컨벤션\n"
        "아래 컨벤션을 어긴 부분도 리뷰 대상입니다. 다만 diff에 실제로 보이는 코드에 한하며, "
        "컨벤션 문서 자체를 요약하지는 않습니다.\n\n"
        + "\n\n".join(parts)
        + "\n\n"
    )


def format_usage(meta) -> str:
    """claude --output-format json 응답에서 사용량 요약을 만든다."""
    if not meta:
        return ""
    u = meta.get("usage") or {}
    inp = u.get("input_tokens", 0)
    cache_new = u.get("cache_creation_input_tokens", 0)
    cache_hit = u.get("cache_read_input_tokens", 0)
    out = u.get("output_tokens", 0)

    lines = [
        f"토큰: 입력 {inp:,} / 캐시생성 {cache_new:,} / 캐시읽기 {cache_hit:,} / 출력 {out:,}"
        f"  (합계 {inp + cache_new + cache_hit + out:,})"
    ]
    model = meta.get("modelUsage")
    if isinstance(model, dict):
        lines.append(f"모델: {', '.join(model.keys())}")
    elif model:
        lines.append(f"모델: {model}")
    cost = meta.get("total_cost_usd")
    if cost is not None:
        lines.append(f"환산 비용: ${cost:.4f}  (구독 사용 시 청구되지 않음 — 한도 소모 참고치)")
    duration = meta.get("duration_ms")
    if duration:
        lines.append(f"소요 시간: {duration / 1000:.1f}s")
    return "\n".join(lines)


# ------------------------- 메인 로직 -------------------------

SEVERITY_EMOJI = {"major": "🔴", "minor": "🟡", "suggestion": "💡"}


def format_group(comments) -> str:
    """한 변경 블록에 모인 지적들을 하나의 코멘트 본문으로 합친다.

    앵커가 원래 줄과 다를 수 있으므로 각 항목에 원래 줄번호를 남긴다.
    """
    parts = []
    for c in comments:
        emoji = SEVERITY_EMOJI.get(c.get("severity", "suggestion"), "💡")
        parts.append(f"{emoji} **L{c.get('line')}** {c.get('comment', '').strip()}")
    return "\n\n".join(parts)


def build_summary(marker, review, extras, skipped, head_sha) -> str:
    """요약 코멘트 본문. extras는 인라인으로 달지 않은(또는 달지 못한) 지적들."""
    s = f"{marker}\n## 🤖 AI 코드리뷰\n\n{review['summary']}"

    if review["walkthrough"]:
        s += "\n\n### 📋 Walkthrough\n\n| 파일 | 변경 내용 |\n|---|---|\n"
        for row in review["walkthrough"]:
            files = str(row.get("files", "")).replace("|", "\\|")
            change = str(row.get("change", "")).replace("|", "\\|")
            s += f"| `{files}` | {change} |\n"

    if review["diagram"]:
        diagram = strip_mermaid_fence(str(review["diagram"]))
        if diagram:
            s += (
                "\n\n<details>\n<summary>🔀 <b>코드 흐름 다이어그램</b> (펼치기)</summary>\n\n"
                f"```mermaid\n{diagram}\n```\n\n</details>"
            )

    if extras:
        s += f"\n\n### 그 밖의 지적 ({len(extras)}건)\n"
        for c in extras:
            s += (
                f"- **{c.get('file')}:{c.get('line')}** "
                f"{SEVERITY_EMOJI.get(c.get('severity', 'suggestion'), '💡')} "
                f"{c.get('comment', '').strip()}\n"
            )

    if skipped:
        s += "\n\n> 크기 제한으로 리뷰에서 제외된 파일: " + ", ".join(f"`{p}`" for p in skipped)
    s += f"\n\n---\n*reviewed at `{head_sha[:8]}`*"
    return s


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project-id", required=True)
    ap.add_argument("--mr-iid", required=True)
    ap.add_argument("--backend", choices=["claude-code", "anthropic-api"],
                    default=os.environ.get("REVIEW_BACKEND", "claude-code"))
    args = ap.parse_args()

    # 이전 빌드가 남긴 덤프를 먼저 지운다. Jenkins는 같은 잡의 워크스페이스를 재사용하므로,
    # 지우지 않으면 이번 빌드가 다른 이유로 실패했을 때 옛 응답이 아티팩트로 붙어
    # "이 빌드의 원본 응답"이라는 안내가 거짓이 된다.
    try:
        os.remove(RAW_DUMP_PATH)
    except FileNotFoundError:
        pass
    except OSError as e:
        print(f"이전 덤프 파일을 지우지 못했습니다: {e}", file=sys.stderr)

    gl = GitLab(
        os.environ.get("GITLAB_URL", "https://gitlab.example.com"),
        os.environ["GITLAB_TOKEN"],
        args.project_id,
    )

    mr = gl.mr(args.mr_iid)

    # 1) draft / closed / merged 스킵
    if mr.get("draft") or mr.get("work_in_progress"):
        print("Draft MR — 리뷰 스킵")
        return
    if mr.get("state") != "opened":
        print(f"MR 상태가 {mr.get('state')} — 리뷰 스킵")
        return

    diff_refs = mr["diff_refs"]
    head_sha = diff_refs["head_sha"]
    marker = f"<!-- {REVIEW_MARKER}:{head_sha} -->"

    # 2) 중복 방지: 같은 head_sha에 이미 리뷰했으면 스킵
    for note in gl.mr_notes(args.mr_iid):
        if marker in (note.get("body") or ""):
            print(f"이미 리뷰한 커밋({head_sha[:8]}) — 스킵")
            return

    # 3) diff 수집 및 전처리
    diffs = gl.mr_diffs(args.mr_iid)
    payload, skipped, oversized = build_diff_payload(diffs)

    if oversized:
        gl.post_note(args.mr_iid, (
            f"{marker}\n{BOT_SIGNATURE}\n\n"
            f"⚠️ 이 MR의 변경량이 너무 커서 자동 리뷰를 생략했습니다. "
            f"MR을 작게 나누면 리뷰 품질이 좋아집니다."
        ))
        print("diff 과대 — 안내 코멘트만 작성")
        return

    if not payload:
        print("리뷰할 diff 없음 — 스킵")
        return

    # 4) 리뷰 생성
    backend = AnthropicAPIBackend() if args.backend == "anthropic-api" else ClaudeCodeBackend()
    prompt = (
        f"{SYSTEM_PROMPT}\n\n"
        f"{load_conventions()}"
        f"## MR 제목\n{mr.get('title', '')}\n\n"
        f"## MR 설명\n{mr.get('description') or '(없음)'}\n\n"
        f"## 변경 내용\n{payload}"
    )
    print(
        f"리뷰 생성 중... (backend={args.backend}, "
        f"diff {len(payload):,} chars, 프롬프트 {len(prompt):,} chars)"
    )
    raw = backend.generate(prompt)

    # 사용량을 파싱보다 먼저 출력한다. 파싱이 깨져도 이미 소모한 한도는 로그에 남아야 한다.
    usage = format_usage(backend.last_meta)
    if usage:
        print(usage)

    try:
        review = parse_review(raw)
    except ValueError as e:  # JSONDecodeError와 최상위 타입 검증 실패를 함께 받는다
        # 원본 응답을 남긴다. 이게 없으면 모델이 무엇을 뱉었는지 확인할 방법이 없다.
        dump = os.path.abspath(RAW_DUMP_PATH)
        try:
            with open(dump, "w", encoding="utf-8") as f:
                f.write(raw)
            location = f"원본 응답 저장: {dump}"
        except OSError as write_error:
            location = f"원본 응답 저장 실패: {write_error}"
        print(
            f"리뷰 생성은 성공했지만 출력을 JSON으로 읽지 못했습니다: {e}\n{location}\n"
            f"--- 응답 앞부분 ---\n{raw[:2000]}",
            file=sys.stderr,
        )
        return 1

    # 5) 인라인 코멘트 위치 계산 — 심각도와 무관하게 모두 코드에 단다.
    #    다만 지적된 줄 그대로가 아니라 그 변경 블록의 마지막 추가 줄로 앵커를 옮기고,
    #    한 블록에 여러 지적이 있으면 하나의 코멘트로 합쳐 박스 수를 줄인다.
    valid_lines = added_lines_by_file(diffs)
    anchors = hunk_anchors(diffs)

    groups, extras = {}, []
    for c in review["comments"]:
        path, line = c.get("file"), c.get("line")
        if path in valid_lines and line in valid_lines[path]:
            anchor = anchors.get(path, {}).get(line, line)
            groups.setdefault((path, anchor), []).append(c)
        else:
            extras.append(c)  # 위치를 검증할 수 없는 지적은 요약으로

    # MAX_INLINE_COMMENTS는 이제 '코멘트 박스 개수' 상한이다.
    ordered = sorted(groups.items(), key=lambda kv: (kv[0][0], kv[0][1]))
    keep, overflow = ordered[:MAX_INLINE_COMMENTS], ordered[MAX_INLINE_COMMENTS:]
    for _, cs in overflow:
        extras.extend(cs)
    extras_at_post = len(extras)

    # 6) 요약을 먼저 게시 — MR 타임라인은 시간순이라, 먼저 올려야 Walkthrough가
    #    인라인 코멘트들보다 위에 온다.
    summary_note = gl.post_note(
        args.mr_iid, build_summary(marker, review, extras, skipped, head_sha)
    )

    # 7) 인라인 코멘트 작성 — 블록당 하나씩, 블록 끝 줄에
    posted, merged = 0, 0
    for (path, anchor), cs in keep:
        body = f"{format_group(cs)}\n\n{BOT_SIGNATURE}"
        try:
            gl.post_inline(args.mr_iid, body, diff_refs, path, anchor)
            posted += 1
            merged += len(cs)
        except Exception as e:
            print(f"인라인 실패 {path}:{anchor} — {e}", file=sys.stderr)
            extras.extend(cs)  # 게시 실패분은 요약으로 회수

    # 8) 회수된 지적이 있으면 요약을 수정해 합친다.
    #    노트를 새로 올리지 않고 수정하므로 게시 시각이 유지되어 순서가 그대로다.
    if len(extras) != extras_at_post and summary_note and summary_note.get("id"):
        gl.update_note(
            args.mr_iid,
            summary_note["id"],
            build_summary(marker, review, extras, skipped, head_sha),
        )

    print(
        f"완료 — 인라인 코멘트 {posted}개(지적 {merged}건 병합), "
        f"요약에 합친 지적 {len(extras)}개"
    )


if __name__ == "__main__":
    # main()이 종료 코드를 돌려준다 (정상 종료는 None → 0).
    sys.exit(main())
