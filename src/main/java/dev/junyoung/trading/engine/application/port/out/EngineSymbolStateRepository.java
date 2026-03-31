package dev.junyoung.trading.engine.application.port.out;

import java.util.List;

import dev.junyoung.trading.engine.domain.model.EngineSymbolState;

public interface EngineSymbolStateRepository {
	List<EngineSymbolState> findActiveSymbols();
}
