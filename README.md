# IdleGrid — Distributed Idle And Co-Tenant Compute Resource Harvesting Platform

Design and Prototype Implementation of a Private Compute and Storage Cloud for Educational Institutions.

Final-year mega project — Walchand College of Engineering, Sangli.

## What this is (short version)

A platform that lets students run compute jobs (ML training, builds, data processing) on
otherwise-idle or lightly-used campus lab PCs, using Docker + WSL2 to safely share a PC's
resources even while its owner is actively using it ("co-tenancy" — not just idle-only
harvesting like classic systems such as Condor/BOINC).

Full design context, architecture rationale, research citations, and phase plan:
see [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md).

## Current phase — READ THIS FIRST

We are **not** building the full system yet. Per guide's direction, the current milestone is a
stripped-down demo proving only the core mechanism:

> Docker + WSL2 resource-capped job execution, coordinated by a Master node, running on real
> lab PCs, with a live demo that a job keeps running unaffected while the PC's owner is
> actively using the machine.

**In scope right now:** `backend/` (Master, in-memory, no DB, no auth) + `agent/` (runs on lab PCs).
**Explicitly out of scope right now:** `frontend/`, persistence, fairness scheduling, MinIO storage,
auth. These come after the core demo is proven — folders exist now so the structure doesn't
need to change later, not because we're building them yet.

## Repo structure

```
idlegrid/
├── backend/     Master node — Spring Boot API, node registry, scheduler (Phase 1: in-memory)
├── agent/       Runs on each lab PC — heartbeats, executes jobs in capped Docker containers
├── frontend/    Student web portal — NOT started yet, Phase 2+
└── docs/        Full project context, architecture, research grounding
```

Each folder has its own `README.md` — read it before working in that folder, whether you're
a teammate or an AI coding assistant picking up the task.

## Team split

- Backend (Master): Avadhut
- Agent: [teammate name]
- Frontend: not staffed yet (Phase 2)

## Running the Phase-1 demo locally (before lab PC access)

Both `backend` and `agent` can run on one laptop for local testing, talking over `localhost`,
before any lab PC is involved. See each folder's README for exact run instructions once code
exists there.
# Mega_Project_BTech
# Mega_Project_BTech
