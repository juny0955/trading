package dev.junyoung.trading.order.application.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.order.application.port.out.BalanceSettlementPort;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.dto.PlaceResult;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService")
class SettlementServiceTest {

    private static final AccountId ACCOUNT_A =
            new AccountId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final AccountId ACCOUNT_B =
            new AccountId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final Asset KRW = new Asset("KRW");
    private static final Asset BTC = new Asset("BTC");
    private static final Symbol BTC_SYMBOL = new Symbol("BTC");
    private static final Price P_10000 = new Price(10_000L);
    private static final Price P_11000 = new Price(11_000L);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private BalanceSettlementPort balanceSettlementPort;

    @InjectMocks
    private SettlementService sut;

    @Nested
    @DisplayName("settlement() - 위임 검증")
    class Delegation {

        @Test
        @DisplayName("updatedOrders를 orderRepository.updateAll()에 위임한다")
        void delegatesToOrderRepository() {
            Order order = OrderFixture.createLimit(ACCOUNT_A, Side.BUY, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(3));
            order.activate();
            PlaceResult result = PlaceResult.of(List.of(order), List.of());

            sut.settlement(result);

            verify(orderRepository).updateAll(result.updatedOrders());
        }

        @Test
        @DisplayName("trades를 tradeRepository.saveAll()에 위임한다")
        void delegatesToTradeRepository() {
            Order buy = OrderFixture.createLimit(ACCOUNT_A, Side.BUY, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(3));
            Order sell = OrderFixture.createLimit(ACCOUNT_B, Side.SELL, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(3));
            buy.activate();
            sell.activate();
            buy.fill(new Quantity(3), P_10000);
            sell.fill(new Quantity(3), P_10000);
            Trade trade = Trade.of(buy, sell, new Quantity(3));
            PlaceResult result = PlaceResult.of(List.of(buy, sell), List.of(trade));

            sut.settlement(result);

            verify(tradeRepository).saveAll(result.trades());
        }
    }

    @Nested
    @DisplayName("settlement() - balanceSettlement 미호출 케이스")
    class NoBalanceSettlement {

        @Test
        @DisplayName("updatedOrders와 trades가 모두 비어있으면 balanceSettlementPort가 호출되지 않는다")
        void emptyResult_noBalanceSettlement() {
            PlaceResult result = PlaceResult.of(List.of(), List.of());

            sut.settlement(result);

            verify(balanceSettlementPort, never()).balanceSettlement(any(), any(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("GTC 주문이 NEW 상태(체결 없음)이면 balanceSettlementPort가 호출되지 않는다")
        void newGtcOrder_noTrade_noBalanceSettlement() {
            Order buy = OrderFixture.createLimit(ACCOUNT_A, Side.BUY, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(3));
            buy.activate(); // → NEW
            PlaceResult result = PlaceResult.of(List.of(buy), List.of());

            sut.settlement(result);

            verify(balanceSettlementPort, never()).balanceSettlement(any(), any(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("PARTIALLY_FILLED(alive) 주문은 trade delta만 적용되고 refund는 발생하지 않는다")
        void partiallyFilledGtcOrder_alive_noRefund() {
            Order buy = OrderFixture.createLimit(ACCOUNT_A, Side.BUY, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(5));
            Order sell = OrderFixture.createLimit(ACCOUNT_B, Side.SELL, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(2));
            buy.activate();
            sell.activate();
            buy.fill(new Quantity(2), P_10000);  // → PARTIALLY_FILLED (remaining=3, alive)
            sell.fill(new Quantity(2), P_10000); // → FILLED
            Trade trade = Trade.of(buy, sell, new Quantity(2));
            PlaceResult result = PlaceResult.of(List.of(buy, sell), List.of(trade));

            sut.settlement(result);

            // trade delta만 호출 (A는 isFinal()=false → refund 스킵)
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, KRW, 0L, -20_000L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, BTC, 2L, 0L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, BTC, 0L, -2L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, KRW, 20_000L, 0L);
            verifyNoMoreInteractions(balanceSettlementPort);
        }
    }

    @Nested
    @DisplayName("settlement() - 체결 delta 검증")
    class DeltaVerification {

        @Test
        @DisplayName("LIMIT BUY/SELL 완전 체결 — 정가 체결 시 held 차감 및 자산 교환")
        void limitBuyAndSell_fullyFilled_atExactPrice() {
            Order buy = OrderFixture.createLimit(ACCOUNT_A, Side.BUY, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(3));
            Order sell = OrderFixture.createLimit(ACCOUNT_B, Side.SELL, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(3));
            buy.activate();
            sell.activate();
            buy.fill(new Quantity(3), P_10000);
            sell.fill(new Quantity(3), P_10000);
            Trade trade = Trade.of(buy, sell, new Quantity(3));
            PlaceResult result = PlaceResult.of(List.of(buy, sell), List.of(trade));

            sut.settlement(result);

            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, KRW, 0L, -30_000L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, BTC, 3L, 0L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, BTC, 0L, -3L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, KRW, 30_000L, 0L);
        }

        @Test
        @DisplayName("LIMIT BUY 가격 개선 — executionPrice < limitPrice 시 초과 hold 환불")
        void limitBuy_priceImprovement_refundsExcessHeld() {
            // A: limitPrice=11000, executionPrice=10000 → originalHold=33000, consumed=30000, refund=3000
            Order buy = OrderFixture.createLimit(ACCOUNT_A, Side.BUY, BTC_SYMBOL, TimeInForce.GTC, P_11000, new Quantity(3));
            Order sell = OrderFixture.createLimit(ACCOUNT_B, Side.SELL, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(3));
            buy.activate();
            sell.activate();
            buy.fill(new Quantity(3), P_10000);
            sell.fill(new Quantity(3), P_10000);
            Trade trade = Trade.of(buy, sell, new Quantity(3));
            PlaceResult result = PlaceResult.of(List.of(buy, sell), List.of(trade));

            sut.settlement(result);

            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, KRW, 3_000L, -33_000L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, BTC, 3L, 0L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, BTC, 0L, -3L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, KRW, 30_000L, 0L);
        }

        @Test
        @DisplayName("FOK 취소 — 체결 없이 전액 hold 환불")
        void fokOrder_cancelled_fullRefund() {
            // originalHold=50000, consumed=0, refund=50000
            Order buy = OrderFixture.createLimit(ACCOUNT_A, Side.BUY, BTC_SYMBOL, TimeInForce.FOK, P_10000, new Quantity(5));
            buy.activate();
            buy.cancel();
            PlaceResult result = PlaceResult.of(List.of(buy), List.of());

            sut.settlement(result);

            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, KRW, 50_000L, -50_000L);
        }

        @Test
        @DisplayName("IOC BUY 부분 체결 후 취소 — 미소진 hold 환불")
        void iocBuy_partiallyFilledThenCancelled_refundsRemainingHeld() {
            // originalHold=50000, consumed=20000, refund=30000
            Order buy = OrderFixture.createLimit(ACCOUNT_A, Side.BUY, BTC_SYMBOL, TimeInForce.IOC, P_10000, new Quantity(5));
            Order sell = OrderFixture.createLimit(ACCOUNT_B, Side.SELL, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(2));
            buy.activate();
            sell.activate();
            buy.fill(new Quantity(2), P_10000);  // → PARTIALLY_FILLED, cumQuoteQty=20000
            sell.fill(new Quantity(2), P_10000); // → FILLED
            buy.cancel();                        // → CANCELLED
            Trade trade = Trade.of(buy, sell, new Quantity(2));
            PlaceResult result = PlaceResult.of(List.of(buy, sell), List.of(trade));

            sut.settlement(result);

            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, KRW, 30_000L, -50_000L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, BTC, 2L, 0L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, BTC, 0L, -2L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, KRW, 20_000L, 0L);
        }

        @Test
        @DisplayName("MARKET BUY 미사용 예산 환불 — cumQuoteQty < quoteQty 시 차액 반환")
        void marketBuy_unusedBudgetRefunded() {
            // originalHold=quoteQty=50000, consumed=cumQuoteQty=30000, refund=20000
            Order buy = OrderFixture.createMarketBuyWithQuoteQty(ACCOUNT_A, Side.BUY, BTC_SYMBOL, new QuoteQty(50_000L));
            Order sell = OrderFixture.createLimit(ACCOUNT_B, Side.SELL, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(3));
            buy.activate();
            sell.activate();
            buy.fillQuoteMode(new Quantity(3), P_10000); // cumQuoteQty=30000
            sell.fill(new Quantity(3), P_10000);         // → FILLED
            buy.markFilledByMarketBuy();                 // → FILLED
            Trade trade = Trade.of(buy, sell, new Quantity(3));
            PlaceResult result = PlaceResult.of(List.of(buy, sell), List.of(trade));

            sut.settlement(result);

            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, KRW, 20_000L, -50_000L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, BTC, 3L, 0L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, BTC, 0L, -3L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, KRW, 30_000L, 0L);
        }

        @Test
        @DisplayName("LIMIT SELL IOC 잔량 취소 — 미체결 BTC hold 환불")
        void limitSellIoc_partiallyFilledThenCancelled_refundsRemainingBtcHeld() {
            // A: originalHold=5(BTC), consumed=cumBaseQty=2, refund=3
            Order sell = OrderFixture.createLimit(ACCOUNT_A, Side.SELL, BTC_SYMBOL, TimeInForce.IOC, P_10000, new Quantity(5));
            Order buy = OrderFixture.createLimit(ACCOUNT_B, Side.BUY, BTC_SYMBOL, TimeInForce.GTC, P_10000, new Quantity(2));
            sell.activate();
            buy.activate();
            sell.fill(new Quantity(2), P_10000); // → PARTIALLY_FILLED, cumBaseQty=2
            buy.fill(new Quantity(2), P_10000);  // → FILLED
            sell.cancel();                       // → CANCELLED
            Trade trade = Trade.of(sell, buy, new Quantity(2)); // taker=sell, maker=buy
            PlaceResult result = PlaceResult.of(List.of(sell, buy), List.of(trade));

            sut.settlement(result);

            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, BTC, 3L, -5L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_A, KRW, 20_000L, 0L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, BTC, 2L, 0L);
            verify(balanceSettlementPort).balanceSettlement(ACCOUNT_B, KRW, 0L, -20_000L);
        }
    }
}
