# MVP4 FOK / IOC 집중 TPS 측정 보고서

작성 시각: 2026-03-28T14:20:04Z

## 1. 목적

`docs/MVP_4/TPS_측정_계획.md`의 4.7 시나리오 기준으로, FOK/IOC 주문의 처리량과 지연 시간을 확인했다.

이번 측정의 목적은 다음과 같다.

1. FOK/IOC 혼합 조건에서 안정 처리량을 확인한다.
2. FOK_ONLY 조건을 별도로 측정해 혼합 시나리오와 차이가 있는지 확인한다.
3. 충분한 seed book이 있을 때 FOK 사전 충족성 검사 비용이 처리량에 어떤 영향을 주는지 확인한다.

## 2. 측정 조건

- 시나리오: `scripts/k6/scenarios/fok-ioc-heavy.js`
- 심볼: `BTC`
- account 분포: seed account 사용
- VU: `5`
- 측정 window: `300s`
- 비교 변수: `SLEEP_SECONDS`, `ORDER_TYPE`
- 전제 조건: `book-seed.js` 등으로 반대편 오더북 depth를 충분히 선적재

주의:

- 본 시나리오가 유효하려면 FOK/IOC 주문을 실제로 소화할 수 있는 반대편 depth가 필요하다.
- seed book이 얕으면 FOK 실패율이 과도하게 높아지거나, 체결이 거의 없어질 수 있다.

## 3. 측정 결과

### 3.1 FOK/IOC 혼합 (`ORDER_TYPE=MIXED`)

| `SLEEP_SECONDS` | measure 구간 | `accepted_order_tps` | `trades_per_sec` | `matched_orders_per_sec` | `queue_wait_latency_p95_ms` | `end_to_end_order_latency_p95_ms` | `engine_queue_depth` | 상태 |
|---|---|---:|---:|---:|---:|---:|---|---|
| `0.050` | `2026-03-28T13:16:04.990Z ~ 2026-03-28T13:21:04.990Z` | 87.62 | 43.52 | 86.71 | 31.23 | 42.80 | `BTC=0` | 안정 |
| `0.030` | `2026-03-28T13:48:10.530Z ~ 2026-03-28T13:53:10.530Z` | 140.73 | 69.86 | 139.27 | 44.96 | 54.87 | `BTC=3` | 안정 |
| `0.020` | `2026-03-28T13:28:28.640Z ~ 2026-03-28T13:33:28.640Z` | 199.67 | 93.94 | 187.27 | 15606.50 | 15618.99 | `BTC=3297` | 불안정 |

### 3.2 FOK_ONLY (`ORDER_TYPE=FOK`)

| `SLEEP_SECONDS` | measure 구간 | `accepted_order_tps` | `trades_per_sec` | `matched_orders_per_sec` | `queue_wait_latency_p95_ms` | `end_to_end_order_latency_p95_ms` | `engine_queue_depth` | 상태 |
|---|---|---:|---:|---:|---:|---:|---|---|
| `0.030` | `2026-03-28T14:00:51.448Z ~ 2026-03-28T14:05:51.448Z` | 139.96 | 69.80 | 139.44 | 38.48 | 49.77 | `BTC=3` | 안정 |
| `0.020` | `2026-03-28T14:12:28.583Z ~ 2026-03-28T14:17:28.583Z` | 199.38 | 99.04 | 197.91 | 2456.96 | 2457.44 | `BTC=491` | 불안정 |

### 3.3 보조 지표

| 조건 | `engine_processing_latency_p95_ms` | `order_accept_tx_latency_p95_ms` | `engine_result_tx_latency_p95_ms` | `db_lock_wait_time_p95_ms` | `balance_lock_contention_count` |
|---|---:|---:|---:|---:|---:|
| MIXED `0.050` | 10.91 | 4.12 | 5.13 | 1.15 | 216.33 |
| MIXED `0.030` | 9.54 | 3.01 | 4.26 | 1.00 | 347.52 |
| MIXED `0.020` | 8.54 | 2.68 | 4.45 | 1.16 | 466.83 |
| FOK `0.030` | 9.67 | 3.01 | 4.55 | 1.02 | 347.57 |
| FOK `0.020` | 8.75 | 2.68 | 4.52 | 1.17 | 492.34 |

공통적으로 아래 항목은 문제 없이 유지되었다.

- `queue_full_rollback_count = 0`
- `engine_backpressure_count = 0`
- `error_rate = 0`
- `db_deadlock_count = 0`
- `db_commit_failure_count = 0`
- `db_rollback_count = 0`
- `idempotency_conflict_count = 0`

## 4. 해석

### 4.1 MIXED 시나리오

- `sleep=0.03`에서 `accepted_order_tps=140.73`, `trades_per_sec=69.86`, `engine_queue_depth=3`으로 안정적이었다.
- `sleep=0.02`에서는 처리량이 약 `199.67 TPS`까지 올라갔지만, p95 queue wait와 end-to-end latency가 약 `15.6s`까지 증가했다.
- 따라서 MIXED 기준 안정 처리량은 약 `141 TPS`, 불안정 경계는 약 `200 TPS`로 본다.

### 4.2 FOK_ONLY 시나리오

- `sleep=0.03`에서 `accepted_order_tps=139.96`, `trades_per_sec=69.80`, `engine_queue_depth=3`으로 안정적이었다.
- `sleep=0.02`에서는 `accepted_order_tps=199.38`까지 올라갔지만, p95 latency가 약 `2.46s`까지 증가했고 `engine_queue_depth=491`이 누적되었다.
- 따라서 FOK_ONLY 기준 안정 처리량도 약 `140 TPS`, 불안정 경계는 약 `199 TPS`로 본다.

### 4.3 핵심 비교

- MIXED와 FOK_ONLY의 안정 처리량 차이는 거의 없었다.
- `sleep=0.03` 기준
  - MIXED: `140.73 TPS`
  - FOK_ONLY: `139.96 TPS`
- 즉 현재 seed book 조건에서는 FOK 사전 충족성 검사가 처리량을 눈에 띄게 더 낮추지는 않았다.
- 두 조건 모두 체결이 지속적으로 발생했으며, `matched_orders_per_sec`는 `accepted_order_tps`와 거의 같은 수준이었다.

## 5. 결론

이번 실험 기준 FOK/IOC 집중 시나리오의 결과는 다음과 같이 정리한다.

- MIXED 안정 처리량: 약 `141 TPS`
- FOK_ONLY 안정 처리량: 약 `140 TPS`
- MIXED 불안정 경계: 약 `200 TPS`
- FOK_ONLY 불안정 경계: 약 `199 TPS`

보고서용 결론 문장:

> 충분한 오더북 depth를 선적재한 조건에서 FOK/IOC 혼합 주문은 약 141 TPS를, FOK 전용 주문은 약 140 TPS를 큐 적체 없이 안정 처리했다. 두 조건의 안정 처리량 차이는 거의 없었으며, 현재 실험 범위에서는 FOK 사전 충족성 검사가 처리량을 추가로 크게 낮추지는 않았다. 다만 sleep=0.02 구간에서는 두 조건 모두 지연이 초 단위로 증가해 불안정 구간에 진입했다.

## 6. 시사점

- FOK/IOC 시나리오의 유효성은 충분한 seed book에 크게 의존한다.
- 현재 조건에서는 FOK_ONLY도 실제 체결이 활발히 발생해, 단순 실패 경로보다 충족 후 체결 경로의 비용이 더 크게 반영되었다.
- 이후 IOC_ONLY를 별도로 측정하면 MIXED, FOK_ONLY, IOC_ONLY를 3-way 비교할 수 있다.
