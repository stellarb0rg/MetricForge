package com.analytics.eventanalytics.events;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class EventBuffer {
	public static final int MAX_QUEUE_SIZE = 100_000;

	/**
	 * Queue-based pipeline buffering for ingestion backpressure and batching.
	 */
	private static final BlockingQueue<EventRecord> QUEUE =
		new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
	private static final AtomicInteger QUEUE_SIZE = new AtomicInteger(0);

	public static void initialize(MeterRegistry registry) {
		registry.gauge("ingestion.queue.size", QUEUE_SIZE);
	}

	public static boolean offer(EventRecord event) {
		boolean accepted = QUEUE.offer(event);
		if (accepted) {
			QUEUE_SIZE.incrementAndGet();
		}
		return accepted;
	}

	public static EventRecord take() throws InterruptedException {
		EventRecord record = QUEUE.take();
		QUEUE_SIZE.decrementAndGet();
		return record;
	}

	public static int drainTo(Collection<EventRecord> target, int maxElements) {
		int drained = QUEUE.drainTo(target, maxElements);
		if (drained > 0) {
			QUEUE_SIZE.addAndGet(-drained);
		}
		return drained;
	}

	public static int size() {
		return QUEUE_SIZE.get();
	}
}
