#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.." &&
  pwd
)"
deploy_script="$repo_root/scripts/deploy-ai-service.sh"
test_dir="$(mktemp -d)"

cleanup() {
  rm -rf "$test_dir"
}
trap cleanup EXIT

mkdir -p "$test_dir/bin"

cat >"$test_dir/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

{
  printf 'docker'
  printf ' <%s>' "$@"
  printf '\n'
} >>"$FAKE_DOCKER_LOG"

if [ "${1:-}" = "network" ] && [ "${2:-}" = "inspect" ]; then
  exit 0
fi

if [ "${1:-}" = "inspect" ]; then
  target="${!#}"

  case "$target" in
    ai-service)
      active_image="$(cat "$FAKE_DOCKER_STATE")"

      if [ "$active_image" = "none" ]; then
        exit 1
      fi

      case "$*" in
        *".Config.Image"*)
          printf '%s\n' "$active_image"
          ;;
        *".Config.Env"*)
          printf 'DEPLOY_COMMIT=old-commit\n'
          ;;
        *".State.Health"*)
          case "$active_image" in
            test-image)
              printf 'unhealthy\n'
              ;;
            old-image)
              printf 'healthy\n'
              ;;
          esac
          ;;
      esac
      ;;
  esac
  exit 0
fi

if [ "${1:-}" = "run" ]; then
  if [[ $* == *"publish --stdin"* ]]; then
    cat >>"$FAKE_DEPLOY_EVENTS"
    printf '\n' >>"$FAKE_DEPLOY_EVENTS"
    exit 0
  fi
  if [[ $* == *"summarize --service"* ]]; then
    cat >/dev/null
    printf '{"exception_type":null,"source_file":null,"source_line":null}\n'
    exit 0
  fi
  for argument in "$@"; do
    if [ "$argument" = "-d" ]; then
      printf '%s\n' "${!#}" >"$FAKE_DOCKER_STATE"
      printf 'fake-container-id\n'
      break
    fi
  done
  exit 0
fi

if [ "${1:-}" = "rm" ]; then
  printf 'none\n' >"$FAKE_DOCKER_STATE"
  exit 0
fi

if [ "${1:-}" = "image" ] ||
   [ "${1:-}" = "logs" ]; then
  exit 0
fi

exit 0
EOF

cat >"$test_dir/bin/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

chmod +x "$test_dir/bin/docker" "$test_dir/bin/sleep"

assert_log_contains() {
  local expected="$1"

  if ! grep -F -- "$expected" "$FAKE_DOCKER_LOG" >/dev/null; then
    echo "missing Docker invocation: $expected" >&2
    sed 's/^/  /' "$FAKE_DOCKER_LOG" >&2
    exit 1
  fi
}

assert_log_not_contains() {
  local unexpected="$1"

  if grep -F -- "$unexpected" "$FAKE_DOCKER_LOG" >/dev/null; then
    echo "unexpected Docker invocation: $unexpected" >&2
    sed 's/^/  /' "$FAKE_DOCKER_LOG" >&2
    exit 1
  fi
}

export PATH="$test_dir/bin:$PATH"
export FAKE_DOCKER_LOG="$test_dir/docker.log"
export FAKE_DOCKER_STATE="$test_dir/docker.state"
export FAKE_DEPLOY_EVENTS="$test_dir/deploy-events.jsonl"
runtime_env="$test_dir/runtime.env"
diagnostics_dir="$test_dir/deploy-diagnostics"
secret_marker="must-not-appear-in-output"
printf 'LLM_API_KEY=%s\n' "$secret_marker" >"$runtime_env"

cd "$repo_root"
printf 'old-image\n' >"$FAKE_DOCKER_STATE"
: >"$FAKE_DOCKER_LOG"
: >"$FAKE_DEPLOY_EVENTS"

if deploy_output="$(
  "$deploy_script" \
    develop \
    test-image \
    test-commit \
    "$runtime_env" \
    "$diagnostics_dir" 2>&1
)"; then
  echo "deployment unexpectedly succeeded after an unhealthy AI Service" >&2
  exit 1
fi

assert_log_contains \
  "docker <network> <inspect> <mvp-infra>"
assert_log_contains \
  "docker <run> <-d> <--name> <ai-service> <--restart> <unless-stopped> <--cpus> <1> <--memory> <512m> <--log-driver> <json-file> <--log-opt> <max-size=10m> <--log-opt> <max-file=5> <--label> <c206.service=ai-service> <--label> <c206.environment=develop> <--network> <mvp-infra> <--env-file> <$runtime_env>"
assert_log_not_contains "<-p>"
assert_log_contains \
  "docker <run> <-d> <--name> <ai-service> <--restart> <unless-stopped> <--cpus> <1> <--memory> <512m> <--log-driver> <json-file> <--log-opt> <max-size=10m> <--log-opt> <max-file=5> <--label> <c206.service=ai-service> <--label> <c206.environment=develop> <--network> <mvp-infra> <--env-file> <$runtime_env> <-e> <DEPLOY_BRANCH=develop> <-e> <DEPLOY_COMMIT=old-commit> <old-image>"

[[ -f "$diagnostics_dir/deployment-summary.json" ]] || {
  echo "AI Service diagnostics summary was not created" >&2
  exit 1
}
log_line=$(grep -n -F 'docker <logs> <--timestamps> <ai-service>' "$FAKE_DOCKER_LOG" | tail -n 1 | cut -d: -f1)
remove_line=$(grep -n -F 'docker <rm> <-f> <ai-service>' "$FAKE_DOCKER_LOG" | tail -n 1 | cut -d: -f1)
if [[ -z $log_line || -z $remove_line || $log_line -ge $remove_line ]]; then
  echo "AI diagnostics were not collected before removal" >&2
  exit 1
fi
for status in HEALTH_CHECK_FAILED ROLLBACK_STARTED ROLLBACK_SUCCEEDED; do
  grep -Fq "\"status\":\"$status\"" "$FAKE_DEPLOY_EVENTS" || {
    echo "missing AI deployment event: $status" >&2
    exit 1
  }
done

if printf '%s\n' "$deploy_output" | grep -F "$secret_marker" >/dev/null; then
  echo "deployment output exposed runtime environment contents" >&2
  exit 1
fi

echo "deploy-ai-service fake-Docker tests passed"
