# 로컬 개발 인프라

이 Compose 프로젝트는 로컬 데이터·메시징 인프라만 기동한다.

- PostgreSQL 16 + pgvector (`pgvector/pgvector:pg16`)
- Redis 7
- RabbitMQ 3.13 (관리 UI 포함)

백엔드를 빌드하거나 실행하지는 않는다. 별도로 배포되는 `backend-app-a`·`backend-app-b`
컨테이너가 `mvp-infra` Docker 네트워크에 붙어 이 서비스들을 사용한다.

## 사전 준비

- Docker Desktop (Docker Compose v2 포함)
- 호스트 포트 `5432`, `6379`, `5672`, `15672` 사용 가능

## 시작

저장소 루트에서:

```powershell
Copy-Item infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/compose.yml config
docker compose --env-file infra/.env -f infra/compose.yml up -d
```

`config`는 컨테이너를 띄우기 전에 변수 치환을 검증한다. `infra/.env`의 예시 비밀번호는
로컬 환경에 맞게 바꾼다.

호스트에 게시하는 포트는 `127.0.0.1`에만 바인딩된다. 호스트 프로세스는 아래 주소로 접속한다.

| 서비스 | 호스트 접속 주소 |
|---|---|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| RabbitMQ AMQP | `localhost:5672` |
| RabbitMQ 관리 UI | `http://localhost:15672` |

`mvp-infra`에 붙은 백엔드 컨테이너들은 Docker 서비스 DNS를 쓴다:

| 서비스 | 백엔드 기준 주소 |
|---|---|
| PostgreSQL | `postgres:5432` |
| Redis | `redis:6379` |
| RabbitMQ AMQP | `rabbitmq:5672` |
| RabbitMQ 관리 UI | `http://rabbitmq:15672` |

PostgreSQL 계정은 `POSTGRES_USER`·`POSTGRES_PASSWORD`에서 온다. Redis는
`REDIS_PASSWORD`가 필요하며 공유 HTTP 세션, ShedLock 잠금 키, 리더보드 dirty
pending/processing 셋, 기존 애플리케이션 데이터를 저장한다. RabbitMQ는
`RABBITMQ_USER`·`RABBITMQ_PASSWORD`·`RABBITMQ_VHOST`를 쓴다.

## 상태 확인

```powershell
docker compose --env-file infra/.env -f infra/compose.yml ps
docker compose --env-file infra/.env -f infra/compose.yml logs --follow
```

세 서비스 모두 헬스체크가 있다. PostgreSQL은 `pg_isready`, Redis는 인증 후 `PING`,
RabbitMQ는 `rabbitmq-diagnostics -q ping`을 쓴다.

## 기존 볼륨을 pgvector 이미지로 전환하기

Flyway `V4`가 `CREATE EXTENSION vector`를 실행할 수 있도록 PostgreSQL 이미지를
`postgres:16-alpine`에서 `pgvector/pgvector:pg16`으로 바꿨다. 둘 다 PostgreSQL 16이라
기존 `postgres_data` 볼륨은 덤프/복원 없이 그대로 마운트되고, 이미지가 바뀌면
`docker compose up -d`가 컨테이너를 자동으로 재생성한다.

두 이미지는 C 라이브러리가 다르다(alpine은 musl, Debian은 glibc). 클러스터가 로케일 기반
collation으로 초기화돼 있었다면 전환 후 PostgreSQL이 collation 버전 불일치를 보고하고
텍스트 인덱스를 재구축해야 한다. 배포 **전에** 기존 클러스터를 확인한다:

```bash
docker exec -it postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SHOW lc_collate;"
```

- `C` 또는 `C.UTF-8`: 바이트 순서 collation이라 C 라이브러리와 무관하다. 추가 절차 없이 배포한다.
- 로케일 값(예: `en_US.UTF-8`): 새 컨테이너가 healthy가 된 뒤 아래를 한 번 실행한다.

```bash
docker exec -it postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "REINDEX DATABASE \"$POSTGRES_DB\"; ALTER DATABASE \"$POSTGRES_DB\" REFRESH COLLATION VERSION;"
```

재생성할 수 없는 데이터가 있는 환경이라면 먼저 백업한다. 공유 환경에서의 대안은 볼륨을
새로 만들고 사전을 다시 시드하는 것인데, `down -v`는 데이터를 삭제하므로 공유 환경에는
절대 쓰지 않는다.

`V4`는 `scripts/deploy-backend.sh`의 배포 롤백으로 되돌릴 수 없다. 마이그레이션이 한 번
적용되면 이전 백엔드 이미지는 기록된 마이그레이션이 클래스패스에 없어 Flyway 검증에
실패한다.

## 중지와 초기화

데이터 볼륨을 유지한 채 컨테이너만 중지:

```powershell
docker compose --env-file infra/.env -f infra/compose.yml down
```

컨테이너와 로컬 개발 데이터 볼륨까지 삭제:

```powershell
docker compose --env-file infra/.env -f infra/compose.yml down -v
```

`down -v`는 이 Compose 프로젝트가 선언한 이름 있는 볼륨만 지운다. 로컬 초기화 용도이며
공유 데이터에는 사용하지 않는다.

## 백엔드 페어 연결

백엔드 배포에서 `mvp-infra`를 external 네트워크로 선언하고 `backend-app-a`와
`backend-app-b`를 모두 붙인다. 두 슬롯은 같은 서비스 DNS 주소, 계정, RabbitMQ vhost,
이미지, 커밋, Spring 프로필을 써야 한다. 호스트는 두 컨테이너를 루프백 포트
`8081`·`8082`에만 공개한다. 상세: [이중 인스턴스 런북](../exec/배포-런북/백엔드-이중-인스턴스.md)

## AI 서비스 읽기 전용 DB 계정

`ai-service`는 책 생성 재료를 백엔드 DB에서 직접 읽으므로(ISSUE-122) `SELECT` 권한만
가진 `mvp_ai_readonly` 계정으로 접속한다. 쓰기 금지는 규율이 아니라 DB가 거부한다.

계정은 Flyway 마이그레이션 `V7__add_ai_service_readonly_role.sql`이 생성하며, 비밀번호는
`ai_readonly_password` 플레이스홀더로 받는다. 백엔드가 `AI_READONLY_DB_PASSWORD`로
공급하고, 이 변수가 비어 있으면 마이그레이션은 안내만 남기고 계정을 만들지 않는다 —
로컬·테스트 실행이 계정 없이도 동작하는 이유다.

두 서비스는 같은 비밀값을 받아야 한다. CI에서는 Jenkins 문자열 자격증명
`develop-ai-readonly-db-password`에서 온다:

- 백엔드 — `AI_READONLY_DB_PASSWORD`, 마이그레이션이 소비
- ai-service — `BOOK_DB_PASSWORD` (+ `BOOK_DB_HOST=postgres`, `BOOK_DB_PORT=5432`,
  `BOOK_DB_NAME=mvp_backend`, `BOOK_DB_USER=mvp_ai_readonly`)

비밀값 회전은 이 자격증명 하나를 바꾸고 **백엔드를 먼저** 재배포한다. 마이그레이션이 기존
계정에 `ALTER ROLE ... PASSWORD`를 실행하므로, ai-service는 새 값으로 재배포될 때까지
인증에 실패한다.

배포 후 권한 확인:

```bash
docker exec -it mvp-backend-infra-postgres-1 \
  psql -U mvp_ai_readonly -d mvp_backend \
  -c 'select count(*) from boss_submissions'
```
