# AI agent context — read this before touching this repo

This file is for any AI coding assistant working in this repository. It exists so you don't
need to be re-explained the project from scratch every session.

## Project in one paragraph

IdleGrid is a final-year student mega project: a platform that runs student compute jobs on
campus lab PCs, using Docker containers capped via WSL2's `.wslconfig` mechanism, so a job can
run *even while the PC's owner is actively using the machine* ("co-tenancy") without visibly
degrading the owner's experience. Full rationale and citations: `docs/PROJECT_CONTEXT.md`.

## Current milestone — this is the only thing to build right now

A stripped-down proof-of-concept, requested explicitly by the project guide:

- No auth. No database (in-memory control state in `backend/` is fine).
- **`backend/`** — a Master service: tracks which of 3 lab PCs are online and how much
  CPU, RAM, and disk capacity each has free, accepts job submissions, assigns jobs to nodes
  (first-fit is enough), and manages shared files with a persistent local index and worker replicas.
- **`agent/`** — runs on each lab PC: heartbeats to the Master, and when assigned a job, runs
  it inside a Docker container whose CPU/RAM are capped via that PC's WSL2 configuration; it
  also stores file replicas assigned by the Master.
- The demo target: submit jobs, watch them get assigned across 3 real lab PCs, and show a job
  keeps running with its resource cap intact while someone actively uses that same PC normally.

## What NOT to build yet

Do not add authentication, MinIO, fairness scheduling (DRF), job checkpointing, or a
decentralized/gossip protocol. These remain future work. Shared file storage for this milestone
uses the Master filesystem plus up to two worker replicas, with a JSON metadata index rather
than a database.

## API contract between backend and agent (Phase 1)

```
POST /nodes/heartbeat        agent -> backend, reports node id + free capacity
POST /jobs/submit            (test client) -> backend, submits a job {cpu, ram, command}
GET  /jobs/{id}/status       -> backend, poll job state
POST /jobs/{id}/complete     agent -> backend, reports job result
POST /storage/files          dashboard -> backend, uploads a file and returns its access key
GET  /storage/files/{key}/content -> dashboard/client -> backend, downloads by access key
GET  /agent/{nodeId}/storage/assignment -> agent -> backend, pulls a file replica task
POST /agent/{nodeId}/storage/{key}/complete -> agent -> backend, acknowledges the replica
```

Keep this contract in sync between `backend/README.md` and `agent/README.md` if it changes —
both sides depend on it independently.

## Conventions

- Backend: Java, Spring Boot.
- Agent: see `agent/README.md` for the chosen language.
- Keep Phase-1 code simple and readable over clever — this is a student prototype meant to be
  demoed and explained to a technical guide, not a production system.
