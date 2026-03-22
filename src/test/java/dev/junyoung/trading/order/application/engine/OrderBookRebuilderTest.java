package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderBookRebuilder")
class OrderBookRebuilderTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderBookRebuilder rebuilder;

    private static final Symbol SYMBOL = new Symbol("BTC");

    @BeforeEach
    void setUp() {
        rebuilder = new OrderBookRebuilder(orderRepository);
    }

    @Test
    @DisplayName("loadOpenOrders()는 OrderRepository.findOpenOrdersBySymbol()에 위임한다")
    void loadOpenOrders_delegatesToRepository() {
        Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(5)).activate();
        List<Order> expected = List.of(order);
        when(orderRepository.findOpenOrdersBySymbol(SYMBOL)).thenReturn(expected);

        List<Order> result = rebuilder.loadOpenOrders(SYMBOL);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("오픈 주문이 없으면 빈 리스트를 반환한다")
    void loadOpenOrders_noOrders_returnsEmptyList() {
        when(orderRepository.findOpenOrdersBySymbol(SYMBOL)).thenReturn(List.of());

        List<Order> result = rebuilder.loadOpenOrders(SYMBOL);

        assertThat(result).isEmpty();
    }
}
