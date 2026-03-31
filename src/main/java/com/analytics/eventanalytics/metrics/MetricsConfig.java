package com.analytics.eventanalytics.metrics;

import com.analytics.eventanalytics.events.EventBuffer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

	@Bean
	public MeterRegistry meterRegistry() {
		MeterRegistry registry = new SimpleMeterRegistry();
		EventBuffer.initialize(registry);
		return registry;
	}
}
