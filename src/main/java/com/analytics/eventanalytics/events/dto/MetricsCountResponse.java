package com.analytics.eventanalytics.events.dto;

public class MetricsCountResponse {
	private long count;

	public MetricsCountResponse(long count) {
		this.count = count;
	}

	public long getCount() {
		return count;
	}
}
