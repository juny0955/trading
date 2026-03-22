package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public interface OrderCommandGateway {
	void submit(Symbol symbol, EngineCommand command);
}
