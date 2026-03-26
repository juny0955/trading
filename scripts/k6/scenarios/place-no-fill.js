// 시나리오 4.4: 체결을 최소화하고 주문 insert 비중을 높게 보는 경우
// 매수는 낮은 가격대, 매도는 높은 가격대로 강제해 체결 가능성을 낮춘다.
// 일부 체결은 발생할 수 있으므로 trades_per_sec와 함께 해석해야 한다.
import { check, sleep } from "k6";
import { config } from "../lib/config.js";
import { pickAccount, requireAccounts } from "../lib/accounts.js";
import { buildLimitOrderPayload } from "../lib/orders.js";
import { placeOrder } from "../lib/http.js";
import { buildPhasedOptions } from "../lib/phases.js";
import { buildPhaseReporter } from "../lib/phase-reporter.js";

export const options = buildPhasedOptions({
  defaultVus: 20,
  defaultMeasureDuration: "5m",
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1000"],
  },
});
const reporter = buildPhaseReporter(options._phaseDurations);
const noFillPriceGap = Number(__ENV.NO_FILL_PRICE_GAP || 1);

export function setup() {
  requireAccounts(config.accountIds);
  return { accountIds: config.accountIds };
}

export default function (data) {
  reporter.recordStart();
  const accountId = pickAccount(data.accountIds, __VU, __ITER);
  const payload = buildLimitOrderPayload(config, __VU, __ITER);

  // BUY와 SELL 가격대를 분리해 self-cross와 즉시 체결 가능성을 낮춘다.
  // MIN/MAX가 같은 값이어도 SELL 가격이 BUY 가격보다 항상 높도록 gap을 강제한다.
  const buyPrice = config.minPrice;
  const sellPrice = Math.max(config.maxPrice, config.minPrice + noFillPriceGap);
  payload.price = payload.side === "BUY" ? buyPrice : sellPrice;

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

export const handleSummary = reporter.buildHandleSummary();
