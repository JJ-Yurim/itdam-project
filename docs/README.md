# 프로젝트 레퍼런스

이 디렉터리는 현재 구현을 다시 시작하거나 확장할 때 필요한 최소 기준만 보관한다.
과거 구현 계획과 설계 초안은 Git 이력에서 확인한다.

## 단일 출처 (Source of truth)

| 필요한 것 | 출처 |
| --- | --- |
| 런타임 동작과 DTO | `backend/src/main/java` |
| HTTP 계약 | 실행 중인 Swagger UI `/swagger-ui.html`, 기계 판독용 `/v3/api-docs` |
| 배포 DB 스키마 | `backend/src/main/resources/db/migration/` (Flyway V1~V16) |
| 환경 설정 | `backend/src/main/resources/application-*.properties` |
| 빌드·배포 절차와 외부 서비스 | `exec/` (포팅 매뉴얼·외부 서비스·DB 덤프·시연 시나리오) |

## 프로젝트 지도

| 영역 | 위치 | 책임 |
| --- | --- | --- |
| Backend | `backend/` | Java 21, Spring Boot 4.0.7 — MVC·JPA·Security·AMQP, 이중 인스턴스 배포 |
| AI Service | `ai-service/` | FastAPI — Gemini 평가·책 생성, 임베딩, MQ 컨슈머 |
| Frontend | `frontend/` | React 19 SPA — feature 단위 구조, TanStack Query |
| API | `backend/src/main/java/**/api` | REST 컨트롤러와 OpenAPI 계약 인터페이스 |
| Database | `backend/src/main/resources/db/migration` | PostgreSQL Flyway 마이그레이션 |
| Infrastructure | `infra/`, `Jenkinsfile*`, `scripts/` | 컨테이너·배포 구성, 슬롯 교체·롤백 |
| Operations | `docs/operations/` | 로그·부하 테스트·관리자 API·단어 시딩 Runbook |
| Deployment | `exec/배포-런북/` | Jenkins 자격증명, 프론트 배포, 백엔드 이중 인스턴스 (산출물 제출과 겸용) |
| ERD | `docs/implementation/itdam-erd.dbml` | 운영 스키마(Flyway V1~V16, 30개 테이블)에서 생성한 DBML. [dbdiagram.io](https://dbdiagram.io)에 붙여 넣으면 다이어그램으로 볼 수 있다 |

## 실행과 검증

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

- `local`·`prod`는 PostgreSQL + Flyway 검증을 사용한다.
- `develop`은 PostgreSQL + Flyway + Hibernate `validate`다. 호스트에서는 선택적으로
  `infra/.env`를 읽어 `localhost`에 붙고, Compose 안에서는 공유 네트워크의 `postgres`
  서비스에 붙는다.
- 헬스 엔드포인트: `/actuator/health`
- API 탐색: `/swagger-ui.html` — 앱 수준은 `permitAll`이지만 배포 서버에서는 계약에 관리자
  엔드포인트가 포함되므로 모든 문서 경로(`/api/swagger-ui/`, `/api/v3/api-docs`,
  `/api/v3/api-docs/swagger-config`)가 nginx Basic Auth 뒤에 있다. 엔드포인트를 바꾸면
  이 API 레퍼런스를 함께 갱신한다.
- 중앙 로그 접근과 관측 스택 배포: `docs/operations/observability.md`

## API 접근 계약

- Base path: `/api/v1`
- 인증은 서버 세션(`HttpSession`, `JSESSIONID`)이며 JWT가 아니다. 배포 프로필에서는
  세션이 Redis(spring-session)에 저장되어 이중 인스턴스가 공유한다.
- 변경 요청 전 `GET /api/v1/csrf`를 호출하고, 반환/쿠키 토큰을 `X-XSRF-TOKEN` 헤더로
  credentials와 함께 보낸다.
- 로그인 시 CSRF 토큰이 회전되므로 `POST /api/v1/auth/sessions` 후 다시 받아 온다.
- 공개 업무 엔드포인트: 가입, 로그인, 이메일 인증(`/email-verifications`,
  `/email-verifications/confirm`), CSRF 발급. 나머지 `/api/v1/**`는 세션이 필요하다.
- 이메일 인증 상태는 배포 프로필에서 Redis에 저장되고(test·local은 인메모리 폴백),
  메일은 Gmail SMTP(`SMTP_USERNAME`/`SMTP_PASSWORD`)로 발송된다(test·local은 로깅 폴백).
- 가입은 사전 이메일 인증이 필요하다: `POST /email-verifications/confirm`이 일회성
  `verificationToken`(10분 TTL, 성공 시 소비)을 반환하고, 이를 가입 본문에 담아야 한다.
  누락·만료·불일치는 403 `EMAIL_NOT_VERIFIED`다. 이메일은 정규화된다(소문자, Gmail
  `+tag` 제거, googlemail.com 통일).
- 소셜 로그인(Kakao/Naver/Google): `GET /api/oauth2/authorization/{kakao|naver|google}`로
  진입한다(콜백은 `/api/login/oauth2/code/{registrationId}` — 둘 다 `/api` 아래라 배포
  nginx가 그대로 백엔드로 라우팅한다). 성공 시 비밀번호 로그인과 같은 세션 인증을 저장하고
  `/`로 리다이렉트하며, 실패는 `/login?socialError=<code>`로 간다
  (`social_email_not_provided`, `social_email_already_registered`,
  `social_account_inactive`). 소셜 계정은 비밀번호가 없고(`password_hash` null),
  재로그인은 `(provider, provider_id)`로 매칭한다.
- 오류 응답은 `ApiError`(occurredAt·code·message·fieldErrors) 단일 형식이다.

## API 기능 지도

| 기능 | 엔드포인트 계열 |
| --- | --- |
| 계정·세션 | `/users`, `/auth/sessions`, `/users/me`, `/csrf`, `/email-verifications` |
| 오늘의 단어 | `/daily-words/today` |
| 문장·평가 | `/sentences`, `/sentence-versions/*`, `/evaluation-usage/today` |
| 개인 서고 | `/me/sentences` |
| 보스 레이드·제출 | `/boss-runs` |
| 책 생성·도서관 | `/ai-jobs`, `/boss-runs/{id}/book-generation`, `/library/books` |
| 관리자 | `/api/admin/*` — `docs/operations/admin-api.md` |

요청·응답 스키마, 응답 코드, 보안 요구사항, 전체 엔드포인트 목록은 Swagger를 쓴다.
Swagger 애노테이션은 같은 패키지의 `*Api` 인터페이스에 두고 컨트롤러는 런타임 MVC
동작을 유지한다.

## 어댑터 경계

외부 연동은 전부 `@ConditionalOnProperty` 스위치로 구현이 교체되고, 기본값은 항상 외부
의존이 없는 쪽이다. 배포 프로필·환경변수가 실제 구현을 켠다.

| 스위치 | 기본값 | 배포값 | 구현 |
| --- | --- | --- | --- |
| `ai.evaluation.client` | `fake` | `http` | FastAPI 평가 호출 |
| `evaluation.dispatch` | `sync` | `rabbit` | 평가 접수-발행-폴링 비동기 파이프라인 |
| `book.generation.client` | `logging` | `rabbit` | 책 생성 MQ 발행·결과 수신 |
| `similarity.client` | (미설정) | `pgvector` | 임베딩 코사인 유사도 차단 (임계값 0.70) |
| `boss.stream.channel` | `in-process` | `redis` | SSE 팬아웃 |
| `boss.leaderboard.store` | (인메모리) | `redis` | 리더보드 ZSET |
| `email.verification.store/mail` | 인메모리/로깅 | `redis`/`smtp` | 이메일 인증 |

## 문서 유지 규칙

이 파일은 간결하게 유지한다. 동작은 코드와 OpenAPI 메타데이터에, 스키마 변경은 Flyway에,
임시 계획은 이슈·MR 토론에 둔다 — 장수 계획 문서를 새로 커밋하지 않는다.
