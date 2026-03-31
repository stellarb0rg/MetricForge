package com.analytics.eventanalytics.metrics;

import com.analytics.eventanalytics.events.EventRepository;
import com.analytics.eventanalytics.events.EventRepository.TypeBucketStat;
import com.analytics.eventanalytics.metrics.dto.MetricsExportRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Data aggregation layer for flat exports used in dashboarding.
 */
@Service
public class MetricsExportService {
	private static final Logger logger = LoggerFactory.getLogger(MetricsExportService.class);
	private static final double ANOMALY_ZSCORE_THRESHOLD = 2.0;

	private final EventRepository repository;

	public MetricsExportService(EventRepository repository) {
		this.repository = repository;
	}

	public List<MetricsExportRow> export(String eventType, String start, String end) {
		if (start == null || end == null) {
			throw new IllegalArgumentException("start and end are required");
		}
		Instant startTime = parseInstant(start, "start");
		Instant endTime = parseInstant(end, "end");
		if (endTime.isBefore(startTime)) {
			throw new IllegalArgumentException("end must be after start");
		}

		String normalizedEventType = (eventType == null || eventType.isBlank()) ? null : eventType;
		List<TypeBucketStat> stats = repository.countByHourAll(normalizedEventType, startTime, endTime);
		Map<String, List<TypeBucketStat>> byType = new LinkedHashMap<>();
		for (TypeBucketStat stat : stats) {
			byType.computeIfAbsent(stat.eventType(), key -> new ArrayList<>()).add(stat);
		}

		List<MetricsExportRow> rows = new ArrayList<>(stats.size());
		for (Map.Entry<String, List<TypeBucketStat>> entry : byType.entrySet()) {
			List<TypeBucketStat> buckets = entry.getValue();
			double mean = buckets.isEmpty()
				? 0.0
				: buckets.stream().mapToLong(TypeBucketStat::count).average().orElse(0.0);
			double variance = buckets.isEmpty()
				? 0.0
				: buckets.stream()
					.mapToDouble(stat -> {
						double diff = stat.count() - mean;
						return diff * diff;
					})
					.sum() / buckets.size();
			double stdDev = Math.sqrt(variance);

			for (TypeBucketStat stat : buckets) {
				double zScore = stdDev == 0.0 ? 0.0 : (stat.count() - mean) / stdDev;
				double roundedZ = roundTo2(zScore);
				boolean anomaly = Math.abs(roundedZ) >= ANOMALY_ZSCORE_THRESHOLD;
				if (anomaly) {
					logger.info("Anomaly detected event_type={} bucket_start={}", stat.eventType(), stat.bucketStart());
				}
				rows.add(new MetricsExportRow(
					stat.bucketStart().toString(),
					stat.eventType(),
					stat.count(),
					stat.uniqueUsers(),
					anomaly ? 1 : 0
				));
			}
		}

		return rows;
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
