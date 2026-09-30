#!/usr/bin/env bash
set -uo pipefail

if [[ $# -lt 3 ]]; then
  printf 'WARNING: diagnostics collection skipped because arguments are missing\n' >&2
  exit 0
fi

output_dir=$1
failed_service=$2
shift 2
entries=("$@")
aliases=()
targets=()
for entry in "${entries[@]}"; do
  if [[ $entry == *=* ]]; then
    alias=${entry%%=*}
    target=${entry#*=}
  else
    alias=$entry
    target=$entry
  fi
  [[ $alias =~ ^[A-Za-z0-9_.-]+$ && $target =~ ^[A-Za-z0-9_.-]+$ ]] || continue
  aliases+=("$alias")
  targets+=("$target")
done
umask 077
mkdir -p "$output_dir" 2>/dev/null || {
  printf 'WARNING: diagnostics directory could not be created\n' >&2
  exit 0
}
chmod 700 "$output_dir" 2>/dev/null || true

for index in "${!aliases[@]}"; do
  container=${aliases[$index]}
  target=${targets[$index]}

  docker logs --timestamps "$target" \
    >"$output_dir/$container.log" 2>/dev/null || true

  docker inspect --format \
    '{"image":{{json .Config.Image}},"status":{{json .State.Status}},"exit_code":{{.State.ExitCode}},"oom_killed":{{.State.OOMKilled}},"restart_count":{{.RestartCount}}}' \
    "$target" >"$output_dir/$container-inspect.json" 2>/dev/null || true

  docker inspect --format '{{json .State.Health}}' "$target" \
    >"$output_dir/$container-health.json" 2>/dev/null || true
done

: >"$output_dir/docker-events.log"
until_time=$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || printf '0s')
for index in "${!aliases[@]}"; do
  container=${targets[$index]}
  docker events --since 30m --until "$until_time" \
    --filter "container=$container" --format '{{json .}}' \
    >>"$output_dir/docker-events.log" 2>/dev/null || true
done

failed_container=${aliases[0]:-unknown}
summary_result=''
if [[ -f "$output_dir/$failed_container.log" ]]; then
  summary_result=$(docker run --rm -i \
    --network mvp-observability \
    c206-deploy-event-publisher:local \
    summarize --service "$failed_service" \
    <"$output_dir/$failed_container.log" 2>/dev/null || true)
fi

exception_type=$(printf '%s' "$summary_result" | sed -n \
  's/.*"exception_type"[[:space:]]*:[[:space:]]*\(null\|"[^"]*"\).*/\1/p')
source_file=$(printf '%s' "$summary_result" | sed -n \
  's/.*"source_file"[[:space:]]*:[[:space:]]*\(null\|"[^"]*"\).*/\1/p')
source_line=$(printf '%s' "$summary_result" | sed -n \
  's/.*"source_line"[[:space:]]*:[[:space:]]*\(null\|[0-9][0-9]*\).*/\1/p')
exception_type=${exception_type:-null}
source_file=${source_file:-null}
source_line=${source_line:-null}

json_string() {
  local value=${1:-}
  value=${value//\\/\\\\}
  value=${value//\"/\\\"}
  value=${value//$'\r'/}
  value=${value//$'\n'/\\n}
  printf '"%s"' "$value"
}

summary_temp="$output_dir/.deployment-summary.json.tmp"
{
  printf '{'
  printf '"deploy_id":'; json_string "${DEPLOY_ID:-unknown}"
  printf ',"commit":'; json_string "${GIT_COMMIT_SHORT:-unknown}"
  printf ',"pipeline":'; json_string "${PIPELINE_NAME:-unknown}"
  printf ',"pipeline_stage":'; json_string "${PIPELINE_STAGE:-unknown}"
  printf ',"status":'; json_string "${DEPLOY_STATUS:-FAILED}"
  printf ',"environment":'; json_string "${DEPLOY_BRANCH:-unknown}"
  printf ',"container":'; json_string "$failed_container"
  printf ',"jenkins_url":'; json_string "${BUILD_URL:-unknown}"
  printf ',"exception_type":%s' "$exception_type"
  printf ',"source_file":%s' "$source_file"
  printf ',"source_line":%s' "$source_line"
  printf '}\n'
} >"$summary_temp" 2>/dev/null || true
mv -f "$summary_temp" "$output_dir/deployment-summary.json" 2>/dev/null || true

exit 0
