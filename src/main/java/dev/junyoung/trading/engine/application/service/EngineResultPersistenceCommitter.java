package dev.junyoung.trading.engine.application.service;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.engine.application.dto.CancelCalculationResult;
import dev.junyoung.trading.engine.application.dto.PlaceCalculationResult;
import dev.junyoung.trading.engine.application.port.out.EngineResultCommitPort;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EngineResultPersistenceCommitter implements EngineResultCommitPort {

	private final EngineResultPersistenceService engineResultPersistenceService;

	@Override
	public void commitPlace(PlaceCalculationResult.Accepted accepted) {
		engineResultPersistenceService.persistPlaceResult(accepted);
	}

	@Override
	public void commitCancel(CancelCalculationResult.Cancelled cancelled) {
		engineResultPersistenceService.persistCancelResult(cancelled);
	}
}
