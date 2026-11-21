#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
collector="$repo_root/scripts/collect-deploy-diagnostics.sh"
[[ -x $collector ]] || { printf 'FAIL: diagnostics collector is missing\n' >&2; exit 1; }

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
mkdir -p "$temp_dir/bin"
calls="$temp_dir/calls.log"
output_dir="$temp_dir/output"
secret_marker=diagnostics-secret-must-not-appear

cat >"$temp_dir/bin/docker" <<'FAKE'
#!/usr/bin/env bash
printf 'docker' >>"$FAKE_DOCKER_CALLS"
printf ' <%s>' "$@" >>"$FAKE_DOCKER_CALLS"
printf '\n' >>"$FAKE_DOCKER_CALLS"

case ${1:-} in
  logs)
    printf '2026-08-03T00:00:00Z java.lang.RuntimeException: wrapper\n'
    printf 'Caused by: java.lang.IllegalStateException: failed\n'
    printf '    at com.ssafy.c206.backend.App.run(App.java:77)\n'
    ;;
  inspect)
    if [[ $* == *Config.Env* ]]; then
      printf '%s\n' "$FAKE_SECRET_MARKER"
    elif [[ $* == *State.Health* ]]; then
      printf '{"Status":"unhealthy","FailingStreak":2}\n'
    else
      printf '{"image":"test-image","status":"exited","exit_code":1,"oom_killed":false,"restart_count":0}\n'
    fi
    ;;
  events)
    printf '{"status":"die","id":"backend-app"}\n'
    ;;
  run)
    cat >/dev/null
    printf '{"exception_type":"java.lang.IllegalStateException","source_file":"App.java","source_line":77}\n'
    ;;
esac
exit 0
FAKE
chmod +x "$temp_dir/bin/docker"

FAKE_DOCKER_CALLS="$calls" FAKE_SECRET_MARKER="$secret_marker" \
PATH="$temp_dir/bin:$PATH" DEPLOY_ID=develop-12-abcdef12 \
DEPLOY_BRANCH=develop GIT_COMMIT_SHORT=abcdef12 BUILD_URL=https://jenkins.example/12/ \
PIPELINE_NAME=backend PIPELINE_STAGE='Backend health check' DEPLOY_STATUS=HEALTH_CHECK_FAILED \
  "$collector" "$output_dir" backend backend-app postgres redis rabbitmq

for required in \
  deployment-summary.json backend-app.log backend-app-inspect.json backend-app-health.json \
  postgres.log redis.log rabbitmq.log docker-events.log; do
  [[ -f "$output_dir/$required" ]] \
    || { printf 'FAIL: missing diagnostic file: %s\n' "$required" >&2; exit 1; }
done

grep -Fq 'java.lang.IllegalStateException' "$output_dir/backend-app.log"
grep -Fq '"exception_type":"java.lang.IllegalStateException"' "$output_dir/deployment-summary.json"
grep -Fq '"source_file":"App.java"' "$output_dir/deployment-summary.json"
grep -Fq '"source_line":77' "$output_dir/deployment-summary.json"
grep -Fq '"exit_code":1' "$output_dir/backend-app-inspect.json"
grep -Fq '"Status":"unhealthy"' "$output_dir/backend-app-health.json"
grep -Fq '"status":"die"' "$output_dir/docker-events.log"

if grep -rFq -- "$secret_marker" "$output_dir"; then
  printf 'FAIL: diagnostics serialized container environment secrets\n' >&2
  exit 1
fi
if grep -Fq -- '<--tail>' "$calls"; then
  printf 'FAIL: diagnostics must collect complete container logs\n' >&2
  exit 1
fi
grep -Fq 'docker <logs> <--timestamps> <backend-app>' "$calls" \
  || { printf 'FAIL: timestamped backend logs were not collected\n' >&2; exit 1; }
if grep -Fq 'Config.Env' "$calls"; then
  printf 'FAIL: diagnostics must never inspect Config.Env\n' >&2
  exit 1
fi

printf 'PASS: deployment diagnostics are complete and secret-safe\n'
