package dev.junyoung.trading.engine.application.contract;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.fixture.OrderFixture;
import dev.junyoung.trading.shared.domain.enums.Side;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;
import dev.junyoung.trading.shared.domain.value.Symbol;

@DisplayName("EngineContractMapper")
class EngineContractMapperTest {

	private static final Symbol SYMBOL = new Symbol("BTC");

	@Test
	@DisplayName("Order를 PlaceCommandEnvelope로 변환한 뒤 다시 Order로 복원할 수 있다")
	void placeCommandEnvelope_roundTripsOrder() {
		Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000L), new Quantity(5L));

		PlaceCommandEnvelope envelope = EngineContractMapper.toPlaceCommandEnvelope(order);
		Order restored = EngineContractMapper.toOrder(envelope);

		assertThat(restored.getOrderId()).isEqualTo(order.getOrderId());
		assertThat(restored.getAccountId()).isEqualTo(order.getAccountId());
		assertThat(restored.getAcceptedSeq()).isEqualTo(order.getAcceptedSeq());
		assertThat(restored.getSymbol()).isEqualTo(order.getSymbol());
		assertThat(restored.getStatus()).isEqualTo(order.getStatus());
		assertThat(restored.getRemaining()).isEqualTo(order.getRemaining());
	}
}
