package dev.junyoung.trading.order.application.exception.engine;

import dev.junyoung.trading.common.exception.base.BusinessException;

public class EngineQueueFullException extends BusinessException {
    public EngineQueueFullException() {
        super(EngineErrorCode.ENGINE_BACKPRESSURE);
    }
}
