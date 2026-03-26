// 오더북 적재 전용 시나리오.
// 기준 가격 주변에 비체결 양방향 호가를 계단식으로 쌓아 market 시나리오용 depth를 만든다.
import { check, sleep } from "k6";
import { config } from "../lib/config.js";
import { pickAccount, requireAccounts } from "../lib/accounts.js";
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

const tickSize = parsePositiveInt(__ENV.BOOK_SEED_TICK_SIZE, 1000);
const levels = parsePositiveInt(__ENV.BOOK_SEED_LEVELS, 100);
const spreadTicks = parsePositiveInt(__ENV.BOOK_SEED_SPREAD_TICKS, 5);
const priceStepTicks = parsePositiveInt(__ENV.BOOK_SEED_PRICE_STEP_TICKS, 1);
const midPrice = parsePositiveInt(
  __ENV.BOOK_SEED_MID_PRICE,
  Math.floor((config.minPrice + config.maxPrice) / 2)
);
const quantityBase = parsePositiveInt(__ENV.BOOK_SEED_QTY, config.maxQty);
const quantityStep = parsePositiveInt(__ENV.BOOK_SEED_QTY_STEP, 0);
const sideMode = (__ENV.BOOK_SEED_SIDE_MODE || "BOTH").toUpperCase();

export function setup() {
  requireAccounts(config.accountIds);
  validateSideMode(sideMode);
  return { accountIds: config.accountIds };
}

export default function (data) {
  reporter.recordStart();

  const accountId = pickAccount(data.accountIds, __VU, __ITER);
  const levelIndex = (__VU + __ITER) % levels;
  const side = pickSide(__ITER, sideMode);
  const payload = {
    symbol: config.symbol,
    side,
    orderType: "LIMIT",
    tif: "GTC",
    price: computePrice(side, levelIndex),
    quantity: computeQuantity(levelIndex),
    clientOrderId: buildClientOrderId(__VU, __ITER, side, levelIndex),
  };

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

function parsePositiveInt(rawValue, fallbackValue) {
  const value = Number(rawValue);
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : fallbackValue;
}

function validateSideMode(value) {
  if (value !== "BOTH" && value !== "BUY" && value !== "SELL") {
    throw new Error(
      `Invalid BOOK_SEED_SIDE_MODE: "${value}". Expected one of BOTH, BUY, SELL.`
    );
  }
}

function pickSide(iteration, value) {
  if (value === "BUY" || value === "SELL") {
    return value;
  }
  return iteration % 2 === 0 ? "BUY" : "SELL";
}

function computePrice(side, levelIndex) {
  const offsetTicks = spreadTicks + levelIndex * priceStepTicks;
  const offset = offsetTicks * tickSize;
  return side === "BUY" ? midPrice - offset : midPrice + offset;
}

function computeQuantity(levelIndex) {
  return quantityBase + levelIndex * quantityStep;
}

function buildClientOrderId(vu, iteration, side, levelIndex) {
  return `k6-book-seed-${side}-${levelIndex}-${vu}-${iteration}-${Date.now()}`;
}
