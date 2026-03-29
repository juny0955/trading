package dev.junyoung.trading.engine.application.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.engine.application.dto.CancelCalculationResult;
import dev.junyoung.trading.engine.application.dto.PlaceCalculationResult;
import dev.junyoung.trading.shared.domain.value.Symbol;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineResultPersistenceCommitter")
class EngineResultPersistenceCommitterTest {

	@Mock
	private EngineResultPersistenceService engineResultPersistenceService;

	@Test
	@DisplayName("commitPlace는 EngineResultPersistenceService.persistPlaceResult에 위임한다")
	void commitPlace_delegatesToPersistenceService() {
		EngineResultPersistenceCommitter committer = new EngineResultPersistenceCommitter(engineResultPersistenceService);
		PlaceCalculationResult.Accepted accepted = new PlaceCalculationResult.Accepted(new Symbol("BTC"), 1L, java.util.List.of());

		committer.commitPlace(accepted);

		verify(engineResultPersistenceService).persistPlaceResult(accepted);
	}

	@Test
	@DisplayName("commitCancel은 EngineResultPersistenceService.persistCancelResult에 위임한다")
	void commitCancel_delegatesToPersistenceService() {
		EngineResultPersistenceCommitter committer = new EngineResultPersistenceCommitter(engineResultPersistenceService);
		CancelCalculationResult.Cancelled cancelled = new CancelCalculationResult.Cancelled(new Symbol("BTC"), 1L, java.util.List.of(), java.util.List.of());

		committer.commitCancel(cancelled);

		verify(engineResultPersistenceService).persistCancelResult(cancelled);
	}
}
