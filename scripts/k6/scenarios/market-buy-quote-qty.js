// 시나리오 4.8: MARKET BUY quoteQty 집중 부하
// KRW 예산 기반 시장가 매수로 다단계 체결(maker 여러 개 소진)을 유도한다.
// trades_per_sec, engine_result_tx_latency, hold 반환 정합성 측정에 집중한다.
import { check, sleep } from "k6";
import { config } from "../lib/config.js";
import { pickAccount, requireAccounts } from "../lib/accounts.js";
import { buildMarketBuyQuoteQtyPayload } from "../lib/orders.js";
import { placeOrder } from "../lib/http.js";
import { buildPhasedOptions } from "../lib/phases.js";
import { buildPhaseReporter } from "../lib/phase-reporter.js";

export const options = buildPhasedOptions({
  defaultVus: 10,
  defaultMeasureDuration: "1m",
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<2000"],
  },
});
const reporter = buildPhaseReporter(options._phaseDurations);

export function setup() {
  requireAccounts(config.accountIds);
  return { accountIds: config.accountIds };
}

export default function (data) {
  reporter.recordStart();
  const accountId = pickAccount(data.accountIds, __VU, __ITER);
  const payload = buildMarketBuyQuoteQtyPayload(config, __VU, __ITER);

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
