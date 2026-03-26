// 시나리오 4.5: 체결이 매우 많은 경우
// MARKET 주문으로 taker 체결을 극대화한다.
// trades_per_sec, engine_result_tx_latency 측정에 집중한다.
import { check, sleep } from "k6";
import { config } from "../lib/config.js";
import { pickAccount, requireAccounts } from "../lib/accounts.js";
import { buildMarketOrderPayload } from "../lib/orders.js";
import { placeOrder } from "../lib/http.js";

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || "1m",
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<2000"],
  },
};

export function setup() {
  requireAccounts(config.accountIds);
  return { accountIds: config.accountIds };
}

export default function (data) {
  const accountId = pickAccount(data.accountIds, __VU, __ITER);
  const payload = buildMarketOrderPayload(config, __VU, __ITER);

  const response = placeOrder(config.baseUrl, accountId, payload);

  check(response, {
    "주문 응답 본문에 orderId가 있다": (r) => {
      try {
        const body = r.json();
        return Boolean(body && body.orderId);
      } catch (_) {
        return false;
      }
    },
  });

  sleep(Number(__ENV.SLEEP_SECONDS || 0));
}
