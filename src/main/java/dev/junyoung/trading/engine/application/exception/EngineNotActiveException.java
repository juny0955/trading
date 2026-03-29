package dev.junyoung.trading.engine.application.exception;

import dev.junyoung.trading.common.exception.base.BusinessException;
import dev.junyoung.trading.engine.application.runtime.EngineSymbolState;

public class EngineNotActiveException extends BusinessException {
    public EngineNotActiveException(EngineSymbolState state) {
        super(EngineErrorCode.ENGINE_NOT_ACTIVE, "Engine is not active: " + state);
    }
}
