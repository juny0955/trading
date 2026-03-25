import { randomIntBetween } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

function nextSide() {
  return Math.random() < 0.5 ? "BUY" : "SELL";
}

function nextPrice(minPrice, maxPrice) {
  return randomIntBetween(minPrice, maxPrice);
}

function nextQuantity(minQty, maxQty) {
  return randomIntBetween(minQty, maxQty);
}

function nextClientOrderId(vu, iteration) {
  return `k6-${__ENV.SCENARIO_NAME || "default"}-${vu}-${iteration}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

export function buildLimitOrderPayload(config, vu, iteration, symbolOverride) {
  const side = nextSide();

  return {
    symbol: symbolOverride || config.symbol,
    side,
    orderType: "LIMIT",
    tif: "GTC",
    price: nextPrice(config.minPrice, config.maxPrice),
    quantity: nextQuantity(config.minQty, config.maxQty),
    clientOrderId: nextClientOrderId(vu, iteration),
  };
}
