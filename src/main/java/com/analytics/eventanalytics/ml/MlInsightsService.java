package com.analytics.eventanalytics.ml;

import com.analytics.eventanalytics.events.EventRepository;
import com.analytics.eventanalytics.events.EventRepository.BucketStat;
import com.analytics.eventanalytics.ml.dto.MlBucket;
import com.analytics.eventanalytics.ml.dto.MlSummaryResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MlInsightsService {
	private static final double ANOMALY_ZSCORE_THRESHOLD = 2.0;
	private static final Logger logger = LoggerFactory.getLogger(MlInsightsService.class);

	private final EventRepository repository;

	public MlInsightsService(EventRepository repository) {
		this.repository = repository;
	}

	public MlSummaryResponse buildSummary(String eventType, String start, String end) {
		if (eventType == null || eventType.isBlank()) {
			throw new IllegalArgumentException("event_type is required");
		}
		if (start == null || end == null) {
			throw new IllegalArgumentException("start and end are required");
		}

		Instant startTime = parseInstant(start, "start");
		Instant endTime = parseInstant(end, "end");
		if (endTime.isBefore(startTime)) {
			throw new IllegalArgumentException("end must be after start");
		}

		long totalCount = repository.countByTypeAndRange(eventType, startTime, endTime);
		long uniqueUsers = repository.countDistinctUsersByTypeAndRange(eventType, startTime, endTime);
		double avgEventsPerUser = uniqueUsers == 0 ? 0.0 : (double) totalCount / uniqueUsers;

		// Anomaly detection in plain English:
		// - Baseline is the typical hourly event count in the selected window.
		// - Deviation is how far each hour is from that baseline.
		// - Threshold is the cutoff where a deviation is big enough to flag.
		List<BucketStat> stats = repository.countByHour(eventType, startTime, endTime);
		double mean = stats.isEmpty()
			? 0.0
			: stats.stream().mapToLong(BucketStat::count).average().orElse(0.0);
		double variance = stats.isEmpty()
			? 0.0
			: stats.stream()
				.mapToDouble(stat -> {
					double diff = stat.count() - mean;
					return diff * diff;
				})
				.sum() / stats.size();
		double stdDev = Math.sqrt(variance);

		List<MlBucket> buckets = new ArrayList<>(stats.size());
		List<MlBucket> anomalies = new ArrayList<>();
		for (BucketStat stat : stats) {
			double zScore = stdDev == 0.0 ? 0.0 : (stat.count() - mean) / stdDev;
			double roundedZ = roundTo2(zScore);
			boolean anomaly = Math.abs(roundedZ) >= ANOMALY_ZSCORE_THRESHOLD;
			MlBucket bucket = new MlBucket(
				stat.bucketStart().toString(),
				stat.count(),
				stat.uniqueUsers(),
				roundedZ,
				anomaly
			);
			buckets.add(bucket);
			if (anomaly) {
				anomalies.add(bucket);
				logger.info(
					"Anomaly detected event_type={} bucket_start={}",
					eventType,
					stat.bucketStart()
				);
			}
		}

		return new MlSummaryResponse(
			eventType,
			startTime.toString(),
			endTime.toString(),
			totalCount,
			uniqueUsers,
			avgEventsPerUser,
			roundTo2(mean),
			roundTo2(stdDev),
			ANOMALY_ZSCORE_THRESHOLD,
			buckets,
			anomalies
		);
	}

	private Instant parseInstant(String value, String fieldName) {
		try {
			return Instant.parse(value);
		} catch (Exception e) {
			throw new IllegalArgumentException(fieldName + " must be an ISO-8601 timestamp");
		}
	}

	private double roundTo2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
