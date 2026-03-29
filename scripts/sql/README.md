# SQL Seed Scripts

성능 측정 전 초기화:

```bash
psql -U trading -d trading -f scripts/sql/reset-perf.sql
psql -U trading -d trading -f scripts/sql/perf-seed-100-accounts.sql
```

생성 결과:

- `accounts`: 100건
- `balances`: 400건
  - account당 `KRW 1,000,000,000,000`
  - account당 `BTC 1,000,000`
  - account당 `ETH 10,000,000`
  - account당 `TEST 1,000,000,000`

생성되는 account UUID 패턴:

- `00000000-0000-0000-0000-000000000001`
- `00000000-0000-0000-0000-000000000002`
- ...
- `00000000-0000-0000-0000-000000000100`

k6 예시:

```bash
ACCOUNT_IDS="$(printf '00000000-0000-0000-0000-%012d,' {1..100} | sed 's/,$//')" \
SCENARIO_NAME=place-single-symbol \
k6 run scripts/k6/scenarios/place-single-symbol.js
```
