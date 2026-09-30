#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
deploy_script="$repo_root/scripts/deploy-backend.sh"
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

find_container() {
  awk -F '\t' -v target="$1" '$1 == target { print; found=1 } END { if (!found) exit 1 }' \
    "$FAKE_DOCKER_STATE"
}

remove_container() {
  local target="$1"
  local temporary="${FAKE_DOCKER_STATE}.tmp"
  awk -F '\t' -v target="$target" '$1 != target' "$FAKE_DOCKER_STATE" >"$temporary"
  mv "$temporary" "$FAKE_DOCKER_STATE"
}

save_container() {
  local name="$1" image="$2" commit="$3" health="$4"
  remove_container "$name"
  printf '%s\t%s\t%s\t%s\n' "$name" "$image" "$commit" "$health" >>"$FAKE_DOCKER_STATE"
}

if [ "${1:-}" = "compose" ]; then
  service=""
  for argument in "$@"; do
    case "$argument" in
      postgres|redis|rabbitmq) service="$argument" ;;
    esac
  done
  [ -z "$service" ] || printf '%s-id\n' "$service"
  exit 0
fi

if [ "${1:-}" = "exec" ]; then
  cat >/dev/null || true
  case "$FAKE_DOCKER_SCENARIO" in
    readonly_failure)
      echo 'FATAL: password authentication failed for user "mvp_ai_readonly"' >&2
      exit 2
      ;;
    grant_failure)
      case "$*" in
        *sh*) exit 0 ;;
        *) echo 'ERROR: required relation does not exist' >&2; exit 3 ;;
      esac
      ;;
  esac
  exit 0
fi

if [ "${1:-}" = "inspect" ]; then
  target="${!#}"
  case "$target" in
    postgres-id)
      [ "$FAKE_DOCKER_SCENARIO" = "infra_failure" ] && printf 'exited\n' || printf 'healthy\n'
      exit 0
      ;;
    redis-id|rabbitmq-id)
      printf 'healthy\n'
      exit 0
      ;;
  esac

  row="$(find_container "$target")" || exit 1
  IFS=$'\t' read -r name active_image active_commit active_health <<<"$row"
  case "$*" in
    *'.Config.Image'*) printf '%s\n' "$active_image" ;;
    *'.Config.Env'*) printf 'DEPLOY_COMMIT=%s\n' "$active_commit" ;;
    *'.State.Health'*) printf '%s\n' "$active_health" ;;
    *) printf '%s\n' "$name" ;;
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

  detached=false
  target_name=""
  target_commit=""
  previous=""
  for argument in "$@"; do
    if [ "$previous" = "--name" ]; then
      target_name="$argument"
    elif [ "$previous" = "-e" ] && [[ $argument == DEPLOY_COMMIT=* ]]; then
      target_commit="${argument#DEPLOY_COMMIT=}"
    fi
    [ "$argument" != "-d" ] || detached=true
    previous="$argument"
  done

  if ! $detached; then
    for argument in "$@"; do
      if [ "$argument" = "-i" ]; then
        cat >"$FAKE_DOCKER_STDIN"
        break
      fi
    done
    exit 0
  fi

  target_image="${!#}"
  health="healthy"
  if [ "$target_image" = "test-image" ]; then
    case "$FAKE_DOCKER_SCENARIO:$target_name" in
      slot_a_failure:backend-app-a|slot_b_failure:backend-app-b) health="unhealthy" ;;
    esac
  fi
  save_container "$target_name" "$target_image" "$target_commit" "$health"
  printf 'fake-container-id\n'
  exit 0
fi

if [ "${1:-}" = "rm" ]; then
  if [ "$FAKE_DOCKER_SCENARIO" = "old_pair_second_remove_failure" ] &&
     [ "${!#}" = "backend-app-b" ] &&
     [ ! -f "$FAKE_DOCKER_REMOVE_FAILURE_MARKER" ]; then
    : >"$FAKE_DOCKER_REMOVE_FAILURE_MARKER"
    exit 9
  fi
  remove_container "${!#}"
  exit 0
fi

if [ "${1:-}" = "image" ] || [ "${1:-}" = "logs" ]; then
  exit 0
fi

exit 0
EOF

cat >"$test_dir/bin/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

chmod +x "$test_dir/bin/docker" "$test_dir/bin/sleep"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

assert_log_contains() {
  grep -F -- "$1" "$FAKE_DOCKER_LOG" >/dev/null || {
    sed 's/^/  /' "$FAKE_DOCKER_LOG" >&2
    fail "missing Docker invocation: $1"
  }
}

assert_log_not_contains() {
  ! grep -F -- "$1" "$FAKE_DOCKER_LOG" >/dev/null || fail "unexpected Docker invocation: $1"
}

seed_container() {
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >>"$FAKE_DOCKER_STATE"
}

assert_container() {
  local row
  row="$(awk -F '\t' -v target="$1" '$1 == target { print }' "$FAKE_DOCKER_STATE")"
  [ "$row" = "$1"$'\t'"$2"$'\t'"$3"$'\t'"$4" ] || {
    sed 's/^/  /' "$FAKE_DOCKER_STATE" >&2
    fail "unexpected state for $1"
  }
}

assert_container_absent() {
  ! awk -F '\t' -v target="$1" '$1 == target { found=1 } END { exit !found }' \
    "$FAKE_DOCKER_STATE" || fail "container should be absent: $1"
}

reset_scenario() {
  export FAKE_DOCKER_SCENARIO="$1"
  : >"$FAKE_DOCKER_LOG"
  : >"$FAKE_DOCKER_STATE"
  : >"$FAKE_DEPLOY_EVENTS"
  rm -f "$FAKE_DOCKER_REMOVE_FAILURE_MARKER"
}

run_deploy() {
  "$deploy_script" develop test-image test-commit "$runtime_env" "$diagnostics_dir"
}

assert_deploy_fails() {
  local output
  if output="$(run_deploy 2>&1)"; then
    fail "$1 deployment unexpectedly succeeded"
  fi
  if grep -F "$secret_marker" <<<"$output" >/dev/null; then
    fail "deployment output exposed runtime environment contents"
  fi
}

export PATH="$test_dir/bin:$PATH"
export FAKE_DOCKER_LOG="$test_dir/docker.log"
export FAKE_DOCKER_STATE="$test_dir/docker.state"
export FAKE_DOCKER_STDIN="$test_dir/docker.stdin"
export FAKE_DEPLOY_EVENTS="$test_dir/deploy-events.jsonl"
export FAKE_DOCKER_REMOVE_FAILURE_MARKER="$test_dir/docker-remove-failed-once"
runtime_env="$test_dir/runtime.env"
diagnostics_dir="$test_dir/deploy-diagnostics"
secret_marker="must-not-appear-in-output"
printf '%s\n' \
  'POSTGRES_USER=mvp_backend' \
  'POSTGRES_DB=mvp_backend' \
  "AI_READONLY_DB_PASSWORD=$secret_marker" >"$runtime_env"

cd "$repo_root"

reset_scenario healthy_pair
run_deploy >/dev/null
assert_container backend-app-a test-image test-commit healthy
assert_container backend-app-b test-image test-commit healthy
assert_container_absent backend-app
assert_log_contains "<--name> <backend-app-a>"
assert_log_contains "<-p> <127.0.0.1:8081:8080>"
assert_log_contains "<--label> <c206.slot=a>"
assert_log_contains "<--name> <backend-app-b>"
assert_log_contains "<-p> <127.0.0.1:8082:8080>"
assert_log_contains "<--label> <c206.slot=b>"
assert_log_contains "<-e> <SPRING_PROFILES_ACTIVE=develop>"
[ "$(grep -Fc '<-e> <SPRING_PROFILES_ACTIVE=develop>' "$FAKE_DOCKER_LOG")" -eq 2 ] || \
  fail "both develop slots must receive the same Spring profile"
assert_log_contains "<--cpus> <1> <--memory> <1g>"
assert_log_contains "<-v> <backend-data:/app/data> <-v> </var/www/images:/app/data/images>"
assert_log_contains "<--network> <mvp-infra> <--env-file> <$runtime_env>"
a_health_line="$(grep -n -F '.State.Health' "$FAKE_DOCKER_LOG" | grep -F '<backend-app-a>' | head -n 1 | cut -d: -f1)"
b_start_line="$(grep -n -F "<--name> <backend-app-b>" "$FAKE_DOCKER_LOG" | head -n 1 | cut -d: -f1)"
[ -n "$a_health_line" ] && [ -n "$b_start_line" ] && [ "$a_health_line" -lt "$b_start_line" ] || \
  fail "slot B started before slot A became healthy"

if ! cmp -s "$repo_root/infra/assets/boss-phase-placeholder.svg" "$FAKE_DOCKER_STDIN"; then
  fail "placeholder asset was not streamed to the volume preparation container"
fi
if grep -F "$secret_marker" "$FAKE_DOCKER_LOG" >/dev/null; then
  fail "readonly password leaked into Docker arguments"
fi

reset_scenario healthy_pair
"$deploy_script" main test-image test-commit "$runtime_env" "$diagnostics_dir" >/dev/null
assert_container backend-app-a test-image test-commit healthy
assert_container backend-app-b test-image test-commit healthy
[ "$(grep -Fc '<-e> <SPRING_PROFILES_ACTIVE=prod>' "$FAKE_DOCKER_LOG")" -eq 2 ] || \
  fail "main branch must map both slots to the prod Spring profile"

reset_scenario split_previous_pair
seed_container backend-app-a old-image old-commit healthy
seed_container backend-app-b other-image old-commit healthy
assert_deploy_fails split_previous_pair
assert_container backend-app-a old-image old-commit healthy
assert_container backend-app-b other-image old-commit healthy
assert_log_not_contains "docker <rm> <-f> <backend-app-a>"
assert_log_not_contains "docker <rm> <-f> <backend-app-b>"

reset_scenario partial_previous_pair
seed_container backend-app-a old-image old-commit healthy
assert_deploy_fails partial_previous_pair
assert_container backend-app-a old-image old-commit healthy
assert_container_absent backend-app-b
assert_log_not_contains "docker <rm> <-f> <backend-app-a>"

reset_scenario slot_a_failure
seed_container backend-app old-image old-commit healthy
assert_deploy_fails slot_a_failure
assert_container backend-app old-image old-commit healthy
assert_container_absent backend-app-a
assert_container_absent backend-app-b
log_line="$(grep -n -F 'docker <logs> <--timestamps> <backend-app-a>' "$FAKE_DOCKER_LOG" | tail -n 1 | cut -d: -f1)"
remove_line="$(grep -n -F 'docker <rm> <-f> <backend-app-a>' "$FAKE_DOCKER_LOG" | head -n 1 | cut -d: -f1)"
[ -n "$log_line" ] && [ -n "$remove_line" ] && [ "$log_line" -lt "$remove_line" ] || \
  fail "failed slot diagnostics were not collected before removal"

reset_scenario slot_b_failure
seed_container backend-app-a old-image old-commit healthy
seed_container backend-app-b old-image old-commit healthy
assert_deploy_fails slot_b_failure
assert_container backend-app-a old-image old-commit healthy
assert_container backend-app-b old-image old-commit healthy

reset_scenario old_pair_second_remove_failure
seed_container backend-app-a old-image old-commit healthy
seed_container backend-app-b old-image old-commit healthy
assert_deploy_fails old_pair_second_remove_failure
assert_container backend-app-a old-image old-commit healthy
assert_container backend-app-b old-image old-commit healthy

reset_scenario readonly_failure
seed_container backend-app old-image old-commit healthy
assert_deploy_fails readonly_failure
assert_container backend-app old-image old-commit healthy
assert_container_absent backend-app-a
assert_container_absent backend-app-b

reset_scenario infra_failure
seed_container backend-app-a old-image old-commit healthy
seed_container backend-app-b old-image old-commit healthy
assert_deploy_fails infra_failure
assert_container backend-app-a old-image old-commit healthy
assert_container backend-app-b old-image old-commit healthy
assert_log_not_contains "docker <rm> <-f> <backend-app-a>"
assert_log_not_contains "docker <rm> <-f> <backend-app-b>"

reset_scenario slot_a_failure
seed_container backend-app old-image old-commit healthy
assert_deploy_fails event_check
for status in HEALTH_CHECK_FAILED ROLLBACK_STARTED ROLLBACK_SUCCEEDED; do
  grep -Fq "\"status\":\"$status\"" "$FAKE_DEPLOY_EVENTS" || fail "missing deployment event: $status"
done

echo "deploy-backend fake-Docker tests passed"
