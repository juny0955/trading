package dev.junyoung.trading.order.adapter.in.rest;

import dev.junyoung.trading.order.adapter.in.rest.request.PlaceOrderRequest;
import dev.junyoung.trading.order.adapter.in.rest.response.PlaceOrderResponse;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.GetOrderUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Mock
    private PlaceOrderUseCase placeOrderUseCase;

    @Mock
    private CancelOrderUseCase cancelOrderUseCase;

    @Mock
    private GetOrderUseCase getOrderUseCase;

    @InjectMocks
    private OrderController sut;

    @Test
    @DisplayName("잘못된 side 입력이면 IllegalArgumentException을 던진다")
    void placeOrder_invalidSide_throwsIllegalArgumentException() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "INVALID", "LIMIT", null, 10_000L, null, 1L, null);

        assertThatThrownBy(() -> sut.placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("잘못된 orderType 입력이면 IllegalArgumentException을 던진다")
    void placeOrder_invalidOrderType_throwsIllegalArgumentException() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "BUY", "INVALID", null, 10_000L, null, 1L, null);

        assertThatThrownBy(() -> sut.placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("LIMIT + price=null이면 useCase에 위임한다")
    void placeOrder_limitWithoutPrice_delegatesToUseCase() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "BUY", "LIMIT", null, null, null, 1L, null);
        when(placeOrderUseCase.placeOrder(any())).thenReturn("order-1");

        ResponseEntity<PlaceOrderResponse> response = sut.placeOrder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(placeOrderUseCase).placeOrder(any());
    }

    @Test
    @DisplayName("side/orderType가 모두 잘못되면 IllegalArgumentException을 던진다")
    void placeOrder_invalidSideAndOrderType_throwsIllegalArgumentException() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "WRONG", "BAD", null, null, null, 1L, null);

        assertThatThrownBy(() -> sut.placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("유효한 요청이면 useCase에 위임하고 202를 반환한다")
    void placeOrder_validRequest_delegatesToUseCaseAndReturnsAccepted() {
        PlaceOrderRequest request = new PlaceOrderRequest("BTC", "BUY", "LIMIT", null, 10_000L, null, 1L, null);
        when(placeOrderUseCase.placeOrder(any())).thenReturn("order-1");

        ResponseEntity<PlaceOrderResponse> response = sut.placeOrder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isEqualTo("order-1");
        verify(placeOrderUseCase).placeOrder(any());
    }
}
