package dev.junyoung.trading.order.application.engine;

public interface EngineRuntimeOwner {
	EngineSymbolState state();
	void transitionToActive();
	void transitionToRebuilding();
	void transitionToDirty();
	void attemptRebuild();
}
