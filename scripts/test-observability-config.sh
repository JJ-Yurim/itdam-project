#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file="$repo_root/infra/observability/compose.yml"
env_file="$repo_root/infra/observability/.env.example"
loki_file="$repo_root/infra/observability/loki.yml"
alloy_file="$repo_root/infra/observability/alloy.alloy"
datasource_file="$repo_root/infra/observability/grafana/provisioning/datasources/loki.yml"
deploy_script="$repo_root/scripts/deploy-observability.sh"
env_writer="$repo_root/scripts/write-observability-runtime-env.sh"
secret_guard="$repo_root/scripts/check-observability-secrets.sh"
jenkinsfile="$repo_root/Jenkinsfile.observability"
event_publisher="$repo_root/scripts/publish-deploy-event.sh"
event_publisher_dockerfile="$repo_root/infra/observability/event-publisher/Dockerfile"
dashboard_provider="$repo_root/infra/observability/grafana/provisioning/dashboards/provider.yml"
dashboard_file="$repo_root/infra/observability/grafana/dashboards/operations-logs.json"
smoke_script="$repo_root/scripts/smoke-observability.sh"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

for required_file in \
  "$compose_file" "$env_file" "$loki_file" "$alloy_file" "$datasource_file" \
  "$deploy_script" "$env_writer" "$secret_guard" "$jenkinsfile"; do
  [[ -f "$required_file" ]] || fail "missing required file: $required_file"
done

for required_file in \
  "$event_publisher" "$event_publisher_dockerfile" "$dashboard_provider" "$dashboard_file" "$smoke_script"; do
  [[ -f "$required_file" ]] || fail "missing required file: $required_file"
done

rendered=$(mktemp)
trap 'rm -f "$rendered"' EXIT

validation_password=validation-only-not-a-secret
export GRAFANA_ADMIN_PASSWORD="$validation_password"
docker compose --env-file "$env_file" -f "$compose_file" config >"$rendered"
unset GRAFANA_ADMIN_PASSWORD

for image in grafana/loki:3.7.0 grafana/alloy:v1.18.0 grafana/grafana:12.4.0; do
  grep -Fxq "    image: $image" "$rendered" || fail "missing exact image: $image"
done

[[ $(grep -c '^[[:space:]]*published:' "$rendered") -eq 1 ]] \
  || fail "only Grafana may publish a host port"
grep -Eq '^[[:space:]]*host_ip:[[:space:]]*127\.0\.0\.1$' "$rendered" \
  || fail "Grafana host IP must be 127.0.0.1"
grep -Eq '^[[:space:]]*target:[[:space:]]*3000$' "$rendered" \
  || fail "Grafana container port must be 3000"
grep -Eq '^[[:space:]]*published:[[:space:]]*"?3001"?$' "$rendered" \
  || fail "Grafana published port must be 3001"
if grep -Eq '^[[:space:]]*published:[[:space:]]*"?3100"?$' "$rendered"; then
  fail "Loki port 3100 must not be published"
fi

socket_block=$(grep -A4 -F 'source: /var/run/docker.sock' "$rendered" || true)
grep -Fq 'target: /var/run/docker.sock' <<<"$socket_block" \
  || fail "Alloy Docker socket target is missing"
grep -Fq 'read_only: true' <<<"$socket_block" \
  || fail "Alloy Docker socket mount must be read-only"

[[ $(grep -c '^[[:space:]]*driver:[[:space:]]*json-file$' "$rendered") -eq 4 ]] \
  || fail "all three services and the logging extension must use json-file logging"
[[ $(grep -c '^[[:space:]]*max-size:[[:space:]]*10m$' "$rendered") -eq 4 ]] \
  || fail "all service log rotation max-size values must be 10m"
[[ $(grep -Ec '^[[:space:]]*max-file:[[:space:]]*"?5"?$' "$rendered") -eq 4 ]] \
  || fail "all service log rotation max-file values must be 5"
[[ $(grep -c '^[[:space:]]*healthcheck:$' "$rendered") -eq 3 ]] \
  || fail "all observability services must have healthchecks"
[[ $(grep -c '^[[:space:]]*cpus:' "$rendered") -eq 3 ]] \
  || fail "all observability services must have CPU limits"
[[ $(grep -c '^[[:space:]]*memory:' "$rendered") -eq 3 ]] \
  || fail "all observability services must have memory limits"

grep -Fq 'GF_SERVER_ROOT_URL: https://app.example.com/ops/logs/' "$rendered" \
  || fail "Grafana root URL must use the /ops/logs/ HTTPS path"
grep -Eq 'GF_SERVER_SERVE_FROM_SUB_PATH:[[:space:]]*"?true"?$' "$rendered" \
  || fail "Grafana must serve from its configured subpath"

grep -Eq '^[[:space:]]*retention_period:[[:space:]]*336h[[:space:]]*$' "$loki_file" \
  || fail "Loki retention_period must be exactly 336h"
grep -Eq '^[[:space:]]*max_query_lookback:[[:space:]]*336h[[:space:]]*$' "$loki_file" \
  || fail "Loki max_query_lookback must be exactly 336h"
grep -Eq '^[[:space:]]*retention_enabled:[[:space:]]*true[[:space:]]*$' "$loki_file" \
  || fail "Loki compactor retention must be enabled"

for forbidden_label in request_id deploy_id commit; do
  if grep -Eq "target_label[[:space:]]*=[[:space:]]*\"${forbidden_label}\"" "$alloy_file"; then
    fail "high-cardinality Alloy label is forbidden: $forbidden_label"
  fi
done

grep -Eq 'uid:[[:space:]]*loki[[:space:]]*$' "$datasource_file" \
  || fail "Grafana datasource UID must be loki"
grep -Eq 'url:[[:space:]]*http://loki:3100[[:space:]]*$' "$datasource_file" \
  || fail "Grafana datasource must use the internal Loki endpoint"

grep -Eq '^[[:space:]]*external:[[:space:]]*true[[:space:]]*$' "$compose_file" \
  || fail "mvp-observability must be an externally managed network"

# 설정 파일 bind mount의 원본은 파라미터여야 한다. 상대 경로로 되돌리면 컨테이너화된
# Jenkins에서 compose CLI가 워크스페이스 경로를 호스트 데몬에 넘기고, 호스트에는 그
# 경로가 없어 Docker가 빈 디렉터리를 만든다. Loki는 설정을 읽지 못하지만 증상은
# healthcheck 타임아웃으로만 보인다. loki, alloy, grafana provisioning, grafana
# dashboards 네 곳 모두 해당한다.
[[ $(grep -c '\${OBSERVABILITY_CONFIG_DIR' "$compose_file") -eq 4 ]] \
  || fail "all four config bind mounts must use OBSERVABILITY_CONFIG_DIR"

bash -n "$deploy_script" "$env_writer" "$secret_guard" "$event_publisher" "$smoke_script"

# 셸 스크립트는 Git 인덱스에서 실행 가능해야 한다. Windows 체크아웃은
# core.filemode=false라 파일시스템의 실행 비트가 무의미하고 [[ -x ]]가 항상 참이므로,
# 인덱스 모드를 직접 본다.
#
# 비트가 빠지면 Jenkins(Linux)에서 `./scripts/...` 직접 호출이 exit 126
# (Permission denied)으로 죽는다. 관측 파이프라인만의 문제가 아니다. 세 앱
# Jenkinsfile이 모두 publish-deploy-event.sh와 check-diagnostics-secrets.sh를
# 직접 호출하고, 배포 스크립트들도 collect-deploy-diagnostics.sh를 직접 호출한다.
non_executable=$(
  git -C "$repo_root" ls-files -s -- 'scripts/*.sh' |
    awk '$1 != "100755" { print $4 }'
)
[[ -z $non_executable ]] || fail "scripts must be executable in the Git index: $non_executable"

"$secret_guard"

grep -Fq "credentialsId: 'develop-grafana-admin'" "$jenkinsfile" \
  || fail "Jenkins must inject the develop-grafana-admin credential"
grep -Fq 'buildDiscarder(logRotator(numToKeepStr:' "$jenkinsfile" \
  || fail "Jenkins must configure build retention"
grep -Fq "numToKeepStr: '20'" "$jenkinsfile" \
  || fail "Jenkins must retain exactly 20 builds"
grep -Fq 'timestamps()' "$jenkinsfile" || fail "Jenkins timestamps are required"
grep -Fq 'deleteDir()' "$jenkinsfile" || fail "Jenkins workspace cleanup is required"
grep -Fq 'mktemp' "$jenkinsfile" || fail "Jenkins must create a temporary runtime env file"
grep -Fq 'umask 077' "$jenkinsfile" || fail "Jenkins runtime credentials must use mode 0600"
grep -Fq 'trap' "$jenkinsfile" || fail "Jenkins must remove the runtime env file on exit"
grep -Fq 'infra/observability/event-publisher' "$jenkinsfile" \
  || fail "Jenkins must build the deployment event publisher"
grep -Fq 'bash scripts/smoke-observability.sh' "$jenkinsfile" \
  || fail "Jenkins must run the observability smoke check"
# 스모크 스크립트는 compose를 호출하므로 env 파일이 필요하다. 기본 경로인
# infra/observability/.env는 gitignore 대상이라 Jenkins 워크스페이스에 없고,
# Deploy 스테이지가 만든 임시 파일은 그 셸이 끝날 때 지워진다. 전달을 빠뜨리면
# 스모크가 매번 "env file is not readable"로 실패하므로 여기서 고정한다.
grep -Fq 'OBSERVABILITY_ENV_FILE=' "$jenkinsfile" \
  || fail "Jenkins must pass a readable runtime env file to the smoke check"
# Jenkins는 컨테이너지만 Docker 데몬은 호스트 것이므로, 설정 파일을 복사해 둘 호스트
# 절대 경로를 배포 스크립트에 넘겨야 한다. 빠뜨리면 워크스페이스 경로가 마운트되어
# Loki가 빈 디렉터리를 설정 파일로 읽는다.
grep -Eq 'OBSERVABILITY_CONFIG_DIR=/' "$jenkinsfile" \
  || fail "Jenkins must pass an absolute host config directory to the deploy script"
grep -Fq "archiveArtifacts artifacts: 'observability-smoke-report.txt'" "$jenkinsfile" \
  || fail "Jenkins must archive the observability smoke report"

docker run --rm -i python:3.12-slim python -c '
import json, sys
dashboard = json.load(sys.stdin)
assert dashboard.get("uid") == "c206-operations-logs"
variables = {item.get("name") for item in dashboard.get("templating", {}).get("list", [])}
assert variables == {"environment", "service", "level", "deploy_id", "commit", "request_id"}
all_values = {
    item.get("name"): item.get("allValue")
    for item in dashboard.get("templating", {}).get("list", [])
}
assert all_values["environment"] == ".+"
assert all_values["service"] == ".+"
assert all_values["level"] == ".*"
panels = dashboard.get("panels", [])
assert {panel.get("title") for panel in panels} == {
    "전체 로그", "배포 이력", "Backend 예외", "AI 예외", "인프라 오류", "요청 흐름"
}
expressions = {
    target.get("expr")
    for panel in panels
    for target in panel.get("targets", [])
}
required = {
    "{environment=~\"$environment\", service=~\"$service\", level=~\"$level\"} | json",
    "{service=\"deployment\", environment=~\"$environment\"} | json | deploy_id=~\"$deploy_id\" | commit=~\"$commit\"",
    "{service=\"backend\", level=\"ERROR\"} | json | error_code=\"INTERNAL_ERROR\"",
    "{service=\"ai-service\"} | json |~ \"evaluation_transient_failure|provider_retry|evaluation_invalid_response\"",
    "{environment=~\"$environment\"} | json | request_id=~\"$request_id\"",
    "{service=~\"postgres|redis|rabbitmq|frontend\"} |~ \"(?i)error|fatal|unhealthy|connection refused\"",
}
assert expressions == required
backend_panel = next(panel for panel in panels if panel.get("title") == "Backend 예외")
backend_columns = backend_panel["transformations"][0]["options"]["indexByName"]
assert backend_columns == {
    "Time": 0,
    "error_message": 1,
    "exception_type": 2,
    "root_cause_type": 3,
    "source_file": 4,
    "source_line": 5,
    "http_method": 6,
    "request_path": 7,
    "request_id": 8,
}
serialized = json.dumps(dashboard)
assert "${__value.raw}" in serialized
for field in ("exception_type", "source_file", "source_line", "pipeline_stage", "exit_code", "oom_killed", "jenkins_url"):
    assert field in serialized
' <"$dashboard_file" || fail "Grafana operations dashboard is invalid"

if grep -Eq '\{[^}]*\b(request_id|deploy_id|commit)[[:space:]]*=' "$dashboard_file"; then
  fail "high-cardinality fields must not be used as Loki stream labels"
fi

grep -Fq 'path: /var/lib/grafana/dashboards' "$dashboard_provider" \
  || fail "Grafana dashboard provider path is invalid"

# 스모크는 Loki 설정을 컨테이너에서 꺼내 읽어야 한다. docker inspect가 알려주는
# 마운트 Source는 Docker 데몬(호스트) 기준 경로라, 컨테이너화된 Jenkins에서 그
# 경로를 직접 열면 없는 파일이 된다. Loki 이미지에는 셸이 없어 docker exec grep도
# 쓸 수 없으므로 docker cp로 읽는다.
grep -Fq 'docker cp' "$smoke_script" \
  || fail "smoke must read the Loki config out of the container, not from a host path"

printf 'PASS: observability Compose configuration is valid\n'
