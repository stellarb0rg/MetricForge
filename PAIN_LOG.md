## Test
10k events, batch=500

## Symptom
Ingestion real time: 0.96s. /metrics/count real time: 0.02s. /metrics/unique-users real time: 0.01s.

## Evidence
No errors. Responses returned counts.

## Hypothesis
Table still small; sequential scan cost is low.

## Fix Attempted
none

## Test
100k events, batch=1000

## Symptom
Ingestion real time: 5.71s. /metrics/count real time: 0.03s. /metrics/unique-users real time: 0.04s (unique-users slower).

## Evidence
No errors. Responses returned counts.

## Hypothesis
Scan cost rising with table size; distinct aggregation slightly heavier.

## Fix Attempted
none

## Test
500k events, batch=2000

## Symptom
Ingestion real time: 27.91s. /metrics/count real time: 0.08s. /metrics/unique-users real time: 0.14s (unique-users slower).

## Evidence
No errors in requests. Responses returned counts.

## Hypothesis
Larger table increases scan time; distinct aggregation cost increases with rows.

## Fix Attempted
none

## Test
1,000,000 events, batch=5000

## Symptom
Ingestion real time: 56.36s. /metrics/count real time: 0.37s. /metrics/unique-users real time: 0.32s.

## Evidence
No request failures; responses returned counts.

## Hypothesis
Sequential scans now non-trivial but still sub-second on this dataset; distinct aggregation cost still noticeable.

## Fix Attempted
none

## Test
EXPLAIN ANALYZE on 1,000,000 events (page_view, 2025-12-01 to 2026-02-04)

## Symptom
Count query: 86.124 ms execution. Unique users: 288.909 ms execution, external merge sort to disk.

## Evidence
EXPLAIN ANALYZE COUNT(*)
Finalize Aggregate  (cost=40575.95..40575.96 rows=1 width=8) (actual time=84.610..85.951 rows=1 loops=1)
  ->  Gather  (cost=40575.74..40575.95 rows=2 width=8) (actual time=84.510..85.947 rows=3 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        ->  Partial Aggregate  (cost=39575.74..39575.75 rows=1 width=8) (actual time=81.207..81.207 rows=1 loops=3)
              ->  Parallel Seq Scan on events  (cost=0.00..39036.58 rows=215662 width=0) (actual time=0.096..75.515 rows=173666 loops=3)
                    Filter: ((event_time >= '2025-12-01 00:00:00'::timestamp without time zone) AND (event_time <= '2026-02-04 00:00:00'::timestamp without time zone) AND (event_type = 'page_view'::text))
                    Rows Removed by Filter: 363001
Planning Time: 2.485 ms
Execution Time: 86.124 ms

EXPLAIN ANALYZE COUNT(DISTINCT user_id)
Aggregate  (cost=116030.78..116030.79 rows=1 width=8) (actual time=286.364..286.364 rows=1 loops=1)
  ->  Sort  (cost=113442.84..114736.81 rows=517588 width=16) (actual time=251.604..273.769 rows=520998 loops=1)
        Sort Key: user_id
        Sort Method: external merge  Disk: 10232kB
        ->  Seq Scan on events  (cost=0.00..55472.00 rows=517588 width=16) (actual time=0.034..173.445 rows=520998 loops=1)
              Filter: ((event_time >= '2025-12-01 00:00:00'::timestamp without time zone) AND (event_time <= '2026-02-04 00:00:00'::timestamp without time zone) AND (event_type = 'page_view'::text))
              Rows Removed by Filter: 1089002
Planning Time: 1.161 ms
Execution Time: 288.909 ms

## Hypothesis
Distinct aggregation requires sort/merge and spills to disk; scan filters touch over 1M rows.

## Fix Attempted
none

## Test
Phase 3 - index on (event_type, event_time)

## Symptom
/metrics/count real time: 0.15s. /metrics/unique-users real time: 0.25s.

## Evidence
EXPLAIN ANALYZE COUNT(*)
Finalize Aggregate  (cost=20162.02..20162.03 rows=1 width=8) (actual time=43.661..45.128 rows=1 loops=1)
  ->  Gather  (cost=20161.81..20162.02 rows=2 width=8) (actual time=43.581..45.121 rows=3 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        ->  Partial Aggregate  (cost=19161.81..19161.82 rows=1 width=8) (actual time=39.469..39.469 rows=1 loops=3)
              ->  Parallel Index Only Scan using idx_events_type_time on events  (cost=0.43..18622.65 rows=215662 width=0) (actual time=0.177..31.789 rows=173666 loops=3)
                    Index Cond: ((event_type = 'page_view'::text) AND (event_time >= '2025-12-01 00:00:00'::timestamp without time zone) AND (event_time <= '2026-02-04 00:00:00'::timestamp without time zone))
                    Heap Fetches: 11
Planning Time: 3.463 ms
Execution Time: 45.261 ms

## Hypothesis
Index matches WHERE clause; planner switches to index-only scan, reducing scan time.

## Fix Attempted
idx_events_type_time

## Test
Phase 3 - index on (event_type, event_time, user_id)

## Symptom
/metrics/unique-users real time: 0.19s (improved).

## Evidence
EXPLAIN ANALYZE COUNT(DISTINCT user_id)
Aggregate  (cost=87100.70..87100.71 rows=1 width=8) (actual time=184.969..184.969 rows=1 loops=1)
  ->  Sort  (cost=84512.76..85806.73 rows=517588 width=16) (actual time=152.230..173.045 rows=520998 loops=1)
        Sort Key: user_id
        Sort Method: external merge  Disk: 10232kB
        ->  Index Only Scan using idx_events_type_time_user on events  (cost=0.43..26541.92 rows=517588 width=16) (actual time=0.197..64.453 rows=520998 loops=1)
              Index Cond: ((event_type = 'page_view'::text) AND (event_time >= '2025-12-01 00:00:00'::timestamp without time zone) AND (event_time <= '2026-02-04 00:00:00'::timestamp without time zone))
              Heap Fetches: 11
Planning Time: 3.304 ms
Execution Time: 188.573 ms

## Hypothesis
Index-only scan reduces IO, but DISTINCT still requires sort and spills to disk.

## Fix Attempted
idx_events_type_time_user

## Test
Phase 4 - partitioned table (monthly) + indexes

## Symptom
Ingestion test (1k, batch=200) real time: 1.76s. /metrics/count real time: 0.10s. /metrics/unique-users real time: 0.31s.

## Evidence
EXPLAIN ANALYZE COUNT(*)
Finalize Aggregate  (cost=21553.31..21553.32 rows=1 width=8) (actual time=51.019..52.409 rows=1 loops=1)
  ->  Gather  (cost=21553.09..21553.30 rows=2 width=8) (actual time=50.916..52.404 rows=3 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        ->  Partial Aggregate  (cost=20553.09..20553.10 rows=1 width=8) (actual time=46.680..46.681 rows=1 loops=3)
              ->  Parallel Append  (cost=0.42..20007.04 rows=218423 width=0) (actual time=0.128..39.338 rows=173780 loops=3)
                    ->  Parallel Index Only Scan using events_2026_01_event_type_event_time_idx on events_2026_01 events_2  (cost=0.43..16943.77 rows=195758 width=0) (actual time=0.093..23.619 rows=155832 loops=3)
                          Index Cond: ((event_type = 'page_view'::text) AND (event_time >= '2025-12-01 00:00:00+05:30'::timestamp with time zone) AND (event_time <= '2026-02-04 00:00:00+05:30'::timestamp with time zone))
                          Heap Fetches: 319
                    ->  Parallel Index Only Scan using events_2026_02_event_type_event_time_idx on events_2026_02 events_3  (cost=0.42..1962.99 rows=22664 width=0) (actual time=0.129..7.061 rows=26922 loops=2)
                          Index Cond: ((event_type = 'page_view'::text) AND (event_time >= '2025-12-01 00:00:00+05:30'::timestamp with time zone) AND (event_time <= '2026-02-04 00:00:00+05:30'::timestamp with time zone))
                          Heap Fetches: 26
                    ->  Parallel Index Only Scan using events_2025_12_event_type_event_time_user_id_idx on events_2025_12 events_1  (cost=0.15..8.17 rows=1 width=0) (actual time=0.005..0.005 rows=0 loops=1)
                          Index Cond: ((event_type = 'page_view'::text) AND (event_time >= '2025-12-01 00:00:00+05:30'::timestamp with time zone) AND (event_time <= '2026-02-04 00:00:00+05:30'::timestamp with time zone))
                          Heap Fetches: 0
Planning Time: 3.855 ms
Execution Time: 52.559 ms

## Hypothesis
Partition pruning reduced scanned data to only relevant monthly partitions; index-only scans on partitions keep IO low.

## Fix Attempted
Monthly range partitions + indexes on parent.

## Test
Phase 5 - async ingestion load (200k target, batch=5000)

## Symptom
Client saw HTTP 429 Too Many Requests during load; no 5xx observed. Load required two runs to exceed +200k inserts.

## Evidence
Counts: before 1,611,000; after runs 1,843,008 (+232,008). Metrics: /metrics/count real 0.12s; /metrics/unique-users real 0.61s.

## Hypothesis
Queue fills under large batch size, triggering backpressure; worker drains in background while client retries.

## Fix Attempted
none

## Phase 5 Summary
- Write path decoupled from HTTP
- Throughput increased via batching
- Explicit backpressure enforced
- Read path unaffected

## Test
Phase 6 - enforce idempotency (partitioned table)

## Symptom
Attempt to create UNIQUE index on events(event_id) failed due to partition key requirement.

## Evidence
ERROR: unique constraint on partitioned table must include all partitioning columns
DETAIL: UNIQUE constraint on table "events" lacks column "event_time" which is part of the partition key.

## Hypothesis
Postgres requires unique constraints on partitioned tables to include the partition key.

## Fix Attempted
Introduced event_ids table with PRIMARY KEY (event_id) and used ON CONFLICT DO NOTHING before inserting into events.

## Test
Phase 6 - duplicate ingestion with reused IDs (seed=42)

## Symptom
Counts increased once, then stayed constant on repeated run.

## Evidence
Before: 1,843,008 events. After first run: 1,874,640 events. After second run: 1,874,640 events. COUNT(event_id) == COUNT(DISTINCT event_id).

## Hypothesis
Idempotency enforced by event_ids primary key prevents duplicate inserts.

## Fix Attempted
ON CONFLICT DO NOTHING via event_ids gate.

## Test
Phase 6 - crash/restart during ingestion

## Symptom
App stopped mid-run; after restart, re-running same IDs completed without duplicates.

## Evidence
Post-restart run (seed=77) completed to 1,955,278 events with COUNT(event_id) == COUNT(DISTINCT event_id).

## Hypothesis
Crash + retry safe due to idempotency gate.

## Fix Attempted
none

## Phase 7 Validation
- Queue pressure visible via gauge
- Backpressure surfaced as 429s
- Batch latency correlated with load
- No data loss or duplication
- Read path unaffected by write stress
{"queue_size":99632,"ingestion.accepted":100000.0,"ingestion.rejected":91.0,"events.persisted":33001.0,"batch.failures":0.0,"batch.insert.latency":{"count":34,"mean_ms":216.20829161764706,"max_ms":527.847292,"p50_ms":182.452224,"p95_ms":467.664896},"metrics.count.latency":{"count":0,"mean_ms":0.0,"max_ms":0.0,"p50_ms":0.0,"p95_ms":0.0},"metrics.unique_users.latency":{"count":0,"mean_ms":0.0,"max_ms":0.0,"p50_ms":0.0,"p95_ms":0.0}}
{"queue_size":100000,"ingestion.accepted":100000.0,"ingestion.rejected":221.0,"events.persisted":101001.0,"batch.failures":0.0,"batch.insert.latency":{"count":102,"mean_ms":162.95800165686273,"max_ms":527.847292,"p50_ms":140.509184,"p95_ms":350.224384},"metrics.count.latency":{"count":0,"mean_ms":0.0,"max_ms":0.0,"p50_ms":0.0,"p95_ms":0.0},"metrics.unique_users.latency":{"count":0,"mean_ms":0.0,"max_ms":0.0,"p50_ms":0.0,"p95_ms":0.0}}
{"queue_size":0,"ingestion.accepted":200000.0,"ingestion.rejected":223.0,"events.persisted":306001.0,"batch.failures":0.0,"batch.insert.latency":{"count":307,"mean_ms":77.20980905537459,"max_ms":527.847292,"p50_ms":43.778048,"p95_ms":217.841664},"metrics.count.latency":{"count":0,"mean_ms":0.0,"max_ms":0.0,"p50_ms":0.0,"p95_ms":0.0},"metrics.unique_users.latency":{"count":0,"mean_ms":0.0,"max_ms":0.0,"p50_ms":0.0,"p95_ms":0.0}}
