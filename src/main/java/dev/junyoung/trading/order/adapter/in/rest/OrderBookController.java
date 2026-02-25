package dev.junyoung.trading.order.adapter.in.rest;

import dev.junyoung.trading.order.adapter.in.rest.response.OrderBookResponse;
import dev.junyoung.trading.order.application.port.in.GetOrderBookUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orderbook")
@RequiredArgsConstructor
public class OrderBookController {

    private final GetOrderBookUseCase getOrderBookUseCase;

    @GetMapping
    public ResponseEntity<OrderBookResponse> getOrderBook() {
        OrderBookResult result = getOrderBookUseCase.getOrderBookCache();
        return ResponseEntity
                .ok(OrderBookResponse.from(result));
    }
}
