package dev.junyoung.trading.engine.application.contract;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;

public final class EngineContractMapper {

	private EngineContractMapper() {}

	public static PlaceCommandEnvelope toPlaceCommandEnvelope(Order order) {
		return new PlaceCommandEnvelope(
			order.getOrderId(),
			order.getAccountId(),
			order.getClientOrderId(),
			order.getAcceptedSeq(),
			order.getSide(),
			order.getSymbol(),
			order.getOrderType(),
			order.getTif(),
			order.getPriceValue().map(dev.junyoung.trading.shared.domain.value.Price::new).orElse(null),
			order.getQuoteQty(),
			order.getQuantity(),
			order.getRemaining(),
			order.getStatus(),
			order.getCumQuoteQty(),
			order.getCumBaseQty(),
			order.getOrderedAt(),
			order.getCreatedAt(),
			order.getUpdatedAt()
		);
	}

	public static Order toOrder(PlaceCommandEnvelope command) {
		return Order.restore(
			command.orderId(),
			command.accountId(),
			command.clientOrderId(),
			command.acceptedSeq(),
			command.side(),
			command.symbol(),
			command.orderType(),
			command.tif(),
			command.price(),
			command.quoteQty(),
			command.quantity(),
			command.remaining(),
			command.status(),
			command.cumQuoteQty(),
			command.cumBaseQty(),
			command.orderedAt(),
			command.createdAt(),
			command.updatedAt()
		);
	}

	public static EngineTradeResult toEngineTradeResult(Trade trade) {
		return new EngineTradeResult(
			trade.tradeId(),
			trade.symbol(),
			trade.buyOrderId(),
			trade.sellOrderId(),
			trade.price(),
			trade.quantity(),
			trade.createdAt()
		);
	}
}
