#!/usr/bin/env bash

set -euo pipefail

SPLUNK_HEC_URL="${SPLUNK_HEC_URL:-https://localhost:8088/services/collector/event}"
SPLUNK_HEC_TOKEN="${SPLUNK_HEC_TOKEN:?Set SPLUNK_HEC_TOKEN first}"
LOG_FILE="${LOG_FILE:-logs/hello-service.log}"

tail -n 0 -F "$LOG_FILE" | while IFS= read -r line; do
  event=$(jq -cn \
    --arg sourcetype "spring_boot_json" \
    --arg source "$LOG_FILE" \
    --arg application "hello-service" \
    --arg environment "local" \
    --arg raw "$line" \
    '{
      sourcetype: $sourcetype,
      source: $source,
      event: {
        application: $application,
        environment: $environment,
        raw: $raw
      }
    }')

  curl -sk "$SPLUNK_HEC_URL" \
    -H "Authorization: Splunk ${SPLUNK_HEC_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$event" \
    >/dev/null
done
