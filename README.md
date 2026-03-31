# MetricForge – Event Analytics & Monitoring Platform

MetricForge is a simple event analytics pipeline that ingests events, buffers them in memory, writes them to PostgreSQL in batches, and exposes analytics plus anomaly signals for monitoring.

## Pipeline Components (Naming)
- Data Ingestion Layer (`EventController`)
- Pipeline Buffering (`EventBuffer`)
- Batch Processing Step (`EventIngestionWorker`)
- Data Aggregation + Storage Layer (`EventRepository`)

## Pipeline Flow (Simple)
1. Receive events via the ingestion API.
2. Validate required fields and reject future timestamps (data validation step in pipeline).
3. Queue events in memory to smooth spikes.
4. Batch events for efficient writes.
5. Persist events to PostgreSQL for durable storage.
6. Aggregate hourly metrics and detect anomalies.
7. Export metrics for dashboarding.

## How It Works (Simple)
- Clients send event batches.
- The system checks basic data quality.
- Events wait in a queue until a worker groups them.
- Batches are stored in PostgreSQL.
- Analytics endpoints read counts and unique users.
- A simple anomaly check flags unusual hours.

## Tableau Dashboard
- Export data using `GET /export/metrics?start=...&end=...` (CSV by default).
- Load the CSV into Tableau as a data source.
- Build a time-series chart on `event_count` and color by `anomaly_flag`.

## Testing
- Run all tests: `./mvnw test` or `mvn test`
- Coverage includes unit tests for export/anomaly logic, integration tests for CSV/JSON export, and data validation checks.
- Integration tests use H2 in-memory DB (no Docker required).
Example API calls:
```bash
curl "http://localhost:8080/export/metrics?start=2026-03-30T00:00:00Z&end=2026-03-31T00:00:00Z&format=csv"
curl "http://localhost:8080/export/metrics?start=2026-03-30T00:00:00Z&end=2026-03-31T00:00:00Z&event_type=click&format=json"
```
