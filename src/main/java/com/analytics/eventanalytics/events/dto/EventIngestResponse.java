package com.analytics.eventanalytics.events.dto;

public class EventIngestResponse {
	private int accepted;

	public EventIngestResponse(int accepted) {
		this.accepted = accepted;
	}

	public int getAccepted() {
		return accepted;
	}
}
