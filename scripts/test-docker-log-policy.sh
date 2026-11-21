#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file="$repo_root/infra/compose.yml"
rendered=$(mktemp)
trap 'rm -f "$rendered"' EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

POSTGRES_DB=test POSTGRES_USER=test POSTGRES_PASSWORD=test \
REDIS_PASSWORD=test RABBITMQ_USER=test RABBITMQ_PASSWORD=test \
RABBITMQ_VHOST=test DEPLOY_BRANCH=develop \
  docker compose -f "$compose_file" config >"$rendered"

[[ $(grep -c '^[[:space:]]*driver:[[:space:]]*json-file$' "$rendered") -eq 3 ]] \
  || fail "PostgreSQL, Redis, and RabbitMQ must use json-file logging"
[[ $(grep -c '^[[:space:]]*max-size:[[:space:]]*10m$' "$rendered") -eq 3 ]] \
  || fail "all infrastructure log files must rotate at 10m"
[[ $(grep -Ec '^[[:space:]]*max-file:[[:space:]]*"?5"?$' "$rendered") -eq 3 ]] \
  || fail "all infrastructure services must retain five log files"
[[ $(grep -c 'c206.environment:[[:space:]]*develop$' "$rendered") -eq 3 ]] \
  || fail "all infrastructure services must carry the deployment environment label"

for service in postgres redis rabbitmq; do
  grep -Eq "c206.service:[[:space:]]*${service}$" "$rendered" \
    || fail "missing c206.service label for $service"
done

assert_start_policy() {
  local script=$1
  local service=$2
  local block
  block=$(sed -n '/^start_container()/,/^}/p' "$repo_root/$script")

  for expected in \
    '--log-driver json-file' \
    '--log-opt max-size=10m' \
    '--log-opt max-file=5' \
    "--label c206.service=${service}" \
    '--label "c206.environment=$branch"'; do
    grep -Fq -- "$expected" <<<"$block" \
      || fail "$script start_container is missing: $expected"
  done
}

assert_start_policy scripts/deploy-backend.sh backend
assert_start_policy scripts/deploy-ai-service.sh ai-service
assert_start_policy scripts/deploy-frontend.sh frontend

printf 'PASS: Docker log rotation and collection labels are consistent\n'
