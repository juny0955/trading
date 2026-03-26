$ErrorActionPreference = "Stop"

$PROM_URL = if ($env:PROM_URL) { $env:PROM_URL } else { "http://localhost:9090" }

function Require-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Write-Error "required command not found: $Name"
        exit 1
    }
}

Require-Command "curl.exe"

function Query-Prometheus {
    param([string]$Query)

    $encodedQuery = [System.Uri]::EscapeDataString($Query)
    $url = "${PROM_URL}/api/v1/query?query=${encodedQuery}"

    curl.exe -s $url
}

function Extract-Value {
    param([string]$JsonText)

    try {
        $json = $JsonText | ConvertFrom-Json

        if (-not $json.data.result -or $json.data.result.Count -eq 0) {
            return @("NO_DATA")
        }

        $results = @()

        foreach ($item in $json.data.result) {
            if ($item.metric.symbol) {
                $results += "$($item.metric.symbol)=$($item.value[1])"
            } else {
                $results += "$($item.value[1])"
            }
        }

        return $results
    } catch {
        return @("NO_DATA")
    }
}

function Print-Metric {
    param(
        [string]$Name,
        [string]$Query
    )

    $raw = Query-Prometheus $Query
    $result = Extract-Value $raw

    if (-not $result -or $result.Count -eq 0) {
        $result = @("NO_DATA")
    }

    Write-Output ("{0}: {1}" -f $Name, ($result -join ", "))
}

Print-Metric "place_order_tps" 'rate(place_order_tps_total[1m])'
Print-Metric "accepted_order_tps" 'rate(accepted_order_tps_total[1m])'
Print-Metric "cancel_order_tps" 'rate(cancel_order_tps_total[1m])'
Print-Metric "cancel_completed_tps" 'rate(cancel_completed_tps_total[1m])'
Print-Metric "cancel_rejected_tps" 'rate(cancel_rejected_tps_total[1m])'
Print-Metric "rejected_order_tps" 'rate(rejected_order_tps_total[1m])'
Print-Metric "trades_per_sec" 'rate(trades_per_sec_total[1m])'
Print-Metric "matched_orders_per_sec" 'rate(matched_orders_per_sec_total[1m])'
Print-Metric "queue_wait_latency_p95_ms" 'histogram_quantile(0.95, sum(rate(queue_wait_latency_seconds_bucket[1m])) by (le)) * 1000'
Print-Metric "engine_processing_latency_p95_ms" 'histogram_quantile(0.95, sum(rate(engine_processing_latency_seconds_bucket[1m])) by (le)) * 1000'
Print-Metric "order_accept_tx_latency_p95_ms" 'histogram_quantile(0.95, sum(rate(order_accept_tx_latency_seconds_bucket[1m])) by (le)) * 1000'
Print-Metric "engine_result_tx_latency_p95_ms" 'histogram_quantile(0.95, sum(rate(engine_result_tx_latency_seconds_bucket[1m])) by (le)) * 1000'
Print-Metric "end_to_end_order_latency_p95_ms" 'histogram_quantile(0.95, sum(rate(end_to_end_order_latency_seconds_bucket[1m])) by (le)) * 1000'
Print-Metric "end_to_end_cancel_latency_p95_ms" 'histogram_quantile(0.95, sum(rate(end_to_end_cancel_latency_seconds_bucket[1m])) by (le)) * 1000'
Print-Metric "db_lock_wait_time_p95_ms" 'histogram_quantile(0.95, sum(rate(db_lock_wait_time_seconds_bucket[1m])) by (le)) * 1000'
Print-Metric "engine_queue_depth" 'engine_queue_depth'
Print-Metric "engine_backpressure_count" 'rate(engine_backpressure_count_total[1m])'
Print-Metric "queue_full_rollback_count" 'rate(queue_full_rollback_count_total[1m])'
Print-Metric "balance_lock_contention_count" 'rate(balance_lock_contention_count_total[1m])'
Print-Metric "db_deadlock_count" 'rate(db_deadlock_count_total[5m])'
Print-Metric "db_commit_failure_count" 'rate(db_commit_failure_count_total[5m])'
Print-Metric "db_rollback_count" 'rate(db_rollback_count_total[5m])'
Print-Metric "idempotency_conflict_count" 'rate(idempotency_conflict_count_total[1m])'
Print-Metric "error_rate" 'rate(error_rate_total[1m])'
Print-Metric "replay_duration_on_startup_p95_ms" 'histogram_quantile(0.95, sum(rate(replay_duration_on_startup_seconds_bucket[5m])) by (le)) * 1000'
Print-Metric "replay_open_order_count" 'replay_open_order_count_total'
Print-Metric "replay_consistency_check" 'replay_consistency_check'
Print-Metric "replay_duration_by_symbol_p95_ms" 'histogram_quantile(0.95, sum(rate(replay_duration_by_symbol_seconds_bucket[5m])) by (le, symbol)) * 1000'