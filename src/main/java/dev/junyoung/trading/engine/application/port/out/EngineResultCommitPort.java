package dev.junyoung.trading.engine.application.port.out;

import dev.junyoung.trading.engine.application.dto.CancelCalculationResult;
import dev.junyoung.trading.engine.application.dto.PlaceCalculationResult;

public interface EngineResultCommitPort {
	void commitPlace(PlaceCalculationResult.Accepted accepted);

	void commitCancel(CancelCalculationResult.Cancelled cancelled);
}
