package com.analytics.eventanalytics.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.analytics.eventanalytics.events.EventRepository;
import com.analytics.eventanalytics.events.EventRepository.TypeBucketStat;
import com.analytics.eventanalytics.metrics.dto.MetricsExportRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MetricsExportServiceTest {
	@Mock
	private EventRepository repository;

	@InjectMocks
	private MetricsExportService service;

	@Test
	void export_emptyDataset_returnsEmpty() {
		Instant start = Instant.parse("2026-03-30T00:00:00Z");
		Instant end = Instant.parse("2026-03-30T01:00:00Z");
		when(repository.countByHourAll(eq(null), eq(start), eq(end))).thenReturn(List.of());

		List<MetricsExportRow> rows = service.export(null, start.toString(), end.toString());

		assertThat(rows).isEmpty();
	}

	@Test
	void export_singleBucket_stdDevZero_noAnomaly() {
		Instant start = Instant.parse("2026-03-30T00:00:00Z");
		Instant end = Instant.parse("2026-03-30T01:00:00Z");
		Instant bucket = Instant.parse("2026-03-30T00:00:00Z");
		when(repository.countByHourAll(eq("click"), eq(start), eq(end)))
			.thenReturn(List.of(new TypeBucketStat(bucket, "click", 5, 2)));

		List<MetricsExportRow> rows = service.export("click", start.toString(), end.toString());

		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).anomalyFlag()).isZero();
	}

	@Test
	void export_multipleEventTypes_returnsFlatRowsPerType() {
		Instant start = Instant.parse("2026-03-30T00:00:00Z");
		Instant end = Instant.parse("2026-03-30T02:00:00Z");
		List<TypeBucketStat> stats = List.of(
			new TypeBucketStat(Instant.parse("2026-03-30T00:00:00Z"), "signup", 10, 8),
			new TypeBucketStat(Instant.parse("2026-03-30T01:00:00Z"), "signup", 12, 9),
			new TypeBucketStat(Instant.parse("2026-03-30T00:00:00Z"), "purchase", 3, 3)
		);
		when(repository.countByHourAll(eq(null), eq(start), eq(end))).thenReturn(stats);

		List<MetricsExportRow> rows = service.export(null, start.toString(), end.toString());

		assertThat(rows).hasSize(3);
		assertThat(rows).extracting(MetricsExportRow::eventType)
			.containsExactly("signup", "signup", "purchase");
	}

	@Test
	void export_anomalyDetection_flagsOutlier(CapturedOutput output) {
		Instant start = Instant.parse("2026-03-30T00:00:00Z");
		Instant end = Instant.parse("2026-03-30T10:00:00Z");
		List<TypeBucketStat> stats = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			long count = i == 9 ? 100 : 10;
			stats.add(new TypeBucketStat(start.plusSeconds(3600L * i), "click", count, 5));
		}
		when(repository.countByHourAll(eq("click"), eq(start), eq(end))).thenReturn(stats);

		List<MetricsExportRow> rows = service.export("click", start.toString(), end.toString());

		MetricsExportRow outlier = rows.get(9);
		assertThat(outlier.eventCount()).isEqualTo(100);
		assertThat(outlier.anomalyFlag()).isEqualTo(1);
		assertThat(output.getOut()).contains("Anomaly detected event_type=click bucket_start=");
	}

	@Test
	void export_nullOrMalformedInputs_throwErrors() {
		assertThatThrownBy(() -> service.export(null, null, "2026-03-30T01:00:00Z"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.export(null, "bad", "2026-03-30T01:00:00Z"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
