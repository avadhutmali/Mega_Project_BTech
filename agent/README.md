# agent — runs on each lab PC

Owns: reporting this PC's available capacity to the Master, and executing assigned jobs inside
a resource-capped Docker container.

## Phase 1 scope (current)

Runs on Windows lab PCs via WSL2 + Docker Desktop. Talks only to the Master (`backend/`) —
never to other Agents.

## Prerequisites on each lab PC (set up once, manually, before the agent runs)

1. WSL2 enabled (`wsl --install`, needs a reboot, needs virtualization on in BIOS)
2. Docker Desktop installed, WSL2 backend selected (not Hyper-V)
3. A `.wslconfig` in the Windows user profile folder capping the reserved slice, e.g.:
   ```
   [wsl2]
   memory=2GB
   processors=1
   ```
   This cap is what makes co-tenancy safe — the PC owner's session never sees this memory as
   available, whether a job is running or not.

(A PowerShell script to automate steps 1–3 across multiple PCs is planned — check with Avadhut
before writing a new one, one may already exist.)

## What the agent process does (three loops)

1. **Heartbeat loop** (every 3–5s): `POST /nodes/heartbeat` to the Master with this node's id,
   ip, and current free capacity (cap size minus whatever a running job is using).
2. **Job poll loop**: ask the Master if anything is assigned to this node (add a
   `GET /agent/{nodeId}/assignment`-style endpoint if the Master doesn't already have one you
   can use — coordinate with backend on this, it's not finalized yet).
3. **Job executor**: on receiving a job, run it as:
   ```
   docker run --rm --cpus=<cpuReq> --memory=<ramReqMb>m <image> <command>
   ```
   capture stdout/exit code, then `POST /jobs/{id}/complete` with the result.

## Language choice

Not fixed yet — Python is the faster path to a working demo (use `requests` + `subprocess` or
the `docker` SDK); Go is a fine alternative if you're more comfortable there. Pick one and
note it here once decided, so `AGENTS.md` stays accurate.

## Testing before lab PC access

Run this on your own laptop (needs WSL2 + Docker Desktop installed locally the same way) and
point it at a `backend` instance also running on `localhost`. This proves the whole pipeline
before a single lab PC is involved — see root `README.md`.

## Do not build yet

Checkpointing/persistence, terminal/SSH access into the container, multi-node job splitting.
See `AGENTS.md` at repo root.
