package com.analytics.eventanalytics.ml.dto;

import java.util.List;

public record MlSummaryResponse(
	String eventType,
	String start,
	String end,
	long totalCount,
	long uniqueUsers,
	double avgEventsPerUser,
	double meanPerHour,
	double stdDevPerHour,
	double anomalyZScoreThreshold,
	List<MlBucket> buckets,
	List<MlBucket> anomalies
) {}
