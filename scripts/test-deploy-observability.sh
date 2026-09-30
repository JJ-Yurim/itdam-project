#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
deploy_script="$repo_root/scripts/deploy-observability.sh"
[[ -x "$deploy_script" ]] || { printf 'FAIL: deploy script is missing\n' >&2; exit 1; }

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
fake_bin="$temp_dir/bin"
mkdir -p "$fake_bin"
calls="$temp_dir/calls.log"
state="$temp_dir/state"
runtime_env="$temp_dir/runtime.env"
printf 'GRAFANA_ADMIN_USER=admin\nGRAFANA_ADMIN_PASSWORD=test-only\n' >"$runtime_env"

cat >"$fake_bin/docker" <<'FAKE'
#!/usr/bin/env bash
set -u
printf '%s\n' "$*" >>"$FAKE_DOCKER_CALLS"

if [[ $1 == network && $2 == inspect ]]; then
  exit 1
fi
if [[ $1 == network && $2 == create ]]; then
  exit 0
fi
if [[ $1 == compose ]]; then
  shift
  while [[ $# -gt 0 ]]; do
    case $1 in
      --env-file|-f) shift 2 ;;
      *) break ;;
    esac
  done
  if [[ $1 == config && ${2:-} == --services ]]; then
    printf 'loki\nalloy\ngrafana\n'
    exit 0
  fi
  if [[ $1 == config ]]; then exit 0; fi
  if [[ $1 == up ]]; then exit 0; fi
  if [[ $1 == ps && $2 == -q ]]; then
    count=0
    [[ -f $FAKE_DOCKER_STATE ]] && count=$(cat "$FAKE_DOCKER_STATE")
    count=$((count + 1))
    printf '%s' "$count" >"$FAKE_DOCKER_STATE"
    if [[ $count -eq 1 ]]; then exit 1; fi
    printf '%s-id\n' "$3"
    exit 0
  fi
  if [[ $1 == logs ]]; then exit 0; fi
fi
if [[ $1 == run ]]; then
  # 설정 스테이징은 tar를 stdin으로 넘긴다. 여기서 읽어 버리지 않으면 tar가 EPIPE로
  # 죽고 배포 스크립트의 pipefail에 걸려 원인이 엉뚱하게 보인다.
  cat >/dev/null
  exit 0
fi
if [[ $1 == inspect ]]; then
  count=$(cat "$FAKE_DOCKER_STATE")
  if [[ $count -eq 2 ]]; then exit 1; fi
  printf 'healthy\n'
  exit 0
fi
exit 2
FAKE
chmod +x "$fake_bin/docker"

FAKE_DOCKER_CALLS="$calls" FAKE_DOCKER_STATE="$state" \
PATH="$fake_bin:$PATH" OBSERVABILITY_HEALTH_TIMEOUT_SECONDS=3 \
OBSERVABILITY_HEALTH_POLL_SECONDS=0 \
  "$deploy_script" "$runtime_env"

grep -Fxq 'network inspect mvp-observability' "$calls" \
  || { printf 'FAIL: deploy must inspect the external network\n' >&2; exit 1; }
grep -Fxq 'network create mvp-observability' "$calls" \
  || { printf 'FAIL: deploy must create a missing external network\n' >&2; exit 1; }
grep -Fq 'config --quiet' "$calls" \
  || { printf 'FAIL: deploy must validate Compose\n' >&2; exit 1; }
grep -Fq 'config --services' "$calls" \
  || { printf 'FAIL: deploy must health-check every configured service\n' >&2; exit 1; }
grep -Fq 'up -d --remove-orphans --force-recreate' "$calls" \
  || { printf 'FAIL: deploy must recreate containers after replacing bind mount sources\n' >&2; exit 1; }

grep -Fq 'run --rm -i' "$calls" \
  && { printf 'FAIL: deploy must not stage config when no host dir is given\n' >&2; exit 1; }

# OBSERVABILITY_CONFIG_DIR가 주어지면 설정을 그 호스트 경로로 복사해야 한다.
# 컨테이너화된 Jenkins에서 워크스페이스 경로를 그대로 마운트하면 호스트에 없는
# 경로가 되어 Docker가 빈 디렉터리를 만들고, Loki가 설정을 읽지 못한 채
# healthcheck 타임아웃으로만 드러난다.
staged_calls="$temp_dir/calls-staged.log"
staged_state="$temp_dir/state-staged"
config_host_dir=/opt/test-observability-config

FAKE_DOCKER_CALLS="$staged_calls" FAKE_DOCKER_STATE="$staged_state" \
PATH="$fake_bin:$PATH" OBSERVABILITY_HEALTH_TIMEOUT_SECONDS=3 \
OBSERVABILITY_HEALTH_POLL_SECONDS=0 \
OBSERVABILITY_CONFIG_DIR="$config_host_dir" \
  "$deploy_script" "$runtime_env"

grep -Fq -- "-v $config_host_dir:/target" "$staged_calls" \
  || { printf 'FAIL: deploy must stage config into the host directory\n' >&2; exit 1; }

# 마운트 원본에 Jenkins 워크스페이스 경로가 등장하면 이 수정이 되돌려진 것이다.
if grep -E -- '-v +[^ ]*(jenkins|workspace)[^ ]*:' "$staged_calls" >/dev/null; then
  printf 'FAIL: deploy must never bind mount a Jenkins workspace path\n' >&2
  exit 1
fi

printf 'PASS: deploy handles external network ownership, transient health checks, and host config staging\n'
