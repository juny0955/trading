// 시나리오 4.4: 체결 적고 주문만 많은 경우
// 매수는 극단 저가, 매도는 극단 고가로 고정해 체결을 최소화한다.
// order insert + hold reserve 비용만 측정할 수 있다.
import { check, sleep } from "k6";
import { config } from "../lib/config.js";
import { pickAccount, requireAccounts } from "../lib/accounts.js";
import { buildLimitOrderPayload } from "../lib/orders.js";
import { placeOrder } from "../lib/http.js";

export const options = {
  vus: Number(__ENV.VUS || 20),
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

  // 체결이 발생하지 않도록 가격을 극단으로 고정한다.
  // BUY: 오더북 ask 최저가보다 훨씬 낮은 가격
  // SELL: 오더북 bid 최고가보다 훨씬 높은 가격
  payload.price = payload.side === "BUY" ? config.minPrice : config.maxPrice;

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
