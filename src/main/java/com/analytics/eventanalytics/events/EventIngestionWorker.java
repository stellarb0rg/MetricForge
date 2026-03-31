package com.analytics.eventanalytics.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Batch processing worker for the ingestion pipeline.
 */
@Component
public class EventIngestionWorker {
	private static final Logger logger = LoggerFactory.getLogger(EventIngestionWorker.class);
	private static final int MAX_BATCH_SIZE = 1000;

	private final EventRepository repository;
	private final Counter eventsPersisted;
	private final Counter batchFailures;
	private final Timer batchLatency;

	public EventIngestionWorker(EventRepository repository, MeterRegistry registry) {
		this.repository = repository;
		this.eventsPersisted = registry.counter("events.persisted");
		this.batchFailures = registry.counter("batch.failures");
		this.batchLatency = Timer.builder("batch.insert.latency")
			.publishPercentiles(0.5, 0.95)
			.register(registry);
	}

	@PostConstruct
	public void start() {
		Thread worker = new Thread(this::runLoop, "event-ingestion-worker");
		worker.setDaemon(true);
		worker.start();
	}

	private void runLoop() {
		List<EventRecord> batch = new ArrayList<>(MAX_BATCH_SIZE);

		while (true) {
			try {
				batch.clear();
				batch.add(EventBuffer.take());
				EventBuffer.drainTo(batch, MAX_BATCH_SIZE - 1);
				batchLatency.record(() -> repository.batchInsert(batch));
				eventsPersisted.increment(batch.size());
				logger.info("Batch processed batch_size={}", batch.size());
				logger.info("DB write success records={}", batch.size());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.warn("Event ingestion worker interrupted", e);
				return;
			} catch (Exception e) {
				batchFailures.increment();
				logger.error("Failed to insert event batch", e);
			}
		}
	}
}
