# AI 코드리뷰 자동화 (GitLab MR → Jenkins → Claude Code)

MR이 열리거나 소스 브랜치에 push되면 Jenkins가 리뷰 잡을 돌려, MR에 요약 코멘트 1개와 인라인 코멘트 여러 개를 자동으로 남긴다.

- 리뷰 파이프라인: [Jenkinsfile.review](../../Jenkinsfile.review)
- 리뷰 스크립트: [scripts/ai_review.py](../../scripts/ai_review.py)
- 리뷰 생성기: Claude Code 헤드리스(`claude -p`) — **구독 로그인 기반이라 API 키가 필요 없다**
- 실행 위치: 팀 서버용 노트북 (Jenkins 에이전트 라벨 `ai-review-laptop`, Windows)

## 전체 흐름

```
GitLab MR (open / push)
  └─ 웹훅 (Merge request events + secret token)
       └─ Jenkins 잡 "AI Review"  (agent: ai-review-laptop)
            └─ python scripts\ai_review.py --project-id … --mr-iid …
                 ├─ GitLab API: MR 메타 + diff 수집
                 ├─ claude -p --output-format json : 리뷰 JSON 생성
                 └─ GitLab API: 요약 노트 → 인라인 discussion
```

배포 잡(`Jenkinsfile`)과 **완전히 분리된 별도 잡**이다. 리뷰가 실패해도 빌드·배포·머지에는 영향이 없다.

## 왜 직접 만들었나 (CodeRabbit 대신)

처음에는 CodeRabbit을 붙이려 했다. 하지만 이 저장소는 조직에서 운영하는 자체 호스팅 GitLab(`gitlab.example.com`)에 있어서, 외부 SaaS 리뷰 봇을 연결할 수 없다. 그래서 **같은 역할을 하는 봇을 팀이 통제하는 머신에서 직접 돌리는 방식**으로 대체했다. MR 요약 + Walkthrough 표 + Mermaid 흐름 다이어그램 + 인라인 지적이라는 출력 형태를 CodeRabbit에서 그대로 가져왔다.

## 왜 노트북에서 돌리는가

리뷰 생성을 Anthropic API 대신 Claude Code 헤드리스 모드로 한다. `claude -p`는 그 머신에 로그인된 구독 계정을 쓰므로 API 크레딧을 소모하지 않는다. 대신 **로그인 세션이 살아있는 물리 머신**이 필요해서, 팀 서버용 노트북을 Jenkins 에이전트로 붙여 그 노트북에서만 리뷰 잡을 실행한다.

API 방식으로 갈아탈 여지는 코드에 남겨뒀다. `scripts/ai_review.py`의 `AnthropicAPIBackend`를 `--backend anthropic-api`(또는 `REVIEW_BACKEND=anthropic-api`)로 선택하면 되고, 그때는 `ANTHROPIC_API_KEY`가 필요하다.

## 노트북(에이전트) 준비

1. **Jenkins 에이전트 등록** — 컨트롤러에서 노드를 만들고 라벨을 `ai-review-laptop`으로 준다. 이 라벨이 `Jenkinsfile.review`의 `agent { label 'ai-review-laptop' }`와 짝이다.
2. **Claude Code 설치 + 로그인** — 노트북에서 `claude` 로그인을 끝내 둔다. 이 세션이 끊기면 리뷰 잡이 실패한다(빌드 로그에 안내 메시지가 남는다).
3. **에이전트를 서비스가 아닌 사용자 세션에서 실행** — `claude`의 로그인 자격은 로그인한 사용자 프로필에 저장된다. 다른 계정의 Windows 서비스로 에이전트를 돌리면 로그인 상태를 못 찾는다.
4. **Python 3 설치, PATH 등록** — 스크립트는 표준 라이브러리만 쓴다. 별도 패키지 설치가 없다.
5. **`claude`가 PATH에 있는지 확인** — Windows에서 `claude`는 `claude.cmd` 셰임이다. `subprocess`는 `.exe`만 붙여 찾기 때문에 스크립트가 `shutil.which("claude")`로 풀 경로를 구해 넘긴다. PATH에 없으면 즉시 실패한다.
6. **노트북 절전 해제** — 뚜껑을 닫거나 잠자기로 들어가면 에이전트가 offline이 되어 웹훅이 큐에 쌓인다.

## Jenkins 잡 설정

Pipeline 잡을 하나 만들고 `Pipeline script from SCM` → `Jenkinsfile.review`를 지정한다.

### 트리거 (반드시 잡 설정 UI에서)

Jenkinsfile의 선언형 `triggers` 블록을 쓰지 않는다. 그 블록은 빌드마다 Jenkinsfile 기준으로 재적용되는데, 웹훅 secret token은 Jenkinsfile로 표현할 수 없어서 **매 빌드마다 토큰이 초기화된다.** 그래서 트리거는 UI에서만 관리한다.

| 항목 | 값 |
| --- | --- |
| Push events | 해제 |
| Merge request events | 체크 |
| Rebuild open MRs | On push to source branch |
| Accepted / Closed MR events | 해제 |
| Allowed branches | Allow all branches to trigger this job |
| Advanced → Secret token | Generate (GitLab 웹훅에 같은 값 입력) |

### Credentials

| ID | 타입 | 용도 |
| --- | --- | --- |
| `gitlab-review-bot-token` | Secret text | GitLab PAT (**api** scope). 코멘트 작성 계정이 된다. |

`GITLAB_URL`은 `https://gitlab.example.com`으로 파이프라인에 하드코딩되어 있다. 모델을 고정하려면 `environment`의 `CLAUDE_MODEL` 주석을 풀면 된다(미지정 시 기본 모델).

### 동시 실행 / 타임아웃

`disableConcurrentBuilds()`로 리뷰 잡의 동시 실행을 막는다. 여러 MR이 동시에 올라와도 순차 처리되며, 구독 사용 한도가 한꺼번에 소모되는 것을 방지한다. 잡 타임아웃 20분, `claude` 호출 타임아웃 600초, 빌드 이력 30개 보관.

### checkout을 넣지 않는 이유

`Pipeline script from SCM`을 쓰면 Jenkins가 `Declarative: Checkout SCM` 스테이지를 자동으로 붙여 워크스페이스에 저장소를 이미 받아 둔다. 여기서 `checkout scm`을 또 쓰면 fetch가 두 번 돈다. 리뷰 대상 소스는 GitLab API로 받으므로 자동 체크아웃(develop)만으로 충분하다. 워크스페이스가 필요한 이유는 `backend/CONVENTIONS.md`를 읽기 위한 것뿐이다.

## GitLab 웹훅 설정

프로젝트 → Settings → Webhooks

- URL: Jenkins 잡의 GitLab 웹훅 엔드포인트
- Secret token: Jenkins 잡에서 Generate한 값과 동일하게
- Trigger: **Merge request events**만 체크

## 스크립트 동작

### 실행 인자

```bash
python scripts/ai_review.py --project-id <id> --mr-iid <iid> [--backend claude-code|anthropic-api]
```

`project-id`는 파이프라인에서 `gitlabMergeRequestTargetProjectId`를 우선 쓰고, 없으면 `gitlabProjectId`로 대체한다(포크 MR 대응). 필수 환경변수는 `GITLAB_URL`, `GITLAB_TOKEN`.

### 리뷰를 건너뛰는 조건

- Draft MR (`draft` / `work_in_progress`)
- MR 상태가 `opened`가 아님 (closed / merged)
- **같은 `head_sha`에 이미 리뷰한 경우** — 요약 코멘트에 `<!-- ai-review:<head_sha> -->` 마커를 숨겨두고, 기존 노트에서 이 마커를 찾아 중복 리뷰를 막는다
- 리뷰할 diff가 없음

### diff 전처리

- 제외 대상: 각종 lock 파일, `.min.js/.css`, `.map`, 이미지·폰트·PDF, `dist/`·`build/`·`node_modules/`, `.snap`, 삭제된 파일
- 파일 하나 diff가 20,000자 초과 → 그 파일만 스킵(요약 하단에 제외 목록 표시)
- 전체 diff가 120,000자 초과 → 리뷰를 생략하고 "MR을 작게 나눠달라"는 안내 코멘트만 작성
- 추가된 줄에 `[L<줄번호>]`를 붙여 모델이 새 파일 기준 줄번호를 정확히 지목할 수 있게 한다

### 프롬프트

`SYSTEM_PROMPT`(리뷰 관점·규칙·출력 JSON 스키마) + 팀 컨벤션 + MR 제목/설명 + 주석 처리된 diff.

컨벤션은 `CONVENTION_FILES = ["backend/CONVENTIONS.md"]`를 워크스페이스 루트 기준으로 읽어 프롬프트에 주입한다(문서당 20,000자 상한). 파일이 없으면 조용히 건너뛰므로 저장소 구조가 바뀌어도 리뷰는 계속 돈다. 리뷰 관점을 늘리려면 이 목록에 문서를 추가한다.

모델 출력은 `summary`, `walkthrough`, `diagram`(Mermaid), `comments[]`(file/line/severity/comment) 4개 필드의 JSON이다. 코드펜스나 앞뒤 잡담이 섞여도 `parse_review()`가 JSON만 뽑아낸다.

### 코멘트 게시 방식

1. **요약을 먼저 게시한다.** MR 타임라인이 시간순이라 나중에 올리면 Walkthrough가 인라인 코멘트들 아래로 밀린다.
2. 인라인 코멘트는 **지적된 줄 그대로가 아니라 그 변경 블록(헝크)의 마지막 추가 줄**에 단다. 코멘트 박스가 diff 중간에 끼면 아래 코드가 밀려 읽기 어렵기 때문이다. GitLab은 diff에 나타난 줄에만 코멘트를 허용하므로 메서드/클래스의 실제 끝줄은 쓸 수 없고, 헝크 끝이 현실적인 최선이다.
3. 같은 블록에 여러 지적이 모이면 **하나의 코멘트로 병합**한다. 각 항목에 원래 줄번호(`L42`)와 심각도 이모지(🔴 major / 🟡 minor / 💡 suggestion)를 남긴다.
4. `MAX_INLINE_COMMENTS = 15`는 지적 건수가 아니라 **코멘트 박스 개수** 상한이다. 초과분과 위치 검증 실패분은 요약의 "그 밖의 지적"으로 내려간다.
5. 인라인 게시가 실패하면 그 지적을 회수해서 **요약 노트를 PUT으로 수정**해 합친다. 새 노트를 올리지 않으므로 게시 시각이 유지되고 요약이 계속 맨 위에 남는다.

### 사용량 로그

`claude -p --output-format json`으로 호출해 리뷰 본문(`result`)과 함께 토큰 사용량·모델·환산 비용·소요 시간을 받아 Jenkins 콘솔에 출력한다. 구독 사용 시 실제로 청구되지는 않지만, 한도 소모량을 빌드 로그에서 바로 확인할 수 있다.

## 겪은 문제와 해결

### 한국어 Windows 인코딩 (cp949 ↔ UTF-8)

증상: 리뷰 생성과 코멘트 게시를 모두 마친 뒤 **마지막 진행 로그에서** `UnicodeEncodeError`로 죽어, 실제로는 성공한 빌드가 Jenkins에서 실패로 표시됐다.

원인이 두 겹이었다.

1. 파이프로 리다이렉트된 stdout이 cp949로 잡혀 em dash(U+2014) 같은 문자를 담지 못하고 `print`가 터진다.
2. 그렇다고 인코딩을 그대로 두면, JDK 18+(JEP 400) 에이전트가 로그를 UTF-8로 읽어 한글이 전부 깨진다.

해결: `sys.stdout`/`sys.stderr`를 스크립트 시작 시 `reconfigure(encoding="utf-8", errors="replace")`로 UTF-8에 고정한다. 두 문제가 동시에 해결된다. (1차 시도로 `errors="replace"`만 적용했다가 한글 깨짐이 남아 UTF-8 고정으로 재수정했다.)

### Mermaid 라벨의 큰따옴표로 JSON 파싱 실패

증상: `json.decoder.JSONDecodeError: Expecting ',' delimiter`로 실패. 리뷰 생성(= `claude` 호출)은 성공해 토큰까지 소모한 뒤, 결과를 파싱하는 `parse_review()`에서 죽는다.

원인: 프롬프트가 Mermaid 라벨에 `A["요청 처리 (POST)"]`처럼 큰따옴표를 쓰라고 지시했는데, `diagram`은 JSON 문자열 필드다. 모델이 `\"`로 이스케이프하지 않으면 JSON 전체가 깨진다.

해결 세 가지:

1. `diagram`을 **줄 배열**로 받는다(`["flowchart TD", "  A[...] --> B[...]"]`). 개행 이스케이프 위험이 사라진다. `parse_review()`가 배열을 개행으로 합치고, 예전 문자열 형식도 계속 받아들인다.
2. Mermaid 작성 규칙에서 **큰따옴표를 금지**한다. 따옴표가 필요해지는 괄호·콜론·세미콜론·파이프를 라벨에 넣지 말고 지우거나 공백/하이픈으로 바꾸게 했다. 한글·공백은 따옴표 없이 그대로 쓸 수 있다. (이게 이번 실패의 직접적인 수정이다 — 배열 형식만으로는 따옴표 문제가 해결되지 않는다.)
3. 파싱 실패 시 **모델 원본 응답을 `ai-review-raw-output.txt`로 덤프**하고 앞 2,000자를 stderr에 출력한다. 파이프라인은 실패 시 이 파일을 아티팩트로 보관한다. 이전에는 원본이 어디에도 남지 않아 무엇이 잘못됐는지 볼 수 없었다.
   - 최상위가 객체가 아닌 JSON(배열·문자열 하나 등)도 `parse_review()`에서 `ValueError`로 정규화해 같은 경로로 덤프한다. 그대로 두면 `setdefault`에서 `AttributeError`가 나 덤프 없이 죽는다.
   - 스크립트는 **시작 시 이전 덤프를 지운다.** Jenkins가 같은 잡의 워크스페이스를 재사용하므로, 지우지 않으면 이번 빌드가 다른 이유로 실패했을 때 옛 응답이 아티팩트로 붙어 진단을 정반대로 이끈다.

함께 고친 것: 사용량 로그(`format_usage`)를 파싱보다 **먼저** 출력한다. 파싱이 깨져도 이미 소모한 한도가 로그에 남는다. `post { failure }` 안내도 실패 유형별로 볼 곳을 가리키도록 바꿨다(기존에는 원인과 무관하게 claude 로그인만 지목했다).

### `claude` 실행 파일을 못 찾음

Windows의 `claude`는 `claude.cmd` 셰임인데 `subprocess`는 `.exe`만 붙여 탐색한다. `shutil.which("claude")`로 풀 경로를 구해 넘긴다.

### 웹훅 secret token이 매 빌드마다 초기화

선언형 `triggers` 블록이 빌드마다 재적용되면서 UI에서 생성한 secret token을 덮어썼다. 트리거 설정 전체를 잡 UI로 옮기고, Jenkinsfile에는 UI에 넣을 값만 주석으로 남겼다.

### 매 빌드 fetch 2회

`Pipeline script from SCM`의 자동 체크아웃 스테이지에 더해 명시적 `checkout scm`이 있었다. 명시적 스텝을 제거했다.

## 장애 확인

리뷰가 안 달릴 때 순서대로 본다.

1. GitLab MR 웹훅 → Settings → Webhooks → Recent Deliveries 응답 코드
2. Jenkins 노드 목록에서 `ai-review-laptop`이 online인지 (노트북 절전/네트워크)
3. 리뷰 잡 콘솔 로그 — 스킵 사유(`Draft MR`, `이미 리뷰한 커밋`, `diff 과대`)가 그대로 찍힌다
4. 노트북에서 `claude` 로그인 상태 (`claude` 실행 후 확인) — 로그에 `claude 실행 실패` / `claude 오류 응답`이 있을 때
5. 빌드 아티팩트 `ai-review-raw-output.txt` — 로그에 `출력을 JSON으로 읽지 못했습니다`가 있을 때 모델 원본 응답
6. `gitlab-review-bot-token`의 만료·scope (api 필요)

같은 커밋을 다시 리뷰하고 싶으면 MR에서 해당 요약 코멘트를 지운 뒤(마커가 사라짐) 잡을 다시 돌린다.

## 조정 지점

| 바꾸고 싶은 것 | 위치 |
| --- | --- |
| 리뷰 관점·톤·출력 스키마 | `scripts/ai_review.py`의 `SYSTEM_PROMPT` |
| 팀 컨벤션 주입 대상 | `CONVENTION_FILES` |
| diff 크기 상한 | `MAX_FILE_DIFF_CHARS`, `MAX_TOTAL_DIFF_CHARS` |
| 인라인 코멘트 박스 개수 | `MAX_INLINE_COMMENTS` |
| 리뷰 제외 파일 | `EXCLUDE_PATTERNS` |
| 모델 | 파이프라인 `environment`의 `CLAUDE_MODEL` |
| 생성 백엔드 (구독 → API) | `--backend anthropic-api` 또는 `REVIEW_BACKEND` |
