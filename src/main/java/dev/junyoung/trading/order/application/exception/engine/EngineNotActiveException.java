package dev.junyoung.trading.order.application.exception.engine;

import dev.junyoung.trading.common.exception.base.BusinessException;
import dev.junyoung.trading.order.application.engine.EngineSymbolState;

public class EngineNotActiveException extends BusinessException {
    public EngineNotActiveException(EngineSymbolState state) {
        super(EngineErrorCode.ENGINE_NOT_ACTIVE, "Engine is not active: " + state);
    }
}
