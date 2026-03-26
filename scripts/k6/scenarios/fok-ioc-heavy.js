// 시나리오 4.7: FOK / IOC 집중 부하
// ORDER_TYPE 환경변수로 "FOK", "IOC", "MIXED"(기본) 선택 가능.
// MIXED: FOK 50% + IOC 50% 혼합.
// engine_processing_latency와 FOK 실패율 측정에 집중한다.
import { check, sleep } from "k6";
import { config } from "../lib/config.js";
import { pickAccount, requireAccounts } from "../lib/accounts.js";
import { buildFokOrderPayload, buildIocOrderPayload } from "../lib/orders.js";
import { placeOrder } from "../lib/http.js";
import { buildPhasedOptions } from "../lib/phases.js";
import { buildPhaseReporter } from "../lib/phase-reporter.js";

const ORDER_TYPE = __ENV.ORDER_TYPE || "MIXED";

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

  let payload;
  if (ORDER_TYPE === "FOK") {
    payload = buildFokOrderPayload(config, __VU, __ITER);
  } else if (ORDER_TYPE === "IOC") {
    payload = buildIocOrderPayload(config, __VU, __ITER);
  } else {
    // MIXED: 50% FOK, 50% IOC
    payload =
      Math.random() < 0.5
        ? buildFokOrderPayload(config, __VU, __ITER)
        : buildIocOrderPayload(config, __VU, __ITER);
  }

  const response = placeOrder(config.baseUrl, accountId, payload);

  check(response, {
    "주문이 수신됐다 (체결 여부 무관)": (r) =>
      r.status === 200 || r.status === 202,
  });

  sleep(Number(__ENV.SLEEP_SECONDS || 0));
}

export const handleSummary = reporter.buildHandleSummary();
