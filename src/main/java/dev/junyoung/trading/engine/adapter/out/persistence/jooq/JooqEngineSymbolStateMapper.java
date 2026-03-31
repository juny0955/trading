package dev.junyoung.trading.engine.adapter.out.persistence.jooq;

import org.jooq.Record2;

import dev.junyoung.trading.engine.domain.model.EngineSymbolState;
import dev.junyoung.trading.shared.domain.value.Symbol;

public final class JooqEngineSymbolStateMapper {

	public static EngineSymbolState toDomain(Record2<String, Long> record) {
		return new EngineSymbolState(new Symbol(record.value1()), record.value2());
	}

}
