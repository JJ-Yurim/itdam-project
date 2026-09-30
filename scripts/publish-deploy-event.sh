#!/usr/bin/env bash
set -uo pipefail

if ! docker run --rm -i \
  --network mvp-observability \
  c206-deploy-event-publisher:local \
  publish --stdin; then
  printf 'WARNING: deployment event could not be sent to Loki\n' >&2
fi

exit 0
