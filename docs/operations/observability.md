# 중앙 로그 운영 가이드

Grafana는 서버의 `127.0.0.1:3001`에만 바인딩하고 외부에서는 `https://app.example.com/ops/logs/`로 접근한다. Loki의 3100 포트는 호스트에 공개하지 않는다. 관측 스택은 애플리케이션 배포와 분리된 `Jenkinsfile.observability` 작업으로 배포하므로 Loki·Alloy·Grafana 장애가 Backend, AI, Frontend 롤백을 막지 않는다.

## 최초 준비와 배포

### 1. Jenkins 자격 증명 등록

Jenkins → Manage Jenkins → Credentials에 Username/Password 자격 증명 `develop-grafana-admin`을 등록한다. 이 값이 Grafana 관리자 계정이 되며, 아래 Nginx Basic Auth 암호와는 다르게 관리한다.

### 2. Jenkins Job 생성

애플리케이션 파이프라인과 별도의 Job으로 만든다. 관측 스택 장애가 Backend·AI·Frontend 배포를 막지 않게 하는 것이 이 분리의 목적이므로 기존 Job에 스테이지를 얹지 않는다.

1. New Item → Pipeline (이름 예: `c206-observability-develop`)
2. Pipeline → Definition: `Pipeline script from SCM`
3. SCM: Git, 저장소와 자격 증명은 기존 Job과 동일
4. Branches to build: `*/develop`
5. Script Path: `Jenkinsfile.observability`

애플리케이션 배포마다 실행할 필요는 없다. 관측 스택 설정이 바뀔 때만 돌린다.

### 3. Nginx 경로 추가

호스트 nginx의 기존 server 블록(TLS와 `/api/`가 설정된 파일)에 아래 두 블록을 추가한다. Basic Auth 파일이 없으면 먼저 만든다.

```bash
sudo htpasswd -c /etc/nginx/.htpasswd-ops-log <사용자명>
```

`map` 지시어는 `server` 블록 밖(`http` 컨텍스트)에 둔다.

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
```

```nginx
    location /ops/logs/api/live/ {
        auth_basic "Operations logs";
        auth_basic_user_file /etc/nginx/.htpasswd-ops-log;
        proxy_http_version 1.1;
        proxy_set_header Upgrade           $http_upgrade;
        proxy_set_header Connection        $connection_upgrade;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_pass http://127.0.0.1:3001;
    }

    location /ops/logs/ {
        auth_basic "Operations logs";
        auth_basic_user_file /etc/nginx/.htpasswd-ops-log;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_pass http://127.0.0.1:3001;
    }
```

두 가지를 반드시 지킨다. 둘 다 `nginx -t`를 통과하므로 설정 검사로는 드러나지 않고 브라우저에서만 증상이 보인다.

- **`proxy_pass` 끝에 슬래시를 붙이지 않는다.** 컨테이너는 `GF_SERVER_SERVE_FROM_SUB_PATH=true`로 `/ops/logs` 접두사가 그대로 오기를 기대한다. 슬래시를 붙이면 nginx가 접두사를 벗겨내 Grafana가 자기 라우트를 찾지 못하고, 정적 자산이 404가 되거나 리다이렉트가 반복된다.
- **`/ops/logs/api/live/`는 WebSocket이다.** `Upgrade`·`Connection` 헤더가 없으면 화면은 뜨지만 실시간 tail(Live)이 동작하지 않는다. prefix가 더 길어 nginx가 이 블록을 먼저 매칭하므로 두 블록의 순서는 상관없다.

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 4. 파이프라인 실행

`c206-observability-develop` Job을 실행한다. 파이프라인은 임시 env 파일을 mode 0600으로 만들고 배포 후 삭제하며, 스택 health 확인 후 Loki 스모크 테스트를 수행한다.

```bash
curl -fsS -o /dev/null -w '%{http_code}\n' -u <사용자명>:<암호> https://app.example.com/ops/logs/login
```

`200`이면 외부 경로까지 완료다. 브라우저에서 `https://app.example.com/ops/logs/`에 접속해 Basic Auth를 통과한 뒤 `develop-grafana-admin` 계정으로 로그인한다.

### 설정 파일이 호스트의 어디에 있는가

Jenkins는 컨테이너지만 Docker 데몬은 호스트 것이다. 워크스페이스 경로를 그대로 bind mount하면 compose CLI가 Jenkins 컨테이너 기준 경로(`/var/jenkins_home/...`)를 호스트 데몬에 넘기고, 호스트에 그 경로가 없으므로 Docker가 그 자리에 **빈 디렉터리**를 만든다. Loki는 디렉터리를 설정 파일로 읽으려다 죽지만 증상은 healthcheck 타임아웃으로만 보인다.

그래서 파이프라인은 `OBSERVABILITY_CONFIG_DIR=/opt/c206-observability`를 넘기고, `deploy-observability.sh`가 배포마다 저장소의 설정을 그 호스트 경로로 복사한다. 복사도 워크스페이스 마운트 없이 tar를 stdin으로 흘려보내 수행한다.

```bash
sudo ls -l /opt/c206-observability
```

`loki.yml`, `alloy.alloy`, `grafana/`가 보여야 한다. **호스트에서 이 파일을 직접 고치지 않는다.** 다음 배포에서 덮어쓰므로, 변경은 저장소의 `infra/observability/`에 하고 파이프라인을 다시 돌린다.

로컬에서 직접 compose를 띄울 때는 이 변수가 필요 없다. 기본값이 compose 파일이 있는 디렉터리이며, 그 경우 CLI와 데몬이 같은 파일시스템을 보므로 상대 경로가 그대로 동작한다.

## 로그 검색

Grafana의 **배포·운영 로그** 대시보드를 연다.

- 배포 실패: `deploy_id` 또는 `commit` 변수를 입력해 배포 이력 패널에서 stage, exit code, OOM 여부, 예외 종류, 파일과 줄 번호를 확인한다. `jenkins_url` 링크로 해당 Jenkins 실행을 연다.
- 요청 추적: Backend 응답의 `X-Request-ID` 또는 장애 응답과 함께 기록된 `request_id`를 요청 흐름 패널에 입력한다. 같은 ID로 Backend와 AI 로그를 시간순으로 확인한다.
- 애플리케이션 예외: Backend 예외 및 AI 예외 패널에서 `exception_type`, `source_file`, `source_line`, stack trace를 확인한다.
- 인프라 장애: 인프라 오류 패널에서 PostgreSQL, Redis, RabbitMQ, Frontend의 error, fatal, unhealthy, connection refused 로그를 검색한다.

`request_id`, `deploy_id`, `commit`은 Loki label이 아니라 JSON 필드로 검색한다. 값 종류가 계속 늘어나는 필드를 label로 만들면 Loki 인덱스 부하가 커지므로 변경하지 않는다.

## 배포 실패 대응

1. Grafana 배포 이력에서 `deploy_id`로 실패 이벤트와 요약을 찾는다.
2. Jenkins 링크를 열고 실패 stage와 원본 console stack trace를 확인한다.
3. Jenkins Artifact의 `deploy-diagnostics/`를 내려받아 전체 컨테이너 로그, 안전하게 축약된 inspect 결과, health, Docker event, 예외 요약을 확인한다. 비밀값 검사가 실패한 Artifact는 자동 삭제된다.
4. Grafana가 내려간 경우 Jenkins Artifact를 우선 사용하고 서버에서 `docker logs --tail 200 <container>`로 Loki, Alloy, Grafana 순서로 확인한다. 애플리케이션 원본 Jenkins console 로그를 Loki로 복제하지 않는다.

## 보존, 용량, 자격 증명

- Loki 보존 기간은 정확히 14일(`336h`)이다. 주 1회 `docker system df -v`와 `docker volume inspect c206-observability_loki-data`를 확인하고 디스크 경보 기준을 별도로 설정한다.
- Jenkins는 최근 20개 관측 스택 빌드를 보존한다. 조직의 Artifact 용량 정책과 함께 주기적으로 확인한다.
- Grafana 관리자 암호와 Nginx Basic Auth는 정기 교체하고, 교체 직후 관측 스택 파이프라인 및 외부 로그인을 검증한다.
- 배포 전후 `scripts/check-observability-secrets.sh`와 `scripts/check-diagnostics-secrets.sh`를 실행한다. `password`, `token`, `authorization`, `cookie`, `secret` marker가 Jenkins 요약, Artifact, Loki 이벤트에 없는지 표본 감사한다.

## 점검 명령

```bash
docker compose --env-file infra/observability/.env -f infra/observability/compose.yml ps
curl -fsS http://127.0.0.1:3001/api/health
bash scripts/smoke-observability.sh
```

스모크 테스트는 고유한 배포 실패, Backend JSON, 일반 텍스트 로그를 발행하고 60초 안에 Loki에서 모두 검색되는지 확인한다. 테스트가 만든 컨테이너만 삭제하며 `retention_period: 336h`도 함께 검증한다. Jenkins에서는 결과가 `observability-smoke-report.txt` Artifact로 남는다.
