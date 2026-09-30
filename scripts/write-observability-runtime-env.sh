#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'usage: write-observability-runtime-env.sh OUTPUT_FILE\n' >&2
  exit 64
fi

: "${GRAFANA_ADMIN_USER:?GRAFANA_ADMIN_USER is required}"
: "${GRAFANA_ADMIN_PASSWORD:?GRAFANA_ADMIN_PASSWORD is required}"

output_file=$1
temp_file="${output_file}.tmp.$$"
umask 077
trap 'rm -f "$temp_file"' EXIT

dotenv_literal() {
  local value=$1
  value=${value//\'/\\\'}
  printf "'%s'" "$value"
}

{
  printf 'GRAFANA_ADMIN_USER='
  dotenv_literal "$GRAFANA_ADMIN_USER"
  printf '\nGRAFANA_ADMIN_PASSWORD='
  dotenv_literal "$GRAFANA_ADMIN_PASSWORD"
  printf '\n'
} >"$temp_file"

chmod 600 "$temp_file"
mv -f "$temp_file" "$output_file"
trap - EXIT
