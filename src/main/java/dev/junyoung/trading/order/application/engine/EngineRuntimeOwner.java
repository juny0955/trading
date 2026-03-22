package dev.junyoung.trading.order.application.engine;

public interface EngineRuntimeOwner {
	EngineSymbolState state();
	void transitionToRebuilding();
	void transitionToDirty();
	void transitionToHalted();
}
