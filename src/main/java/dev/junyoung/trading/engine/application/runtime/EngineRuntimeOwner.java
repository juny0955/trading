package dev.junyoung.trading.engine.application.runtime;

public interface EngineRuntimeOwner {
	EngineSymbolState state();
	void transitionToActive();
	void transitionToRebuilding();
	void transitionToDirty();
	void attemptRebuild();
}
