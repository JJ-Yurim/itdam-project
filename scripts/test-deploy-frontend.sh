#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
deploy_script="$repo_root/scripts/deploy-frontend.sh"
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
mkdir -p "$test_dir/bin"

cat >"$test_dir/bin/docker" <<'FAKE'
#!/usr/bin/env bash
printf 'docker' >>"$FAKE_DOCKER_LOG"
printf ' <%s>' "$@" >>"$FAKE_DOCKER_LOG"
printf '\n' >>"$FAKE_DOCKER_LOG"

if [[ ${1:-} == inspect ]]; then
  target=${!#}
  if [[ $target == frontend-app ]]; then
    image=$(cat "$FAKE_DOCKER_STATE")
    [[ $image != none ]] || exit 1
    if [[ $* == *Config.Image* ]]; then printf '%s\n' "$image";
    elif [[ $* == *Config.Env* ]]; then printf 'DEPLOY_COMMIT=old-commit\n';
    else printf 'healthy\n'; fi
  fi
  exit 0
fi

if [[ ${1:-} == run ]]; then
  if [[ $* == *"publish --stdin"* ]]; then
    cat >>"$FAKE_DEPLOY_EVENTS"; printf '\n' >>"$FAKE_DEPLOY_EVENTS"; exit 0
  fi
  if [[ $* == *"summarize --service"* ]]; then
    cat >/dev/null; printf '{"exception_type":null,"source_file":null,"source_line":null}\n'; exit 0
  fi
  detached=false
  for argument in "$@"; do [[ $argument == -d ]] && detached=true; done
  if [[ $detached == true ]]; then
    printf '%s\n' "${!#}" >"$FAKE_DOCKER_STATE"
    printf 'container-id\n'
    exit 0
  fi
  probe_image=''
  for argument in "$@"; do
    case $argument in test-image|old-image) probe_image=$argument ;; esac
  done
  [[ $probe_image == old-image ]]
  exit $?
fi

if [[ ${1:-} == rm ]]; then printf 'none\n' >"$FAKE_DOCKER_STATE"; exit 0; fi
if [[ ${1:-} == image || ${1:-} == logs || ${1:-} == events ]]; then exit 0; fi
exit 0
FAKE

cat >"$test_dir/bin/sleep" <<'FAKE'
#!/usr/bin/env bash
exit 0
FAKE
chmod +x "$test_dir/bin/docker" "$test_dir/bin/sleep"

export PATH="$test_dir/bin:$PATH"
export FAKE_DOCKER_LOG="$test_dir/docker.log"
export FAKE_DOCKER_STATE="$test_dir/docker.state"
export FAKE_DEPLOY_EVENTS="$test_dir/deploy-events.jsonl"
diagnostics_dir="$test_dir/deploy-diagnostics"
printf 'old-image\n' >"$FAKE_DOCKER_STATE"
: >"$FAKE_DOCKER_LOG"
: >"$FAKE_DEPLOY_EVENTS"

cd "$repo_root"
if DEPLOY_ID=develop-1-abcdef12 BUILD_URL=https://jenkins.example/1/ \
  "$deploy_script" develop test-image test-commit "$diagnostics_dir" >/dev/null 2>&1; then
  echo "frontend deployment unexpectedly succeeded" >&2
  exit 1
fi

[[ -f "$diagnostics_dir/deployment-summary.json" ]] || {
  echo "Frontend diagnostics summary was not created" >&2; exit 1;
}
log_line=$(grep -n -F 'docker <logs> <--timestamps> <frontend-app>' "$FAKE_DOCKER_LOG" | tail -n 1 | cut -d: -f1)
remove_line=$(grep -n -F 'docker <rm> <-f> <frontend-app>' "$FAKE_DOCKER_LOG" | tail -n 1 | cut -d: -f1)
if [[ -z $log_line || -z $remove_line || $log_line -ge $remove_line ]]; then
  echo "Frontend diagnostics were not collected before removal" >&2; exit 1
fi
for status in HEALTH_CHECK_FAILED ROLLBACK_STARTED ROLLBACK_SUCCEEDED; do
  grep -Fq "\"status\":\"$status\"" "$FAKE_DEPLOY_EVENTS" || {
    echo "missing Frontend deployment event: $status" >&2; exit 1;
  }
done

echo "deploy-frontend fake-Docker tests passed"
