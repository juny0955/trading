package dev.junyoung.trading.order.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.GetTradeUseCase;
import dev.junyoung.trading.order.application.port.in.result.TradeResult;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTradeService implements GetTradeUseCase {

	private final OrderRepository orderRepository;
	private final TradeRepository tradeRepository;

	@Override
	public List<TradeResult> getTradesByOrder(String accountId, String orderId) {
		Order order = orderRepository.findById(OrderId.from(orderId))
			.orElseThrow(() -> new OrderNotFoundException(orderId));

		if (!order.getAccountId().equals(AccountId.from(accountId)))
			throw new OrderNotFoundException(orderId);

		return tradeRepository.findByOrderId(order.getOrderId()).stream()
			.map(t -> TradeResult.fromOrder(orderId, t))
			.toList();
	}

	@Override
	public List<TradeResult> getTradesByAccount(String accountId) {
		return tradeRepository.findByAccountIdWithSide(AccountId.from(accountId)).stream()
			.map(TradeResult::fromAccountResult)
			.toList();
	}
}
