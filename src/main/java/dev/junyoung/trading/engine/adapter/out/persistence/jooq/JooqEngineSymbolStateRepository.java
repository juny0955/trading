package dev.junyoung.trading.engine.adapter.out.persistence.jooq;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import dev.junyoung.trading.engine.application.port.out.EngineSymbolStateRepository;
import dev.junyoung.trading.engine.domain.entity.EngineSymbolState;
import dev.junyoung.trading.jooq.Tables;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JooqEngineSymbolStateRepository implements EngineSymbolStateRepository {

	private final DSLContext dslContext;

	@Override
	public List<EngineSymbolState> findActiveSymbols() {
		return dslContext.select(Tables.SYMBOLS.SYMBOL, DSL.coalesce(Tables.SYMBOL_STATES.LAST_EVENT_SEQUENCE, 0L))
			.from(Tables.SYMBOLS)
			.leftJoin(Tables.SYMBOL_STATES)
				.on(Tables.SYMBOLS.SYMBOL.eq(Tables.SYMBOL_STATES.SYMBOL))
			.where(Tables.SYMBOLS.STATUS.eq("ACTIVE"))
			.fetch(JooqEngineSymbolStateMapper::toDomain);

	}
}
