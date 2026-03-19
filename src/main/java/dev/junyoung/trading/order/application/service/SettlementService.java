package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.port.out.BalanceSettlementPort;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.service.SettlementCalculator;
import dev.junyoung.trading.order.domain.service.dto.PlaceResult;
import dev.junyoung.trading.order.domain.service.dto.SettlementInput;
import dev.junyoung.trading.order.domain.service.dto.SettlementResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SettlementService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final BalanceSettlementPort balanceSettlementPort;

    public void settlement(PlaceResult placeResult) {
        orderRepository.updateAll(placeResult.updatedOrders());
        tradeRepository.saveAll(placeResult.trades());

        SettlementResult result = SettlementCalculator.settle(new SettlementInput(placeResult.updatedOrders(), placeResult.trades()));
        for (SettlementResult.BalanceDelta balanceDelta : result.balanceDeltas()) {
            balanceSettlementPort.balanceSettlement(
                balanceDelta.accountId(),
                balanceDelta.asset(),
                balanceDelta.availableDelta(),
                balanceDelta.heldDelta()
            );
        }
    }

    public void cancelSettlement(Order cancelled) {
        orderRepository.save(cancelled);
        SettlementResult result = SettlementCalculator.settle(new SettlementInput(List.of(cancelled), List.of()));
        for (SettlementResult.BalanceDelta balanceDelta : result.balanceDeltas()) {
            balanceSettlementPort.balanceSettlement(
                balanceDelta.accountId(),
                balanceDelta.asset(),
                balanceDelta.availableDelta(),
                balanceDelta.heldDelta()
            );
        }
    }
}
