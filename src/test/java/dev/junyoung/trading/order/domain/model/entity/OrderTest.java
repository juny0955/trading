package dev.junyoung.trading.order.domain.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.common.exception.ConflictException;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;

@DisplayName("Order")
class OrderTest {

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private static final Symbol SYMBOL = new Symbol("BTC");

    private Order buyOrder(long price, long qty) {
        return Order.createLimit(Side.BUY, SYMBOL, new Price(price), new Quantity(qty));
    }

    private Order sellOrder(long price, long qty) {
        return Order.createLimit(Side.SELL, SYMBOL, new Price(price), new Quantity(qty));
    }

    /** ACCEPTED → activate() → NEW 상태인 BUY 주문 */
    private Order newBuyOrder(long price, long qty) {
        Order order = buyOrder(price, qty);
        order.activate();
        return order;
    }

    // ── 생성 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("생성")
    class Creation {

        @Nested
        @DisplayName("LIMIT 주문")
        class LimitCreation {

            @Test
            @DisplayName("BUY LIMIT 주문을 정상 생성한다")
            void createBuyLimitOrder() {
                Order order = Order.createLimit(Side.BUY, SYMBOL, new Price(10_000), new Quantity(5));

                assertThat(order.getOrderId()).isNotNull();
                assertThat(order.getSide()).isEqualTo(Side.BUY);
                assertThat(order.getSymbol()).isEqualTo(SYMBOL);
                assertThat(order.getOrderType()).isEqualTo(OrderType.LIMIT);
                assertThat(order.getLimitPriceOrThrow()).isEqualTo(new Price(10_000));
                assertThat(order.getQuantity()).isEqualTo(new Quantity(5));
                assertThat(order.getRemaining()).isEqualTo(new Quantity(5));
                assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
                assertThat(order.getOrderedAt()).isNotNull();
            }

            @Test
            @DisplayName("SELL LIMIT 주문을 정상 생성한다")
            void createSellLimitOrder() {
                Order order = sellOrder(9_500, 3);

                assertThat(order.getSide()).isEqualTo(Side.SELL);
                assertThat(order.getOrderType()).isEqualTo(OrderType.LIMIT);
                assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            }

            @Test
            @DisplayName("초기 상태는 ACCEPTED이다")
            void initialStatusIsAccepted() {
                assertThat(buyOrder(10_000, 5).getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            }

            @Test
            @DisplayName("초기 remaining은 quantity와 같다")
            void initialRemainingEqualsQuantity() {
                Order order = buyOrder(10_000, 7);
                assertThat(order.getRemaining()).isEqualTo(order.getQuantity());
            }

            @Test
            @DisplayName("quantity = 1(최솟값) 주문을 생성할 수 있다")
            void createOrderWithMinQuantity() {
                assertThatCode(() -> buyOrder(1, 1)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("quantity = 0이면 BusinessRuleException이 발생한다")
            void rejectZeroQuantity() {
                assertThrows(BusinessRuleException.class, () -> buyOrder(10_000, 0));
            }

            @Test
            @DisplayName("quantity < 0이면 BusinessRuleException이 발생한다 (Quantity VO 검증)")
            void rejectNegativeQuantity() {
                assertThrows(BusinessRuleException.class, () -> buyOrder(10_000, -1));
            }

            @Test
            @DisplayName("side = null이면 NullPointerException이 발생한다")
            void rejectNullSide() {
                assertThatNullPointerException()
                        .isThrownBy(() -> Order.createLimit(null, SYMBOL, new Price(10_000), new Quantity(5)));
            }

            @Test
            @DisplayName("symbol = null이면 NullPointerException이 발생한다")
            void rejectNullSymbol() {
                assertThatNullPointerException()
                        .isThrownBy(() -> Order.createLimit(Side.BUY, null, new Price(10_000), new Quantity(5)));
            }

            @Test
            @DisplayName("price = null이면 NullPointerException이 발생한다")
            void rejectNullPrice() {
                assertThatNullPointerException()
                        .isThrownBy(() -> Order.createLimit(Side.BUY, SYMBOL, null, new Quantity(5)));
            }

            @Test
            @DisplayName("quantity = null이면 NullPointerException이 발생한다")
            void rejectNullQuantity() {
                assertThatNullPointerException()
                        .isThrownBy(() -> Order.createLimit(Side.BUY, SYMBOL, new Price(10_000), null));
            }

            @Test
            @DisplayName("각 주문은 고유한 orderId를 갖는다")
            void eachOrderHasUniqueId() {
                Order o1 = buyOrder(10_000, 5);
                Order o2 = buyOrder(10_000, 5);
                assertThat(o1.getOrderId()).isNotEqualTo(o2.getOrderId());
            }
        }

        @Nested
        @DisplayName("MARKET 주문")
        class MarketCreation {

            @Test
            @DisplayName("BUY MARKET 주문을 정상 생성한다")
            void createBuyMarketOrder() {
                Order order = Order.createMarket(Side.BUY, SYMBOL, new Quantity(5));

                assertThat(order.getOrderId()).isNotNull();
                assertThat(order.getSide()).isEqualTo(Side.BUY);
                assertThat(order.getSymbol()).isEqualTo(SYMBOL);
                assertThat(order.getOrderType()).isEqualTo(OrderType.MARKET);
                assertThat(order.getQuantity()).isEqualTo(new Quantity(5));
                assertThat(order.getRemaining()).isEqualTo(new Quantity(5));
                assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            }

            @Test
            @DisplayName("MARKET 주문에서 getLimitPriceOrThrow()를 호출하면 BusinessRuleException이 발생한다")
            void marketOrderThrowsOnGetPrice() {
                Order order = Order.createMarket(Side.BUY, SYMBOL, new Quantity(5));
                assertThrows(BusinessRuleException.class, order::getLimitPriceOrThrow);
            }

            @Test
            @DisplayName("isMarket()은 MARKET 주문에서 true를 반환한다")
            void isMarketReturnsTrueForMarketOrder() {
                Order order = Order.createMarket(Side.BUY, SYMBOL, new Quantity(5));
                assertThat(order.isMarket()).isTrue();
            }

            @Test
            @DisplayName("isMarket()은 LIMIT 주문에서 false를 반환한다")
            void isMarketReturnsFalseForLimitOrder() {
                Order order = buyOrder(10_000, 5);
                assertThat(order.isMarket()).isFalse();
            }

            @Test
            @DisplayName("MARKET 주문 side = null이면 NullPointerException이 발생한다")
            void rejectNullSide() {
                assertThatNullPointerException()
                        .isThrownBy(() -> Order.createMarket(null, SYMBOL, new Quantity(5)));
            }

            @Test
            @DisplayName("MARKET 주문 symbol = null이면 NullPointerException이 발생한다")
            void rejectNullSymbol() {
                assertThatNullPointerException()
                        .isThrownBy(() -> Order.createMarket(Side.BUY, null, new Quantity(5)));
            }
        }
    }

    // ── activate() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("activate()")
    class Activate {

        @Test
        @DisplayName("ACCEPTED → NEW로 전이한다")
        void acceptedToNew() {
            Order order = buyOrder(10_000, 5);
            order.activate();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        }

        @Test
        @DisplayName("NEW 상태에서 activate() 호출하면 ConflictException이 발생한다")
        void cannotActivateFromNew() {
            Order order = newBuyOrder(10_000, 5);
            assertThrows(ConflictException.class, order::activate);
        }

        @Test
        @DisplayName("PARTIALLY_FILLED 상태에서 activate() 호출하면 ConflictException이 발생한다")
        void cannotActivateFromPartiallyFilled() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(2));
            assertThrows(ConflictException.class, order::activate);
        }

        @Test
        @DisplayName("FILLED 상태에서 activate() 호출하면 ConflictException이 발생한다")
        void cannotActivateFromFilled() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(5));
            assertThrows(ConflictException.class, order::activate);
        }

        @Test
        @DisplayName("CANCELLED 상태에서 activate() 호출하면 ConflictException이 발생한다")
        void cannotActivateFromCancelled() {
            Order order = newBuyOrder(10_000, 5);
            order.cancel();
            assertThrows(ConflictException.class, order::activate);
        }
    }

    // ── fill() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fill()")
    class Fill {

        @Test
        @DisplayName("NEW 상태에서 부분 체결 → PARTIALLY_FILLED, remaining 감소")
        void partialFillFromNew() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(2));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(3));
        }

        @Test
        @DisplayName("NEW 상태에서 전량 체결 → FILLED, remaining = 0")
        void fullFillFromNew() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(5));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(0));
        }

        @Test
        @DisplayName("PARTIALLY_FILLED 상태에서 추가 부분 체결 → PARTIALLY_FILLED 유지, remaining 감소")
        void partialFillFromPartiallyFilled() {
            Order order = newBuyOrder(10_000, 10);
            order.fill(new Quantity(3));  // remaining = 7
            order.fill(new Quantity(4));  // remaining = 3

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(3));
        }

        @Test
        @DisplayName("PARTIALLY_FILLED 상태에서 잔량 전량 체결 → FILLED")
        void fullFillFromPartiallyFilled() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(2));  // remaining = 3
            order.fill(new Quantity(3));  // remaining = 0

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(0));
        }

        @Test
        @DisplayName("1개씩 여러 번 체결하여 전량 체결된다")
        void fillOneByOne() {
            Order order = newBuyOrder(10_000, 3);
            order.fill(new Quantity(1));
            order.fill(new Quantity(1));
            order.fill(new Quantity(1));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(0));
        }

        @Test
        @DisplayName("quantity = 1인 주문을 전량 체결한다")
        void fillMinQuantityOrder() {
            Order order = newBuyOrder(1, 1);
            order.fill(new Quantity(1));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(0));
        }

        @Test
        @DisplayName("원래 quantity는 체결 후에도 변경되지 않는다")
        void quantityUnchangedAfterFill() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(3));

            assertThat(order.getQuantity()).isEqualTo(new Quantity(5));
        }

        @Test
        @DisplayName("체결 수량이 잔량보다 크면 BusinessRuleException이 발생한다 (remaining 음수 방지)")
        void fillExceedingRemainingThrows() {
            Order order = newBuyOrder(10_000, 3);
            assertThrows(BusinessRuleException.class, () -> order.fill(new Quantity(4)));
        }

        @Test
        @DisplayName("부분 체결 후 체결 수량이 잔량보다 크면 BusinessRuleException이 발생한다")
        void fillExceedingRemainingAfterPartialFillThrows() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(3));  // remaining = 2
            assertThrows(BusinessRuleException.class, () -> order.fill(new Quantity(3)));
        }

        @Test
        @DisplayName("ACCEPTED 상태에서 fill() 호출하면 ConflictException이 발생한다")
        void cannotFillFromAccepted() {
            Order order = buyOrder(10_000, 5);
            assertThrows(ConflictException.class, () -> order.fill(new Quantity(1)));
        }

        @Test
        @DisplayName("FILLED 상태에서 fill() 호출하면 ConflictException이 발생한다")
        void cannotFillFromFilled() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(5));
            assertThrows(ConflictException.class, () -> order.fill(new Quantity(1)));
        }

        @Test
        @DisplayName("CANCELLED 상태에서 fill() 호출하면 ConflictException이 발생한다")
        void cannotFillFromCancelled() {
            Order order = newBuyOrder(10_000, 5);
            order.cancel();
            assertThrows(ConflictException.class, () -> order.fill(new Quantity(1)));
        }
    }

    // ── cancel() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("NEW 상태에서 취소 → CANCELLED")
        void cancelFromNew() {
            Order order = newBuyOrder(10_000, 5);
            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("PARTIALLY_FILLED 상태에서 취소 → CANCELLED")
        void cancelFromPartiallyFilled() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(2));
            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("취소 후 remaining은 변경되지 않는다")
        void cancelDoesNotChangeRemaining() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(2));  // remaining = 3
            order.cancel();
            assertThat(order.getRemaining()).isEqualTo(new Quantity(3));
        }

        @Test
        @DisplayName("ACCEPTED 상태에서 cancel() 호출하면 ConflictException이 발생한다")
        void cannotCancelFromAccepted() {
            Order order = buyOrder(10_000, 5);
            assertThrows(ConflictException.class, order::cancel);
        }

        @Test
        @DisplayName("FILLED 상태에서 cancel() 호출하면 ConflictException이 발생한다")
        void cannotCancelFromFilled() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(5));
            assertThrows(ConflictException.class, order::cancel);
        }

        @Test
        @DisplayName("CANCELLED 상태에서 cancel() 호출하면 ConflictException이 발생한다 (중복 취소 방지)")
        void cannotCancelFromCancelled() {
            Order order = newBuyOrder(10_000, 5);
            order.cancel();
            assertThrows(ConflictException.class, order::cancel);
        }
    }

    // ── 상태 전이 시나리오 ────────────────────────────────────────────────

    @Nested
    @DisplayName("상태 전이 시나리오")
    class StateTransitionScenarios {

        @Test
        @DisplayName("ACCEPTED → NEW → PARTIALLY_FILLED → FILLED 전체 흐름")
        void fullLifecycle_PartialThenFull() {
            Order order = buyOrder(10_000, 10);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

            order.activate();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);

            order.fill(new Quantity(4));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(6));

            order.fill(new Quantity(6));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(0));
        }

        @Test
        @DisplayName("ACCEPTED → NEW → CANCELLED 전체 흐름")
        void fullLifecycle_Cancel() {
            Order order = buyOrder(10_000, 5);

            order.activate();
            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("ACCEPTED → NEW → PARTIALLY_FILLED → CANCELLED 전체 흐름")
        void fullLifecycle_PartialThenCancel() {
            Order order = buyOrder(10_000, 5);

            order.activate();
            order.fill(new Quantity(2));
            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(3));
        }

        @Test
        @DisplayName("PARTIALLY_FILLED → PARTIALLY_FILLED → FILLED 반복 체결 흐름")
        void multiplePartialFillsThenFilled() {
            Order order = newBuyOrder(10_000, 9);

            order.fill(new Quantity(3));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(6));

            order.fill(new Quantity(3));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(3));

            order.fill(new Quantity(3));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(0));
        }

        @Test
        @DisplayName("MARKET 주문 ACCEPTED → NEW → FILLED 전체 흐름")
        void marketOrder_fullLifecycle_Filled() {
            Order order = Order.createMarket(Side.BUY, SYMBOL, new Quantity(5));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

            order.activate();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);

            order.fill(new Quantity(5));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        @DisplayName("MARKET 주문 유동성 부족 → PARTIALLY_FILLED → CANCELLED 전체 흐름")
        void marketOrder_fullLifecycle_PartialThenCancelled() {
            Order order = Order.createMarket(Side.BUY, SYMBOL, new Quantity(5));

            order.activate();
            order.fill(new Quantity(3));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);

            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getRemaining()).isEqualTo(new Quantity(2));
        }
    }

    // ── 종료 상태 불변성 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("종료 상태 불변성")
    class TerminalStateImmutability {

        @Test
        @DisplayName("FILLED 이후 fill()은 불가하다")
        void filledOrder_cannotBeFilled() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(5));
            assertThrows(ConflictException.class, () -> order.fill(new Quantity(1)));
        }

        @Test
        @DisplayName("FILLED 이후 cancel()은 불가하다")
        void filledOrder_cannotBeCancelled() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(5));
            assertThrows(ConflictException.class, order::cancel);
        }

        @Test
        @DisplayName("FILLED 이후 activate()는 불가하다")
        void filledOrder_cannotBeActivated() {
            Order order = newBuyOrder(10_000, 5);
            order.fill(new Quantity(5));
            assertThrows(ConflictException.class, order::activate);
        }

        @Test
        @DisplayName("CANCELLED 이후 fill()은 불가하다")
        void cancelledOrder_cannotBeFilled() {
            Order order = newBuyOrder(10_000, 5);
            order.cancel();
            assertThrows(ConflictException.class, () -> order.fill(new Quantity(1)));
        }

        @Test
        @DisplayName("CANCELLED 이후 cancel()은 불가하다")
        void cancelledOrder_cannotBeCancelledAgain() {
            Order order = newBuyOrder(10_000, 5);
            order.cancel();
            assertThrows(ConflictException.class, order::cancel);
        }

        @Test
        @DisplayName("CANCELLED 이후 activate()는 불가하다")
        void cancelledOrder_cannotBeActivated() {
            Order order = newBuyOrder(10_000, 5);
            order.cancel();
            assertThrows(ConflictException.class, order::activate);
        }
    }
}
