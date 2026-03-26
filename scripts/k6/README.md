# k6 시나리오

TPS 측정 계획(docs/MVP_4/TPS_측정_계획.md) 기준 시나리오 목록이다.

---

## 시나리오 목록

| 시나리오 파일 | 측정 계획 | 주요 목적 |
|---|---|---|
| `place-single-symbol.js` | 4.1, 4.2 | 단일 심볼 주문 처리량 및 큐 병목 확인 |
| `place-multi-symbol.js` | 4.3 | 심볼 분산 효과 및 확장성 확인 |
| `place-no-fill.js` | 4.4 | 체결 없는 주문 insert / hold reserve 비용 측정 |
| `place-market-order.js` | 4.5 | MARKET 주문으로 체결 극대화, trade 처리 비용 측정 |
| `cancel-heavy.js` | 4.6 | 취소 비율 집중, hold release 및 cancel latency 측정 |
| `fok-ioc-heavy.js` | 4.7 | FOK/IOC 집중, engine_processing_latency 및 FOK 실패율 측정 |
| `market-buy-quote-qty.js` | 4.8 | quoteQty 기반 매수, 다단계 체결 및 leftover hold 반환 비용 측정 |
| `idempotency-concurrent.js` | 4.10 | 동일 clientOrderId 동시 요청, 중복 주문 0건 검증 |

4.9 (재기동 복구), 4.11 (Queue rollback), 4.12 (데드락)은 아래 수동 절차 참고.

---

## 실행 예시

> `ACCOUNT_IDS` 기본값은 seed 데이터 100개 계정(`00000000-0000-0000-0000-000000000001` ~ `000000000100`)이므로 생략 가능하다.
> 모든 명령은 `-e` 플래그를 사용하므로 Windows / macOS / Linux에서 동일하게 동작한다.

### 공통 실행 phase

duration 기반 시나리오는 기본적으로 `warmup -> measure -> cooldown` 구조를 지원한다.

- `WARMUP_DURATION`: 워밍업 구간. 기본값 `0s`
- `MEASURE_DURATION`: 측정 구간. 미지정 시 `DURATION` 값을 사용하고, 둘 다 없으면 시나리오 기본값 `1m`
- `COOLDOWN_DURATION`: 쿨다운 구간. 기본값 `0s`
- `DURATION`: 하위 호환용 측정 구간 길이
- `GRACEFUL_RAMP_DOWN`: cooldown 종료 후 in-flight 요청 완료 대기 시간. 기본값 `5s`

> **threshold 주의**: k6 threshold는 warmup + measure + cooldown 전체 구간에 걸쳐 집계된다.
> warmup 중 시스템이 아직 안정화되지 않은 상태의 latency가 p(95) 등의 지표에 포함된다.
> 정밀한 측정이 필요한 경우 `WARMUP_DURATION=0s`로 설정하거나 threshold 기준값에 여유를 두도록 한다.

> **SLEEP_SECONDS 주의**: `SLEEP_SECONDS`는 warmup · measure · cooldown 모든 구간에 동일하게 적용된다.
> sleep이 설정된 경우 warmup 단계의 실제 ramp 속도가 `WARMUP_DURATION`만으로 예측한 것보다 느려질 수 있다.

예시:

```bash
k6 run -e VUS=20 -e WARMUP_DURATION=30s -e MEASURE_DURATION=2m -e COOLDOWN_DURATION=10s \
  scripts/k6/scenarios/place-single-symbol.js
```

### 4.2 단일 심볼 / 다수 account

```bash
k6.exe run -e SYMBOL=BTC -e VUS=2 -e SLEEP_SECONDS=0.001 -e SCENARIO_NAME=place-single-symbol scripts/k6/scenarios/place-single-symbol.js
```

### 4.1 단일 심볼 / 단일 account (ACCOUNT_IDS를 1개만 지정)

```bash
k6 run -e SYMBOL=BTC -e VUS=1 -e SLEEP_SECONDS=0.002 -e SCENARIO_NAME=place-single-symbol \
  -e ACCOUNT_IDS=00000000-0000-0000-0000-000000000001 \
  scripts/k6/scenarios/place-single-symbol.js
```

### 4.3 다중 심볼 / 다수 account

```bash
k6 run -e SYMBOLS=BTC,ETH,TEST -e VUS=2 -e SLEEP_SECONDS=0.001 -e SCENARIO_NAME=place-multi-symbol \
  scripts/k6/scenarios/place-multi-symbol.js
```

### 4.4 체결 적고 주문만 많음

```bash
k6 run -e SYMBOL=BTC -e MIN_PRICE=1 -e MAX_PRICE=1 -e VUS=5 -e SLEEP_SECONDS=0 \
  -e SCENARIO_NAME=place-no-fill \
  scripts/k6/scenarios/place-no-fill.js
```

### 4.5 체결 매우 많음 (MARKET 주문)

```bash
k6 run -e SYMBOL=BTC -e MIN_QTY=1 -e MAX_QTY=1 -e VUS=5 -e SLEEP_SECONDS=0 \
  -e SCENARIO_NAME=place-market-order \
  scripts/k6/scenarios/place-market-order.js
```

### 4.6 취소 비율 높음

```bash
k6 run -e SYMBOL=BTC -e MIN_PRICE=1 -e MAX_PRICE=999999999999 \
  -e CANCEL_DELAY_MIN_MS=100 -e CANCEL_DELAY_MAX_MS=500 -e VUS=10 \
  -e SCENARIO_NAME=cancel-heavy \
  scripts/k6/scenarios/cancel-heavy.js
```

### 4.7 FOK / IOC 집중

```bash
# MIXED (기본): FOK 50% + IOC 50%
k6 run -e SYMBOL=BTC -e ORDER_TYPE=MIXED -e VUS=5 -e SLEEP_SECONDS=0 \
  -e SCENARIO_NAME=fok-ioc-heavy \
  scripts/k6/scenarios/fok-ioc-heavy.js

# FOK 전용
k6 run -e SYMBOL=BTC -e ORDER_TYPE=FOK -e VUS=5 -e SLEEP_SECONDS=0 \
  scripts/k6/scenarios/fok-ioc-heavy.js
```

### 4.8 MARKET BUY quoteQty

```bash
k6 run -e SYMBOL=BTC -e MIN_QUOTE_QTY=100000 -e MAX_QUOTE_QTY=5000000 \
  -e VUS=5 -e SLEEP_SECONDS=0 -e SCENARIO_NAME=market-buy-quote-qty \
  scripts/k6/scenarios/market-buy-quote-qty.js
```

### 4.10 멱등성 동시 요청

```bash
# account 10개, account당 50개 동시 요청 → 총 500 VU
k6 run -e SYMBOL=BTC -e CONCURRENT_PER_ACCOUNT=50 -e SCENARIO_NAME=idempotency-concurrent \
  -e ACCOUNT_IDS=00000000-0000-0000-0000-000000000001,00000000-0000-0000-0000-000000000002,00000000-0000-0000-0000-000000000003,00000000-0000-0000-0000-000000000004,00000000-0000-0000-0000-000000000005,00000000-0000-0000-0000-000000000006,00000000-0000-0000-0000-000000000007,00000000-0000-0000-0000-000000000008,00000000-0000-0000-0000-000000000009,00000000-0000-0000-0000-000000000010 \
  scripts/k6/scenarios/idempotency-concurrent.js

# 종료 후 DB에서 중복 주문 없음 확인:
# SELECT account_id, client_order_id, COUNT(*) FROM orders GROUP BY account_id, client_order_id HAVING COUNT(*) > 1;
```

---

## 주요 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 서버 주소 |
| `SYMBOL` | `BTC` | 단일 심볼 |
| `SYMBOLS` | `BTC` | 다중 심볼, 쉼표 구분 |
| `ACCOUNT_IDS` | 더미 4개 | 쉼표 구분 account UUID 목록 |
| `VUS` | 시나리오별 상이 | 동시 가상 사용자 수 |
| `WARMUP_DURATION` | `0s` | 워밍업 구간 시간 |
| `MEASURE_DURATION` | `DURATION` 또는 `1m` | 측정 구간 시간 |
| `COOLDOWN_DURATION` | `0s` | 쿨다운 구간 시간 |
| `DURATION` | `1m` | 하위 호환용 측정 구간 시간 |
| `GRACEFUL_RAMP_DOWN` | `5s` | cooldown 종료 후 in-flight 요청 완료 대기 시간 |
| `SLEEP_SECONDS` | `0` | 주문 간 대기 시간 (TPS 조절용) |
| `MIN_PRICE` / `MAX_PRICE` | `99000000` / `101000000` | 지정가 가격 범위 |
| `MIN_QTY` / `MAX_QTY` | `1` / `3` | 주문 수량 범위 |
| `MIN_QUOTE_QTY` / `MAX_QUOTE_QTY` | `100000` / `5000000` | MARKET BUY quoteQty 범위 |
| `ORDER_TYPE` | `MIXED` | FOK/IOC 시나리오용: `FOK`, `IOC`, `MIXED` |
| `CANCEL_DELAY_MIN_MS` / `MAX_MS` | `100` / `1000` | 주문 후 취소까지 대기 시간 |
| `CONCURRENT_PER_ACCOUNT` | `50` | 멱등성 시나리오: account당 동시 요청 수 |
| `SCENARIO_NAME` | `default` | clientOrderId prefix (구분용) |
| `GRAFANA_URL` | `http://localhost:3000` | Grafana 주소 (phase annotation 전송용) |
| `GRAFANA_USER` | `admin` | Grafana 사용자 이름 |
| `GRAFANA_PASSWORD` | `admin` | Grafana 비밀번호 |

---

## k6 외 수동 시나리오

### 4.9 재기동 복구 시간

1. open order가 쌓인 상태에서 서버를 종료한다.
2. 서버를 재시작하고 `replay_duration_on_startup` 메트릭을 Grafana에서 확인한다.
3. open order 1,000건 / 10,000건 두 단계로 측정한다.
4. 재시작 후 오더북 depth와 DB 주문 상태 일치를 쿼리로 검증한다.

```sql
-- ACCEPTED 상태 잔존 주문이 없어야 한다
SELECT COUNT(*) FROM orders WHERE status = 'ACCEPTED';
```

### 4.11 Queue Submit 실패 보상 롤백

서버 설정에서 engine queue capacity를 10으로 낮춘 뒤 기존 `place-single-symbol.js`를 과부하로 실행한다.

```bash
# application.yml 또는 환경변수: engine.queue.capacity=10
VUS=50 SLEEP_SECONDS=0 k6 run scripts/k6/scenarios/place-single-symbol.js
```

종료 후 확인:
- `queue_full_rollback_count` > 0
- 롤백된 주문이 `orders` 테이블에 없음
- 잔고 = 롤백 전 잔고 (hold 복구 완료)

### 4.12 다중 자산 lock 데드락 검증

2개 account, 단일 심볼, `place-single-symbol.js`로 BUY/SELL 교차 체결을 유도한다.

```bash
ACCOUNT_IDS="00000000-0000-0000-0000-000000000001,00000000-0000-0000-0000-000000000002" \
SYMBOL=BTC \
VUS=2 \
SLEEP_SECONDS=0 \
k6 run scripts/k6/scenarios/place-single-symbol.js
```

종료 후 확인:
- `db_deadlock_count = 0`
- `balance_lock_contention_count` (경합 수, 데드락 아님)

---

## Phase Report

duration 기반 시나리오는 실행 종료 후 두 가지 출력물을 생성한다.

**1. JSON 파일** (`phase-report-{SCENARIO_NAME}-{timestamp}.json`)

```json
{
  "scenario": "place-single-symbol",
  "phases": {
    "warmup":  { "start": "2025-03-26T10:00:00.000Z", "end": "2025-03-26T10:00:30.000Z", "durationMs": 30000 },
    "measure": { "start": "2025-03-26T10:00:30.000Z", "end": "2025-03-26T10:02:30.000Z", "durationMs": 120000 },
    "cooldown":{ "start": "2025-03-26T10:02:30.000Z", "end": "2025-03-26T10:02:40.000Z", "durationMs": 10000 }
  }
}
```

**2. Grafana annotation** — measure 구간 region annotation 1개 자동 등록

Grafana 대시보드에서 measure 구간의 시간 범위를 annotation으로 확인하거나,
JSON 파일의 `phases.measure.start` / `phases.measure.end` 값으로 시간 범위 피커를 설정한다.

콘솔에도 measure 구간이 출력된다:
```
[phase-reporter] measure 구간: 2025-03-26T10:00:30.000Z ~ 2025-03-26T10:02:30.000Z
```

---

## 주의

- 모든 시나리오는 account와 잔고가 미리 준비되어 있다고 가정한다.
- `place-no-fill.js`는 `MIN_PRICE`를 극단 저가로 설정해야 체결이 없다. 오더북 상황에 따라 실제로 체결될 수 있으므로 Grafana에서 `trades_per_sec ≈ 0`을 확인한다.
- `fok-ioc-heavy.js`는 오더북 depth가 충분해야 FOK/IOC 충족 가능성이 높아진다. 얕은 오더북에서는 FOK 실패율이 높아진다.
- `idempotency-concurrent.js` 종료 후 반드시 DB 쿼리로 중복 주문 수를 확인한다.
