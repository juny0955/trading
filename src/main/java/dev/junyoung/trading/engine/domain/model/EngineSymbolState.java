package dev.junyoung.trading.engine.domain.model;

import dev.junyoung.trading.shared.domain.value.Symbol;

public record EngineSymbolState(
	Symbol symbol,
	long lastEventSequence
) {
}
