package dev.junyoung.trading.order.adapter.in.rest.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import jakarta.validation.ConstraintValidatorContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.order.adapter.in.rest.request.PlaceOrderRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceOrderValidator")
class PlaceOrderValidatorTest {

    private PlaceOrderValidator sut;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    @BeforeEach
    void setUp() {
        sut = new PlaceOrderValidator();
        lenient().when(context.buildConstraintViolationWithTemplate(any())).thenReturn(builder);
        lenient().when(builder.addPropertyNode(any())).thenReturn(nodeBuilder);
        lenient().when(nodeBuilder.addConstraintViolation()).thenReturn(context);
    }

    private PlaceOrderRequest request(String side, String orderType, String tif, Long quoteQty, Long quantity) {
        return new PlaceOrderRequest("BTC", side, orderType, tif, null, quoteQty, quantity, null);
    }

    // ── MARKET BUY ────────────────────────────────────────────────────────

    @Test
    @DisplayName("MARKET BUY: quoteQty 없으면 false")
    void marketBuy_quoteQtyMissing_invalid() {
        assertThat(sut.isValid(request("BUY", "MARKET", null, null, null), context)).isFalse();
        verify(builder, atLeastOnce()).addPropertyNode("quoteQty");
    }

    @Test
    @DisplayName("MARKET BUY: quantity, quoteQty 둘 다 입력 → false")
    void marketBuy_bothPresent_invalid() {
        assertThat(sut.isValid(request("BUY", "MARKET", null, 50_000L, 5L), context)).isFalse();
        verify(builder, atLeastOnce()).addPropertyNode("quantity");
    }

    @Test
    @DisplayName("MARKET BUY: quoteQty만 입력 → true")
    void marketBuy_quoteQtyOnly_valid() {
        assertThat(sut.isValid(request("BUY", "MARKET", null, 50_000L, null), context)).isTrue();
    }

    @Test
    @DisplayName("MARKET BUY: quantity만 입력 → false")
    void marketBuy_quantityOnly_invalid() {
        assertThat(sut.isValid(request("BUY", "MARKET", null, null, 5L), context)).isFalse();
        verify(builder, atLeastOnce()).addPropertyNode("quoteQty");
    }

    // ── MARKET SELL ───────────────────────────────────────────────────────

    @Test
    @DisplayName("MARKET SELL: quantity=null → false")
    void marketSell_quantityNull_invalid() {
        assertThat(sut.isValid(request("SELL", "MARKET", null, null, null), context)).isFalse();
    }

    @Test
    @DisplayName("MARKET SELL: quantity 있음 → true")
    void marketSell_quantityPresent_valid() {
        assertThat(sut.isValid(request("SELL", "MARKET", null, null, 5L), context)).isTrue();
    }

    // ── LIMIT ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LIMIT: quantity=null → false")
    void limit_quantityNull_invalid() {
        assertThat(sut.isValid(request("BUY", "LIMIT", null, null, null), context)).isFalse();
        verify(builder, atLeastOnce()).addPropertyNode("quantity");
    }

    @Test
    @DisplayName("LIMIT: quantity 있음 → true")
    void limit_quantityPresent_valid() {
        assertThat(sut.isValid(request("BUY", "LIMIT", null, null, 5L), context)).isTrue();
    }

    // ── MARKET + TIF ──────────────────────────────────────────────────────

    @Test
    @DisplayName("MARKET: tif 입력 시 → false")
    void market_tifPresent_invalid() {
        assertThat(sut.isValid(request("BUY", "MARKET", "GTC", null, 5L), context)).isFalse();
        verify(builder, atLeastOnce()).addPropertyNode("tif");
    }
}
