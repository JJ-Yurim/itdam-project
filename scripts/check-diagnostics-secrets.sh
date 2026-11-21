#!/usr/bin/env bash
set -uo pipefail

if [[ $# -ne 1 || ! -d $1 ]]; then
  exit 0
fi

diagnostics_dir=$(cd "$1" 2>/dev/null && pwd -P) || exit 0
[[ -n $diagnostics_dir && $diagnostics_dir != / ]] || {
  printf 'Diagnostics secret check refused an unsafe directory.\n' >&2
  exit 2
}

credential_names=(
  POSTGRES_USER POSTGRES_PASSWORD
  RABBITMQ_USER RABBITMQ_PASSWORD
  REDIS_PASSWORD
  ADMIN_ACCOUNT_EMAIL ADMIN_ACCOUNT_PASSWORD
  SMTP_USERNAME SMTP_PASSWORD
  LLM_API_KEY EMBEDDING_API_KEY
  SECRET_CHECK_VALUE
)

contaminated_files=''
for credential_name in "${credential_names[@]}"; do
  credential_value=${!credential_name-}
  [[ -n $credential_value ]] || continue
  matches=$(grep -rIlF -- "$credential_value" "$diagnostics_dir" 2>/dev/null || true)
  if [[ -n $matches ]]; then
    contaminated_files+="$matches"$'\n'
  fi
done

if [[ -n $contaminated_files ]]; then
  printf 'Diagnostics contained credential values; removing contaminated files:\n' >&2
  printf '%s' "$contaminated_files" | sed '/^$/d' | sort -u | while IFS= read -r file; do
    printf '  %s\n' "${file#"$diagnostics_dir"/}" >&2
  done
  rm -rf -- "$diagnostics_dir"
  exit 1
fi

exit 0
