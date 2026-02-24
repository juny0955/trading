package dev.junyoung.trading.order.adapter.in.rest;

import dev.junyoung.trading.order.adapter.in.rest.response.OrderBookResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orderbook")
public class OrderBookController {

    @GetMapping
    public ResponseEntity<OrderBookResponse> getOrderBook() {
        return ResponseEntity
                .ok(null);
    }
}
