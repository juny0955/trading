package dev.junyoung.trading.engine.application.runtime;

public interface EngineRuntimeOwner {
	EngineSymbolStatus state();
	void transitionToActive();
	void transitionToRebuilding();
	void transitionToDirty();
	void attemptRebuild();
}
