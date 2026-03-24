const DEFAULT_ACCOUNT_IDS = [
  "11111111-1111-1111-1111-111111111111",
  "22222222-2222-2222-2222-222222222222",
  "33333333-3333-3333-3333-333333333333",
  "44444444-4444-4444-4444-444444444444",
];

function parseAccountIds(rawValue) {
  if (!rawValue) {
    return DEFAULT_ACCOUNT_IDS;
  }

  return rawValue
    .split(",")
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
}

export const config = {
  baseUrl: __ENV.BASE_URL || "http://localhost:8080",
  symbol: __ENV.SYMBOL || "BTC",
  accountIds: parseAccountIds(__ENV.ACCOUNT_IDS),
  minPrice: Number(__ENV.MIN_PRICE || 99000000),
  maxPrice: Number(__ENV.MAX_PRICE || 101000000),
  minQty: Number(__ENV.MIN_QTY || 1),
  maxQty: Number(__ENV.MAX_QTY || 3),
  cancelDelayMinMs: Number(__ENV.CANCEL_DELAY_MIN_MS || 100),
  cancelDelayMaxMs: Number(__ENV.CANCEL_DELAY_MAX_MS || 1000),
};
