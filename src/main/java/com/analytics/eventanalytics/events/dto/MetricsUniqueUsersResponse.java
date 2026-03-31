package com.analytics.eventanalytics.events.dto;

public class MetricsUniqueUsersResponse {
	private long unique_users;

	public MetricsUniqueUsersResponse(long uniqueUsers) {
		this.unique_users = uniqueUsers;
	}

	public long getUnique_users() {
		return unique_users;
	}
}
