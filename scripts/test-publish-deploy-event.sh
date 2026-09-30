#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
publisher="$repo_root/scripts/publish-deploy-event.sh"
[[ -x $publisher ]] || { printf 'FAIL: deploy event wrapper is missing\n' >&2; exit 1; }

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
mkdir -p "$temp_dir/bin"
calls="$temp_dir/calls.log"
stdin_capture="$temp_dir/stdin.json"

cat >"$temp_dir/bin/docker" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >"$FAKE_DOCKER_CALLS"
cat >"$FAKE_DOCKER_STDIN"
exit 42
FAKE
chmod +x "$temp_dir/bin/docker"

event='{"deploy_id":"develop-1-abc","commit":"abc","pipeline":"backend","pipeline_stage":"deploy","status":"FAILED","jenkins_url":"https://jenkins.example/1","environment":"develop"}'
set +e
output=$(printf '%s' "$event" | FAKE_DOCKER_CALLS="$calls" \
  FAKE_DOCKER_STDIN="$stdin_capture" PATH="$temp_dir/bin:$PATH" \
  "$publisher" 2>&1)
exit_code=$?
set -e

[[ $exit_code -eq 0 ]] || { printf 'FAIL: publisher wrapper must fail open\n' >&2; exit 1; }
[[ $(grep -c '^WARNING:' <<<"$output") -eq 1 ]] \
  || { printf 'FAIL: publisher failure must print exactly one warning\n' >&2; exit 1; }
grep -Fxq -- 'run --rm -i --network mvp-observability c206-deploy-event-publisher:local publish --stdin' "$calls" \
  || { printf 'FAIL: publisher Docker invocation is not fixed and isolated\n' >&2; exit 1; }
[[ $(cat "$stdin_capture") == "$event" ]] \
  || { printf 'FAIL: event JSON was not forwarded through stdin\n' >&2; exit 1; }
if grep -Fq -- '--env-file' "$calls"; then
  printf 'FAIL: publisher must never receive an env file\n' >&2
  exit 1
fi

printf 'PASS: deploy event publisher fails open without credentials\n'
