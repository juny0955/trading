package dev.junyoung.trading.order.application.engine.handler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;

import dev.junyoung.trading.order.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.order.application.exception.engine.PersistenceInvariantViolationException;
import dev.junyoung.trading.order.application.exception.engine.RetryablePersistenceException;
import dev.junyoung.trading.order.application.metrics.EngineMetrics;
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
			// 계산 결과와 persistence 모델이 논리적으로 충돌하는 경우로 본다.
			engineMetrics.incrementDbRollback();
			throw new PersistenceInvariantViolationException("Persistence invariant violated while persisting place result", e);
		} catch (RuntimeException e) {
			// 나머지 런타임 예외는 우선 재시도 가능 실패로 분류한다.
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
			// 계산 결과와 persistence 모델이 논리적으로 충돌하는 경우로 본다.
			engineMetrics.incrementDbRollback();
			throw new PersistenceInvariantViolationException("Persistence invariant violated while persisting cancel result", e);
		} catch (RuntimeException e) {
			// 나머지 런타임 예외는 우선 재시도 가능 실패로 분류한다.
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
		// H2 deadlock detection (dev environment)
		if (e.getMessage() != null && e.getMessage().toLowerCase().contains("deadlock")) return true;
		if (e.getCause() != null && e.getCause().getMessage() != null
				&& e.getCause().getMessage().toLowerCase().contains("deadlock")) return true;
		return false;
	}

	private boolean isCommitFailure(Throwable e) {
		return e instanceof TransactionSystemException;
	}
}
