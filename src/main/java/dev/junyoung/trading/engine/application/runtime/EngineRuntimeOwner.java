package dev.junyoung.trading.engine.application.runtime;

public interface EngineRuntimeOwner {
	long nextEventSequence();
	EngineSymbolStatus state();
	void transitionToActive();
	void transitionToRebuilding();
	void transitionToDirty();
	void attemptRebuild();
}
