#!/usr/bin/env bash
set -Eeuo pipefail

branch="${1:?branch is required}"
image="${2:?image is required}"
commit="${3:?commit is required}"
diagnostics_dir="${4:-}"
container="frontend-app"

case "$branch" in
  main|develop)
    host_port="3000"
    public_url="https://app.example.com/"
    ;;
  *)
    echo "배포할 수 없는 브랜치입니다: $branch" >&2
    exit 64
    ;;
esac

previous_image=""
previous_commit=""
deploy_id="${DEPLOY_ID:-${branch}-manual-${commit}}"
jenkins_url="${BUILD_URL:-unknown}"

publish_deploy_event() {
  local status="$1"
  local stage="$2"
  printf '{"deploy_id":"%s","commit":"%s","pipeline":"frontend","pipeline_stage":"%s","status":"%s","jenkins_url":"%s","environment":"%s","container":"%s"}' \
    "$deploy_id" "$commit" "$stage" "$status" "$jenkins_url" "$branch" "$container" |
    ./scripts/publish-deploy-event.sh
}

collect_diagnostics() {
  [[ -n $diagnostics_dir ]] || return 0
  DEPLOY_ID="$deploy_id" DEPLOY_BRANCH="$branch" GIT_COMMIT_SHORT="$commit" \
    BUILD_URL="$jenkins_url" PIPELINE_NAME=frontend \
    PIPELINE_STAGE="Frontend host serving" DEPLOY_STATUS=HEALTH_CHECK_FAILED \
    ./scripts/collect-deploy-diagnostics.sh \
      "$diagnostics_dir" frontend "$container" || true
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

# 컨테이너 HEALTHCHECK은 컨테이너 내부에서 실행되므로 호스트 포트 게시가
# 실제로 동작하는지는 증명하지 못한다. 배포 판정에 호스트 경로를 포함한다.
#
# 이 스크립트 자체가 컨테이너화된 Jenkins 안에서 실행되므로, 여기서 직접
# curl을 치면 Jenkins 컨테이너의 루프백으로 가서 항상 실패한다. Docker
# 데몬은 호스트 것이므로 --network host 헬퍼 컨테이너를 띄우면 호스트
# 루프백의 게시 포트를 정확히 확인할 수 있다. 별도 이미지를 받지 않도록
# wget(busybox)이 있는 배포 이미지 자신을 헬퍼로 재사용한다.
wait_until_serving() {
  local probe_image="$1"

  for _ in $(seq 1 30); do
    if docker run --rm \
         --network host \
         --entrypoint wget \
         "$probe_image" \
         -q -O /dev/null "http://127.0.0.1:${host_port}/healthz"; then
      return 0
    fi

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

  # 공개 트래픽은 호스트 nginx가 받으므로 루프백에만 바인딩한다.
  docker run -d \
    --name "$container" \
    --restart unless-stopped \
    --cpus 0.5 \
    --memory 256m \
    --log-driver json-file \
    --log-opt max-size=10m \
    --log-opt max-file=5 \
    --label c206.service=frontend \
    --label "c206.environment=$branch" \
    -p "127.0.0.1:${host_port}:80" \
    -e "DEPLOY_BRANCH=$branch" \
    -e "DEPLOY_COMMIT=$target_commit" \
    "$target_image"
}

if docker inspect "$container" >/dev/null 2>&1; then
  docker rm -f "$container"
fi

echo "배포 시작: $image → $container"

# set -e 환경에서 docker run 실패가 스크립트를 즉시 종료시키면 롤백 경로를
# 건너뛰게 되므로, 기동 실패와 헬스체크 실패를 같은 실패 경계로 묶는다.
if start_container "$image" "$commit" &&
   wait_until_healthy "$container" &&
   wait_until_serving "$image"; then
  echo "배포 성공: $public_url"
  exit 0
fi

echo "신규 컨테이너 헬스체크 또는 호스트 응답 확인 실패" >&2
collect_diagnostics
publish_deploy_event HEALTH_CHECK_FAILED "Frontend host serving"
docker logs --tail 200 "$container" || true
docker rm -f "$container" || true

if [ -n "$previous_image" ] &&
   docker image inspect "$previous_image" >/dev/null 2>&1; then
  echo "이전 이미지로 롤백: $previous_image" >&2
  publish_deploy_event ROLLBACK_STARTED "Frontend rollback"

  if start_container "$previous_image" "${previous_commit:-rollback}" &&
     wait_until_healthy "$container" &&
     wait_until_serving "$previous_image"; then
    echo "롤백 성공. 신규 배포는 실패 처리합니다." >&2
    publish_deploy_event ROLLBACK_SUCCEEDED "Frontend rollback"
    exit 1
  fi

  echo "롤백 컨테이너도 응답 확인에 실패했습니다." >&2
  publish_deploy_event ROLLBACK_FAILED "Frontend rollback"
  docker logs --tail 200 "$container" || true
else
  echo "롤백할 이전 이미지가 없습니다." >&2
fi

exit 1
