package com.analytics.eventanalytics.events.dto;

import java.util.List;

public class EventIngestRequest {
	private List<EventPayload> events;

	public List<EventPayload> getEvents() {
		return events;
	}

	public void setEvents(List<EventPayload> events) {
		this.events = events;
	}
}
