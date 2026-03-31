#!/usr/bin/env python3
import argparse
import json
import random
import time
import uuid
from datetime import datetime, timedelta, timezone
from urllib import request

EVENT_TYPES = ["user_signup", "page_view", "purchase"]


def iso_time(dt):
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def make_event(user_id, base_time, event_id, rng):
    event_time = base_time - timedelta(seconds=rng.randint(0, 60 * 60 * 24 * 30))
    return {
        "event_id": str(event_id),
        "user_id": str(user_id),
        "event_type": rng.choice(EVENT_TYPES),
        "event_time": iso_time(event_time),
        "properties": {
            "page": rng.choice(["/", "/home", "/pricing", "/docs", "/checkout"]),
            "source": rng.choice(["ads", "organic", "email", "referral"]),
        },
    }


def post_batch(url, events):
    payload = json.dumps({"events": events}).encode("utf-8")
    req = request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    with request.urlopen(req) as resp:
        body = resp.read().decode("utf-8")
    return body


def main():
    parser = argparse.ArgumentParser(description="Generate events via /events API")
    parser.add_argument("--url", default="http://localhost:8080/events")
    parser.add_argument("--total", type=int, default=10000)
    parser.add_argument("--batch", type=int, default=500)
    parser.add_argument("--users", type=int, default=1000)
    parser.add_argument("--pause", type=float, default=0.0, help="seconds between batches")
    parser.add_argument("--reuse-ids", action="store_true", help="reuse event_ids from a fixed pool")
    parser.add_argument("--id-pool", type=int, default=50000, help="size of reusable event_id pool")
    parser.add_argument("--seed", type=int, default=None, help="seed for deterministic ids/events")
    args = parser.parse_args()

    rng = random.Random(args.seed) if args.seed is not None else random
    user_ids = [uuid.UUID(int=rng.getrandbits(128)) for _ in range(args.users)]
    event_ids = [uuid.UUID(int=rng.getrandbits(128)) for _ in range(args.id_pool)] if args.reuse_ids else None

    sent = 0
    base_time = datetime.now(timezone.utc)

    while sent < args.total:
        batch_size = min(args.batch, args.total - sent)
        events = []
        for _ in range(batch_size):
            user_id = rng.choice(user_ids)
            if event_ids is not None:
                event_id = rng.choice(event_ids)
            else:
                event_id = uuid.UUID(int=rng.getrandbits(128))
            events.append(make_event(user_id, base_time, event_id, rng))

        while True:
            try:
                post_batch(args.url, events)
                break
            except request.HTTPError as e:
                if e.code == 429:
                    time.sleep(0.05)
                    continue
                raise

        sent += batch_size
        if sent % (args.batch * 10) == 0 or sent == args.total:
            print(f"sent {sent}/{args.total}")
        if args.pause > 0:
            time.sleep(args.pause)


if __name__ == "__main__":
    main()
