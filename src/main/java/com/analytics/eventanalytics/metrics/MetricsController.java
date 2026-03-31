package com.analytics.eventanalytics.metrics;

import com.analytics.eventanalytics.events.EventBuffer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
	private final MeterRegistry registry;

	public MetricsController(MeterRegistry registry) {
		this.registry = registry;
	}

	@GetMapping("/internal/metrics")
	public Map<String, Object> metrics() {
		Map<String, Object> payload = new LinkedHashMap<>();

		payload.put("queue_size", EventBuffer.size());
		payload.put("ingestion.accepted", counterValue("ingestion.accepted"));
		payload.put("ingestion.rejected", counterValue("ingestion.rejected"));
		payload.put("events.persisted", counterValue("events.persisted"));
		payload.put("batch.failures", counterValue("batch.failures"));

		payload.put("batch.insert.latency", timerStats("batch.insert.latency"));
		payload.put("metrics.count.latency", timerStats("metrics.count.latency"));
		payload.put("metrics.unique_users.latency", timerStats("metrics.unique_users.latency"));

		return payload;
	}

	private double counterValue(String name) {
		Counter counter = registry.find(name).counter();
		return counter == null ? 0.0 : counter.count();
	}

	private Map<String, Object> timerStats(String name) {
		Timer timer = registry.find(name).timer();
		Map<String, Object> stats = new LinkedHashMap<>();
		if (timer == null) {
			stats.put("count", 0);
			stats.put("mean_ms", 0.0);
			stats.put("max_ms", 0.0);
			stats.put("p50_ms", 0.0);
			stats.put("p95_ms", 0.0);
			return stats;
		}

		stats.put("count", timer.count());
		stats.put("mean_ms", timer.mean(TimeUnit.MILLISECONDS));
		stats.put("max_ms", timer.max(TimeUnit.MILLISECONDS));

		double p50 = 0.0;
		double p95 = 0.0;
		for (ValueAtPercentile percentile : timer.takeSnapshot().percentileValues()) {
			if (percentile.percentile() == 0.5) {
				p50 = percentile.value(TimeUnit.MILLISECONDS);
			}
			if (percentile.percentile() == 0.95) {
				p95 = percentile.value(TimeUnit.MILLISECONDS);
			}
		}

		stats.put("p50_ms", p50);
		stats.put("p95_ms", p95);
		return stats;
	}
}
