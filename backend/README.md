# backend — Master node

Owns: node registry, job queue, scheduler, file index, REST API. The frontend and Agents
talk only to this service, never to each other directly.

## Phase 1 scope (current)

- Node/job state is in memory; file bytes and their JSON index persist under `./storage`.
  Don't add Postgres yet; it's planned for Phase 2 (see `docs/PROJECT_CONTEXT.md`).
- **No authentication.** Any request is trusted for now.
- **Scheduler: first-fit.** Loop over online nodes, assign a queued job to the first one with
  enough free capacity. Fairness algorithms (DRF) are Phase 2.

## Data model (minimum viable)

```
Node
  id            string
  ip            string
  cpuTotal      number (logical cores)
  ramTotalMb    number (MB)
  cpuFree       number (core equivalents currently available)
  ramFreeMb     number (MB currently available)
  diskTotalMb   number (MB on the Agent working-directory volume)
  diskFreeMb    number (MB currently available on that volume)
  lastHeartbeat timestamp
  status        ONLINE | OFFLINE   (OFFLINE if heartbeat older than timeout, e.g. 15s)

Job
  id            string (generate on submit)
  cpuReq        number (core equivalents; fractional values allowed)
  ramReqMb      int
  command       string             (what the container should run)
  status        QUEUED | ASSIGNED | RUNNING | DONE | FAILED
  assignedNode  string (nullable)
  result        string (nullable)
```

## Endpoints to implement

```
POST /nodes/heartbeat
  body: { nodeId, ip, cpuTotal, cpuFree, ramTotalMb, ramFreeMb, diskTotalMb, diskFreeMb, status }
  -> upserts the node, refreshes lastHeartbeat

POST /jobs/submit
  body: { cpuReq, ramReqMb, command }  (cpuReq is in core equivalents; fractions allowed)
  -> creates a QUEUED job, returns { jobId }

GET  /jobs/{id}/status
  -> returns the Job

POST /jobs/{id}/complete
  body: { result, success: bool }
  -> marks job DONE or FAILED; reservation accounting updates from job state

GET  /nodes/summary        (useful for manual testing / demo visibility)
  -> returns all nodes and their status

POST /storage/files        browser -> backend, multipart field `file`; returns a random access key
GET  /storage/files        -> stored file metadata and keys
GET  /storage/files/{key}/content -> downloads that file using its access key
GET  /storage/summary      -> aggregate online node disk capacity and file count
GET  /agent/{nodeId}/storage/assignment -> agent pulls a replica task (204 if none)
GET /agent/storage/{key}/content -> agent downloads the source bytes for a replica
POST /agent/{nodeId}/storage/{key}/complete -> agent confirms replica placement
```

The Master keeps a canonical file copy and metadata index. Agents hold up to two worker
replicas when online disk capacity allows. The returned random key is a bearer key: anyone
who has it can download that file. The prototype has no user authentication.

## Scheduler loop

Run on a fixed timer (e.g. every 2–3s, a `@Scheduled` method in Spring):
1. Mark any node OFFLINE if `now - lastHeartbeat > timeout`. If it had a job assigned, requeue
   that job (set back to QUEUED, clear assignedNode).
2. For each QUEUED job, find the first ONLINE node with enough free cpu/ram, assign it
   (job -> ASSIGNED; future placement subtracts active job reservations). The Agent picks it up on its next poll
   (see `agent/README.md` — Agent pulls, Master doesn't push).

## Local testing before lab PC access

Run this on your own laptop. Test with `curl` or Postman against `localhost:9090` — no Agent
needed yet to verify the scheduler logic; you can POST fake heartbeats to simulate nodes.

## Do not build yet

Authentication, database, MinIO, DRF fairness, and job checkpointing remain out of scope.
The dashboard includes the storage view and upload flow for this prototype.
