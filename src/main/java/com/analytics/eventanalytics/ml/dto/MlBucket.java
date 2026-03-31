package com.analytics.eventanalytics.ml.dto;

public record MlBucket(
	String bucketStart,
	long count,
	long uniqueUsers,
	double zScore,
	boolean anomaly
) {}
