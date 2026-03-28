# MVP4 MARKET 주문 체결 집중 TPS 측정 보고서

작성 시각: 2026-03-28T09:44:43Z

## 1. 목적

`docs/MVP_4/TPS_측정_계획.md`의 4.5 시나리오 기준으로, MARKET 주문을 통해 체결을 적극적으로 발생시키는 조건에서 처리량과 지연 시간을 확인했다.

이번 측정의 목적은 다음과 같다.

1. 실제 체결이 지속적으로 발생하는 MARKET 주문 조건에서 안정 처리량을 확인한다.
2. 체결 최소화 시나리오 및 일반 단일 심볼 시나리오와 비교해 trade 생성/후처리 비용 영향을 확인한다.

## 2. 측정 조건

- 시나리오: `scripts/k6/scenarios/place-market-order.js`
- 심볼: `BTC`
- account 분포: seed account 사용
- VU: `5`
- 측정 window: `300s`
- 비교 변수: `SLEEP_SECONDS`
- 전제 조건: MARKET 실행 전에 `book-seed.js` 등으로 반대편 오더북 depth를 충분히 선적재

주의:

- MARKET 주문 시나리오는 오더북 반대편 depth가 충분해야 실제 체결이 많이 발생한다.
- 오더북이 비어 있거나 얕으면 MARKET 주문이어도 `trades_per_sec`가 0으로 나올 수 있다.

## 3. 측정 결과

### 3.1 결과 요약

| `SLEEP_SECONDS` | measure 구간 | `place_order_tps` | `accepted_order_tps` | `trades_per_sec` | `matched_orders_per_sec` | `queue_wait_latency_p95_ms` | `end_to_end_order_latency_p95_ms` | `engine_queue_depth` | 해석 |
|---|---|---:|---:|---:|---:|---:|---:|---|---|
| `0.100` | `2026-03-28T08:55:27.756Z ~ 2026-03-28T09:00:27.756Z` | 23.69 | 23.69 | 23.70 | 47.40 | 39.73 | 55.24 | `BTC=0` | 안정 구간 |
| `0.050` | `2026-03-28T09:12:39.446Z ~ 2026-03-28T09:17:39.446Z` | 46.61 | 46.61 | 46.61 | 93.21 | 25.71 | 36.84 | `BTC=2` | 안정 구간 |
| `0.030` | `2026-03-28T09:35:01.775Z ~ 2026-03-28T09:40:01.775Z` | 76.36 | 76.35 | 76.35 | 152.71 | 22.89 | 32.45 | `BTC=0` | 안정 구간 |
| `0.020` | `2026-03-28T09:24:35.227Z ~ 2026-03-28T09:29:35.227Z` | 110.15 | 110.15 | 110.14 | 220.28 | 2095.10 | 2114.40 | `BTC=4` | 롤백은 없지만 지연 급증, 불안정 |

### 3.2 보조 지표

| `SLEEP_SECONDS` | `engine_processing_latency_p95_ms` | `order_accept_tx_latency_p95_ms` | `engine_result_tx_latency_p95_ms` | `db_lock_wait_time_p95_ms` | `balance_lock_contention_count` |
|---|---:|---:|---:|---:|---:|
| `0.100` | 27.99 | 4.01 | 8.96 | 1.68 | 94.32 |
| `0.050` | 15.14 | 2.62 | 6.03 | 1.22 | 185.48 |
| `0.030` | 11.38 | 2.33 | 5.08 | 0.99 | 303.85 |
| `0.020` | 12.20 | 2.33 | 5.36 | 1.17 | 438.26 |

공통적으로 아래 항목은 문제 없이 유지되었다.

- `error_rate = 0`
- `queue_full_rollback_count = 0`
- `engine_backpressure_count = 0`
- `db_deadlock_count = 0`
- `db_commit_failure_count = 0`
- `db_rollback_count = 0`
- `idempotency_conflict_count = 0`

## 4. 해석

### 4.1 `SLEEP_SECONDS=0.100`

- 실제 체결이 발생했다. `trades_per_sec=23.70`, `matched_orders_per_sec=47.40`
- 큐 적체 없이 안정적이지만 처리량은 낮다.

### 4.2 `SLEEP_SECONDS=0.050`

- `accepted_order_tps=46.61`
- `trades_per_sec=46.61`
- `engine_queue_depth: BTC=2`
- p95 지연도 낮아 안정 구간이다.

### 4.3 `SLEEP_SECONDS=0.030`

- `accepted_order_tps=76.35`
- `trades_per_sec=76.35`
- `matched_orders_per_sec=152.71`
- `queue_wait_latency_p95_ms=22.89`
- `end_to_end_order_latency_p95_ms=32.45`
- `engine_queue_depth: BTC=0`

이번 측정 기준 가장 높은 안정 처리량 구간이다. 거의 모든 주문이 즉시 체결되고, 큐 적체도 없다.

### 4.4 `SLEEP_SECONDS=0.020`

- 처리량은 `110.15 TPS`까지 올라갔다.
- 하지만 `queue_wait_latency_p95_ms=2095.10`, `end_to_end_order_latency_p95_ms=2114.40`으로 지연이 급증했다.
- `queue_full_rollback_count`는 0이지만, 안정 구간으로 보기는 어렵다.

## 5. 다른 시나리오와 비교

| 조건 | `accepted_order_tps` | `trades_per_sec` | `queue_wait_latency_p95_ms` | `end_to_end_order_latency_p95_ms` | 해석 |
|---|---:|---:|---:|---:|---|
| 단일 심볼 다중 계좌 LIMIT | 66.42 | 38.56 | 0.97 | 15.11 | 안정 |
| 체결 최소화 주문 집중 | 152.67 | 0.00 | 27.97 | 39.07 | 안정 후보 |
| MARKET 체결 집중 | 76.35 | 76.35 | 22.89 | 32.45 | 안정 |

관찰 내용:

- 체결 최소화 시나리오가 가장 높은 처리량을 보였다.
- MARKET 체결 집중 시나리오는 일반 LIMIT 단일 심볼보다 약간 높은 TPS를 보였지만, 실제 체결이 거의 모든 주문에서 발생했다는 점이 다르다.
- `trades_per_sec=76.35`는 이번 MARKET 시나리오의 핵심 지표다. 일반 LIMIT 시나리오보다 체결 자체가 훨씬 강하게 발생했다.
- 따라서 현재 조건에서는 체결 생성과 후처리 비용이 TPS를 제한하지만, 충분한 seed book이 있으면 약 `76 TPS`까지는 안정적으로 처리 가능했다.

## 6. 결론

이번 실험 기준 MARKET 주문 체결 집중 시나리오의 결과는 다음과 같이 정리한다.

- 안정 처리량: 약 `76 TPS`
- 포화 직전/불안정 구간: 약 `110 TPS`
- 특징: `trades_per_sec ≈ accepted_order_tps`, `matched_orders_per_sec ≈ accepted_order_tps * 2`

보고서용 결론 문장:

> 충분한 오더북 depth를 선적재한 MARKET 주문 시나리오에서는 sleep=0.03 조건에서 약 76 TPS를 큐 적체 없이 안정 처리했으며, 거의 모든 주문이 즉시 체결되었다. sleep=0.02 조건에서는 약 110 TPS까지 수용되었지만 p95 지연이 약 2.1초까지 증가해 안정 구간으로 보기 어려웠다.

## 7. 시사점

- MARKET 체결 집중 시나리오의 유효성은 오더북 seed 상태에 크게 의존한다.
- 안정 처리량 자체는 체결 최소화 시나리오보다 낮아, trade 생성과 체결 후처리 비용 영향이 확인된다.
- 다음 단계에서는 `MARKET_SIDE` 제어 또는 seed 전략 고정으로 재현성을 높이는 것이 좋다.
