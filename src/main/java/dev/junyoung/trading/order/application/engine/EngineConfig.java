package dev.junyoung.trading.order.application.engine;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.junyoung.trading.order.domain.model.OrderBook;

@Configuration
public class EngineConfig {

	@Bean
	public OrderBook orderBook() {
		return new OrderBook();
	}
}
