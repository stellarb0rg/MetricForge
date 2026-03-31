package com.analytics.eventanalytics.metrics;

import com.analytics.eventanalytics.metrics.dto.MetricsExportRow;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export endpoint for dashboard-ready metrics.
 */
@RestController
public class MetricsExportController {
	private final MetricsExportService exportService;

	public MetricsExportController(MetricsExportService exportService) {
		this.exportService = exportService;
	}

	@GetMapping("/export/metrics")
	public ResponseEntity<?> exportMetrics(
		@RequestParam(name = "start") String start,
		@RequestParam(name = "end") String end,
		@RequestParam(name = "event_type", required = false) String eventType,
		@RequestParam(name = "format", defaultValue = "csv") String format
	) {
		List<MetricsExportRow> rows = exportService.export(eventType, start, end);
		if ("json".equalsIgnoreCase(format)) {
			return ResponseEntity.ok(rows);
		}

		String csv = toCsv(rows);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/csv"));
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"metrics_export.csv\"");
		return new ResponseEntity<>(csv, headers, HttpStatus.OK);
	}

	private String toCsv(List<MetricsExportRow> rows) {
		StringBuilder sb = new StringBuilder();
		sb.append("timestamp,event_type,event_count,unique_users,anomaly_flag\n");
		for (MetricsExportRow row : rows) {
			sb.append(csv(row.timestamp())).append(',');
			sb.append(csv(row.eventType())).append(',');
			sb.append(row.eventCount()).append(',');
			sb.append(row.uniqueUsers()).append(',');
			sb.append(row.anomalyFlag()).append('\n');
		}
		return sb.toString();
	}

	private String csv(String value) {
		if (value == null) {
			return "";
		}
		boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n");
		if (!needsQuotes) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
