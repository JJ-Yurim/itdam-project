#!/usr/bin/env bash
# 보스 제출 부하 측정 1회 실행 (ISSUE-198).
#
# 시드 → 매니페스트 생성 → 락 샘플러 시작 → k6 실행 → 요약 순으로 진행한다.
# k6는 로컬에 설치하지 않고 컨테이너로 실행하므로 Docker만 있으면 된다.
#
# 사전 조건
#   1. 측정 전용 postgres가 떠 있다 (개발용 infra/compose.yml이 아니다):
#        docker compose -f scripts/loadtest/compose.yml up -d
#   2. backend가 측정용 DB를 바라보며 loadtest 프로필로 떠 있다:
#        POSTGRES_PORT=55432 POSTGRES_DB=loadtest POSTGRES_USER=loadtest POSTGRES_PASSWORD=loadtest \
#          ./gradlew -p backend bootRun --args='--spring.profiles.active=local,loadtest'
#
# 사용 예
#   scripts/loadtest/run.sh --vus 20 --duration 60s
#   scripts/loadtest/run.sh --vus 50 --duration 60s --users 50 --versions 400
#   scripts/loadtest/run.sh --vus 20 --duration 60s --skip-seed   # 같은 시드로 재측정
set -euo pipefail

VUS=20
DURATION=60s
USERS=""
VERSIONS=400
SKIP_SEED=0
BASE_URL="${BASE_URL:-http://host.docker.internal:8080}"
PASSWORD="${LOADTEST_PASSWORD:-LoadTest1!}"
LABEL=""

while [[ $# -gt 0 ]]; do
	case "$1" in
		--vus) VUS="$2"; shift 2 ;;
		--duration) DURATION="$2"; shift 2 ;;
		--users) USERS="$2"; shift 2 ;;
		--versions) VERSIONS="$2"; shift 2 ;;
		--label) LABEL="$2"; shift 2 ;;
		--base-url) BASE_URL="$2"; shift 2 ;;
		--skip-seed) SKIP_SEED=1; shift ;;
		-h|--help) sed -n '2,20p' "$0"; exit 0 ;;
		*) echo "unknown option: $1" >&2; exit 2 ;;
	esac
done

# 계정 수를 따로 주지 않으면 VU 수와 맞춘다. 계정이 VU보다 적으면 한 계정을 두 VU가 나눠 쓰며
# 같은 문장 버전을 재제출해 409만 쌓인다.
[[ -n "$USERS" ]] || USERS="$VUS"
[[ -n "$LABEL" ]] || LABEL="vus${VUS}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_DIR="$REPO_ROOT/build/loadtest/$LABEL"
mkdir -p "$OUT_DIR"

# Git Bash에서 Docker에 넘기는 호스트 경로는 C:/... 형태여야 한다. 컨테이너 안 경로(/loadtest 등)는
# MSYS가 호스트 경로로 바꿔 버리므로 docker 호출마다 MSYS_NO_PATHCONV=1을 붙인다.
to_docker_path() {
	if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

# 측정 전용 컨테이너를 먼저 찾는다. 개발용 postgres(infra/compose.yml)에 시드를 쏟아붓지 않도록
# 폴백은 두지 않는다. 잘못된 DB를 지우는 것보다 실패하는 편이 낫다.
PG_CONTAINER="$(docker ps --filter 'label=c206.service=postgres-loadtest' --format '{{.Names}}' | head -1)"
if [[ -z "$PG_CONTAINER" ]]; then
	echo "측정 전용 postgres를 찾지 못했습니다. 먼저 올리세요:" >&2
	echo "  docker compose -f scripts/loadtest/compose.yml up -d" >&2
	exit 1
fi

# 자격 증명은 컨테이너 환경변수에서 읽는다. 스크립트에 DB 비밀번호를 남기지 않는다.
PG_USER="$(docker exec "$PG_CONTAINER" printenv POSTGRES_USER)"
PG_DB="$(docker exec "$PG_CONTAINER" printenv POSTGRES_DB)"
psql_in_container() { docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" "$@"; }

if [[ "$SKIP_SEED" -eq 0 ]]; then
	echo "== 시드 (users=$USERS, versions_per_user=$VERSIONS)"
	psql_in_container -q -v users="$USERS" -v versions_per_user="$VERSIONS" -v password="$PASSWORD" \
		< "$SCRIPT_DIR/seed.sql"
fi

echo "== 매니페스트 생성"
# json_build_object는 값이 null이어도 키를 출력하므로 키 존재가 아니라 숫자 값까지 확인한다.
# 시드가 실패해 활성 회차가 없으면 여기서 멈춰야지, k6를 띄운 뒤 setup()에서 실패하면 늦다.
psql_in_container -q < "$SCRIPT_DIR/manifest.sql" | tr -d '\r' > "$OUT_DIR/manifest.json"
if ! grep -Eq '"bossRunId" *: *[0-9]+' "$OUT_DIR/manifest.json"; then
	echo "매니페스트에 활성 보스 회차가 없습니다. 시드를 먼저 실행하세요." >&2
	exit 1
fi

echo "== 락 샘플러 시작"
SAMPLE_FILE="$OUT_DIR/locks.tsv"
printf 'at\tactive\tidle_in_tx\tlock_waiting\tmax_lock_wait_ms\tboss_runs_waiting\n' > "$SAMPLE_FILE"
(
	while true; do
		psql_in_container -q < "$SCRIPT_DIR/sample-locks.sql" | tr -d '\r' >> "$SAMPLE_FILE" 2>/dev/null || true
		sleep 1
	done
) &
SAMPLER_PID=$!
# k6가 실패해도 샘플러가 남지 않도록 한다.
trap 'kill "$SAMPLER_PID" 2>/dev/null || true' EXIT

echo "== k6 실행 (vus=$VUS, duration=$DURATION, base_url=$BASE_URL)"
set +e
MSYS_NO_PATHCONV=1 docker run --rm -i \
	--add-host host.docker.internal:host-gateway \
	-v "$(to_docker_path "$SCRIPT_DIR")":/scripts:ro \
	-v "$(to_docker_path "$OUT_DIR")":/loadtest \
	-e MANIFEST_PATH=/loadtest/manifest.json \
	-e BASE_URL="$BASE_URL" \
	-e LOADTEST_PASSWORD="$PASSWORD" \
	-e VUS="$VUS" \
	-e DURATION="$DURATION" \
	grafana/k6:latest run --summary-export=/loadtest/summary.json /scripts/boss-submission.js \
	2>&1 | tee "$OUT_DIR/k6.log"
K6_STATUS="${PIPESTATUS[0]}"
set -e

kill "$SAMPLER_PID" 2>/dev/null || true
trap - EXIT

echo
echo "== 락 대기 요약 ($SAMPLE_FILE)"
awk -F'\t' 'NR>1 {
	samples++
	if ($4 > maxWaiting) maxWaiting = $4
	sumWaiting += $4
	if ($5 > maxWaitMs) maxWaitMs = $5
	if ($3 > maxIdleInTx) maxIdleInTx = $3
} END {
	if (samples == 0) { print "  샘플이 없습니다."; exit }
	printf "  샘플 수            : %d\n", samples
	printf "  락 대기 최대 세션  : %d\n", maxWaiting
	printf "  락 대기 평균 세션  : %.1f\n", sumWaiting / samples
	printf "  최대 락 대기 시간  : %d ms\n", maxWaitMs
	printf "  트랜잭션 내 유휴 최대: %d\n", maxIdleInTx
}' "$SAMPLE_FILE"

echo
echo "결과: $OUT_DIR"
exit "$K6_STATUS"
