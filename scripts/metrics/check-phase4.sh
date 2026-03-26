#!/usr/bin/env bash

set -euo pipefail

PROM_URL="${PROM_URL:-http://localhost:9090}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "required command not found: $1" >&2
    exit 1
  fi
}

require_command curl

# ── measure 구간 결정 ─────────────────────────────────────────────────────────
# 우선순위: MEASURE_START/END 직접 지정 > phase-report-*.json 자동 탐지 > 현재 시각 fallback

parse_unix_time() {
  local value="$1"
  if [[ "$value" =~ ^[0-9]+$ ]]; then
    echo "$value"
  else
    python3 -c "
from datetime import datetime, timezone
print(int(datetime.fromisoformat('${value}'.replace('Z', '+00:00')).timestamp()))
"
  fi
}

if [[ -n "${MEASURE_START:-}" && -n "${MEASURE_END:-}" ]]; then
  query_time=$(parse_unix_time "$MEASURE_END")
  duration_s=$(( $(parse_unix_time "$MEASURE_END") - $(parse_unix_time "$MEASURE_START") ))
  rate_window="${duration_s}s"
  echo "# measure 구간 (직접 지정): ${MEASURE_START} ~ ${MEASURE_END} (window: ${rate_window})"
else
  phase_report_path="${PHASE_REPORT:-}"
  if [[ -z "$phase_report_path" ]]; then
    phase_report_path=$(ls -t phase-report-*.json 2>/dev/null | head -1 || true)
  fi

  if [[ -n "$phase_report_path" && -f "$phase_report_path" ]]; then
    if ! command -v jq >/dev/null 2>&1; then
      echo "# jq 없음 — phase report 파싱 불가, 현재 시각 기준 조회 (window: 1m)" >&2
      query_time=""
      rate_window="1m"
    else
      measure_start=$(jq -r '.phases.measure.start' "$phase_report_path")
      measure_end=$(jq -r '.phases.measure.end' "$phase_report_path")
      duration_ms=$(jq -r '.phases.measure.durationMs' "$phase_report_path")
      query_time=$(parse_unix_time "$measure_end")
      duration_s=$(( duration_ms / 1000 ))
      rate_window="${duration_s}s"
      echo "# measure 구간 ($phase_report_path): ${measure_start} ~ ${measure_end} (window: ${rate_window})"
    fi
  else
    query_time=""
    rate_window="1m"
    echo "# phase report 없음 — 현재 시각 기준 조회 (window: ${rate_window})"
  fi
fi

# ── Prometheus 조회 헬퍼 ──────────────────────────────────────────────────────

query_prometheus() {
  local query="$1"
  local time="${2:-}"

  if [[ -n "$time" ]]; then
    curl -sG "${PROM_URL}/api/v1/query" \
      --data-urlencode "query=${query}" \
      --data-urlencode "time=${time}"
  else
    curl -sG "${PROM_URL}/api/v1/query" \
      --data-urlencode "query=${query}"
  fi
}

extract_value() {
  if command -v jq >/dev/null 2>&1; then
    jq -r '
      if .data.result | length == 0 then
        "NO_DATA"
      else
        .data.result[]
        | if .metric.symbol then
            "\(.metric.symbol)=\(.value[1])"
          else
            .value[1]
          end
      end
    '
  else
    sed -n 's/.*"value":\[[^,]*,"\([^"]*\)"\].*/\1/p'
  fi
}

print_metric() {
  local name="$1"
  local query="$2"
  local result
  result="$(query_prometheus "$query" "${query_time:-}" | extract_value)"

  if [[ -z "${result}" ]]; then
    result="NO_DATA"
  fi

  echo "${name}: ${result}"
}

# ── 메트릭 조회 ───────────────────────────────────────────────────────────────

print_metric "place_order_tps"                   "rate(place_order_tps_total[${rate_window}])"
print_metric "accepted_order_tps"                "rate(accepted_order_tps_total[${rate_window}])"
print_metric "cancel_order_tps"                  "rate(cancel_order_tps_total[${rate_window}])"
print_metric "cancel_completed_tps"              "rate(cancel_completed_tps_total[${rate_window}])"
print_metric "cancel_rejected_tps"               "rate(cancel_rejected_tps_total[${rate_window}])"
print_metric "rejected_order_tps"                "rate(rejected_order_tps_total[${rate_window}])"
print_metric "trades_per_sec"                    "rate(trades_per_sec_total[${rate_window}])"
print_metric "matched_orders_per_sec"            "rate(matched_orders_per_sec_total[${rate_window}])"
print_metric "queue_wait_latency_p95_ms"         "histogram_quantile(0.95, sum(rate(queue_wait_latency_seconds_bucket[${rate_window}])) by (le)) * 1000"
print_metric "engine_processing_latency_p95_ms"  "histogram_quantile(0.95, sum(rate(engine_processing_latency_seconds_bucket[${rate_window}])) by (le)) * 1000"
print_metric "order_accept_tx_latency_p95_ms"    "histogram_quantile(0.95, sum(rate(order_accept_tx_latency_seconds_bucket[${rate_window}])) by (le)) * 1000"
print_metric "engine_result_tx_latency_p95_ms"   "histogram_quantile(0.95, sum(rate(engine_result_tx_latency_seconds_bucket[${rate_window}])) by (le)) * 1000"
print_metric "end_to_end_order_latency_p95_ms"   "histogram_quantile(0.95, sum(rate(end_to_end_order_latency_seconds_bucket[${rate_window}])) by (le)) * 1000"
print_metric "end_to_end_cancel_latency_p95_ms"  "histogram_quantile(0.95, sum(rate(end_to_end_cancel_latency_seconds_bucket[${rate_window}])) by (le)) * 1000"
print_metric "db_lock_wait_time_p95_ms"          "histogram_quantile(0.95, sum(rate(db_lock_wait_time_seconds_bucket[${rate_window}])) by (le)) * 1000"
print_metric "engine_queue_depth"                "engine_queue_depth"
print_metric "engine_backpressure_count"         "rate(engine_backpressure_count_total[${rate_window}])"
print_metric "queue_full_rollback_count"         "rate(queue_full_rollback_count_total[${rate_window}])"
print_metric "balance_lock_contention_count"     "rate(balance_lock_contention_count_total[${rate_window}])"
print_metric "idempotency_conflict_count"        "rate(idempotency_conflict_count_total[${rate_window}])"
print_metric "error_rate"                        "rate(error_rate_total[${rate_window}])"
print_metric "db_deadlock_count"                 "rate(db_deadlock_count_total[5m])"
print_metric "db_commit_failure_count"           "rate(db_commit_failure_count_total[5m])"
print_metric "db_rollback_count"                 "rate(db_rollback_count_total[5m])"
print_metric "replay_duration_on_startup_p95_ms" "histogram_quantile(0.95, sum(rate(replay_duration_on_startup_seconds_bucket[5m])) by (le)) * 1000"
print_metric "replay_open_order_count"           "replay_open_order_count_total"
print_metric "replay_consistency_check"          "replay_consistency_check"
print_metric "replay_duration_by_symbol_p95_ms"  "histogram_quantile(0.95, sum(rate(replay_duration_by_symbol_seconds_bucket[5m])) by (le, symbol)) * 1000"
