package com.analytics.eventanalytics.ml;

import com.analytics.eventanalytics.ml.dto.MlSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MlInsightsController {
	private final MlInsightsService insightsService;

	public MlInsightsController(MlInsightsService insightsService) {
		this.insightsService = insightsService;
	}

	@GetMapping("/ml/summary")
	public MlSummaryResponse summary(
		@RequestParam(name = "event_type") String eventType,
		@RequestParam(name = "start") String start,
		@RequestParam(name = "end") String end
	) {
		return insightsService.buildSummary(eventType, start, end);
	}
}
