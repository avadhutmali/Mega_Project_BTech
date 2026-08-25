# frontend — student web portal

**Status: not started. Do not build yet.** This folder exists so the repo structure is stable
when this phase starts — it is not part of the current milestone.

## What this will eventually be (Phase 2+)

A web app talking only to `backend/`'s API — never directly to any Agent. Planned features,
for context (see `docs/PROJECT_CONTEXT.md` for full detail):

- Login
- Live view of node availability (`GET /nodes/summary`)
- Resource-request form (choose cpu/ram within quota) + "start session"
- Embedded terminal (xterm.js over WebSocket, proxied to `docker exec` on the assigned node)
- Storage bucket view (once MinIO is integrated)

Nothing here should be implemented until backend + agent's core demo (Docker/WSL2 resource
capping across real lab PCs) is proven and the guide has signed off on moving forward.
