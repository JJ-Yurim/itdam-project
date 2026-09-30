#!/usr/bin/env bash
set -Eeuo pipefail

branch="${1:?branch is required}"
image="${2:?image is required}"
commit="${3:?commit is required}"
runtime_env="${4:?runtime_env is required}"
diagnostics_dir="${5:-}"
container="ai-service"
network="mvp-infra"

case "$branch" in
  main|develop)
    ;;
  *)
    echo "Unsupported deployment branch: $branch" >&2
    exit 64
    ;;
esac

if [ ! -r "$runtime_env" ]; then
  echo "Runtime environment file is not readable: $runtime_env" >&2
  exit 66
fi

if ! docker network inspect "$network" >/dev/null 2>&1; then
  echo "Required Docker network does not exist: $network" >&2
  exit 1
fi

previous_image=""
previous_commit=""
deploy_id="${DEPLOY_ID:-${branch}-manual-${commit}}"
jenkins_url="${BUILD_URL:-unknown}"

publish_deploy_event() {
  local status="$1"
  local stage="$2"
  printf '{"deploy_id":"%s","commit":"%s","pipeline":"ai-service","pipeline_stage":"%s","status":"%s","jenkins_url":"%s","environment":"%s","container":"%s"}' \
    "$deploy_id" "$commit" "$stage" "$status" "$jenkins_url" "$branch" "$container" |
    ./scripts/publish-deploy-event.sh
}

collect_diagnostics() {
  [[ -n $diagnostics_dir ]] || return 0
  DEPLOY_ID="$deploy_id" DEPLOY_BRANCH="$branch" GIT_COMMIT_SHORT="$commit" \
    BUILD_URL="$jenkins_url" PIPELINE_NAME=ai-service \
    PIPELINE_STAGE="AI Service readiness" DEPLOY_STATUS=HEALTH_CHECK_FAILED \
    ./scripts/collect-deploy-diagnostics.sh \
      "$diagnostics_dir" ai-service "$container" || true
}

wait_until_healthy() {
  local target="$1"
  local status

  for _ in $(seq 1 60); do
    status="$(
      docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$target"
    )" || return 1

    # HEALTHCHECK이 없는 이미지(예: 롤백 대상인 과거 이미지)는 컨테이너 상태가
    # running으로 나오므로 healthy와 동일하게 성공으로 판정한다.
    case "$status" in
      healthy|running)
        return 0
        ;;
      exited|dead|unhealthy)
        return 1
        ;;
    esac

    sleep 2
  done

  return 1
}

if docker inspect "$container" >/dev/null 2>&1; then
  previous_image="$(
    docker inspect \
      --format '{{.Config.Image}}' \
      "$container"
  )"

  previous_commit="$(
    docker inspect \
      --format '{{range .Config.Env}}{{println .}}{{end}}' \
      "$container" |
      sed -n 's/^DEPLOY_COMMIT=//p' |
      head -n 1
  )"
fi

start_container() {
  local target_image="$1"
  local target_commit="$2"

  docker run -d \
    --name "$container" \
    --restart unless-stopped \
    --cpus 1 \
    --memory 512m \
    --log-driver json-file \
    --log-opt max-size=10m \
    --log-opt max-file=5 \
    --label c206.service=ai-service \
    --label "c206.environment=$branch" \
    --network "$network" \
    --env-file "$runtime_env" \
    -e "DEPLOY_BRANCH=$branch" \
    -e "DEPLOY_COMMIT=$target_commit" \
    "$target_image"
}

if docker inspect "$container" >/dev/null 2>&1; then
  docker rm -f "$container"
fi

echo "Starting AI Service deployment: $image"

# set -e 환경에서 docker run 실패가 스크립트를 즉시 종료시키면 롤백 경로를
# 건너뛰게 되므로, 기동 실패와 헬스체크 실패를 같은 실패 경계로 묶는다.
if start_container "$image" "$commit" && wait_until_healthy "$container"; then
  echo "AI Service deployment succeeded: $image"
  exit 0
fi

echo "New AI Service container health check failed" >&2
collect_diagnostics
publish_deploy_event HEALTH_CHECK_FAILED "AI Service readiness"
docker logs --tail 200 "$container" || true
docker rm -f "$container" || true

if [ -n "$previous_image" ] &&
   docker image inspect "$previous_image" >/dev/null 2>&1; then
  echo "Rolling back AI Service to: $previous_image" >&2
  publish_deploy_event ROLLBACK_STARTED "AI Service rollback"

  if start_container "$previous_image" "${previous_commit:-rollback}" &&
     wait_until_healthy "$container"; then
    echo "Rollback succeeded; new AI Service deployment failed" >&2
    publish_deploy_event ROLLBACK_SUCCEEDED "AI Service rollback"
    exit 1
  fi

  echo "Rollback AI Service health check also failed" >&2
  publish_deploy_event ROLLBACK_FAILED "AI Service rollback"
  docker logs --tail 200 "$container" || true
else
  echo "No previous AI Service image is available for rollback" >&2
fi

exit 1
