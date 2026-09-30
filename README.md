# 잇담

매일 발급되는 다섯 단어로 문장을 쓰고, AI 평가를 받아 보스 레이드에 제출하고, 레이드가 끝나면
제출된 문장들이 한 권의 공동 책으로 엮이는 창작 게임 서비스입니다.

## 저장소 구성

| 영역 | 위치 | 스택 |
| --- | --- | --- |
| Backend | `backend/` | Java 21, Spring Boot 4.0.7 (MVC·JPA·Security·AMQP), Flyway |
| AI Service | `ai-service/` | Python 3.12, FastAPI, Gemini(GMS), aio-pika |
| Frontend | `frontend/` | React 19, TypeScript, Vite, TanStack Query, Tailwind v4 |
| Infra | `infra/` | Docker Compose — PostgreSQL(pgvector), Redis, RabbitMQ, 관측 스택(Loki·Alloy·Grafana) |
| 배포 | `Jenkinsfile*` | Jenkins 파이프라인 4개 (backend / ai-service / frontend / observability), 단일 EC2 |
| 문서 | `docs/` | 프로젝트 레퍼런스, 운영 Runbook, 배포 가이드 — 색인은 `docs/README.md` |

## 현재 연동 상태

배포 환경(develop)에서 전부 실제 구현으로 동작합니다.

- **문장 평가**: Spring → RabbitMQ → FastAPI(Gemini) 비동기 파이프라인. 접수-발행-폴링 구조로
  평가 지연이 톰캣 스레드를 점유하지 않습니다. `evaluation.dispatch` 스위치(기본 `sync`)로 켭니다.
- **문장 임베딩·유사도**: 평가 성공 시 임베딩(GMS, 768차원)을 pgvector에 저장하고, 보스 제출 시
  동일 사용자·동일 회차의 이전 제출과 코사인 유사도를 비교해 재제출을 차단합니다
  (`similarity.block-threshold`, 실측 근거로 0.70).
- **공동 책 생성**: 레이드 클리어 시 RabbitMQ로 실행 지시를 발행하고, FastAPI가 SELECT 전용
  계정으로 재료를 읽어 생성한 뒤 결과 큐로 응답합니다. 발행 보장(잡 행 + 릴레이)·DLQ·계약 버전
  검증을 포함합니다.
- **실시간**: 보스 HP·리더보드는 SSE로 내려가고, 팬아웃은 Redis pub/sub(배포)·in-process(로컬)
  구현을 프로퍼티로 교체합니다.
- **Redis**: 이메일 인증 상태, 리더보드 ZSET, SSE 팬아웃 채널에 사용합니다.

외부 연동이 없는 환경에서도 기동·테스트가 가능하도록 모든 어댑터에 Fake/Logging 구현과
`@ConditionalOnProperty` 스위치가 있습니다. 기본값은 항상 외부 의존이 없는 쪽입니다.

## 로컬 실행

사전 준비: Docker Desktop, Java 21, Node.js 24.18.0 + pnpm 11.17.0, Python 3.12.

```powershell
# 1. 인프라 (PostgreSQL + Redis + RabbitMQ)
Copy-Item infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/compose.yml up -d

# 2. 백엔드
cd backend
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

- Flyway가 `db/migration`의 마이그레이션(V1~V16)을 적용한 뒤 Hibernate가 `validate`로 검증합니다.
- Health: `/actuator/health`, API 문서: `/swagger-ui.html`
- AI Service 실행: [`ai-service/README.md`](ai-service/README.md)
- Frontend 실행: [`frontend/README.md`](frontend/README.md)
- 인프라 상세(볼륨 전환·readonly 계정 등): [`infra/README.md`](infra/README.md)

## 테스트

```powershell
cd backend
.\gradlew.bat test        # 단위·JPA·E2E (H2, Docker 불필요)
.\gradlew.bat infraTest   # Testcontainers — pgvector/pgvector:pg16, RabbitMQ, 계약 컨테이너
```

- `infraTest`는 Docker가 필요하며, Spring↔FastAPI HTTP 계약 검증에는
  `ai-service/Dockerfile.test`로 빌드한 이미지를 `AI_CONTRACT_TEST_IMAGE` 환경변수로 지정합니다.
- AI Service: `python -m pytest` (ai-service/), Frontend: `pnpm typecheck && pnpm lint && pnpm test` (frontend/)
- 마이그레이션을 추가하면 스키마 검증 테스트(`PostgresSchemaIntegrationTest`)가 infraTest에서
  목록을 고정하므로 함께 돌려야 합니다.

## API 접근 계약 요약

- Base path `/api/v1`, 인증은 서버 세션(`HttpSession`, `JSESSIONID`)이며 JWT를 쓰지 않습니다.
- 변경 요청 전 `GET /api/v1/csrf`로 토큰을 받아 `X-XSRF-TOKEN` 헤더로 보냅니다. 로그인 성공 시
  토큰이 회전되므로 다시 받아야 합니다.
- 소셜 로그인(Kakao/Naver/Google)은 `GET /api/oauth2/authorization/{provider}`로 진입합니다.
- 상세 규칙과 엔드포인트 지도: [`docs/README.md`](docs/README.md)

## 배포

develop 브랜치 머지 시 Jenkins가 변경된 영역만 감지해 테스트·빌드·배포합니다. 관측 스택은
별도 파이프라인이라 로그 스택 장애가 애플리케이션 배포를 막지 않습니다.

백엔드는 **A/B 두 슬롯**(`backend-app-a`:8081, `backend-app-b`:8082)으로 떠서 호스트 nginx가
분산하고, 배포는 슬롯을 순차 교체합니다. 두 인스턴스가 상태를 공유하기 위해 HTTP 세션은
Redis(spring-session)에 저장하고, 주기 작업(평가 발행 릴레이·회수, 책 생성 회수, 이미지 정리)은
**ShedLock**으로 한 인스턴스만 실행합니다.

- 빌드·배포 전체 절차와 환경 변수: [`exec/01-포팅-매뉴얼.md`](exec/01-포팅-매뉴얼.md)
- 배포 런북(이중 인스턴스·프론트·Jenkins 자격증명): [`exec/배포-런북/`](exec/배포-런북)
- 운영 Runbook(로그·부하 테스트·관리자 API): `docs/operations/`
