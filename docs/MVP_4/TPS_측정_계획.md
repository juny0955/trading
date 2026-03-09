# MVP Phase 4 TPS 측정 계획

---

# 1. 목적

Phase 4는 단순 엔진 로직이 아니라 다음 비용이 추가되는 구간이다.

* account 검증
* hold reserve / release
* DB 트랜잭션
* trade 영속화
* replay 가능한 상태 저장

따라서 Phase 4에서는 단순 `orders/sec`만 보지 않고,
**주문 처리량 + 체결 처리량 + 지연 시간 + DB/락 병목**을 함께 측정해야 한다.

이 문서의 목적은 다음과 같다.

1. Phase 4 구현 완료 직후 최소 성능 기준 확인
2. 병목이 엔진인지, DB인지, balance lock인지 분리
3. Phase 5 최적화(FOK 성능, 메트릭, outbox) 착수 전 기준선 확보

Phase 4는 KRW 정산 마켓만 지원한다. 계정 잔고는 KRW와 거래 심볼 자산 단위로 관리하며, 복수 quote 자산 및 환전은 측정 범위에 포함하지 않는다.

---

# 2. 측정 원칙

* TPS는 반드시 `latency`와 함께 본다
* 평균값보다 `p95`, `p99`를 우선 확인한다
* 전체 TPS보다 `심볼별`, `주문 타입별`, `계정 분포별` 편차를 같이 본다
* `queue depth`, `backpressure`, `DB lock wait`를 병목 지표로 함께 수집한다
* 동일 테스트는 최소 3회 반복해 중앙값을 기록한다

---

# 3. 핵심 측정 지표

## 3.1 주문 처리량

필수 지표:

* `place_order_tps`
* `cancel_order_tps`
* `cancel_completed_tps`
* `cancel_rejected_tps`
* `accepted_order_tps`
* `rejected_order_tps`

정의:

* `place_order_tps`
  - 전체 주문 요청 수신 기준 처리량
* `cancel_order_tps`
  - 전체 취소 요청 수신 기준 처리량
* `accepted_order_tps`
  - 잔고 검증 및 멱등성 검사를 통과해 주문 접수 트랜잭션이 커밋된 처리량
* `rejected_order_tps`
  - 검증 실패 / 잔고 부족 등 주문 접수 단계에서 거절된 처리량
  - `queue submit` 이후 발생한 backpressure 보상 롤백은 이 지표에 포함하지 않고 `queue_full_rollback_count`로 별도 측정한다
* `cancel_completed_tps`
  - 비동기 취소 요청 중 실제로 `CANCELLED` 상태가 DB에 반영된 처리량
* `cancel_rejected_tps`
  - 이미 `FILLED` / `CANCELLED` 된 주문 등으로 인해 취소가 거절된 처리량

분리 기준:

* symbol별
* account별 분포
* orderType별
  * `LIMIT`
  * `MARKET`
  * `IOC`
  * `FOK`
  * `MARKET BUY quoteQty`

의도:

* 엔진 큐 병목과 주문 타입별 비용 차이를 확인한다

---

## 3.2 체결 처리량

필수 지표:

* `trades_per_sec`
* `matched_orders_per_sec`
* 주문 1건당 평균 `trade count`
* 주문 1건당 평균 `maker touched count`

의도:

* 주문 TPS는 같아도 체결이 많이 발생하면 비용이 급증한다
* `MARKET BUY quoteQty`, `IOC`, `FOK`는 오더북 접근 비용 차이가 크다

---

## 3.3 지연 시간

필수 지표:

* `http_place_order_latency`
* `http_cancel_order_latency`
* `end_to_end_cancel_latency`
* `queue_wait_latency`
* `engine_processing_latency`
* `order_accept_tx_latency`
* `engine_result_tx_latency`
* `end_to_end_order_latency`

집계:

* `p50`
* `p95`
* `p99`

정의:

* `queue_wait_latency`
  - 주문이 queue에 들어간 시각 ~ engine thread가 꺼낸 시각
* `engine_processing_latency`
  - engine 처리 시작 ~ `PlaceResult` 생성 완료
* `order_accept_tx_latency`
  - hold reserve / order insert / idempotency insert / commit까지의 시간
* `engine_result_tx_latency`
  - orders update / trades insert / balances update / commit까지의 시간
* 파생 지표
  - `db_transaction_latency_total = order_accept_tx_latency + engine_result_tx_latency`
  - `db_transaction_latency_total`은 metrics registry에서 별도 측정하지 않고 파생 지표로 계산한다
* `end_to_end_order_latency`
  - 주문 요청 수신 시점부터 해당 주문의 최종 1차 처리 결과가 DB에 반영될 때까지의 시간
  - Phase 4에서 HTTP 응답은 queue submit 직후 반환된다. DB 반영은 엔진 스레드가 비동기로 처리한다.
  - 따라서 이 지표는 HTTP response time과 별도로 측정한다.
  - 최종 1차 처리 결과는 `NEW / PARTIALLY_FILLED / FILLED / CANCELLED / REJECTED`를 포함한다.
  - ACCEPTED는 중간 상태이므로 종료 조건에 포함하지 않는다
  - 수용 경로: `received_at -> final_state_db_committed_at`
  - 거절 경로: `received_at -> rejected_at`
  - 측정 방법: orderId 또는 request key 기준으로 단계별 timestamp를 기록한다.
* `end_to_end_cancel_latency`
  - 취소 요청 수신 시점부터 취소 결과(`CANCELLED`)가 DB에 반영될 때까지의 시간
  - `http_cancel_order_latency`와 분리해서 본다
  - 측정 방법: `cancel_received_at -> cancel_db_committed_at`
  - 최종 상태 검증은 `GET /accounts/{accountId}/orders/{orderId}` 조회 API 기준으로 확인한다

latency 계산을 위해 각 주문 처리 단계별 timestamp를 structured log 또는 metrics event로 명시적으로 기록한다.

권장 timestamp 필드:

* `received_at`
* `queued_at`
* `dequeued_at`
* `engine_done_at`
* `db_committed_at`
* `rejected_at`
* `cancel_received_at`
* `cancel_db_committed_at`

---

## 3.4 병목 / 안정성 지표

필수 지표:

* `engine_queue_depth`
* `engine_backpressure_count`
* `db_lock_wait_time`
* `balance_lock_contention_count`
* `db_deadlock_count`
* `db_commit_failure_count`
* `db_rollback_count`
* `queue_full_rollback_count`
* `idempotency_conflict_count`
* `error_rate`

의도:

* Phase 4 병목은 engine보다 DB 락/트랜잭션 경합일 가능성이 높다

---

## 3.5 재기동 복구 지표

필수 지표:

* `replay_duration_on_startup`
* `replay_open_order_count`
* `replay_duration_by_symbol`
* `replay_consistency_check`

의도:

* 온라인 처리 성능과 재기동 복구 성능을 분리해서 본다
* replay 시간이 길어지거나 replay 후 오더북과 DB가 어긋나는 문제를 별도 추적한다

---

# 4. 시나리오별 측정 항목

## 4.1 단일 심볼 / 단일 account 집중 부하

목적:

* 동일 account에서 특정 balance row 경합 및 lock wait 확인

입력 예:

* symbol 1개 (`BTC`)
* account 1개
* 초기 잔고: KRW 100,000,000 / BTC 10
* 초기 오더북 depth: bid 100레벨, ask 100레벨
* 주문 가격 분포: best bid/ask 기준 ±1~5 tick
* 주문 수량 분포: 1~10 랜덤
* LIMIT BUY / SELL 동시 혼합

확인 포인트:

* 주문 접수 단계에서는 주문 유형별로 단일 asset row만 lock한다
* 주된 관찰 대상은 deadlock보다 lock contention / lock wait이다

확인 지표:

* `place_order_tps`
* `db_lock_wait_time`
* `balance_lock_contention_count`
* `p95 end_to_end_order_latency`

---

## 4.2 단일 심볼 / 다수 account

목적:

* 순수 symbol 엔진 처리량과 체결 처리량 확인

입력 예:

* symbol 1개 (`BTC`)
* account 100개
* 초기 잔고: account당 KRW 100,000,000 / BTC 10
* 초기 오더북 depth: bid 100레벨, ask 100레벨
* maker/taker 비율: 7:3

확인 지표:

* `place_order_tps`
* `trades_per_sec`
* `queue_wait_latency`
* `engine_processing_latency`

---

## 4.3 다수 심볼 / 다수 account

목적:

* 심볼 증가 시 엔진/큐/DB 처리 편차와 확장 한계 확인

입력 예:

* `BTC`, `ETH`, `TEST` (모두 KRW 마켓)
* account 100개
* 초기 잔고: account당 KRW 100,000,000 / BTC 10 / ETH 100
* 초기 오더북 depth: symbol당 bid 100레벨, ask 100레벨
* symbol별 부하 비율 다르게 설정

확인 지표:

* symbol별 TPS
* symbol별 latency
* 전체 TPS 대비 편차
* 특정 symbol 편중 시 다른 symbol 영향

---

## 4.4 체결 적고 주문만 많은 경우

목적:

* order insert / hold reserve / cancel 위주의 비용 확인

입력 예:

* 가격이 어긋난 LIMIT 주문 다량 생성
* 체결 거의 없음
* account 100개
* 주문 수량 분포: 1~10 랜덤

확인 지표:

* `accepted_order_tps`
* `order_accept_tx_latency`
* `open_order_count`
* replay 대상 주문 수 증가에 따른 영향

---

## 4.5 체결이 매우 많은 경우

목적:

* trade insert + balance update 비용 확인

입력 예:

* MARKET 주문 다량
* 얕은 오더북에 taker 집중
* maker/taker 비율: 3:7

확인 지표:

* `trades_per_sec`
* 주문 1건당 평균 `trade count`
* `engine_result_tx_latency`
* `p95 engine_processing_latency`

---

## 4.6 취소 비율이 높은 경우

목적:

* hold release, 취소 정합성 비용 확인

입력 예:

* open order 생성 후 빠른 취소 반복
* account 100개
* open order 유지 시간: 100ms ~ 1s

확인 지표:

* `cancel_order_tps`
* 취소 요청 `p95 latency`
* `end_to_end_cancel_latency`
* `cancel_completed_tps`
* `queue_wait_latency` (취소도 엔진 큐 경유)
* hold release 후 balance 정합성

---

## 4.7 FOK / IOC 집중 부하

목적:

* 선형 스캔 기반 사전 충족성 검사 비용 확인

입력 예:

* 깊은 오더북
* FOK, IOC 주문 비중 높음
* 초기 오더북 depth: 1,000레벨 이상

확인 지표:

* `engine_processing_latency`
* `p95/p99 place_order_latency`
* FOK 실패율

---

## 4.8 MARKET BUY `quoteQty` 집중 부하

목적:

* 예산 기반 체결과 leftover hold 반환 비용 확인

입력 예:

* MARKET BUY `quoteQty` 비율 높음
* maker 다단계 체결 유도
* account 100개
* quoteQty 분포: 100,000 ~ 5,000,000 랜덤

확인 지표:

* `trades_per_sec`
* 주문 1건당 평균 `trade count`
* `engine_result_tx_latency`
* hold 반환 정합성

---

## 4.9 재기동 복구 시간

목적:

* open order 수 규모에 따른 replay 기동 시간 측정

입력 예:

* open order 1,000건 상태에서 서버 재시작
* open order 10,000건 상태에서 서버 재시작
* symbol 수 1개 / 다수 비교

확인 지표:

* `replay_duration_on_startup`
* `replay_duration_by_symbol`
* `replay_open_order_count`
* `replay_consistency_check` (재기동 후 오더북 depth/잔량/주문 상태 ↔ DB 일치 여부)

검증 제외 범위:

* 동일 가격 레벨 내 완전한 순서 재현은 Phase 4 측정 범위에 포함하지 않는다
* Phase 4는 오더북 집계 상태(depth/잔량)와 주문 상태의 일치를 기준으로 검증한다

엣지 케이스 — ACCEPTED 상태 잔존 주문:

기획서 리스크: commit 직후 ~ queue submit 전 프로세스 종료 시 ACCEPTED 상태 order가 DB에 잔존할 수 있음.

검증 조건:

* 시작 전 DB에 status = ACCEPTED 주문을 수동으로 삽입
* 재시작 후 해당 주문이 replay 대상에서 제외되는지 확인
* 재시작 후 ACCEPTED → CANCELLED 로 전환되는지 확인
* hold가 정상 복구되는지 확인
* `idempotency_keys`에서 해당 key가 삭제되는지 확인

Phase 4 권장 처리:

* ACCEPTED 상태는 엔진이 아직 수용하지 않은 주문이므로 replay 대상에서 제외
* 재시작 시 ACCEPTED → CANCELLED 로 일괄 전환
* 확인 지표:
  - 재시작 후 ACCEPTED 잔존 주문 수 = 0
  - orphan 주문의 hold 복구 완료
  - orphan 주문의 idempotency key 삭제 완료

---

## 4.10 멱등성 동시 요청 부하

목적:

* 동일 `(accountId, clientOrderId)`로 동시 요청 시 중복 주문 방지 검증

입력 예:

* account 10개
* 각 account에서 동일 `clientOrderId`로 50개 동시 요청
* symbol 1개 (`BTC`)

확인 지표:

* `idempotency_conflict_count` (충돌 발생 수)
* 생성된 order 수 = 요청 account 수 (중복 없음)
* `place_order_tps`
* `db_lock_wait_time`

---

## 4.11 Queue Submit 실패 보상 롤백 검증

목적:

* 엔진 큐를 의도적으로 포화시켜 보상 롤백 경로의 정확성 검증

입력 예:

* 엔진 큐 용량을 테스트 전 최솟값으로 설정 (예: queue capacity = 10)
* 대량 주문 요청으로 큐 포화 유발
* 큐 full 상태에서 신규 주문 요청 송신

확인 지표:

* `queue_full_rollback_count` (보상 롤백 발생 수)
* 롤백 후 잔고 = 롤백 전 잔고 (hold 복구 검증)
* 롤백된 order_id가 orders 테이블에 존재하지 않음
* 롤백된 (accountId, clientOrderId)로 재시도 시 정상 주문 생성 성공
* `idempotency_keys` 테이블에 해당 key가 삭제됨

---

## 4.12 다중 자산 lock 데드락 검증

목적:

* `engine_result_tx` 단계의 balance update에서 데드락 미발생 검증
* lock 순서 고정 정책(KRW + 주문 심볼 자산 기준 정렬)이 실제로 동작하는지 확인

입력 예:

* buy account 1개, sell account 1개
* symbol 1개 (`BTC`, KRW 마켓)
* trade settlement가 지속적으로 발생하도록 BUY/SELL 체결 유도
* settlement 시
  - BUY 측: KRW 감소, BTC 증가
  - SELL 측: BTC 감소, KRW 증가
* 두 account의 balance update가 교차 lock 조건을 만들 수 있는 부하 구성

데드락 방지 조건:

* Persistence Layer에서 KRW + 주문 심볼 자산 balance row lock 순서를 asset 이름 오름차순으로 고정
* `BTC` → `KRW` 순서로 항상 lock (알파벳 순)

확인 지표:

* `db_deadlock_count` = 0 (lock 순서 고정 시 반드시 0)
* `balance_lock_contention_count` (경합 발생 수, 데드락 없이 직렬화됨)
* `db_lock_wait_time`
* `p95 engine_result_tx_latency`
* `trades_per_sec`

---

# 5. 수집 포인트

## 5.1 애플리케이션 레벨

최소 계측 위치:

* `OrderController.placeOrder()`
* `OrderController.cancelOrder()`
* `OrderCommandService.placeOrder()`
* queue submit 직전 / 직후
* `EngineLoop` dequeue 시점
* `EngineHandler.handle()` 시작 / 종료
* order_accept_tx 시작 / commit  (주문 접수 단계)
* engine_result_tx 시작 / commit (엔진 결과 반영 단계)
* replay 시작 / 종료

기록 방법:

* structured log 또는 metrics registry
* traceId, accountId, symbol, orderType, tif 포함
* `received_at`, `queued_at`, `dequeued_at`, `engine_done_at`, `db_committed_at` 기록

---

## 5.2 DB 레벨

필수 수집:

* transaction duration
* lock wait duration
* deadlock count
* commit failure count
* rollback count
* row update count
* trade insert count
* idempotency conflict count

집중 테이블:

* `orders`
* `trades`
* `balances`
* `idempotency_keys`

---

# 6. 결과 기록 형식

각 시나리오는 아래 형식으로 기록한다.

```
Scenario:
Environment:
Symbols:
Accounts:
Order Mix:
Warm-up:    (권장: 30s)
Measure:    (권장: 120s)
Cooldown:   (권장: 10s)

TPS:
- place_order_tps
- cancel_order_tps
- cancel_completed_tps
- cancel_rejected_tps
- trades_per_sec

Latency:
- p50 / p95 / p99 place_order
- p50 / p95 / p99 queue_wait
- p50 / p95 / p99 engine_processing
- p50 / p95 / p99 order_accept_tx
- p50 / p95 / p99 engine_result_tx
- p50 / p95 / p99 end_to_end_order
- p50 / p95 / p99 end_to_end_cancel

Stability:
- backpressure_count
- deadlock_count
- db_commit_failure_count
- db_rollback_count
- queue_full_rollback_count
- lock_wait_time
- idempotency_conflict_count
- error_rate

Replay:
- replay_duration_on_startup
- replay_open_order_count
- replay_duration_by_symbol
- replay_consistency_check (depth/잔량/주문 상태 기준)

Notes:
- 병목 추정 원인
- 정합성 이슈 여부
```

---

# 7. Phase 4 최소 통과 기준

Phase 4에서는 절대 성능 수치보다 **정합성을 유지한 상태에서 병목을 설명할 수 있는지**가 더 중요하다.

최소 통과 기준:

* 모든 시나리오에서 잔고/hold/order/trade 정합성 오류가 0건
* 동일 `(accountId, clientOrderId)` 재시도 시 중복 주문 0건
* queue submit 실패 시 주문 / hold / idempotency가 모두 정리될 것
* replay 이후 오더북과 DB 불일치 0건
* replay 이후 open order로부터 재계산한 held 합계와 `balances.held` 합계가 정합할 것
* 단일 시나리오 결과가 아니라 최소 3회 반복 결과 중앙값 확보
* 각 시나리오별 병목 지점이 설명 가능할 것

권장 확인 기준:

* `p95 end_to_end_order_latency`
* `p95 order_accept_tx_latency`
* `p95 engine_result_tx_latency`
* `engine_backpressure_count`
* `balance_lock_contention_count`
* `db_rollback_count`
* `idempotency_conflict_count`
* `replay_duration_on_startup`

## 정합성 검증 불변식

각 시나리오 종료 후 아래 불변식을 DB 쿼리로 확인한다.

**잔고 불변식**:

```sql
-- 각 (account_id, asset)에 대해:
-- available + held == 초기 잔고 + 순입금 - 순출금

SELECT account_id, asset,
       SUM(available + held) AS total
FROM balances
GROUP BY account_id, asset;
-- → 초기값 대비 체결 순변동과 일치해야 함
```

**trade 기반 잔고 검증**:

```sql
-- BUY 체결: buyer의 KRW held 감소 == SUM(trade.price * trade.quantity)
-- SELL 체결: seller의 주문 심볼 자산 held 감소 == SUM(trade.quantity)
-- trade 기반 검증은 체결 delta만 검증한다.
-- 최종 balance 정합성은 trade delta + hold 반환 delta를 함께 고려해야 한다.
-- 예: LIMIT BUY 가격개선 차액 반환, MARKET BUY 미사용 예산 반환, 취소 시 잔여 hold 반환
```

**hold 불변식**:

* 종료된 주문(FILLED / CANCELLED)의 잔여 hold = 0
* `SUM(held) >= 0` for all accounts

**order 상태 불변식**:

* `remaining == 0` for all FILLED orders
* 체결 없는 취소(`cum_base_qty == 0`)는 `remaining == quantity`
* 부분체결 후 취소는 `0 < remaining < quantity` 가능
* ACCEPTED 상태 주문 = 0 (재시작 후)

권장 성능 관찰 기준:

* 단일 심볼 / 다수 account 시나리오에서 backpressure 없이 안정적으로 처리 가능할 것
* `p95 order_accept_tx_latency`와 `p95 engine_result_tx_latency`가 장시간 비정상적으로 증가하지 않을 것
* `replay_duration_on_startup`이 open order 수 증가에 따라 선형적으로 증가하는지 확인할 것

---

# 8. Phase 4 종료 전 반드시 남길 산출물

* 시나리오별 TPS 결과표
* 병목 분석 메모
* DB 락 경합 분석 결과
* replay 시간 측정 결과
* Phase 5 최적화 후보 목록

---

# 9. Phase 5로 넘길 최적화 후보 예시

* FOK pre-check 성능 최적화
* balance lock 범위 축소
* trade bulk insert 검토
* symbol별 queue / worker 구조 재검토
* outbox 추가 후 트랜잭션 비용 재측정
