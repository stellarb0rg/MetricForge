package com.analytics.eventanalytics.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Data aggregation + storage layer for the event pipeline.
 */
@Repository
public class EventRepository {
	public record BucketStat(Instant bucketStart, long count, long uniqueUsers) {}
	public record TypeBucketStat(Instant bucketStart, String eventType, long count, long uniqueUsers) {}

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private static final String INSERT_SQL_POSITIONAL =
		"""
		WITH inserted AS (
			INSERT INTO event_ids (event_id)
			VALUES (?)
			ON CONFLICT (event_id) DO NOTHING
			RETURNING event_id
		)
		INSERT INTO events (event_id, user_id, event_type, event_time, properties)
		SELECT ?, ?, ?, ?, ?
		FROM inserted
		""";

	public EventRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public void insertEvent(UUID eventId, UUID userId, String eventType, Instant eventTime, Map<String, Object> properties) {
		jdbcTemplate.getJdbcTemplate().update(
			INSERT_SQL_POSITIONAL,
			eventId,
			eventId,
			userId,
			eventType,
			Timestamp.from(eventTime),
			toJsonb(properties)
		);
	}

	public void batchInsert(List<EventRecord> events) {
		if (events == null || events.isEmpty()) {
			return;
		}

		jdbcTemplate.getJdbcTemplate().batchUpdate(
			INSERT_SQL_POSITIONAL,
			events,
			events.size(),
			(ps, e) -> {
				ps.setObject(1, e.eventId());
				ps.setObject(2, e.eventId());
				ps.setObject(3, e.userId());
				ps.setString(4, e.eventType());
				ps.setTimestamp(5, Timestamp.from(e.eventTime()));
				ps.setObject(6, toJsonb(e.properties()));
			}
		);
	}

	public long countByTypeAndRange(String eventType, Instant start, Instant end) {
		String sql = """
			SELECT COUNT(*)
			FROM events
			WHERE event_type = :event_type
			AND event_time BETWEEN :start AND :end
			""";

		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("event_type", eventType)
			.addValue("start", Timestamp.from(start))
			.addValue("end", Timestamp.from(end));

		Long result = jdbcTemplate.queryForObject(sql, params, Long.class);
		return result == null ? 0L : result;
	}

	public long countDistinctUsersByTypeAndRange(String eventType, Instant start, Instant end) {
		String sql = """
			SELECT COUNT(DISTINCT user_id)
			FROM events
			WHERE event_type = :event_type
			AND event_time BETWEEN :start AND :end
			""";

		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("event_type", eventType)
			.addValue("start", Timestamp.from(start))
			.addValue("end", Timestamp.from(end));

		Long result = jdbcTemplate.queryForObject(sql, params, Long.class);
		return result == null ? 0L : result;
	}

	public List<BucketStat> countByHour(String eventType, Instant start, Instant end) {
		String sql = """
			SELECT date_trunc('hour', event_time) AS bucket_start,
			       COUNT(*) AS count,
			       COUNT(DISTINCT user_id) AS unique_users
			FROM events
			WHERE event_type = :event_type
			AND event_time BETWEEN :start AND :end
			GROUP BY bucket_start
			ORDER BY bucket_start
			""";

		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("event_type", eventType)
			.addValue("start", Timestamp.from(start))
			.addValue("end", Timestamp.from(end));

		return jdbcTemplate.query(
			sql,
			params,
			(rs, rowNum) -> new BucketStat(
				rs.getTimestamp("bucket_start").toInstant(),
				rs.getLong("count"),
				rs.getLong("unique_users")
			)
		);
	}

	public List<TypeBucketStat> countByHourAll(String eventType, Instant start, Instant end) {
		String sql = """
			SELECT date_trunc('hour', event_time) AS bucket_start,
			       event_type AS event_type,
			       COUNT(*) AS count,
			       COUNT(DISTINCT user_id) AS unique_users
			FROM events
			WHERE event_time BETWEEN :start AND :end
			AND (:event_type IS NULL OR event_type = :event_type)
			GROUP BY bucket_start, event_type
			ORDER BY bucket_start, event_type
			""";

		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("event_type", eventType)
			.addValue("start", Timestamp.from(start))
			.addValue("end", Timestamp.from(end));

		return jdbcTemplate.query(
			sql,
			params,
			(rs, rowNum) -> new TypeBucketStat(
				rs.getTimestamp("bucket_start").toInstant(),
				rs.getString("event_type"),
				rs.getLong("count"),
				rs.getLong("unique_users")
			)
		);
	}

	private PGobject toJsonb(Map<String, Object> properties) {
		if (properties == null) {
			return null;
		}

		try {
			PGobject jsonb = new PGobject();
			jsonb.setType("jsonb");
			jsonb.setValue(objectMapper.writeValueAsString(properties));
			return jsonb;
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("properties must be valid JSON", e);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to serialize properties", e);
		}
	}
}
