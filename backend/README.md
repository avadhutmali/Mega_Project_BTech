# backend — Master node

Owns: node registry, job queue, scheduler, REST API. This is the single service every other
component talks to — the frontend (later) and every Agent talk only to this, never to each
other directly.

## Phase 1 scope (current)

- **In-memory state only** — no database yet. A `Map<String, Node>` and a
  `Map<String, Job>` (or equivalent) is sufficient. Don't add Postgres yet; it's planned for
  Phase 2 (see `docs/PROJECT_CONTEXT.md`), not needed for this milestone.
- **No authentication.** Any request is trusted for now.
- **Scheduler: first-fit.** Loop over online nodes, assign a queued job to the first one with
  enough free capacity. Fairness algorithms (DRF) are Phase 2.

## Data model (minimum viable)

```
Node
  id            string
  ip            string
  cpuTotal      int
  ramTotalMb    int
  cpuFree       int
  ramFreeMb     int
  lastHeartbeat timestamp
  status        ONLINE | OFFLINE   (OFFLINE if heartbeat older than timeout, e.g. 15s)

Job
  id            string (generate on submit)
  cpuReq        int
  ramReqMb      int
  command       string             (what the container should run)
  status        QUEUED | ASSIGNED | RUNNING | DONE | FAILED
  assignedNode  string (nullable)
  result        string (nullable)
```

## Endpoints to implement

```
POST /nodes/heartbeat
  body: { nodeId, ip, cpuFree, ramFreeMb }
  -> upserts the node, refreshes lastHeartbeat

POST /jobs/submit
  body: { cpuReq, ramReqMb, command }
  -> creates a QUEUED job, returns { jobId }

GET  /jobs/{id}/status
  -> returns the Job

POST /jobs/{id}/complete
  body: { result, success: bool }
  -> marks job DONE or FAILED, frees the assigned node's capacity

GET  /nodes/summary        (useful for manual testing / demo visibility)
  -> returns all nodes and their status
```

## Scheduler loop

Run on a fixed timer (e.g. every 2–3s, a `@Scheduled` method in Spring):
1. Mark any node OFFLINE if `now - lastHeartbeat > timeout`. If it had a job assigned, requeue
   that job (set back to QUEUED, clear assignedNode).
2. For each QUEUED job, find the first ONLINE node with enough free cpu/ram, assign it
   (job -> ASSIGNED, node's free capacity decremented). The Agent picks it up on its next poll
   (see `agent/README.md` — Agent pulls, Master doesn't push).

## Local testing before lab PC access

Run this on your own laptop. Test with `curl` or Postman against `localhost:8080` — no Agent
needed yet to verify the scheduler logic; you can POST fake heartbeats to simulate nodes.

## Do not build yet

Auth, database, MinIO, DRF fairness, frontend integration beyond this API contract. See
`AGENTS.md` at repo root.
