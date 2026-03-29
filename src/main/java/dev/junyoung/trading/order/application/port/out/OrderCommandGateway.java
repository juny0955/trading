package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.engine.application.engine.loop.EngineCommand;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public interface OrderCommandGateway {
	void submit(Symbol symbol, EngineCommand command);
}
