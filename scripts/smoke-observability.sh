#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file="$repo_root/infra/observability/compose.yml"
env_file=${OBSERVABILITY_ENV_FILE:-$repo_root/infra/observability/.env}
publisher_image=${DEPLOY_EVENT_PUBLISHER_IMAGE:-c206-deploy-event-publisher:local}
network_name=mvp-observability
timeout_seconds=${OBSERVABILITY_SMOKE_TIMEOUT_SECONDS:-60}
poll_seconds=${OBSERVABILITY_SMOKE_POLL_SECONDS:-2}
report_file=${SMOKE_REPORT_FILE:-}

if [[ -n $report_file ]]; then
  mkdir -p "$(dirname "$report_file")"
  exec > >(tee "$report_file") 2>&1
fi

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || fail 'docker is required'
[[ -r $env_file ]] || fail "observability env file is not readable: $env_file"

fixture_suffix="$(date +%s)-$$-$RANDOM"
deploy_id="smoke-deploy-$fixture_suffix"
commit_id="smoke-commit-$fixture_suffix"
request_id="00000000-0000-4000-8000-$(printf '%012d' $((RANDOM * 1000 + RANDOM)))"
json_marker="smoke-json-$fixture_suffix"
plain_marker="smoke-infra-$fixture_suffix"
fixture_container="c206-observability-smoke-$fixture_suffix"

cleanup() {
  docker rm -f "$fixture_container" >/dev/null 2>&1 || true
}
trap cleanup EXIT

compose=(docker compose --env-file "$env_file" -f "$compose_file")
loki_id=$("${compose[@]}" ps -q loki)
[[ -n $loki_id ]] || fail 'Loki container is not running'

loki_config_source=$(docker inspect --format \
  '{{range .Mounts}}{{if eq .Destination "/etc/loki/config.yml"}}{{.Source}}{{end}}{{end}}' \
  "$loki_id")
[[ -n $loki_config_source ]] || fail 'Loki mounted config was not found'

# 설정 내용은 컨테이너에서 직접 꺼내 읽는다. 위 Source는 Docker 데몬(호스트) 기준
# 경로이므로, 컨테이너화된 Jenkins에서 그 경로를 그대로 열면 없는 파일이 된다.
# Loki 이미지에는 셸이 없어 docker exec grep도 쓸 수 없다. docker cp는 tar를
# stdout으로 내보내므로 셸 없이도 내용을 읽을 수 있고, 마운트가 깨져 빈 디렉터리가
# 잡힌 경우에도 본문이 비어 검사가 실패한다.
loki_config=$(docker cp "$loki_id:/etc/loki/config.yml" - | tar -xO) \
  || fail 'Loki mounted config could not be read from the container'
grep -Eq '^[[:space:]]*retention_period:[[:space:]]*336h[[:space:]]*$' \
  <<<"$loki_config" || fail 'Loki mounted config does not retain logs for 336h'

printf '%s\n' "{\"deploy_id\":\"$deploy_id\",\"commit\":\"$commit_id\",\"pipeline\":\"observability-smoke\",\"pipeline_stage\":\"fixture\",\"status\":\"failure\",\"jenkins_url\":\"https://jenkins.invalid/smoke/$fixture_suffix\",\"level\":\"ERROR\",\"service\":\"deployment\",\"environment\":\"develop\",\"exit_code\":1,\"oom_killed\":false,\"exception_type\":\"SmokeDeploymentError\",\"source_file\":\"smoke-observability.sh\",\"source_line\":1}" \
  | "$repo_root/scripts/publish-deploy-event.sh"

docker run -d --name "$fixture_container" \
  --label c206_service=backend \
  --label c206_environment=develop \
  --entrypoint python \
  "$publisher_image" -c \
  'import json,sys,time; print(json.dumps({"timestamp":"2026-08-03T00:00:00Z","level":"ERROR","service":"backend","environment":"develop","request_id":sys.argv[1],"message":sys.argv[2],"exception_type":"SmokeBackendException","source_file":"SmokeFixture.java","source_line":42}), flush=True); print(sys.argv[3], flush=True); time.sleep(45)' \
  "$request_id" "$json_marker" "$plain_marker" >/dev/null

query_fixture() {
  local marker=$1
  docker run --rm --network "$network_name" --entrypoint python "$publisher_image" -c '
import json, sys, urllib.parse, urllib.request
query = "{environment=\"develop\"} |= " + json.dumps(sys.argv[1])
url = "http://loki:3100/loki/api/v1/query_range?" + urllib.parse.urlencode({"query": query, "limit": "20"})
try:
    with urllib.request.urlopen(url, timeout=5) as response:
        payload = json.load(response)
except Exception:
    raise SystemExit(1)
raise SystemExit(0 if payload.get("data", {}).get("result") else 1)
' "$marker" >/dev/null 2>&1
}

declare -A fixtures=(
  [deployment]="$deploy_id"
  [backend_json]="$json_marker"
  [infrastructure_plain]="$plain_marker"
)
declare -A found=()
deadline=$((SECONDS + timeout_seconds))

while (( SECONDS < deadline )); do
  for fixture in "${!fixtures[@]}"; do
    if [[ ${found[$fixture]:-false} == false ]] && query_fixture "${fixtures[$fixture]}"; then
      found[$fixture]=true
      printf 'FOUND: %s fixture (%s)\n' "$fixture" "${fixtures[$fixture]}"
    fi
  done

  [[ ${#found[@]} -eq ${#fixtures[@]} ]] && break
  sleep "$poll_seconds"
done

missing=()
for fixture in deployment backend_json infrastructure_plain; do
  [[ ${found[$fixture]:-false} == true ]] || missing+=("$fixture=${fixtures[$fixture]}")
done
[[ ${#missing[@]} -eq 0 ]] || fail "missing Loki fixtures: ${missing[*]}"

printf 'PASS: Loki contains deployment, Backend JSON, and plain infrastructure fixtures; retention is 336h\n'
