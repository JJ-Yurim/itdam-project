# develop Jenkins Credentials 및 백엔드 배포 절차

## Jenkins Credentials

파이프라인 4개가 참조하는 자격증명 전체 목록이다. Jenkins를 새로 구성할 때 아래 11개가
모두 등록돼야 배포가 성공한다.

| ID | 종류 | 환경 변수 | 사용 파이프라인 |
| --- | --- | --- | --- |
| `develop-postgres` | Username with password | `POSTGRES_USER`, `POSTGRES_PASSWORD` | backend |
| `develop-rabbitmq` | Username with password | `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | backend, ai-service |
| `develop-redis-password` | Secret text | `REDIS_PASSWORD` | backend |
| `develop-ai-readonly-db-password` | Secret text | `AI_READONLY_DB_PASSWORD`(backend) / `BOOK_DB_PASSWORD`(ai-service) | backend, ai-service |
| `develop-admin-account` | Username with password | `ADMIN_ACCOUNT_EMAIL`, `ADMIN_ACCOUNT_PASSWORD` | backend |
| `develop-smtp` | Username with password | `SMTP_USERNAME`, `SMTP_PASSWORD` (Gmail 앱 비밀번호) | backend |
| `develop-kakao-oauth` | Username with password | `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | backend |
| `develop-naver-oauth` | Username with password | `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | backend |
| `develop-google-oauth` | Username with password | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | backend |
| `gms-api-key` | Secret text | `LLM_API_KEY`, `EMBEDDING_API_KEY` | ai-service |
| `develop-grafana-admin` | Username with password | Grafana 관리자 계정 | observability |

비밀값은 Jenkins에만 저장한다. Pipeline은 배포 단계에서만 Credentials를 바인딩하고 `umask 077` 권한의 임시 환경 파일을 만든 뒤 종료 시 삭제한다. 비밀값을 Git, Docker 이미지, Docker 인자 또는 콘솔 로그에 직접 기록하지 않는다. Credential 값에는 줄바꿈을 사용하지 않는다.

## 배포 순서

1. Jenkins가 백엔드 테스트와 이미지 빌드를 수행한다.
2. PostgreSQL, Redis, RabbitMQ를 `mvp-infra` 네트워크에서 시작하고 각 healthcheck를 확인한다.
3. 기존 백엔드 topology가 단일 `backend-app`, 정상 A/B 쌍 또는 없음인지 읽기 전용으로 확인한다. 부분 쌍이나 서로 다른 이미지·커밋이면 기존 컨테이너를 건드리지 않고 실패한다.
4. `backend-app-a`를 `127.0.0.1:8081`에 시작하고 healthy가 될 때까지 기다린다.
5. `backend-app-b`를 `127.0.0.1:8082`에 시작하고 healthy가 될 때까지 기다린다.
6. AI 읽기 전용 DB 역할을 확인하고 두 슬롯의 이미지와 `DEPLOY_COMMIT`이 같은지 검증한다.

인프라가 준비되지 않으면 기존 백엔드를 제거하지 않는다. 새 A/B 중 어느 하나가 실패하면 양쪽 새 슬롯의 진단 자료를 수집한 뒤 모두 제거하고 이전 단일 topology 또는 A/B 쌍으로 롤백한다. 롤백 성공 후에도 해당 Jenkins 빌드는 실패 처리한다.

상세한 Nginx 적용, EC2 점검, 세션·CSRF 장애 전환 검증은 [백엔드 2개 인스턴스 배포 런북](백엔드-이중-인스턴스.md)을 따른다.

## 상태와 로그 확인

```bash
docker compose --env-file <runtime-env> -f infra/compose.yml ps
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:8082/actuator/health

docker inspect --format '{{.Name}} {{.Config.Image}} {{range .Config.Env}}{{println .}}{{end}}' \
  backend-app-a backend-app-b | grep -E '/backend-app-|DEPLOY_COMMIT=|SPRING_PROFILES_ACTIVE='

for container in backend-app-a backend-app-b; do
  docker logs --tail 200 "$container"
done
```

`docker compose down -v`는 로컬 초기화 전용이다. Jenkins 또는 공유 환경에서 실행하면 PostgreSQL과 Redis 영속 데이터를 삭제하므로 사용하지 않는다.
