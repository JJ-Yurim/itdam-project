#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
guard_source="$repo_root/scripts/check-observability-secrets.sh"
[[ -x "$guard_source" ]] || { printf 'FAIL: secret guard is missing\n' >&2; exit 1; }

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT

assert_guard_result() {
  local expected_exit=$1
  local assignment=$2
  local case_dir
  case_dir=$(mktemp -d "$temp_dir/case.XXXXXX")
  cp "$guard_source" "$case_dir/check-observability-secrets.sh"
  printf '%s\n' "$assignment" >"$case_dir/fixture.env"
  git -C "$case_dir" init -q
  git -C "$case_dir" add .

  set +e
  (cd "$case_dir" && ./check-observability-secrets.sh >/dev/null 2>&1)
  local actual_exit=$?
  set -e
  [[ $actual_exit -eq $expected_exit ]] || {
    printf 'FAIL: secret guard returned %s for fixture %q\n' "$actual_exit" "$assignment" >&2
    exit 1
  }
}

key=GRAFANA_ADMIN_PASSWORD
assert_guard_result 1 "$key=committed-secret"
assert_guard_result 1 "  $key = 'committed-secret'"
assert_guard_result 1 "export $key=\"committed-secret\""
assert_guard_result 1 "$key: committed-secret"
assert_guard_result 1 "$key=\"#committed-secret\""
assert_guard_result 1 "$key='#committed-secret'"
assert_guard_result 0 "$key=\$RUNTIME_GRAFANA_PASSWORD"
assert_guard_result 0 "$key=\"\${RUNTIME_GRAFANA_PASSWORD}\""
assert_guard_result 0 "$key=\${RUNTIME_GRAFANA_PASSWORD}"
assert_guard_result 0 "$key="
assert_guard_result 0 "# $key is injected at runtime"

printf 'PASS: secret guard rejects committed Grafana passwords\n'
