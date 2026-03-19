package dev.junyoung.trading.order.adapter.in.rest;

import java.time.Instant;
import java.util.List;

import dev.junyoung.trading.order.adapter.in.rest.request.PlaceOrderRequest;
import dev.junyoung.trading.order.adapter.in.rest.response.OrderResponse;
import dev.junyoung.trading.order.adapter.in.rest.response.PlaceOrderResponse;
import dev.junyoung.trading.order.adapter.in.rest.response.TradeResponse;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.GetOrderUseCase;
import dev.junyoung.trading.order.application.port.in.GetTradeUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import dev.junyoung.trading.order.application.port.in.result.TradeResult;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController")
class OrderControllerTest {

    private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ORDER_ID = "order-1";
    private static final OrderId PLACE_ORDER_ID = OrderId.newId();

    @Mock
    private PlaceOrderUseCase placeOrderUseCase;

    @Mock
    private CancelOrderUseCase cancelOrderUseCase;

    @Mock
    private GetOrderUseCase getOrderUseCase;

    @Mock
    private GetTradeUseCase getTradeUseCase;

    @InjectMocks
    private OrderController sut;

    private static final TradeResult TRADE_RESULT = new TradeResult(
            "trade-1",
            "BTC",
            ORDER_ID,
            "BUY",
            50_000L,
            10L,
            Instant.parse("2026-03-10T00:00:00Z")
    );

    @Test
    @DisplayName("잘못된 side 값이면 IllegalArgumentException이 발생한다")
    void placeOrder_invalidSide_throwsIllegalArgumentException() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "INVALID", "LIMIT", null, 10_000L, null, 1L, "client-001");

        assertThatThrownBy(() -> sut.placeOrder(ACCOUNT_ID, request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("잘못된 orderType 값이면 IllegalArgumentException이 발생한다")
    void placeOrder_invalidOrderType_throwsIllegalArgumentException() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "BUY", "INVALID", null, 10_000L, null, 1L, "client-001");

        assertThatThrownBy(() -> sut.placeOrder(ACCOUNT_ID, request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("정상 요청이면 account scope 커맨드를 위임하고 202를 반환한다")
    void placeOrder_validRequest_delegatesToUseCaseAndReturnsAccepted() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "BUY", "LIMIT", null, 10_000L, null, 1L, "client-001");
        when(placeOrderUseCase.placeOrder(any())).thenReturn(PLACE_ORDER_ID);

        ResponseEntity<PlaceOrderResponse> response = sut.placeOrder(ACCOUNT_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isEqualTo(PLACE_ORDER_ID.value());
        verify(placeOrderUseCase).placeOrder(any());
    }

    @Test
    @DisplayName("생성 요청은 path의 accountId를 커맨드로 전달한다")
    void placeOrder_passesAccountIdToCommand() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "BUY", "LIMIT", null, 10_000L, null, 1L, "client-001");
        when(placeOrderUseCase.placeOrder(any())).thenReturn(PLACE_ORDER_ID);

        sut.placeOrder(ACCOUNT_ID, request);

        ArgumentCaptor<dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand> captor =
                forClass(dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand.class);
        verify(placeOrderUseCase).placeOrder(captor.capture());
        assertThat(captor.getValue().accountId().value().toString()).isEqualTo(ACCOUNT_ID);
        assertThat(captor.getValue().clientOrderId()).isEqualTo("client-001");
    }

    @Test
    @DisplayName("취소 요청은 accountId와 orderId를 useCase에 전달하고 202를 반환한다")
    void cancelOrder_delegatesToUseCaseAndReturnsAccepted() {
        ResponseEntity<Void> response = sut.cancelOrder(ACCOUNT_ID, ORDER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(cancelOrderUseCase).cancelOrder(ACCOUNT_ID, ORDER_ID);
    }

    @Test
    @DisplayName("조회 요청은 accountId와 orderId를 useCase에 전달하고 200을 반환한다")
    void getOrder_delegatesToUseCaseAndReturnsOk() {
        OrderResult result = new OrderResult(
                ORDER_ID,
                "BUY",
                10_000L,
                1L,
                1L,
                "ACCEPTED",
                Instant.parse("2026-03-10T00:00:00Z"),
                null,
                1L,
                null,
                0L,
                null
        );
        when(getOrderUseCase.getOrder(ACCOUNT_ID, ORDER_ID)).thenReturn(result);

        ResponseEntity<OrderResponse> response = sut.getOrder(ACCOUNT_ID, ORDER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isEqualTo(ORDER_ID);
        assertThat(response.getBody().side()).isEqualTo("BUY");
        verify(getOrderUseCase).getOrder(ACCOUNT_ID, ORDER_ID);
    }

    @Nested
    @DisplayName("getTradesByOrder()")
    class GetTradesByOrder {

        @Test
        @DisplayName("useCase에 accountId와 orderId를 전달하고 200을 반환한다")
        void delegatesToUseCaseAndReturnsOk() {
            when(getTradeUseCase.getTradesByOrder(ACCOUNT_ID, ORDER_ID)).thenReturn(List.of());

            ResponseEntity<List<TradeResponse>> response = sut.getTradesByOrder(ACCOUNT_ID, ORDER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(getTradeUseCase).getTradesByOrder(ACCOUNT_ID, ORDER_ID);
        }

        @Test
        @DisplayName("TradeResult가 TradeResponse로 변환되어 응답 body에 포함된다")
        void tradeResultMappedToResponseBody() {
            when(getTradeUseCase.getTradesByOrder(ACCOUNT_ID, ORDER_ID)).thenReturn(List.of(TRADE_RESULT));

            ResponseEntity<List<TradeResponse>> response = sut.getTradesByOrder(ACCOUNT_ID, ORDER_ID);

            assertThat(response.getBody()).hasSize(1);
            TradeResponse body = response.getBody().getFirst();
            assertThat(body.tradeId()).isEqualTo(TRADE_RESULT.tradeId());
            assertThat(body.symbol()).isEqualTo(TRADE_RESULT.symbol());
            assertThat(body.orderId()).isEqualTo(TRADE_RESULT.orderId());
            assertThat(body.side()).isEqualTo(TRADE_RESULT.side());
            assertThat(body.price()).isEqualTo(TRADE_RESULT.price());
            assertThat(body.quantity()).isEqualTo(TRADE_RESULT.quantity());
        }
    }

    @Nested
    @DisplayName("getTradesByAccount()")
    class GetTradesByAccount {

        @Test
        @DisplayName("useCase에 accountId를 전달하고 200을 반환한다")
        void delegatesToUseCaseAndReturnsOk() {
            when(getTradeUseCase.getTradesByAccount(ACCOUNT_ID)).thenReturn(List.of());

            ResponseEntity<List<TradeResponse>> response = sut.getTradesByAccount(ACCOUNT_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(getTradeUseCase).getTradesByAccount(ACCOUNT_ID);
        }

        @Test
        @DisplayName("TradeResult가 TradeResponse로 변환되어 응답 body에 포함된다")
        void tradeResultMappedToResponseBody() {
            when(getTradeUseCase.getTradesByAccount(ACCOUNT_ID)).thenReturn(List.of(TRADE_RESULT));

            ResponseEntity<List<TradeResponse>> response = sut.getTradesByAccount(ACCOUNT_ID);

            assertThat(response.getBody()).hasSize(1);
            TradeResponse body = response.getBody().getFirst();
            assertThat(body.tradeId()).isEqualTo(TRADE_RESULT.tradeId());
            assertThat(body.side()).isEqualTo(TRADE_RESULT.side());
            assertThat(body.price()).isEqualTo(TRADE_RESULT.price());
        }
    }
}
