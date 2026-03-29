# MVP4 MARKET BUY quoteQty TPS 측정 보고서

작성 시각: 2026-03-28T15:22:18Z

## 1. 목적

`docs/MVP_4/TPS_측정_계획.md`의 4.8 시나리오 기준으로, `quoteQty` 기반 MARKET BUY 주문의 처리량과 다단계 체결 특성을 확인했다.

이번 측정의 목적은 다음과 같다.

1. `quoteQty` 기반 BUY 주문이 실제로 여러 SELL 레벨을 소모하는지 확인한다.
2. `MARKET BUY quoteQty` 경로의 안정 처리량을 확인한다.
3. 일반 MARKET 주문 및 다른 체결 시나리오와 비교해 처리 특성을 정리한다.

## 2. 측정 조건

- 시나리오: `scripts/k6/scenarios/market-buy-quote-qty.js`
- 심볼: `BTC`
- account 분포: seed account 사용
- VU: `5`
- 측정 window: `300s`
- 비교 변수: `SLEEP_SECONDS`
- 전제 조건: `book-seed.js`로 SELL 오더북을 충분히 선적재
- `quoteQty` 범위: `100000 ~ 5000000 KRW`

주의:

- 현재 구현은 소수점 거래를 지원하지 않으므로, SELL 가격이 `quoteQty`보다 높으면 체결이 시작되지 않는다.
- 따라서 `book-seed.js`로 SELL 가격대를 `quoteQty` 범위 안쪽으로 맞춰서 적재해야 한다.

## 3. 측정 결과

### 3.1 결과 요약

| `SLEEP_SECONDS` | measure 구간 | `accepted_order_tps` | `trades_per_sec` | `matched_orders_per_sec` | `queue_wait_latency_p95_ms` | `end_to_end_order_latency_p95_ms` | `engine_queue_depth` | 상태 |
|---|---|---:|---:|---:|---:|---:|---|---|
| `0.050` | `2026-03-28T14:38:18.272Z ~ 2026-03-28T14:43:18.272Z` | 88.14 | 91.64 | 179.64 | 31.71 | 42.60 | `BTC=3` | 안정 |
| `0.030` | `2026-03-28T14:59:50.866Z ~ 2026-03-28T15:04:50.866Z` | 141.47 | 145.69 | 286.86 | 23.09 | 32.45 | `BTC=0` | 안정 |
| `0.020` | `2026-03-28T15:15:29.014Z ~ 2026-03-28T15:20:29.014Z` | 199.48 | 193.89 | 382.31 | 14875.04 | 14875.25 | `BTC=3065` | 불안정 |

### 3.2 보조 지표

| `SLEEP_SECONDS` | `engine_processing_latency_p95_ms` | `order_accept_tx_latency_p95_ms` | `engine_result_tx_latency_p95_ms` | `db_lock_wait_time_p95_ms` | `balance_lock_contention_count` |
|---|---:|---:|---:|---:|---:|
| `0.050` | 10.49 | 3.14 | 5.34 | 0.99 | 350.39 |
| `0.030` | 7.41 | 2.62 | 4.21 | 0.98 | 562.23 |
| `0.020` | 7.84 | 2.66 | 5.16 | 1.15 | 751.69 |

공통적으로 아래 항목은 문제 없이 유지되었다.

- `queue_full_rollback_count = 0`
- `engine_backpressure_count = 0`
- `error_rate = 0`
- `db_deadlock_count = 0`
- `db_commit_failure_count = 0`
- `db_rollback_count = 0`
- `idempotency_conflict_count = 0`

## 4. 해석

### 4.1 `SLEEP_SECONDS=0.050`

- `accepted_order_tps=88.14`
- `trades_per_sec=91.64`
- `matched_orders_per_sec=179.64`
- 큐 적체 없이 안정적이었다.

이미 `trades_per_sec > accepted_order_tps`가 나타나 주문 1건당 평균 trade 수가 1보다 큰 다단계 체결이 발생했다.

### 4.2 `SLEEP_SECONDS=0.030`

- `accepted_order_tps=141.47`
- `trades_per_sec=145.69`
- `matched_orders_per_sec=286.86`
- `queue_wait_latency_p95_ms=23.09`
- `end_to_end_order_latency_p95_ms=32.45`
- `engine_queue_depth=0`

이번 측정 기준 가장 높은 안정 처리량 구간이다. quoteQty 기반 BUY가 여러 SELL 레벨을 소모하며 실제로 다단계 체결을 일으키고 있다.

### 4.3 `SLEEP_SECONDS=0.020`

- 처리량은 `199.48 TPS`까지 올라갔다.
- 하지만 `queue_wait_latency_p95_ms=14.88s`, `end_to_end_order_latency_p95_ms=14.88s`, `engine_queue_depth=3065`로 불안정 구간에 진입했다.
- 롤백은 없지만 안정 구간으로 볼 수 없다.

## 5. 다른 시나리오와 비교

| 조건 | `accepted_order_tps` | `trades_per_sec` | `queue_wait_latency_p95_ms` | `end_to_end_order_latency_p95_ms` | 해석 |
|---|---:|---:|---:|---:|---|
| 일반 MARKET 주문 | 76.35 | 76.35 | 22.89 | 32.45 | 안정 |
| MARKET BUY quoteQty | 141.47 | 145.69 | 23.09 | 32.45 | 안정 |

관찰 내용:

- 현재 실험 조건에서는 `MARKET BUY quoteQty`가 일반 MARKET 주문보다 더 높은 안정 처리량을 보였다.
- 일반 MARKET 주문은 주문당 trade 수가 상대적으로 단순한 편이었고, `quoteQty` 시나리오는 `trades_per_sec > accepted_order_tps`로 다단계 체결이 더 분명하게 나타났다.
- 즉 이번 조건에서는 `quoteQty` 경로가 실제로 여러 SELL 레벨을 소모하며 작동하고 있었다.

## 6. 결론

이번 실험 기준 `MARKET BUY quoteQty` 시나리오의 결과는 다음과 같이 정리한다.

- 안정 처리량: 약 `141 TPS`
- 불안정 경계: 약 `199 TPS`
- 특징: `trades_per_sec > accepted_order_tps`, `matched_orders_per_sec ≈ 2 * trades_per_sec`

보고서용 결론 문장:

> SELL 오더북을 충분히 선적재한 조건에서 `MARKET BUY quoteQty` 주문은 sleep=0.03 기준 약 141 TPS를 큐 적체 없이 안정 처리했다. 이 구간에서는 trades_per_sec가 accepted_order_tps를 상회해 주문 1건당 다수 체결이 지속적으로 발생했으며, `quoteQty` 기반 BUY가 실제로 여러 SELL 레벨을 소모하는 특성이 확인되었다. sleep=0.02에서는 처리량이 약 199 TPS까지 증가했지만 p95 지연이 약 14.9초까지 올라 불안정 구간에 진입했다.

## 7. 시사점

- `MARKET BUY quoteQty` 시나리오는 seed된 SELL book의 가격대와 두께에 매우 민감하다.
- 소수점 거래가 없는 현재 구현에서는 SELL 가격이 `quoteQty` 범위보다 높으면 체결이 시작되지 않는다.
- 이후에는 `quoteQty` 범위를 더 세분화해 작은 금액대와 큰 금액대를 분리 측정하면 다단계 체결 비용을 더 정밀하게 비교할 수 있다.
