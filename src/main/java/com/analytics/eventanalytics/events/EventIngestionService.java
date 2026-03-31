package com.analytics.eventanalytics.events;

import com.analytics.eventanalytics.events.dto.EventPayload;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventIngestionService {
	private final EventRepository repository;

	public EventIngestionService(EventRepository repository) {
		this.repository = repository;
	}

	public long count(String eventType, Instant start, Instant end) {
		if (eventType == null || eventType.isBlank()) {
			throw new IllegalArgumentException("event_type is required");
		}
		if (start == null || end == null) {
			throw new IllegalArgumentException("start and end are required");
		}
		if (end.isBefore(start)) {
			throw new IllegalArgumentException("end must be after start");
		}

		return repository.countByTypeAndRange(eventType, start, end);
	}

	public long countDistinctUsers(String eventType, Instant start, Instant end) {
		if (eventType == null || eventType.isBlank()) {
			throw new IllegalArgumentException("event_type is required");
		}
		if (start == null || end == null) {
			throw new IllegalArgumentException("start and end are required");
		}
		if (end.isBefore(start)) {
			throw new IllegalArgumentException("end must be after start");
		}

		return repository.countDistinctUsersByTypeAndRange(eventType, start, end);
	}

	public EventRecord validateAndMap(EventPayload event) {
		// Data validation step in the pipeline: required fields, valid IDs, and no future timestamps.
		if (event == null) {
			throw new IllegalArgumentException("event is required");
		}
		if (event.getEvent_id() == null || event.getEvent_id().isBlank()) {
			throw new IllegalArgumentException("event_id is required");
		}
		if (event.getUser_id() == null || event.getUser_id().isBlank()) {
			throw new IllegalArgumentException("user_id is required");
		}
		if (event.getEvent_type() == null || event.getEvent_type().isBlank()) {
			throw new IllegalArgumentException("event_type is required");
		}
		if (event.getEvent_time() == null || event.getEvent_time().isBlank()) {
			throw new IllegalArgumentException("event_time is required");
		}

		UUID eventId = parseUuid(event.getEvent_id(), "event_id");
		UUID userId = parseUuid(event.getUser_id(), "user_id");
		Instant eventTime = parseInstant(event.getEvent_time(), "event_time");

		Instant now = Instant.now();
		if (eventTime.isAfter(now)) {
			throw new IllegalArgumentException("event_time cannot be in the future");
		}

		return new EventRecord(eventId, userId, event.getEvent_type(), eventTime, event.getProperties());
	}

	private UUID parseUuid(String value, String fieldName) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(fieldName + " must be a valid UUID");
		}
	}

	private Instant parseInstant(String value, String fieldName) {
		try {
			return Instant.parse(value);
		} catch (Exception e) {
			throw new IllegalArgumentException(fieldName + " must be an ISO-8601 timestamp");
		}
	}

}
