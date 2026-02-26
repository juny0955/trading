package dev.junyoung.trading.order.adapter.in.rest;

import dev.junyoung.trading.order.adapter.in.rest.request.PlaceOrderRequest;
import dev.junyoung.trading.order.adapter.in.rest.response.OrderResponse;
import dev.junyoung.trading.order.adapter.in.rest.response.PlaceOrderResponse;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.GetOrderUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(@RequestBody PlaceOrderRequest request) {
        String orderId = placeOrderUseCase.placeOrder(request.symbol(), request.side(), request.orderType(), request.price(), request.quantity());

        return ResponseEntity
                .accepted()
                .body(new PlaceOrderResponse(orderId));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String orderId) {
        cancelOrderUseCase.cancelOrder(orderId);

        return ResponseEntity
                .ok()
                .build();
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        OrderResult result = getOrderUseCase.getOrder(orderId);
        return ResponseEntity
                .ok(OrderResponse.from(result));
    }
}
