CREATE TABLE IF NOT EXISTS users (
  user_id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS event_ids (
  event_id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS events (
  event_id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  event_type TEXT NOT NULL,
  event_time TIMESTAMP NOT NULL,
  properties JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);
