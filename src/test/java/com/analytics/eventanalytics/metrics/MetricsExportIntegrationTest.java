package com.analytics.eventanalytics.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.analytics.eventanalytics.events.EventRecord;
import com.analytics.eventanalytics.testutil.TestDataGenerator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetricsExportIntegrationTest {
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetDb() {
		jdbcTemplate.execute("TRUNCATE TABLE events");
		jdbcTemplate.execute("TRUNCATE TABLE event_ids");
		jdbcTemplate.execute("TRUNCATE TABLE users");
	}

	@Test
	void exportMetricsCsv_normalTraffic() {
		Instant start = Instant.parse("2026-03-30T10:00:00Z");
		Instant end = Instant.parse("2026-03-30T12:00:00Z");

		UUID userA = TestDataGenerator.uuidFrom("user-A");
		UUID userB = TestDataGenerator.uuidFrom("user-B");
		UUID userC = TestDataGenerator.uuidFrom("user-C");

		insertEvent(start.plusSeconds(600), "signup", userA);
		insertEvent(start.plusSeconds(900), "signup", userB);
		insertEvent(start.plusSeconds(1200), "signup", userB);

		insertEvent(start.plusSeconds(3600 + 300), "signup", userA);
		insertEvent(start.plusSeconds(3600 + 1200), "signup", userC);

		HttpResponse<String> response = get(
			"/export/metrics?start=" + start + "&end=" + end + "&event_type=signup&format=csv"
		);

		assertThat(response.statusCode()).isEqualTo(200);
		List<String[]> rows = parseCsv(response.body());
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)[0]).isEqualTo("2026-03-30T10:00:00Z");
		assertThat(rows.get(0)[2]).isEqualTo("3");
		assertThat(rows.get(0)[3]).isEqualTo("2");
		assertThat(rows.get(0)[4]).isEqualTo("0");
		assertThat(rows.get(1)[0]).isEqualTo("2026-03-30T11:00:00Z");
		assertThat(rows.get(1)[2]).isEqualTo("2");
		assertThat(rows.get(1)[3]).isEqualTo("2");
		assertThat(rows.get(1)[4]).isEqualTo("0");
	}

	@Test
	void exportMetricsCsv_burstTraffic_detectsAnomaly() {
		Instant start = Instant.parse("2026-03-30T00:00:00Z");
		Instant end = Instant.parse("2026-03-30T10:00:00Z");

		TestDataGenerator.insertHourlyPattern(
			jdbcTemplate,
			start,
			10,
			"click",
			10,
			9,
			100,
			5,
			20
		);

		HttpResponse<String> response = get(
			"/export/metrics?start=" + start + "&end=" + end + "&event_type=click&format=csv"
		);

		List<String[]> rows = parseCsv(response.body());
		assertThat(rows).hasSize(10);
		String[] spikeRow = rows.get(9);
		assertThat(spikeRow[0]).isEqualTo("2026-03-30T09:00:00Z");
		assertThat(spikeRow[2]).isEqualTo("100");
		assertThat(spikeRow[4]).isEqualTo("1");
	}

	@Test
	void exportMetricsCsv_multipleEventTypes() {
		Instant start = Instant.parse("2026-03-30T10:00:00Z");
		Instant end = Instant.parse("2026-03-30T11:00:00Z");

		insertEvent(start.plusSeconds(120), "signup", TestDataGenerator.uuidFrom("user-1"));
		insertEvent(start.plusSeconds(180), "purchase", TestDataGenerator.uuidFrom("user-2"));

		HttpResponse<String> response = get(
			"/export/metrics?start=" + start + "&end=" + end + "&format=csv"
		);

		List<String[]> rows = parseCsv(response.body());
		assertThat(rows).hasSize(2);
		assertThat(rows).extracting(row -> row[1]).contains("signup", "purchase");
	}

	@Test
	void exportMetricsCsv_filteredEventType_onlyReturnsMatches() {
		Instant start = Instant.parse("2026-03-30T10:00:00Z");
		Instant end = Instant.parse("2026-03-30T11:00:00Z");

		insertEvent(start.plusSeconds(60), "click", TestDataGenerator.uuidFrom("user-1"));
		insertEvent(start.plusSeconds(120), "view", TestDataGenerator.uuidFrom("user-2"));

		HttpResponse<String> response = get(
			"/export/metrics?start=" + start + "&end=" + end + "&event_type=click&format=csv"
		);

		List<String[]> rows = parseCsv(response.body());
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0)[1]).isEqualTo("click");
	}

	@Test
	void exportMetricsCsv_validatesSchemaAndTimestamps() {
		Instant start = Instant.parse("2026-03-30T10:00:00Z");
		Instant end = Instant.parse("2026-03-30T11:00:00Z");

		insertEvent(start.plusSeconds(60), "signup", TestDataGenerator.uuidFrom("user-1"));

		HttpResponse<String> response = get(
			"/export/metrics?start=" + start + "&end=" + end + "&format=csv"
		);

		String body = response.body() == null ? "" : response.body().trim();
		String[] lines = body.split("\\r?\\n");
		assertThat(lines[0]).isEqualTo("timestamp,event_type,event_count,unique_users,anomaly_flag");
		List<String[]> rows = parseCsv(body);
		for (String[] row : rows) {
			assertThat(row).hasSize(5);
			assertThat(row[0]).isNotBlank();
			assertThat(row[1]).isNotBlank();
			assertThat(row[2]).isNotBlank();
			assertThat(row[3]).isNotBlank();
			assertThat(row[4]).isNotBlank();
			Instant.parse(row[0]);
		}
	}

	@Test
	void exportMetrics_performanceSanity() {
		Instant start = Instant.parse("2026-03-30T00:00:00Z");
		Instant end = Instant.parse("2026-03-31T00:00:00Z");
		List<EventRecord> batch = new ArrayList<>(10_000);
		for (int i = 0; i < 10_000; i++) {
			UUID eventId = TestDataGenerator.uuidFrom("perf-event-" + i);
			UUID userId = TestDataGenerator.uuidFrom("perf-user-" + (i % 200));
			Instant time = start.plusSeconds(i % (24 * 3600));
			batch.add(new EventRecord(eventId, userId, "perf", time, null));
		}
		insertBatch(batch);

		long startNs = System.nanoTime();
		HttpResponse<String> response = get(
			"/export/metrics?start=" + start + "&end=" + end + "&event_type=perf&format=csv"
		);
		long durationMs = (System.nanoTime() - startNs) / 1_000_000;

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(durationMs).isLessThan(5000);
	}

	private void insertEvent(Instant time, String eventType, UUID userId) {
		jdbcTemplate.update(
			"INSERT INTO events (event_id, user_id, event_type, event_time, properties) VALUES (?, ?, ?, ?, ?)",
			UUID.randomUUID(),
			userId,
			eventType,
			Timestamp.from(time),
			null
		);
	}

	private List<String[]> parseCsv(String body) {
		if (body == null || body.isBlank()) {
			return List.of();
		}
		String[] lines = body.trim().split("\\r?\\n");
		List<String[]> rows = new ArrayList<>();
		for (int i = 1; i < lines.length; i++) {
			rows.add(lines[i].split(",", -1));
		}
		return rows;
	}

	private void insertBatch(List<EventRecord> batch) {
		String sql = "INSERT INTO events (event_id, user_id, event_type, event_time, properties) VALUES (?, ?, ?, ?, ?)";
		jdbcTemplate.batchUpdate(
			sql,
			batch,
			batch.size(),
			(ps, e) -> {
				ps.setObject(1, e.eventId());
				ps.setObject(2, e.userId());
				ps.setString(3, e.eventType());
				ps.setTimestamp(4, Timestamp.from(e.eventTime()));
				ps.setObject(5, null);
			}
		);
	}

	private HttpResponse<String> get(String path) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.GET()
				.build();
			return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			throw new IllegalStateException("HTTP request failed", e);
		}
	}
}
