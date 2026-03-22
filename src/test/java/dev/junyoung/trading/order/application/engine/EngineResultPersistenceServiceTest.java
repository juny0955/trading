package dev.junyoung.trading.order.application.engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.order.application.port.out.BalanceSettlementPort;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.SettlementCalculatorTest;
import dev.junyoung.trading.order.fixture.OrderFixture;

/**
 * {@link EngineResultPersistenceService} 단위 테스트.
 *
 * <p>SettlementCalculator는 static 유틸리티이므로 실제 계산을 수행한다.
 * 정산 계산 자체의 정확성은 {@link SettlementCalculatorTest}에서 별도 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EngineResultPersistenceService")
class EngineResultPersistenceServiceTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private TradeRepository tradeRepository;

	@Mock
	private BalanceSettlementPort balanceSettlementPort;

	private EngineResultPersistenceService service;

	private static final Symbol BTC = new Symbol("BTC");
	private static final AccountId BUYER = new AccountId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
	private static final AccountId SELLER = new AccountId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

	@BeforeEach
	void setUp() {
		service = new EngineResultPersistenceService(orderRepository, tradeRepository, balanceSettlementPort);
	}

	private Order activatedBuyLimit(long price, long qty) {
		return OrderFixture.createLimit(BUYER, Side.BUY, BTC, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
	}

	private Order activatedSellLimit(long price, long qty) {
		return OrderFixture.createLimit(SELLER, Side.SELL, BTC, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
	}

	// ── persistPlaceResult ───────────────────────────────────────────────────

	@Nested
	@DisplayName("persistPlaceResult")
	class PersistPlaceResult {

		@Test
		@DisplayName("updatedOrders와 trades가 없으면 updateAll·saveAll이 호출되고 balanceSettlement는 호출되지 않는다")
		void emptyResult_updatesRepositoriesWithoutSettlement() {
			var accepted = new PlaceCalculationResult.Accepted(BTC, 1L, List.of(), List.of(), List.of());

			service.persistPlaceResult(accepted);

			verify(orderRepository).updateAll(List.of());
			verify(tradeRepository).saveAll(List.of());
			verifyNoInteractions(balanceSettlementPort);
		}

		@Test
		@DisplayName("trade가 있으면 settlement delta 수만큼 balanceSettlement가 호출된다")
		void withTrade_callsBalanceSettlementPerDelta() {
			Order buy = activatedBuyLimit(10_000, 1).fill(new Quantity(1), new Price(10_000));
			Order sell = activatedSellLimit(10_000, 1).fill(new Quantity(1), new Price(10_000));
			Trade trade = Trade.of(buy, sell, new Quantity(1));
			var accepted = new PlaceCalculationResult.Accepted(BTC, 1L, List.of(buy, sell), List.of(trade), List.of());

			service.persistPlaceResult(accepted);

			// BUYER/KRW, BUYER/BTC, SELLER/BTC, SELLER/KRW → 4개 delta
			verify(balanceSettlementPort, times(4)).balanceSettlement(any(), any(), anyLong(), anyLong());
		}

		@Test
		@DisplayName("호출 순서: orderRepository.updateAll → tradeRepository.saveAll → balanceSettlement")
		void callOrder_updateAll_saveAll_balanceSettlement() {
			Order buy = activatedBuyLimit(10_000, 1).fill(new Quantity(1), new Price(10_000));
			Order sell = activatedSellLimit(10_000, 1).fill(new Quantity(1), new Price(10_000));
			Trade trade = Trade.of(buy, sell, new Quantity(1));
			var accepted = new PlaceCalculationResult.Accepted(BTC, 1L, List.of(buy, sell), List.of(trade), List.of());

			service.persistPlaceResult(accepted);

			InOrder inOrder = inOrder(orderRepository, tradeRepository, balanceSettlementPort);
			inOrder.verify(orderRepository).updateAll(any());
			inOrder.verify(tradeRepository).saveAll(any());
			inOrder.verify(balanceSettlementPort, atLeastOnce()).balanceSettlement(any(), any(), anyLong(), anyLong());
		}
	}

	// ── persistCancelResult ──────────────────────────────────────────────────

	@Nested
	@DisplayName("persistCancelResult")
	class PersistCancelResult {

		@Test
		@DisplayName("취소된 주문을 orderRepository.save로 저장한다")
		void savesTheCancelledOrder() {
			Order cancelledOrder = activatedBuyLimit(10_000, 5).cancel();
			var cancelled = new CancelCalculationResult.Cancelled(BTC, 1L, List.of(cancelledOrder), List.of());

			service.persistCancelResult(cancelled);

			verify(orderRepository).save(cancelledOrder);
		}

		@Test
		@DisplayName("LIMIT BUY 취소 시 잔여 KRW hold를 반환하는 balanceSettlement가 1회 호출된다")
		void limitBuyCancel_refundsHold_callsBalanceSettlementOnce() {
			Order cancelledOrder = activatedBuyLimit(10_000, 5).cancel();
			var cancelled = new CancelCalculationResult.Cancelled(BTC, 1L, List.of(cancelledOrder), List.of());

			service.persistCancelResult(cancelled);

			verify(balanceSettlementPort, times(1)).balanceSettlement(any(), any(), anyLong(), anyLong());
		}

		@Test
		@DisplayName("호출 순서: orderRepository.save → balanceSettlement")
		void callOrder_save_balanceSettlement() {
			Order cancelledOrder = activatedBuyLimit(10_000, 5).cancel();
			var cancelled = new CancelCalculationResult.Cancelled(BTC, 1L, List.of(cancelledOrder), List.of());

			service.persistCancelResult(cancelled);

			InOrder inOrder = inOrder(orderRepository, balanceSettlementPort);
			inOrder.verify(orderRepository).save(cancelledOrder);
			inOrder.verify(balanceSettlementPort).balanceSettlement(any(), any(), anyLong(), anyLong());
		}
	}
}
