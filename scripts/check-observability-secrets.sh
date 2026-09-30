#!/usr/bin/env bash
set -euo pipefail

secret_key=GRAFANA_ADMIN_PASSWORD
matches=$(git grep -n -I -E \
  "^[[:space:]]*(export[[:space:]]+)?${secret_key}[[:space:]]*[:=]" \
  -- . 2>/dev/null || true)

found_secret=false
while IFS= read -r match; do
  [[ -n $match ]] || continue
  file=${match%%:*}
  remainder=${match#*:}
  line_number=${remainder%%:*}
  content=${remainder#*:}

  assignment=$(printf '%s' "$content" | sed -E 's/^[[:space:]]*export[[:space:]]+//')
  raw_value=$(printf '%s' "$assignment" | sed -E \
    "s/^${secret_key}[[:space:]]*[:=][[:space:]]*//; s/[[:space:]]+$//")

  [[ -z $raw_value ]] && continue
  [[ $raw_value == \#* ]] && continue

  value=$raw_value
  if [[ $value == \"*\" && ${#value} -ge 2 ]]; then
    value=${value:1:${#value}-2}
  elif [[ $value == \'*\' && ${#value} -ge 2 ]]; then
    value=${value:1:${#value}-2}
  fi

  if [[ $value =~ ^\$[A-Za-z_][A-Za-z0-9_]*$ ]] \
    || [[ $value =~ ^\$\{[A-Za-z_][A-Za-z0-9_]*\}$ ]]; then
    continue
  fi

  printf 'FAIL: committed Grafana password assignment at %s:%s\n' \
    "$file" "$line_number" >&2
  found_secret=true
done <<<"$matches"

[[ $found_secret == false ]]
