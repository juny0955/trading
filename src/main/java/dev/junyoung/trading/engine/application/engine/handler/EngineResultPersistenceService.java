package dev.junyoung.trading.engine.application.engine.handler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;

import dev.junyoung.trading.engine.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.engine.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.engine.application.exception.PersistenceInvariantViolationException;
import dev.junyoung.trading.engine.application.exception.RetryablePersistenceException;
import dev.junyoung.trading.engine.application.metrics.EngineMetrics;
import dev.junyoung.trading.order.application.port.out.BalanceSettlementPort;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
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
	private final EngineMetrics engineMetrics;

	public void persistPlaceResult(PlaceCalculationResult.Accepted accepted) {
		Instant txStart = Instant.now();
		try {
			updateOrders(accepted.updatedOrders());
			saveTrades(accepted.trades());
			settleBalances(new SettlementInput(accepted.updatedOrders(), accepted.trades()));
		} catch (IllegalStateException e) {
			engineMetrics.incrementDbRollback();
			throw new PersistenceInvariantViolationException("Persistence invariant violated while persisting place result", e);
		} catch (RuntimeException e) {
			classifyAndCountError(e);
			throw new RetryablePersistenceException("Retryable persistence failure while persisting place result", e);
		} finally {
			engineMetrics.recordEngineResultTxLatency(Duration.between(txStart, Instant.now()));
		}
	}

	public void persistCancelResult(CancelCalculationResult.Cancelled cancelled) {
		Instant txStart = Instant.now();
		try {
			saveCancelledOrder(cancelled);
			settleBalances(new SettlementInput(cancelled.updatedOrders(), List.of()));
		} catch (IllegalStateException e) {
			engineMetrics.incrementDbRollback();
			throw new PersistenceInvariantViolationException("Persistence invariant violated while persisting cancel result", e);
		} catch (RuntimeException e) {
			classifyAndCountError(e);
			throw new RetryablePersistenceException("Retryable persistence failure while persisting cancel result", e);
		} finally {
			engineMetrics.recordEngineResultTxLatency(Duration.between(txStart, Instant.now()));
		}
	}

	private void updateOrders(List<Order> updatedOrders) {
		orderRepository.updateAll(updatedOrders);
	}

	private void saveTrades(List<Trade> trades) {
		tradeRepository.saveAll(trades);
		if (!trades.isEmpty())
			engineMetrics.incrementTradesPerSec(trades.size());
	}

	private void saveCancelledOrder(CancelCalculationResult.Cancelled cancelled) {
		orderRepository.save(cancelled.updatedOrders().getFirst());
	}

	private void settleBalances(SettlementInput input) {
		SettlementResult result = SettlementCalculator.settle(input);
		for (SettlementResult.BalanceDelta balanceDelta : result.balanceDeltas()) {
			Instant lockStart = Instant.now();
			balanceSettlementPort.balanceSettlement(
				balanceDelta.accountId(),
				balanceDelta.asset(),
				balanceDelta.availableDelta(),
				balanceDelta.heldDelta()
			);
			engineMetrics.recordDbLockWaitTime(Duration.between(lockStart, Instant.now()));
			engineMetrics.incrementBalanceLockContention();
		}
	}

	private void classifyAndCountError(RuntimeException e) {
		if (isDeadlock(e)) {
			engineMetrics.incrementDbDeadlock();
		} else if (isCommitFailure(e)) {
			engineMetrics.incrementDbCommitFailure();
		}
		engineMetrics.incrementDbRollback();
	}

	private boolean isDeadlock(Throwable e) {
		if (e.getMessage() != null && e.getMessage().toLowerCase().contains("deadlock")) return true;
		return e.getCause() != null && e.getCause().getMessage() != null
			&& e.getCause().getMessage().toLowerCase().contains("deadlock");
	}

	private boolean isCommitFailure(Throwable e) {
		return e instanceof TransactionSystemException;
	}
}
