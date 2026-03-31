package com.analytics.eventanalytics.events;

import com.analytics.eventanalytics.events.dto.EventIngestRequest;
import com.analytics.eventanalytics.events.dto.EventIngestResponse;
import com.analytics.eventanalytics.events.dto.EventPayload;
import com.analytics.eventanalytics.events.dto.MetricsCountResponse;
import com.analytics.eventanalytics.events.dto.MetricsUniqueUsersResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Data ingestion layer (API) for the event analytics pipeline.
 */
@RestController
public class EventController {
	private static final Logger logger = LoggerFactory.getLogger(EventController.class);
	private final EventIngestionService ingestionService;
	private final Counter ingestionAccepted;
	private final Counter ingestionRejected;
	private final Timer countTimer;
	private final Timer uniqueUsersTimer;

	public EventController(EventIngestionService ingestionService, MeterRegistry registry) {
		this.ingestionService = ingestionService;
		this.ingestionAccepted = registry.counter("ingestion.accepted");
		this.ingestionRejected = registry.counter("ingestion.rejected");
		this.countTimer = Timer.builder("metrics.count.latency")
			.publishPercentiles(0.5, 0.95)
			.register(registry);
		this.uniqueUsersTimer = Timer.builder("metrics.unique_users.latency")
			.publishPercentiles(0.5, 0.95)
			.register(registry);
	}

	@PostMapping("/events")
	public ResponseEntity<EventIngestResponse> ingest(@RequestBody EventIngestRequest request) {
		List<EventPayload> events = request == null ? null : request.getEvents();
		if (events == null || events.isEmpty()) {
			throw new IllegalArgumentException("events must be a non-empty array");
		}

		logger.info("Ingestion received events={}", events.size());
		int accepted = 0;
		for (EventPayload event : events) {
			EventRecord record = ingestionService.validateAndMap(event);
			boolean enqueued = EventBuffer.offer(record);
			if (!enqueued) {
				ingestionRejected.increment();
				logger.warn("Ingestion rejected queue_full=true");
				throw new ResponseStatusException(
					HttpStatus.TOO_MANY_REQUESTS,
					"Ingestion queue full"
				);
			}
			accepted++;
		}

		ingestionAccepted.increment(accepted);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(new EventIngestResponse(accepted));
	}

	@GetMapping("/metrics/count")
	public MetricsCountResponse count(
		@RequestParam(name = "event_type") String eventType,
		@RequestParam(name = "start") String start,
		@RequestParam(name = "end") String end
	) {
		Instant startTime = Instant.parse(start);
		Instant endTime = Instant.parse(end);
		long count = countTimer.record(() -> ingestionService.count(eventType, startTime, endTime));
		return new MetricsCountResponse(count);
	}

	@GetMapping("/metrics/unique-users")
	public MetricsUniqueUsersResponse uniqueUsers(
		@RequestParam(name = "event_type") String eventType,
		@RequestParam(name = "start") String start,
		@RequestParam(name = "end") String end
	) {
		Instant startTime = Instant.parse(start);
		Instant endTime = Instant.parse(end);
		long count = uniqueUsersTimer.record(() -> ingestionService.countDistinctUsers(eventType, startTime, endTime));
		return new MetricsUniqueUsersResponse(count);
	}
}
