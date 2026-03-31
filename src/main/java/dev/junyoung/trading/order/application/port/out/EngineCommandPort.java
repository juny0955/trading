package dev.junyoung.trading.order.application.port.out;

import java.time.Instant;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.engine.application.contract.CancelCommandEnvelope;
import dev.junyoung.trading.engine.application.contract.EngineContractMapper;
import dev.junyoung.trading.engine.application.contract.PlaceCommandEnvelope;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.shared.domain.value.Symbol;

public interface EngineCommandPort {
	void submitPlace(PlaceCommandEnvelope command, Instant serviceEnteredAt);

	default void submitPlace(Order order, Instant serviceEnteredAt) {
		submitPlace(EngineContractMapper.toPlaceCommandEnvelope(order), serviceEnteredAt);
	}

	void submitCancel(CancelCommandEnvelope command, Instant serviceEnteredAt);

	default void submitCancel(Symbol symbol, long acceptedSeq, OrderId orderId, AccountId requesterAccountId, Instant serviceEnteredAt) {
		submitCancel(new CancelCommandEnvelope(symbol, acceptedSeq, orderId, requesterAccountId), serviceEnteredAt);
	}
}
