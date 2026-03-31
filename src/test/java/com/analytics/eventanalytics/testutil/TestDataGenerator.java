package com.analytics.eventanalytics.testutil;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TestDataGenerator {
	private TestDataGenerator() {}

	public static void insertHourlyPattern(
		JdbcTemplate jdbcTemplate,
		Instant startHour,
		int hours,
		String eventType,
		int baseCount,
		int spikeHourIndex,
		int spikeCount,
		int baseUniqueUsers,
		int spikeUniqueUsers
	) {
		String sql = "INSERT INTO events (event_id, user_id, event_type, event_time, properties) VALUES (?, ?, ?, ?, ?)";
		for (int hour = 0; hour < hours; hour++) {
			int count = hour == spikeHourIndex ? spikeCount : baseCount;
			int uniqueUsers = hour == spikeHourIndex ? spikeUniqueUsers : baseUniqueUsers;
			Instant bucketStart = startHour.plusSeconds(3600L * hour);
			for (int i = 0; i < count; i++) {
				int userIndex = uniqueUsers == 0 ? 0 : i % uniqueUsers;
				UUID userId = uuidFrom(eventType + "-user-" + userIndex);
				UUID eventId = uuidFrom(eventType + "-event-" + hour + "-" + i);
				Instant eventTime = bucketStart.plusSeconds(i % 3600);
				jdbcTemplate.update(
					sql,
					eventId,
					userId,
					eventType,
					Timestamp.from(eventTime),
					null
				);
			}
		}
	}

	public static UUID uuidFrom(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}
}
