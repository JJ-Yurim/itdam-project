#!/usr/bin/env bash
set -Eeuo pipefail

branch="${1:?branch is required}"
image="${2:?image is required}"
commit="${3:?commit is required}"
runtime_env="${4:?runtime_env is required}"
diagnostics_dir="${5:-}"
compose_file="infra/compose.yml"
image_host_dir="/var/www/images"
placeholder_asset="$(pwd)/infra/assets/boss-phase-placeholder.svg"

case "$branch" in
  develop)
    spring_profile="develop"
    public_url="https://app.example.com/api/"
    ;;
  main)
    spring_profile="prod"
    public_url="https://app.example.com/api/"
    ;;
  *)
    echo "Unsupported deployment branch: $branch" >&2
    exit 64
    ;;
esac

legacy_container="backend-app"
slot_a_container="backend-app-a"
slot_b_container="backend-app-b"
slot_a_port="8081"
slot_b_port="8082"
previous_topology="none"
previous_image=""
previous_commit=""
export DEPLOY_BRANCH="$branch"
deploy_id="${DEPLOY_ID:-${branch}-manual-${commit}}"
jenkins_url="${BUILD_URL:-unknown}"
declare -a infrastructure_diagnostics=()

publish_deploy_event() {
  local status="$1"
  local stage="$2"
  local target_container="${3:-$slot_a_container}"

  printf '{"deploy_id":"%s","commit":"%s","pipeline":"backend","pipeline_stage":"%s","status":"%s","jenkins_url":"%s","environment":"%s","container":"%s"}' \
    "$deploy_id" "$commit" "$stage" "$status" "$jenkins_url" "$branch" "$target_container" |
    ./scripts/publish-deploy-event.sh
}

collect_diagnostics() {
  local failed_alias="$1"
  shift
  [[ -n $diagnostics_dir ]] || return 0
  DEPLOY_ID="$deploy_id" PIPELINE_NAME=backend PIPELINE_STAGE="Backend health check" \
    DEPLOY_STATUS=HEALTH_CHECK_FAILED \
    ./scripts/collect-deploy-diagnostics.sh \
      "$diagnostics_dir" backend "$failed_alias" "$@" || true
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

    case "$status" in
      healthy) return 0 ;;
      unhealthy|exited|dead) return 1 ;;
    esac
    sleep 2
  done
  return 1
}

container_exists() {
  docker inspect "$1" >/dev/null 2>&1
}

container_image() {
  docker inspect --format '{{.Config.Image}}' "$1"
}

container_commit() {
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$1" |
    sed -n 's/^DEPLOY_COMMIT=//p' |
    head -n 1
}

read_previous_identity() {
  previous_image="$(container_image "$1")"
  previous_commit="$(container_commit "$1")"
}

assert_same_identity() {
  local first="$1" second="$2"
  [ "$(container_image "$first")" = "$(container_image "$second")" ] &&
    [ "$(container_commit "$first")" = "$(container_commit "$second")" ]
}

assert_expected_identity() {
  local target="$1" expected_image="$2" expected_commit="$3"
  [ "$(container_image "$target")" = "$expected_image" ] &&
    [ "$(container_commit "$target")" = "$expected_commit" ]
}

discover_previous_topology() {
  local legacy_exists=false a_exists=false b_exists=false
  container_exists "$legacy_container" && legacy_exists=true
  container_exists "$slot_a_container" && a_exists=true
  container_exists "$slot_b_container" && b_exists=true

  if $legacy_exists && { $a_exists || $b_exists; }; then
    echo "Invalid backend topology: legacy and slot containers coexist" >&2
    return 1
  fi
  if [ "$a_exists" != "$b_exists" ]; then
    echo "Invalid backend topology: only one A/B slot exists" >&2
    return 1
  fi

  if $legacy_exists; then
    previous_topology="legacy"
    read_previous_identity "$legacy_container"
  elif $a_exists; then
    if ! assert_same_identity "$slot_a_container" "$slot_b_container"; then
      echo "Invalid backend topology: A/B image or commit differs" >&2
      return 1
    fi
    previous_topology="pair"
    read_previous_identity "$slot_a_container"
  fi
}

runtime_value() {
  sed -n "s/^$1=//p" "$runtime_env" | head -n 1
}

ensure_ai_readonly_role() {
  local password db_user db_name escaped_password login_error

  password="$(runtime_value AI_READONLY_DB_PASSWORD)"
  if [ -z "$password" ]; then
    echo "AI_READONLY_DB_PASSWORD is empty; skipping mvp_ai_readonly role management" >&2
    return 0
  fi

  db_user="$(runtime_value POSTGRES_USER)"
  db_name="$(runtime_value POSTGRES_DB)"
  if [ -z "$db_user" ] || [ -z "$db_name" ]; then
    echo "POSTGRES_USER or POSTGRES_DB is missing from the runtime environment" >&2
    return 1
  fi

  escaped_password="$(printf '%s' "$password" | sed "s/'/''/g")"
  {
    cat <<'SQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mvp_ai_readonly') THEN
    CREATE ROLE mvp_ai_readonly LOGIN;
  END IF;
END $$;
SQL
    printf "ALTER ROLE mvp_ai_readonly WITH LOGIN PASSWORD '%s';\n" "$escaped_password"
    cat <<'SQL'
GRANT CONNECT ON DATABASE :"DBNAME" TO mvp_ai_readonly;
GRANT USAGE ON SCHEMA public TO mvp_ai_readonly;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM mvp_ai_readonly;
GRANT SELECT ON
  ai_jobs, books, boss_runs, bosses, boss_moods,
  boss_submissions, mood_categories, sentence_versions
TO mvp_ai_readonly;
SQL
  } | docker exec -i "$postgres_container" \
        psql -v ON_ERROR_STOP=1 -U "$db_user" -d "$db_name" || {
    echo "mvp_ai_readonly role management SQL failed" >&2
    return 1
  }

  if ! login_error="$(printf '%s\n' "$password" | docker exec -i "$postgres_container" \
        sh -c 'read -r PGPASSWORD; export PGPASSWORD; exec psql -v ON_ERROR_STOP=1 -U mvp_ai_readonly -d "$0" -c "select 1"' \
        "$db_name" 2>&1 >/dev/null)"; then
    echo "mvp_ai_readonly login check failed: $login_error" >&2
    return 1
  fi
}

prepare_data_volume() {
  local target_image="$1"

  if [ ! -f "$placeholder_asset" ]; then
    echo "Placeholder asset is missing: $placeholder_asset" >&2
    return 1
  fi

  docker run --rm -i \
    --user root \
    --entrypoint sh \
    -v "backend-data:/app/data" \
    -v "${image_host_dir}:/host-images" \
    "$target_image" \
    -c 'set -eu
        uid="$(id -u spring)"
        gid="$(id -g spring)"
        mkdir -p /host-images/boss-phases /host-images/system
        cat > /host-images/system/boss-phase-placeholder.svg
        chown -R "$uid:$gid" /app/data /host-images
        chmod 755 /host-images /host-images/boss-phases /host-images/system
        chmod 644 /host-images/system/boss-phase-placeholder.svg' \
    <"$placeholder_asset"
}

start_container() {
  local target_container="$1"
  local target_port="$2"
  local slot="$3"
  local target_image="$4"
  local target_commit="$5"

  docker run -d \
    --name "$target_container" \
    --restart unless-stopped \
    --cpus 1 \
    --memory 1g \
    --log-driver json-file \
    --log-opt max-size=10m \
    --log-opt max-file=5 \
    --label c206.service=backend \
    --label "c206.environment=$branch" \
    --label "c206.slot=$slot" \
    -p "127.0.0.1:${target_port}:8080" \
    -v "backend-data:/app/data" \
    -v "${image_host_dir}:/app/data/images" \
    -e "DEPLOY_BRANCH=$branch" \
    -e "DEPLOY_COMMIT=$target_commit" \
    -e "SPRING_PROFILES_ACTIVE=$spring_profile" \
    --network mvp-infra \
    --env-file "$runtime_env" \
    -e DB_HOST=postgres \
    -e POSTGRES_PORT=5432 \
    -e REDIS_HOST=redis \
    -e REDIS_PORT=6379 \
    -e RABBITMQ_HOST=rabbitmq \
    -e RABBITMQ_PORT=5672 \
    "$target_image"
}

remove_container_if_present() {
  container_exists "$1" && docker rm -f "$1" || true
}

previous_topology_is_intact() {
  case "$previous_topology" in
    legacy)
      container_exists "$legacy_container" &&
        assert_expected_identity "$legacy_container" "$previous_image" "$previous_commit"
      ;;
    pair)
      container_exists "$slot_a_container" &&
        container_exists "$slot_b_container" &&
        assert_expected_identity "$slot_a_container" "$previous_image" "$previous_commit" &&
        assert_expected_identity "$slot_b_container" "$previous_image" "$previous_commit"
      ;;
    none) return 1 ;;
  esac
}

previous_topology_removal_failed() {
  local failed_container="$1"
  local target
  local -a diagnostics=("${infrastructure_diagnostics[@]}")

  for target in "$legacy_container" "$slot_a_container" "$slot_b_container"; do
    if [ "$target" != "$failed_container" ] && container_exists "$target"; then
      diagnostics+=("$target=$target")
    fi
  done
  collect_diagnostics "$failed_container=$failed_container" "${diagnostics[@]}"
  publish_deploy_event HEALTH_CHECK_FAILED "Previous topology removal" "$failed_container"

  if previous_topology_is_intact; then
    echo "Previous backend topology remains intact after removal failure" >&2
    exit 1
  fi

  remove_container_if_present "$legacy_container"
  remove_container_if_present "$slot_a_container"
  remove_container_if_present "$slot_b_container"

  if [ -z "$previous_image" ] || ! docker image inspect "$previous_image" >/dev/null 2>&1; then
    echo "No previous backend image is available after topology removal failure" >&2
    exit 1
  fi

  publish_deploy_event ROLLBACK_STARTED "Backend rollback" "$failed_container"
  if restore_previous_topology; then
    publish_deploy_event ROLLBACK_SUCCEEDED "Backend rollback" "$failed_container"
    echo "Previous backend topology was restored after removal failure" >&2
    exit 1
  fi

  publish_deploy_event ROLLBACK_FAILED "Backend rollback" "$failed_container"
  echo "Failed to restore the previous backend topology after removal failure" >&2
  exit 1
}

remove_previous_topology() {
  case "$previous_topology" in
    legacy)
      docker rm -f "$legacy_container" || previous_topology_removal_failed "$legacy_container"
      ;;
    pair)
      docker rm -f "$slot_a_container" || previous_topology_removal_failed "$slot_a_container"
      docker rm -f "$slot_b_container" || previous_topology_removal_failed "$slot_b_container"
      ;;
  esac
}

restore_previous_topology() {
  local rollback_commit="${previous_commit:-rollback}"
  case "$previous_topology" in
    legacy)
      start_container "$legacy_container" "$slot_a_port" legacy "$previous_image" "$rollback_commit"
      wait_until_healthy "$legacy_container"
      ;;
    pair)
      start_container "$slot_a_container" "$slot_a_port" a "$previous_image" "$rollback_commit"
      wait_until_healthy "$slot_a_container" || return 1
      start_container "$slot_b_container" "$slot_b_port" b "$previous_image" "$rollback_commit"
      wait_until_healthy "$slot_b_container" || return 1
      assert_same_identity "$slot_a_container" "$slot_b_container"
      ;;
    none) return 1 ;;
  esac
}

deployment_failed() {
  local failed_container="$1"
  local target
  local -a diagnostics=("${infrastructure_diagnostics[@]}")

  for target in "$slot_a_container" "$slot_b_container"; do
    if [ "$target" != "$failed_container" ] && container_exists "$target"; then
      diagnostics+=("$target=$target")
    fi
  done
  collect_diagnostics "$failed_container=$failed_container" "${diagnostics[@]}"
  publish_deploy_event HEALTH_CHECK_FAILED "Backend health check" "$failed_container"

  remove_container_if_present "$slot_a_container"
  remove_container_if_present "$slot_b_container"

  if [ -z "$previous_image" ] || ! docker image inspect "$previous_image" >/dev/null 2>&1; then
    echo "No previous backend image is available for rollback" >&2
    exit 1
  fi

  echo "Rolling back the previous backend topology: $previous_topology" >&2
  publish_deploy_event ROLLBACK_STARTED "Backend rollback" "$failed_container"
  if restore_previous_topology; then
    echo "Rollback succeeded; the new deployment remains failed" >&2
    publish_deploy_event ROLLBACK_SUCCEEDED "Backend rollback" "$failed_container"
    exit 1
  fi

  echo "Rollback health check failed" >&2
  publish_deploy_event ROLLBACK_FAILED "Backend rollback" "$failed_container"
  exit 1
}

docker compose --env-file "$runtime_env" -f "$compose_file" up -d

postgres_container=""
for service in postgres redis rabbitmq; do
  service_container="$(docker compose --env-file "$runtime_env" -f "$compose_file" ps -q "$service")"
  infrastructure_diagnostics+=("$service=$service_container")
  if [ -z "$service_container" ] || ! wait_until_healthy "$service_container"; then
    echo "Infrastructure service is not healthy: $service" >&2
    collect_diagnostics "$service=$service_container" "${infrastructure_diagnostics[@]}"
    publish_deploy_event HEALTH_CHECK_FAILED "Infrastructure health check" "$service"
    exit 1
  fi
  [ "$service" != "postgres" ] || postgres_container="$service_container"
done

discover_previous_topology || exit 1
prepare_data_volume "$image"
remove_previous_topology

echo "Starting backend slot A: $image"
start_container "$slot_a_container" "$slot_a_port" a "$image" "$commit" || deployment_failed "$slot_a_container"
wait_until_healthy "$slot_a_container" || deployment_failed "$slot_a_container"

echo "Starting backend slot B: $image"
start_container "$slot_b_container" "$slot_b_port" b "$image" "$commit" || deployment_failed "$slot_b_container"
wait_until_healthy "$slot_b_container" || deployment_failed "$slot_b_container"

ensure_ai_readonly_role || deployment_failed "$slot_b_container"
assert_same_identity "$slot_a_container" "$slot_b_container" || deployment_failed "$slot_b_container"
assert_expected_identity "$slot_a_container" "$image" "$commit" || deployment_failed "$slot_a_container"
assert_expected_identity "$slot_b_container" "$image" "$commit" || deployment_failed "$slot_b_container"

echo "Backend pair deployment succeeded: $public_url"
