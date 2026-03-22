package dev.junyoung.trading.order.application.engine;

/**
 *  복구/장애 처리 정책을 위한 symbol 단위 상태
 */
public enum EngineSymbolState {
    /// 정상 처리 가능
    ACTIVE,

    /// 신규 command 처리 중지. MVP에서는 ingress 차단 또는 reject로 고정
    REBUILDING,

    /// live/DB 정합성 미보장. 자동 처리 금지
    DIRTY
}
