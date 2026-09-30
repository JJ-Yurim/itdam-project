#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
checker="$repo_root/scripts/check-diagnostics-secrets.sh"
[[ -x $checker ]] || { printf 'FAIL: diagnostics secret checker is missing\n' >&2; exit 1; }

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
diagnostics="$temp_dir/diagnostics"
mkdir -p "$diagnostics"
marker=artifact-secret-value
printf 'safe prefix %s safe suffix\n' "$marker" >"$diagnostics/backend.log"

set +e
output=$(SECRET_CHECK_VALUE="$marker" "$checker" "$diagnostics" 2>&1)
exit_code=$?
set -e

[[ $exit_code -ne 0 ]] || { printf 'FAIL: contaminated diagnostics were accepted\n' >&2; exit 1; }
[[ ! -e $diagnostics ]] || { printf 'FAIL: contaminated diagnostics were not deleted\n' >&2; exit 1; }
grep -Fq 'backend.log' <<<"$output" \
  || { printf 'FAIL: contaminated filename was not reported\n' >&2; exit 1; }
if grep -Fq -- "$marker" <<<"$output"; then
  printf 'FAIL: secret checker printed the credential value\n' >&2
  exit 1
fi

printf 'PASS: contaminated diagnostics are removed without printing secrets\n'
