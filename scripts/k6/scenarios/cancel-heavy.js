import { check, sleep } from "k6";
import { randomIntBetween } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";
import { config } from "../lib/config.js";
import { pickAccount, requireAccounts } from "../lib/accounts.js";
import { buildLimitOrderPayload } from "../lib/orders.js";
import { cancelOrder, extractOrderId, placeOrder } from "../lib/http.js";

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || "1m",
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1000"],
  },
};

export function setup() {
  requireAccounts(config.accountIds);
  return { accountIds: config.accountIds };
}

export default function (data) {
  const accountId = pickAccount(data.accountIds, __VU, __ITER);
  const payload = buildLimitOrderPayload(config, __VU, __ITER);

  // 취소 성공률을 높이기 위해 기본 시나리오는 체결이 적은 지정가 주문을 생성한다.
  payload.price = payload.side === "BUY" ? config.minPrice : config.maxPrice;

  const placeResponse = placeOrder(config.baseUrl, accountId, payload);
  const orderId = extractOrderId(placeResponse);

  const delayMs = randomIntBetween(config.cancelDelayMinMs, config.cancelDelayMaxMs);
  sleep(delayMs / 1000);

  const cancelResponse = cancelOrder(config.baseUrl, accountId, orderId);

  check(cancelResponse, {
    "취소 응답이 성공이다": (r) => r.status === 200 || r.status === 202,
  });

  sleep(Number(__ENV.SLEEP_SECONDS || 0));
}
