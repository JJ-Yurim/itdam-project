#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -r $1 ]]; then
  printf 'usage: deploy-observability.sh RUNTIME_ENV_FILE\n' >&2
  exit 64
fi

runtime_env=$1
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file="$repo_root/infra/observability/compose.yml"
network_name=mvp-observability
health_timeout=${OBSERVABILITY_HEALTH_TIMEOUT_SECONDS:-120}
poll_seconds=${OBSERVABILITY_HEALTH_POLL_SECONDS:-2}

if ! docker network inspect "$network_name" >/dev/null 2>&1; then
  docker network create "$network_name" >/dev/null
fi

# compose가 설정 파일을 bind mount할 원본 경로를 정한다.
#
# 이 스크립트는 컨테이너화된 Jenkins 안에서 실행되지만 Docker 데몬은 호스트 것이다.
# 그대로 두면 compose CLI가 워크스페이스 상대 경로를 이 컨테이너 기준 절대 경로
# (/var/jenkins_home/...)로 바꿔 호스트 데몬에 넘기고, 호스트에는 그 경로가 없으므로
# Docker가 그 자리에 빈 디렉터리를 만든다. Loki는 디렉터리를 설정 파일로 읽으려다
# 죽고, 증상은 "healthcheck 타임아웃"으로만 보여 원인을 찾기 어렵다.
#
# 그래서 OBSERVABILITY_CONFIG_DIR가 주어지면 그 호스트 경로로 설정을 먼저 복사한다.
# 복사도 워크스페이스 마운트 없이 해야 하므로 tar를 stdin으로 흘려보내고, 호스트
# 절대 경로만 마운트한 헬퍼 컨테이너가 풀어놓는다. 헬퍼 이미지는 새로 받지 않도록
# 파이프라인이 이미 쓰는 python:3.12-slim을 재사용한다(Debian이라 tar가 있다).
source_dir="$repo_root/infra/observability"
config_dir=${OBSERVABILITY_CONFIG_DIR:-}

if [[ -n $config_dir ]]; then
  tar -C "$source_dir" -cf - loki.yml alloy.alloy grafana |
    docker run --rm -i --user root \
      -v "${config_dir}:/target" \
      python:3.12-slim \
      sh -euc '
        rm -rf /target/loki.yml /target/alloy.alloy /target/grafana
        tar -C /target -xf -
        # loki(uid 10001), grafana(uid 472), alloy는 서로 다른 사용자로 실행된다.
        # 읽기 전용 마운트이므로 모두에게 읽기와 디렉터리 진입만 허용하면 충분하다.
        chmod -R a+rX /target
      '
else
  config_dir="$source_dir"
fi

# compose 보간은 --env-file보다 셸 환경을 우선하므로 export만으로 전달된다.
export OBSERVABILITY_CONFIG_DIR="$config_dir"

compose=(docker compose --env-file "$runtime_env" -f "$compose_file")
"${compose[@]}" config --quiet
# 스테이징 과정에서 bind mount 원본을 교체하므로 기존 컨테이너를 재사용하면 삭제된
# inode를 계속 바라본다. 새 원본 경로를 다시 마운트하도록 컨테이너를 재생성한다.
"${compose[@]}" up -d --remove-orphans --force-recreate

mapfile -t services < <("${compose[@]}" config --services)
if [[ ${#services[@]} -eq 0 ]]; then
  printf 'Observability deployment has no configured services.\n' >&2
  exit 1
fi

deadline=$((SECONDS + health_timeout))
while true; do
  unhealthy=()

  for service in "${services[@]}"; do
    container_id=''
    health='unknown'

    if container_id=$("${compose[@]}" ps -q "$service" 2>/dev/null) \
      && [[ -n $container_id ]]; then
      if ! health=$(docker inspect --format \
        '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$container_id" 2>/dev/null); then
        health='unknown'
      fi
    fi

    [[ $health == healthy ]] || unhealthy+=("$service")
  done

  if [[ ${#unhealthy[@]} -eq 0 ]]; then
    printf 'Observability stack is healthy.\n'
    exit 0
  fi

  if (( SECONDS >= deadline )); then
    printf 'Observability services did not become healthy within %s seconds: %s\n' \
      "$health_timeout" "${unhealthy[*]}" >&2
    for service in "${unhealthy[@]}"; do
      printf '%s\n' "--- $service logs (last 200 lines) ---" >&2
      "${compose[@]}" logs --no-color --tail 200 "$service" >&2 || true
    done
    exit 1
  fi

  sleep "$poll_seconds"
done
