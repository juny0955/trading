package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.port.in.GetOrderUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.domain.model.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService implements GetOrderUseCase {

    private final OrderRepository orderRepository;

    @Override
    public OrderResult getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        return toResult(order);
    }

    // -------------------------------------------------------------------------
    // 내부 매핑
    // -------------------------------------------------------------------------

    private OrderResult toResult(Order order) {
        DerivedFields fields = deriveFields(order);

        return new OrderResult(
            order.getOrderId().toString(),
            order.getSide().name(),
            order.getPriceValue().orElse(null),
            order.getQuantityValue().orElse(null),
            order.getRemaining().value(),
            order.getStatus().name(),
            order.getOrderedAt(),
            fields.requestedQuoteQty(),
            fields.requestedQty(),
            fields.cumQuoteQty(),
            fields.cumBaseQty(),
            fields.leftoverQuoteQty()
        );
    }

    // -------------------------------------------------------------------------
    // 내부 계산
    // -------------------------------------------------------------------------

    private DerivedFields deriveFields(Order order) {
        return order.isQuoteQtyMode() ? deriveQuoteModeFields(order) : deriveQuantityModeFields(order);
    }

    private DerivedFields deriveQuoteModeFields(Order order) {
        Long requestedQuoteQty = order.getQuoteQty().value();
        Long cumQuoteQty = order.getCumQuoteQty();
        Long cumBaseQty = order.getCumBaseQty();
        Long leftoverQuoteQty = requestedQuoteQty - cumQuoteQty;

        return DerivedFields.ofQuoteMode(requestedQuoteQty, cumQuoteQty, cumBaseQty, leftoverQuoteQty);
    }

    private DerivedFields deriveQuantityModeFields(Order order) {
        return order.getQuantityValue()
            .map(requestedQty -> {
                long cumBaseQty = requestedQty - order.getRemaining().value();
                return DerivedFields.ofQuantityMode(requestedQty, cumBaseQty);
            })
            .orElse(DerivedFields.ofNull());
    }

    // -------------------------------------------------------------------------
    // 내부 타입
    // -------------------------------------------------------------------------

    private record DerivedFields(
        Long requestedQuoteQty,
        Long requestedQty,
        Long cumQuoteQty,
        Long cumBaseQty,
        Long leftoverQuoteQty
    ) {
        public static DerivedFields ofNull() {
            return new DerivedFields(null, null, null, null, null);
        }

        public static DerivedFields ofQuoteMode(Long requestedQuoteQty, Long cumQuoteQty, Long cumBaseQty, Long leftoverQuoteQty) {
            return new DerivedFields(requestedQuoteQty, null, cumQuoteQty, cumBaseQty, leftoverQuoteQty);
        }

        public static DerivedFields ofQuantityMode(Long requestedQty, Long cumBaseQty) {
            return new DerivedFields(null, requestedQty, null, cumBaseQty, null);
        }
    }
}
