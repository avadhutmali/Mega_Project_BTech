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

- No database (in-memory control state in `backend/` is fine). Student accounts are file-backed salted hashes with in-memory sessions/job ownership; do not expand this into a production identity system without a new request.
- **`client/`** — a student-facing dashboard separate from the operator frontend. It can view live node capacity, submit jobs with user-selected resources, view that account's terminal/job/DDP history and owned files, and release its own active jobs. It must use the authenticated `/client/**` API and must not expose operator controls.
- **`backend/`** — a Master service: tracks which of 3 lab PCs are online and how much
  CPU, RAM, and disk capacity each has free, accepts job submissions, assigns jobs to nodes
  (first-fit is enough), and manages shared files with a persistent local index and worker replicas.
- **`agent/`** — runs on each lab PC: heartbeats to the Master, and when assigned a job, runs
  it inside a Docker container whose CPU/RAM are capped via that PC's WSL2 configuration; it
  also stores file replicas assigned by the Master.
- The demo target: submit jobs, watch them get assigned across 3 real lab PCs, and show a job
  keeps running with its resource cap intact while someone actively uses that same PC normally.

## What NOT to build yet

Do not expand the student login into a production identity system. MinIO, fairness scheduling
(DRF), job checkpointing, and a decentralized/gossip protocol remain future work. Shared file
storage uses the Master filesystem plus up to two worker replicas, with a JSON metadata index.

## API contract between backend and agent (Phase 1)

```
POST /nodes/heartbeat        agent -> backend, reports node id + free capacity
POST /jobs/submit            (test client) -> backend, submits a job {cpu, ram, command}
GET  /jobs/{id}/status       -> backend, poll job state
POST /jobs/{id}/complete     agent -> backend, reports job result
POST /client/auth/login     student client -> backend, signs in using a configured account
GET  /client/nodes           student client -> backend, returns lab capacity summary
GET  /client/jobs            student client -> backend, lists only the signed-in student's jobs
POST /client/jobs            student client -> backend, submits a resource-limited job
POST /client/jobs/{id}/cancel student client -> backend, releases an owned job
GET  /client/storage/files   student client -> backend, lists only the student's uploads
POST /client/storage/files   student client -> backend, uploads a file owned by the student
GET  /client/storage/files/{key}/content student client -> backend, downloads an owned file
POST /client/training/runs   student client -> backend, submits a DDP run under the student's account
GET  /client/training/runs   student client -> backend, lists the student's persisted DDP history
POST /jobs/{id}/cancel       dashboard -> backend, cancels a queued job or requests running-session release
GET /agent/{nodeId}/jobs/{jobId}/cancel-requested -> agent -> backend, checks for a release request
POST /storage/files          dashboard -> backend, uploads a file and returns its access key
GET  /storage/files/{key}/content -> operator dashboard -> backend, downloads by access key
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
