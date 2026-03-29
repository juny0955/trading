import http from "k6/http";
import { check, fail } from "k6";

const JSON_HEADERS = {
  "Content-Type": "application/json",
};

export function placeOrder(baseUrl, accountId, payload) {
  const response = http.post(
    `${baseUrl}/accounts/${accountId}/orders`,
    JSON.stringify(payload),
    { headers: JSON_HEADERS },
  );

  check(response, {
    "주문 요청이 202 또는 200을 반환한다": (r) => r.status === 202 || r.status === 200,
  });

  return response;
}

export function cancelOrder(baseUrl, accountId, orderId) {
  const response = http.post(
    `${baseUrl}/accounts/${accountId}/orders/${orderId}/cancel`,
    null,
    { headers: JSON_HEADERS },
  );

  check(response, {
    "취소 요청이 202 또는 200을 반환한다": (r) => r.status === 202 || r.status === 200,
  });

  return response;
}

export function extractOrderId(response) {
  try {
    const body = response.json();
    if (!body || !body.orderId) {
      fail(`orderId missing in response body: ${response.body}`);
    }
    return body.orderId;
  } catch (error) {
    fail(`failed to parse order response: ${error}, body=${response.body}`);
  }
}
