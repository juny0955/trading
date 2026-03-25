# k6 시나리오

기본 실행 예시:

```bash
ACCOUNT_IDS="11111111-1111-1111-1111-111111111111,22222222-2222-2222-2222-222222222222" \
SCENARIO_NAME=place-single-symbol \
k6 run scripts/k6/scenarios/place-single-symbol.js
```

```bash
ACCOUNT_IDS="11111111-1111-1111-1111-111111111111,22222222-2222-2222-2222-222222222222" \
SCENARIO_NAME=cancel-heavy \
k6 run scripts/k6/scenarios/cancel-heavy.js
```

```bash
ACCOUNT_IDS="$(printf '00000000-0000-0000-0000-%012d,' {1..100} | sed 's/,$//')" \
SYMBOLS=BTC,ETH,TEST \
SCENARIO_NAME=place-multi-symbol \
k6 run scripts/k6/scenarios/place-multi-symbol.js
```

주요 환경변수:

- `BASE_URL`: 기본값 `http://localhost:8080`
- `SYMBOL`: 기본값 `BTC`
- `SYMBOLS`: 다중 심볼 시나리오용 쉼표 구분 심볼 목록
- `ACCOUNT_IDS`: 쉼표 구분 account UUID 목록
- `VUS`: 동시 가상 사용자 수
- `DURATION`: 실행 시간
- `MIN_PRICE`, `MAX_PRICE`: 지정가 가격 범위
- `MIN_QTY`, `MAX_QTY`: 주문 수량 범위
- `CANCEL_DELAY_MIN_MS`, `CANCEL_DELAY_MAX_MS`: 주문 후 취소까지 대기 시간

주의:

- 이 스크립트는 계정과 잔고가 미리 준비되어 있다고 가정한다.
- `cancel-heavy` 는 체결을 줄이기 위해 매수는 낮은 가격, 매도는 높은 가격으로 주문을 생성한다.
- `place-multi-symbol` 은 `SYMBOLS` 목록을 round-robin 방식으로 분산해 주문한다.
