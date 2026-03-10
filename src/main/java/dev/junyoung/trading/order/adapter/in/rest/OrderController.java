package dev.junyoung.trading.order.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.junyoung.trading.order.adapter.in.rest.request.PlaceOrderRequest;
import dev.junyoung.trading.order.adapter.in.rest.response.OrderResponse;
import dev.junyoung.trading.order.adapter.in.rest.response.PlaceOrderResponse;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.GetOrderUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/accounts/{accountId}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(
        @PathVariable String accountId,
        @RequestBody @Valid PlaceOrderRequest request
    ) {
        String orderId = placeOrderUseCase.placeOrder(request.toCommand(accountId));

        return ResponseEntity
                .accepted()
                .body(new PlaceOrderResponse(orderId));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
        @PathVariable String accountId,
        @PathVariable String orderId
    ) {
        cancelOrderUseCase.cancelOrder(accountId, orderId);

        return ResponseEntity
                .accepted()
                .build();
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
        @PathVariable String accountId,
        @PathVariable String orderId
    ) {
        OrderResult result = getOrderUseCase.getOrder(accountId, orderId);
        return ResponseEntity
                .ok(OrderResponse.from(result));
    }
}
