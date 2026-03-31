package com.analytics.eventanalytics.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventRecord(
	UUID eventId,
	UUID userId,
	String eventType,
	Instant eventTime,
	Map<String, Object> properties
) {}
