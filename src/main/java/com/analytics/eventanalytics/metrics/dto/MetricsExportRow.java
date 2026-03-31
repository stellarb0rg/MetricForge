package com.analytics.eventanalytics.metrics.dto;

public record MetricsExportRow(
	String timestamp,
	String eventType,
	long eventCount,
	long uniqueUsers,
	int anomalyFlag
) {}
