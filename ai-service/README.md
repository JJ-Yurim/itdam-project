# MVP AI Service

FastAPI 기반 AI Service입니다. 운영에서는 GMS 프록시를 통해 Gemini를 호출해 문장 평가
(`gemini-3.5-flash`)·공동 책 생성(`gemini-2.5-pro`)·문장 임베딩(`text-embedding-3-small`,
768차원)을 수행하며, HTTP 엔드포인트와 RabbitMQ 컨슈머 두 경로로 요청을 받습니다.
LLM provider가 설정되지 않은 환경에서도 서버는 정상 기동되고, 유효한 평가 요청에는
`503 LLM_NOT_CONFIGURED`를 반환합니다 — 계약 테스트와 로컬 개발이 키 없이 도는 이유입니다.

## 로컬 실행

```powershell
cd ai-service
conda activate ai_env
python -m pip install -r requirements.txt
Copy-Item .env.example .env
python -m uvicorn main:app --reload --port 8001
```

Miniforge 환경의 Python을 직접 지정해야 하는 경우:

```powershell
& python -m pip install -r requirements.txt
& python -m uvicorn main:app --reload --port 8001
```

서버가 기동되면 다음 주소를 사용할 수 있습니다.

- Health: `http://localhost:8001/health`
- Evaluation contract: `POST http://localhost:8001/internal/v1/evaluations`
- Book generation contract: `POST http://localhost:8001/internal/v1/book-generations`
- OpenAPI UI: `http://localhost:8001/docs`
- OpenAPI JSON: `http://localhost:8001/openapi.json`

## 환경 변수

`.env.example`은 실행에 필요한 설정 이름만 제공합니다. 실제 API Key는 커밋하지 않습니다. LLM 관련 값이 모두 채워진 경우에만 `/health`의 `llmConfigured`가 `true`가 됩니다. 현재 스토리에서는 LLM provider를 연결하지 않으므로 빈 값이어도 서버가 정상 기동됩니다.

## Docker

```powershell
docker build -t mvp-ai-service ./ai-service
docker run --rm -p 8001:8001 --env-file ./ai-service/.env mvp-ai-service
```

## Provider 계약

`LLM_PROVIDER=gemini-gms`, `LLM_API_KEY`, `LLM_MODEL`, `LLM_BASE_URL`이 모두
설정되면 서비스는 GMS Gemini의 `generateContent` 엔드포인트를 호출합니다. Base URL은
`v1beta`로 끝나야 하며, 어댑터가 그 뒤에 `/models/{LLM_MODEL}:generateContent`를
붙이고 키는 `x-goog-api-key` 헤더로 전송합니다.
Provider는 JSON `EvaluationResponse`를 반환해야 합니다. `PASSED` 응답이면
`usedDailyWords`에 요청받은 5개 `wordId`가 요청 순서대로 정확히 한 번씩 담기고, 각 단어마다
boolean `used` 값이 함께 있어야 합니다. `REJECTED` 응답은 `usedDailyWords`가 빈 배열입니다.

## 공동 책 생성 계약

`POST /internal/v1/book-generations`는 클리어된 보스런에 제출된 문장과 보스 테마·목표
분위기를 받아, `book_contents` 행에 1:1로 대응하는 본문 블록 배열로 책 본문을 반환합니다.
선별은 Spring이 이미 마친 상태로 전달되므로, AI Service는 받은 문장을 엮기만 하고 품질이나
분위기를 다시 심사하지 않습니다.

각 블록은 그 문장이 어디서 왔는지 — 사용자 원문 그대로인지, AI가 새로 쓴 서술인지, 최소한만
손본 것인지 — 를 `contentType`에 표시합니다. 이 출처 표시(provenance)는 원문 보존
우선순위(정책 §6.13)를 그대로 따릅니다.

| `contentType` | 의미 | `sourceSubmissionId` |
|---|---|---|
| `USER_ORIGINAL` | 제출 문장 그대로 | 필수 |
| `AI_GENERATED` | 모델이 새로 쓴 연결 서술 | 항상 `null` |
| `USER_EDITED` | 최소 편집. 어두·어미만 다듬을 수 있음 | 필수 |

핵심 의미·감정·주제를 바꿔야만 쓸 수 있는 문장은 본문에서 빠지고
`excludedSubmissionIds`에 담깁니다.

`USER_ORIGINAL` 블록은 제출 원문과 정확히 일치해야 합니다 — 허용되는 차이는 양끝 공백뿐이며,
이는 Spring이 저장 시 적용하는 `trim()` 기준과 같습니다. 이 검증은 AI Service가 응답 전에
직접 수행하므로, 불일치는 저장 단계에서 뒤늦게 거절되는 대신 계약 위반으로 곧바로 돌아옵니다.
`USER_EDITED` 블록도 제출 문장의 핵심부(첫/끝 어절을 뺀 나머지)를 그대로 담고 있어야 합니다.
비교할 때 양쪽 공백은 단일 공백으로 정규화합니다. 두 어절 이하의 문장은 편집 전후에 보존할
핵심부를 판별할 수 없으므로 `USER_EDITED`로 반환할 수 없고, 원문 그대로 쓰거나 제외해야 합니다.

`usedSubmissionIds`는 반환된 블록에서, `excludedSubmissionIds`는 요청 ID와 사용 ID의 차집합으로
직접 계산하며 모델의 자기 보고를 신뢰하지 않습니다 —
프롬프트 실험 중 모델이 60개 문장을 모두 사용했다고 적었지만 실제 본문에는 39개만 있었던
사례가 있었습니다.

생성된 내용은 성공으로 응답하기 전에 안전성을 재검수합니다(정책 §12.4). 생성 결과에는
금지어·연속 자모 우회·PII 계층을 적용하고, 사용자 입력용 프롬프트 인젝션·도배 규칙은 적용하지
않습니다. 금지어의 차단(block) 판정 기준은 공백을 보존한 표면 매칭입니다 — 공백을 지워야만
결합되는 매칭("야, 설마" → 야설)이나 자모 시퀀스 단독 적발("강가는" ⊃ 강간)은 긴 창작 본문에서
우연 결합 확률이 높아 flag로만 기록합니다. 주민등록번호는 차단하고, 허구의 서술일 수 있는
연락처·카드·이메일은 flag로 기록하되 차단하지 않습니다. flag 라벨은 `book_generation_flagged`
로그로 남아 임계값 조정의 실측 근거가 됩니다. `USER_ORIGINAL` 블록은 이미 평가 단계에서
안전성을 통과했으므로 검수 대상에서 제외됩니다. 오류는 구분됩니다.

| status | `errorCode` | 원인 |
|---|---|---|
| 502 | `INVALID_BOOK_RESPONSE` | 모델이 계약을 위반함 |
| 502 | `BOOK_GENERATION_UNSAFE` | 안전성 검수 또는 provider가 내용을 차단함 |
| 503 | `LLM_NOT_CONFIGURED` | provider가 설정되지 않음 |
| 503 | `LLM_PROVIDER_UNAVAILABLE` | 호출 실패·타임아웃 또는 예산 초과 |

책 생성은 모델 설정·요청 예산·read timeout·동시성 게이트를 평가와 따로 씁니다. 평가 값을
그대로 쓰면 본문이 잘리고 호출이 도중에 끊기며, 책 한 건이 평가용 슬롯을 몇 분씩 붙잡으면
문장 평가가 굶습니다. 동시성 슬롯을 기다리는 시간은 호출 예산에 포함하지 않으며, 기본 400초
예산은 180초 read timeout 기준 두 번의 시도와 백오프를 수용합니다.

## RabbitMQ 컨슈머 (배포 환경)

배포 환경에서는 HTTP와 별개로 두 개의 MQ 컨슈머가 켜집니다. 각각 독립 스위치라 한쪽만
켤 수 있고, 브로커가 늦게 떠도 기동을 막지 않고 백그라운드에서 재접속합니다.

| 컨슈머 | 스위치 | 큐 | 특징 |
| --- | --- | --- | --- |
| 문장 평가 (`app/evaluation_mq.py`) | `EVALUATION_MQ_ENABLED` | `sentence.evaluation.request` → `.result` | 재료(문장·오늘의 단어 5개)가 메시지에 실려 와 **DB 접근이 없습니다**. 페이로드가 HTTP 계약(`sentence-evaluation-v1`)과 같아 기존 검증·평가 경로를 그대로 재사용합니다 |
| 책 생성 (`app/book_mq.py`) | `BOOK_MQ_ENABLED` | `book.generation.request` → `.result` | 메시지는 실행 지시(jobId)만 나르고, 재료는 SELECT 전용 계정(`mvp_ai_readonly`)으로 백엔드 DB에서 직접 읽습니다 |

처리 오류는 실패 envelope으로 즉시 응답해 Spring이 회수 타임아웃을 기다리지 않고 실패
전이·자원 반환을 하게 하고, 계약 위반 페이로드는 DLQ로 보내 증거를 보존합니다. 결과 발행이
실패하면 계산한 결과를 쥔 채 백오프로 발행만 재시도합니다 — 즉시 재입큐하면 LLM 호출을
반복하게 되기 때문입니다.

## 안정성과 테스트

애플리케이션은 생명주기 동안 문장 처리와 책 생성용 `httpx.AsyncClient`를 각각 하나씩 소유합니다.
문장 평가와 후속 임베딩은 짧은 호출용 Client를 공유하고, MQ 책 생성은 최대 180초 걸리는 호출을
별도 Client에 격리합니다. 문장용 연결 풀은 `LLM_MAX_CONCURRENCY`와
`EMBEDDING_MAX_CONCURRENCY`의 합(기본 5 + 3), 책 생성용 풀은 `BOOK_MAX_CONCURRENCY`(기본 2)로
제한됩니다. Connect·read·write·pool 타임아웃은 각각 독립적으로 설정됩니다. Provider 호출은
각 동시성 게이트와 총 요청 예산,
적은 재시도 한도로 제한됩니다. `408`, `409`, `425`, `429`, `5xx`, 타임아웃, 네트워크 오류는
재시도 대상이고, 그 외 `4xx` 응답은 잘못된 provider 요청으로 취급합니다. 로그에는 correlation
ID와 sentence version ID만 남고 문장 원문이나 API 키는 절대 남지 않습니다.

책 생성은 잘린 응답 envelope도 재시도 대상입니다 — GMS 프록시가 큰 본문의 앞부분을 간헐적으로
잘라 보내는데, 평가는 응답이 작아 겪지 않지만 수천 토큰짜리 책 본문은 실제로 노출됩니다.
안전성 판정은 계층·범주 라벨만 로그에 남기고 적발된 표현 자체는 남기지 않습니다.

개발 의존성을 설치하고 격리된 테스트 스위트를 실행하려면:

```powershell
python -m pip install -r requirements-dev.txt
python -m pytest
```

Pytest는 loopback이 아닌 소켓 연결을 막습니다. Provider 테스트는 `httpx.MockTransport`를
쓰므로, 셸이나 `.env`에 API 키가 있어도 일반 테스트 실행으로는 Gemini 토큰을 소비할 수 없습니다.

`/health`는 liveness이며 LLM 자격 증명 없이도 항상 사용할 수 있습니다.
`/health/ready`는 지원되는 provider와 필요한 LLM 설정이 모두 있을 때만 `200 READY`를 반환하고,
그렇지 않으면 `503 NOT_READY`를 반환합니다.

## Opt-in 실 Gemini 스모크 테스트

실 서비스 스모크 테스트는 의도적으로 pytest 밖에 둡니다. 실제 자격 증명이 담긴 로컬 `.env`로
서비스를 띄운 뒤, 명시적으로 opt-in 합니다.

```powershell
$env:RUN_LIVE_AI_SMOKE = "true"
$env:AI_SERVICE_URL = "http://127.0.0.1:8001"
python -m scripts.smoke_gemini_evaluation
```

이 스크립트는 성공·단어 일부 사용·프롬프트 인젝션 케이스를 실행 중인 서비스로 보내 공개
응답 계약을 검증합니다. API 키를 읽거나 출력하는 일은 없습니다.

