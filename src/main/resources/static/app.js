const els = {
  queueSize: document.getElementById("queueSize"),
  accepted: document.getElementById("accepted"),
  rejected: document.getElementById("rejected"),
  persisted: document.getElementById("persisted"),
  batchFailures: document.getElementById("batchFailures"),
  latencyBatch: document.getElementById("latencyBatch"),
  latencyCount: document.getElementById("latencyCount"),
  latencyUnique: document.getElementById("latencyUnique"),
  internalError: document.getElementById("internalError"),
  lastUpdated: document.getElementById("lastUpdated"),
  metricsResult: document.getElementById("metricsResult"),
  mlResult: document.getElementById("mlResult"),
  ingestResult: document.getElementById("ingestResult"),
  ingestPayload: document.getElementById("ingestPayload"),
  refreshAll: document.getElementById("refreshAll"),
  metricsForm: document.getElementById("metricsForm"),
  mlForm: document.getElementById("mlForm"),
  countBtn: document.getElementById("countBtn"),
  uniqueBtn: document.getElementById("uniqueBtn"),
  mlBtn: document.getElementById("mlBtn"),
  ingestBtn: document.getElementById("ingestBtn"),
  sampleBtn: document.getElementById("sampleBtn"),
};

const formatNumber = (value) => {
  if (value === null || value === undefined) return "--";
  return new Intl.NumberFormat().format(value);
};

const formatLatency = (stats) => {
  if (!stats) return "--";
  const mean = formatNumber(stats.mean_ms?.toFixed?.(2) ?? stats.mean_ms);
  const p95 = formatNumber(stats.p95_ms?.toFixed?.(2) ?? stats.p95_ms);
  const max = formatNumber(stats.max_ms?.toFixed?.(2) ?? stats.max_ms);
  return `mean ${mean}ms · p95 ${p95}ms · max ${max}ms`;
};

const fetchJson = async (url, options = {}) => {
  const response = await fetch(url, options);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed (${response.status})`);
  }
  return response.json();
};

const updateInternalMetrics = async () => {
  els.internalError.textContent = "";
  try {
    const payload = await fetchJson("/internal/metrics");
    els.queueSize.textContent = formatNumber(payload.queue_size);
    els.accepted.textContent = formatNumber(payload["ingestion.accepted"]);
    els.rejected.textContent = formatNumber(payload["ingestion.rejected"]);
    els.persisted.textContent = formatNumber(payload["events.persisted"]);
    els.batchFailures.textContent = formatNumber(payload["batch.failures"]);
    els.latencyBatch.textContent = formatLatency(payload["batch.insert.latency"]);
    els.latencyCount.textContent = formatLatency(payload["metrics.count.latency"]);
    els.latencyUnique.textContent = formatLatency(payload["metrics.unique_users.latency"]);
    const now = new Date();
    els.lastUpdated.textContent = `Last updated: ${now.toLocaleTimeString()}`;
  } catch (error) {
    els.internalError.textContent = `Internal metrics error: ${error.message}`;
  }
};

const toIsoFromLocal = (value) => {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
};

const queryMetrics = async (kind) => {
  const formData = new FormData(els.metricsForm);
  const eventType = formData.get("eventType");
  const start = toIsoFromLocal(formData.get("start"));
  const end = toIsoFromLocal(formData.get("end"));
  if (!eventType || !start || !end) {
    els.metricsResult.textContent = "Please fill in event type, start, and end.";
    return;
  }
  try {
    els.metricsResult.textContent = "Querying...";
    const url = kind === "count"
      ? `/metrics/count?event_type=${encodeURIComponent(eventType)}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`
      : `/metrics/unique-users?event_type=${encodeURIComponent(eventType)}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`;
    const data = await fetchJson(url);
    const label = kind === "count" ? "Count" : "Unique Users";
    const value = formatNumber(data.count ?? data.unique_users ?? data.value);
    els.metricsResult.textContent = `${label} for “${eventType}”: ${value}`;
  } catch (error) {
    els.metricsResult.textContent = `Metrics error: ${error.message}`;
  }
};

const runMlSummary = async () => {
  const formData = new FormData(els.mlForm);
  const eventType = formData.get("eventType");
  const start = toIsoFromLocal(formData.get("start"));
  const end = toIsoFromLocal(formData.get("end"));
  if (!eventType || !start || !end) {
    els.mlResult.textContent = "Please fill in event type, start, and end.";
    return;
  }
  try {
    els.mlResult.textContent = "Running summary...";
    const url = `/ml/summary?event_type=${encodeURIComponent(eventType)}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`;
    const data = await fetchJson(url);
    const anomalies = data.anomalies ?? [];
    const anomalyCount = formatNumber(anomalies.length);
    const mean = formatNumber(data.meanPerHour?.toFixed?.(2) ?? data.meanPerHour);
    const stdDev = formatNumber(data.stdDevPerHour?.toFixed?.(2) ?? data.stdDevPerHour);
    const avgPerUser = formatNumber(data.avgEventsPerUser?.toFixed?.(2) ?? data.avgEventsPerUser);
    els.mlResult.textContent =
      `Total ${formatNumber(data.totalCount)} events, ` +
      `${formatNumber(data.uniqueUsers)} users, ` +
      `avg ${avgPerUser} events/user. ` +
      `Hourly mean ${mean}, std ${stdDev}. ` +
      `Anomalies: ${anomalyCount}.`;
  } catch (error) {
    els.mlResult.textContent = `ML summary error: ${error.message}`;
  }
};

const sendBatch = async () => {
  try {
    const payload = JSON.parse(els.ingestPayload.value || "{}");
    els.ingestResult.textContent = "Sending batch...";
    const data = await fetchJson("/events", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    els.ingestResult.textContent = `Accepted: ${formatNumber(data.accepted)}`;
    updateInternalMetrics();
  } catch (error) {
    els.ingestResult.textContent = `Ingest error: ${error.message}`;
  }
};

const loadSample = () => {
  const now = new Date();
  const sample = {
    events: [
      {
        event_id: `evt_${Math.random().toString(36).slice(2, 8)}`,
        user_id: "user_001",
        event_type: "signup",
        event_time: new Date(now.getTime() - 1000 * 60 * 12).toISOString(),
        properties: { plan: "pro", channel: "ad" },
      },
      {
        event_id: `evt_${Math.random().toString(36).slice(2, 8)}`,
        user_id: "user_002",
        event_type: "signup",
        event_time: new Date(now.getTime() - 1000 * 60 * 5).toISOString(),
        properties: { plan: "basic", channel: "organic" },
      },
    ],
  };
  els.ingestPayload.value = JSON.stringify(sample, null, 2);
};

const setDefaultWindow = () => {
  const end = new Date();
  const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);
  const fmt = (d) => {
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };
  els.metricsForm.start.value = fmt(start);
  els.metricsForm.end.value = fmt(end);
  if (els.mlForm) {
    els.mlForm.start.value = fmt(start);
    els.mlForm.end.value = fmt(end);
  }
};

els.countBtn.addEventListener("click", () => queryMetrics("count"));
els.uniqueBtn.addEventListener("click", () => queryMetrics("unique"));
els.mlBtn.addEventListener("click", runMlSummary);
els.ingestBtn.addEventListener("click", (event) => {
  event.preventDefault();
  sendBatch();
});
els.sampleBtn.addEventListener("click", loadSample);
els.refreshAll.addEventListener("click", updateInternalMetrics);

setDefaultWindow();
loadSample();
updateInternalMetrics();
setInterval(updateInternalMetrics, 10000);
