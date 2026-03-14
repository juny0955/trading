package dev.junyoung.trading.order.adapter.out.persistence.jooq.idempotencyKey;

import java.time.Instant;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.IdempotencyKeysRecord;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JooqIdempotencyKeyRepository implements IdempotencyKeyRepository {

	private final DSLContext dslContext;

	@Override
	public void save(AccountId accountId, OrderId orderId, String clientOrderId) {
		IdempotencyKeysRecord record = dslContext.newRecord(Tables.IDEMPOTENCY_KEYS);
		record.setAccountId(accountId.value());
		record.setOrderId(orderId.value());
		record.setClientOrderId(clientOrderId);
		record.setCreatedAt(Instant.now());

		dslContext.insertInto(Tables.IDEMPOTENCY_KEYS)
			.set(record)
			.execute();
	}

	@Override
	public OrderId findOrderId(AccountId accountId, String clientOrderId) {
		return dslContext.select(Tables.IDEMPOTENCY_KEYS.ORDER_ID)
			.from(Tables.IDEMPOTENCY_KEYS)
			.where(Tables.IDEMPOTENCY_KEYS.ACCOUNT_ID.eq(accountId.value()))
			.and(Tables.IDEMPOTENCY_KEYS.CLIENT_ORDER_ID.eq(clientOrderId))
			.fetchOne(r -> new OrderId(r.get(Tables.IDEMPOTENCY_KEYS.ORDER_ID)));
	}
}
