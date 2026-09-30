#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
writer="$repo_root/scripts/write-observability-runtime-env.sh"
[[ -x "$writer" ]] || { printf 'FAIL: runtime env writer is missing\n' >&2; exit 1; }

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
runtime_env="$temp_dir/runtime.env"
fixture_compose="$temp_dir/compose.yml"
rendered="$temp_dir/rendered.yml"

expected_user="ops\$user'lead"
expected_password="pa\$\$word'#value"

export GRAFANA_ADMIN_USER="$expected_user"
export GRAFANA_ADMIN_PASSWORD="$expected_password"
"$writer" "$runtime_env"
unset GRAFANA_ADMIN_USER GRAFANA_ADMIN_PASSWORD

cat >"$fixture_compose" <<'YAML'
services:
  value-check:
    image: busybox:1.36
    environment:
      CHECK_USER: ${GRAFANA_ADMIN_USER:?}
      CHECK_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?}
YAML

docker compose --env-file "$runtime_env" -f "$fixture_compose" config >"$rendered"
rendered_user=${expected_user//\$/\$\$}
rendered_password=${expected_password//\$/\$\$}
grep -Fq "CHECK_USER: $rendered_user" "$rendered" \
  || { printf 'FAIL: Grafana username was not preserved\n' >&2; exit 1; }
grep -Fq "CHECK_PASSWORD: $rendered_password" "$rendered" \
  || { printf 'FAIL: Grafana password was not preserved\n' >&2; exit 1; }

if [[ "$(uname -s)" != MINGW* ]]; then
  [[ $(stat -c '%a' "$runtime_env") == 600 ]] \
    || { printf 'FAIL: runtime env file mode must be 600\n' >&2; exit 1; }
fi

printf 'PASS: runtime environment writer preserves special characters\n'
