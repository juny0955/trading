# Metrics Scripts

핵심 Phase 4 지표를 Prometheus에서 한 번에 조회:

```bash
chmod +x scripts/metrics/check-phase4.sh
scripts/metrics/check-phase4.sh
```

Prometheus 주소가 다르면:

```bash
PROM_URL=http://localhost:9090 scripts/metrics/check-phase4.sh
```

출력 예시:

```text
place_order_tps: 123.4
accepted_order_tps: 120.1
queue_wait_latency_p95_ms: 45.6
engine_queue_depth: BTC=12
db_lock_wait_time_p95_ms: 8.7
```

`jq`가 설치되어 있으면 symbol tag가 있는 결과를 더 읽기 쉽게 출력한다.
