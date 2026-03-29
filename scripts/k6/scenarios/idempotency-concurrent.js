// 시나리오 4.10: 멱등성 동시 요청 부하
// 동일 (accountId, clientOrderId) 쌍으로 여러 VU가 동시에 주문을 요청한다.
// 각 account당 주문이 정확히 1건만 생성되는지 검증한다.
//
// 동작 방식:
// - setup()에서 account별로 1개의 clientOrderId를 미리 생성한다.
// - 모든 VU가 동일한 (accountId, clientOrderId) 조합을 공유한다.
// - VU는 (vu + iteration) % accountIds.length 로 account를 선택하고
//   해당 account의 고정 clientOrderId를 사용한다.
// - 테스트 종료 후 DB에서 account별 주문 수 = 1인지 확인해야 한다.
import { check, sleep } from "k6";
import { config } from "../lib/config.js";
import { requireAccounts } from "../lib/accounts.js";
import { placeOrder } from "../lib/http.js";

const CONCURRENT_PER_ACCOUNT = Number(__ENV.CONCURRENT_PER_ACCOUNT || 50);

export const options = {
  vus: Number(__ENV.VUS || (config.accountIds.length * CONCURRENT_PER_ACCOUNT)),
  iterations: Number(__ENV.ITERATIONS || (config.accountIds.length * CONCURRENT_PER_ACCOUNT)),
  thresholds: {
    http_req_failed: ["rate<0.99"],
  },
};

export function setup() {
  requireAccounts(config.accountIds);

  // account별 고정 clientOrderId 생성
  const idempotencyKeys = {};
  for (const accountId of config.accountIds) {
    idempotencyKeys[accountId] = `k6-idempotency-${accountId}-${Date.now()}`;
  }

  return { accountIds: config.accountIds, idempotencyKeys };
}

export default function (data) {
  const idx = (__VU + __ITER) % data.accountIds.length;
  const accountId = data.accountIds[idx];
  const clientOrderId = data.idempotencyKeys[accountId];

  const payload = {
    symbol: config.symbol,
    side: "BUY",
    orderType: "LIMIT",
    tif: "GTC",
    price: config.minPrice,
    quantity: config.minQty,
    clientOrderId,
  };

  const response = placeOrder(config.baseUrl, accountId, payload);

  check(response, {
    "주문이 처리됐다 (성공 또는 중복 거절)": (r) =>
      r.status === 200 || r.status === 202 || r.status === 409,
  });

  sleep(Number(__ENV.SLEEP_SECONDS || 0));
}
