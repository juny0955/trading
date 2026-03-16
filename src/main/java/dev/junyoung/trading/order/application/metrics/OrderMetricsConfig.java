package dev.junyoung.trading.order.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderMetricsConfig {

    private static final String QUEUE_FULL_ROLLBACK_COUNT = "queue_full_rollback_count";

    @Bean
    public Counter queueFullRollbackCounter(MeterRegistry meterRegistry) {
        return Counter.builder(QUEUE_FULL_ROLLBACK_COUNT)
            .description("successful compensating rollbacks after engine queue submit failure")
            .register(meterRegistry);
    }
}
