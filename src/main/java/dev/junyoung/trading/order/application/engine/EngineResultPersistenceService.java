package dev.junyoung.trading.order.application.engine;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.junyoung.trading.order.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.order.application.port.out.BalanceSettlementPort;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.domain.service.SettlementCalculator;
import dev.junyoung.trading.order.domain.service.dto.SettlementInput;
import dev.junyoung.trading.order.domain.service.dto.SettlementResult;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EngineResultPersistenceService {

	private final OrderRepository orderRepository;
	private final TradeRepository tradeRepository;
	private final BalanceSettlementPort balanceSettlementPort;

	public void persistPlaceResult(PlaceCalculationResult.Accepted accepted) {
		orderRepository.updateAll(accepted.updatedOrders());
		tradeRepository.saveAll(accepted.trades());

		SettlementResult result = SettlementCalculator.settle(new SettlementInput(accepted.updatedOrders(), accepted.trades()));
		for (SettlementResult.BalanceDelta balanceDelta : result.balanceDeltas()) {
			balanceSettlementPort.balanceSettlement(
				balanceDelta.accountId(),
				balanceDelta.asset(),
				balanceDelta.availableDelta(),
				balanceDelta.heldDelta()
			);
		}
	}

	public void persistCancelResult(CancelCalculationResult.Cancelled cancelled) {
		orderRepository.save(cancelled.updatedOrders().getFirst());
		SettlementResult result = SettlementCalculator.settle(new SettlementInput(cancelled.updatedOrders(), List.of()));
		for (SettlementResult.BalanceDelta balanceDelta : result.balanceDeltas()) {
			balanceSettlementPort.balanceSettlement(
				balanceDelta.accountId(),
				balanceDelta.asset(),
				balanceDelta.availableDelta(),
				balanceDelta.heldDelta()
			);
		}
	}
}
