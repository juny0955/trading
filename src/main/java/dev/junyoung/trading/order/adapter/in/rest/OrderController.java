package dev.junyoung.trading.order.adapter.in.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.junyoung.trading.order.adapter.in.rest.request.PlaceOrderRequest;
import dev.junyoung.trading.order.adapter.in.rest.response.OrderResponse;
import dev.junyoung.trading.order.adapter.in.rest.response.PlaceOrderResponse;
import dev.junyoung.trading.order.adapter.in.rest.response.TradeResponse;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.GetOrderUseCase;
import dev.junyoung.trading.order.application.port.in.GetTradeUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import dev.junyoung.trading.order.application.port.in.result.TradeResult;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/accounts/{accountId}")
@RequiredArgsConstructor
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final GetTradeUseCase getTradeUseCase;

    @PostMapping("/orders")
    public ResponseEntity<PlaceOrderResponse> placeOrder(
        @PathVariable String accountId,
        @RequestBody @Valid PlaceOrderRequest request
    ) {
        OrderId orderId = placeOrderUseCase.placeOrder(request.toCommand(accountId));

        return ResponseEntity
                .accepted()
                .body(new PlaceOrderResponse(orderId.value()));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
        @PathVariable String accountId,
        @PathVariable String orderId
    ) {
        cancelOrderUseCase.cancelOrder(accountId, orderId);

        return ResponseEntity
                .accepted()
                .build();
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
        @PathVariable String accountId,
        @PathVariable String orderId
    ) {
        OrderResult result = getOrderUseCase.getOrder(accountId, orderId);
        return ResponseEntity.ok(OrderResponse.from(result));
    }

    @GetMapping("/orders/{orderId}/trades")
    public ResponseEntity<List<TradeResponse>> getTradesByOrder(
        @PathVariable String accountId,
        @PathVariable String orderId
    ) {
        List<TradeResult> results = getTradeUseCase.getTradesByOrder(accountId, orderId);

        List<TradeResponse> responses = results.stream()
            .map(TradeResponse::from)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeResponse>> getTradesByAccount(
        @PathVariable String accountId
    ) {
        List<TradeResult> results = getTradeUseCase.getTradesByAccount(accountId);

        List<TradeResponse> responses = results.stream()
            .map(TradeResponse::from)
            .toList();
        return ResponseEntity.ok(responses);
    }
}
