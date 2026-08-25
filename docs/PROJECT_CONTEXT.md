# IdleGrid — Distributed Idle/Co-Tenant Compute Harvesting Platform for Campus Labs

**Team:** Avadhut Mali (PRN 23510003) and team, Final Year B.Tech CSE, Walchand College of Engineering, Sangli
**Type:** Final-year mega project
**Prototype constraint:** Guide has assigned 5 lab PCs to build and demonstrate a working prototype before further scale-up

---

## 1. Problem Statement

Many students rely on low-spec personal laptops (e.g. i3, 8GB RAM) that cannot run compute-heavy coursework — ML model training, running local LLMs, large builds, simulations, or data-heavy pipelines. Meanwhile, college computer labs contain many PCs that sit idle for large portions of the day, or are only partially used even during lab hours. There is no existing system that lets students borrow this unused/underused capacity safely, automatically, and without disturbing the machine's owner.

## 2. Motivation

- Students without powerful hardware are effectively excluded from compute-heavy coursework and experimentation.
- Lab PCs represent a large pool of underused compute that currently delivers no value outside scheduled lab sessions.
- Paid cloud compute (AWS/GCP credits) is not sustainable for routine student use.
- A private, campus-owned compute pool solves this at zero additional hardware cost.

## 3. Objectives

1. Design and prototype a platform that lets students request compute and storage resources from a pool of campus lab PCs via a simple web portal.
2. Allow safe co-location of student workloads with the PC owner's normal use — not just "idle-only" harvesting — using OS-level resource isolation.
3. Provide two distinct services modeled on public cloud patterns: an ephemeral compute service (like EC2) and a persistent storage service (like S3).
4. Demonstrate the system working end-to-end on a 5-PC prototype, with measurable results (job throughput, owner-impact, resource utilization).
5. Explore one advanced/research-level extension in depth as a differentiated contribution beyond standard idle-harvesting systems.

## 4. Scope and Limitations (stated explicitly)

- **Network scope:** campus LAN only for the prototype. The Master service sits on a private IP; there is no route from the public internet, so access is limited to devices on the campus network (WCE WiFi / lab subnet). Off-campus access would require a VPN, a cloud-hosted Master, or a tunnel — out of scope for the prototype.
- **OS scope:** worker nodes are Windows PCs; isolation is achieved via WSL2 + Docker Desktop, not native Windows containers. Not directly portable to non-Windows lab environments without changes.
- **Scale:** prototype targets 5 PCs (1 Master + 4 workers). Not evaluated at production/datacenter scale.
- **Persistence:** in Phase 1, a compute session/job is not persistent — it is lost if the assigned node goes offline or the session is closed; only the storage layer (MinIO) is persistent across sessions. Job/session-level persistence (checkpointing) is deferred to a later phase.
- **Fault tolerance:** Phase 1 uses a simple kill-and-requeue/kill-and-restart model rather than transparent checkpointing. This is a deliberate scoping decision, not an oversight — full process checkpointing (e.g. via CRIU) is noted as future work.
- **Operational dependency:** requires lab-admin approval to install the Agent on lab PCs and to allow the Master's network access — noted as a risk/dependency, not assumed away.

## 5. Novelty / Research Positioning

Existing idle-harvesting systems (Condor, BOINC) assume the *only* harvesting window is when the PC owner is fully absent, and evict jobs the instant the owner returns. This project instead investigates **safe co-location**: applying resource-partitioning and interference-management techniques from datacenter research (Bubble-Up, Heracles — see Section 11) to consumer Windows hardware via WSL2/Docker resource capping, so a lab PC can contribute compute *continuously*, even while its owner is actively using it, without materially degrading the owner's experience.

The system is structured as two cloud-like services (compute + storage), and — as a Phase 3 research extension — explores learned/adaptive alternatives (federated-learning-style distributed training, RL-based scheduling) to the classical rule-based scheduling algorithms used by prior systems, rather than repeating a gossip/eventual-consistency design already covered extensively in the guide's own teaching background.

## 6. System Architecture

### 6.1 High-level components

- **Frontend (Student Portal):** web app (browser-based), talks only to the Master — never directly to worker PCs. Shows live node/resource availability, lets the student configure a compute session (CPU/RAM), start a session, get an embedded terminal, and manage their storage bucket.
- **Master (Backend, PC-1):** Spring Boot service. Owns the node registry, job/session queue, scheduler, and REST API. Single gatekeeper between students and the compute pool.
- **Agent (on each worker PC, PC-2 to PC-5):** background service. Heartbeats resource availability to Master, executes assigned sessions inside Docker containers, reports status back.
- **Storage service:** MinIO running in distributed mode across the 4 worker PCs, S3-compatible, providing persistent per-student storage independent of compute session lifetime.

### 6.2 Master responsibilities

- REST API layer for frontend and Agents
- PostgreSQL database: `nodes`, `jobs`/`sessions`, `job_logs` tables
- Node registry, fed by Agent heartbeats (marks nodes OFFLINE on heartbeat timeout)
- Scheduler loop (first-fit initially; Dominant Resource Fairness considered for Phase 2 — see Section 9)

### 6.3 Core API endpoints (Phase 1 target)

```
GET  /nodes/summary            -> live node/resource availability for the frontend
POST /sessions/start           -> student requests {cpu, ram}; backend assigns a node,
                                    starts a container, returns session ID + terminal URL
GET  /sessions/{id}/terminal   -> WebSocket, proxied to `docker exec` on the assigned Agent
POST /sessions/{id}/end        -> tears down the container
GET  /storage/bucket           -> student's MinIO bucket usage vs quota
POST /nodes/heartbeat          -> Agent -> Master node status report
POST /jobs/{id}/complete       -> Agent -> Master job/session completion report
```

### 6.4 Agent responsibilities

- Heartbeat loop (every 3-5s): reports free reserved-slice capacity to Master
- Owner-activity logger: logs login/input activity locally for research/benchmarking purposes (does not gate scheduling once co-tenancy is adopted)
- Job/session runner: polls Master, pulls session spec, launches Docker container with resource caps, relays terminal I/O, reports completion

## 7. The Co-Tenancy Design Decision

Two harvesting models were considered:

- **Option A — Idle-only harvesting:** classical model (Condor/BOINC-style). Job runs only while the owner is fully absent; hard eviction the instant the owner returns. Simple, low-risk, but contributes zero capacity during active hours — and the owner-motivating case (a weak laptop) applies equally to many lab PCs, making zero-contribution-during-use wasteful.
- **Option B — True co-tenancy (chosen):** a small resource slice is *always* reserved for student sessions, even while the owner is actively using the PC, enforced at the OS level so the owner's experience is not materially affected.

**Decision:** attempt Option B for the prototype itself, with Option A as an explicit fallback if co-tenancy proves too difficult within the prototype timeline.

### 7.1 Enforcement mechanism: WSL2 + Docker Desktop

Docker Desktop on Windows runs containers inside a lightweight Linux VM (WSL2), not natively on Windows. WSL2's total memory/CPU ceiling is configured via `.wslconfig` (e.g. `memory=2GB`, `processors=1`). This cap is enforced by the VM boundary — the Windows host and the owner's applications never see that memory as available, regardless of whether a student session is currently running. This means the "always-reserved slice" required for co-tenancy is achieved through existing Docker/WSL2 configuration rather than custom Windows resource-partitioning code (e.g. hand-rolled Windows Job Objects), which significantly de-risks the approach.

**Known trade-off (stated explicitly):** the WSL2 cap is a static, global VM boundary — not elastic based on real-time host load. That memory is permanently walled off from the owner even when no student session is running. Consistent with the definition of a standing reservation.

## 8. Isolation and Security

- Sessions run as Docker containers with `--cpus`, `--memory` caps matching the WSL2-configured slice.
- `--network=none` by default — no need for a batch/interactive coursework session to reach the network beyond what's provided by the platform itself.
- No host filesystem mounts beyond the student's own workspace (bind-mounted to their MinIO bucket via rclone/s3fs, so their working directory is transparently persistent).
- Container destroyed on session end or failure — nothing persists on the worker PC's local disk afterward.
- Terminal access is provided into the **container**, not the host PC — students get a full interactive shell (via an embedded web terminal, e.g. xterm.js, proxied over WebSocket to `docker exec`) without ever having host-level SSH access to the physical PC.

## 9. Scheduling

- **Phase 1:** first-fit — assign to the first online node with enough free reserved-slice capacity.
- **Phase 2 (planned upgrade):** Dominant Resource Fairness (DRF) — a multi-resource fairness policy (used in production by Apache Mesos) that fairly allocates CPU and RAM together rather than treating them independently, preventing one student's job from monopolizing the pool.
- **Phase 3 (research direction, optional/exploratory):** a learned scheduling policy inspired by Decima (reinforcement learning-based cluster scheduling) instead of a fixed rule-based algorithm — noted as a forward-looking direction, not a committed deliverable.

## 10. Storage Service (S3-equivalent)

- MinIO, run in **distributed erasure-coded mode** across the 4 worker PCs — MinIO's own fault-tolerance model (designed to tolerate node loss) matches the platform's reality of PCs coming and going, which is stated explicitly as a deliberate design fit, not a coincidence.
- Each worker PC reserves 50GB of disk for the storage pool.
- Each student gets a personal bucket, with a **hard 10GB quota** enforced via MinIO's built-in bucket quota feature.
- Storage is **decoupled from compute sessions**: files persist in a student's bucket independent of whether their compute session is currently running, mirroring the real-cloud separation of ephemeral compute (EC2-like) from persistent storage (S3-like). This also gives the platform real data persistence even before job/session-level persistence (checkpointing) is built.
- The student's bucket is mounted inside their session's container at a working directory path, so files are automatically and transparently persistent without a manual upload/download step.

## 11. Research Grounding / Related Work

| # | Work | Year | Venue | Relevance to this project |
|---|------|------|-------|---------------------------|
| 1 | Litzkow, Livny, Mutka — *Condor: A Hunter of Idle Workstations* | 1988 | ICDCS | Foundational idle-cycle harvesting system; matchmaking-based job placement; baseline "idle-only" model this project extends beyond |
| 2 | Anderson — *BOINC: A System for Public-Resource Computing and Storage* | 2004 | IEEE/ACM Int'l Workshop on Grid Computing | Volunteer/idle computing middleware; client-pull, fault-tolerant job model |
| 3 | Douglis & Ousterhout — *Transparent Process Migration: Design Alternatives and the Sprite Implementation* | 1991 | Software — Practice and Experience, 21(8) | Formalizes evicting a process when an idle workstation's owner returns; baseline eviction model for the idle-only fallback |
| 4 | Isard et al. — *Quincy: Fair Scheduling for Distributed Computing Clusters* | 2009 | ACM SOSP | Fairness, locality, and starvation-freedom in cluster scheduling |
| 5 | Burns, Grant, Oppenheimer, Brewer, Wilkes — *Borg, Omega, and Kubernetes* | 2016 | Communications of the ACM, 59(5) | Lessons from datacenter-scale container orchestration; bin-packing scheduling concepts |
| 6 | Jarkas, Ko, Dong, Mahmud — *A Container Security Survey: Exploits, Attacks, and Defenses* | 2025 | ACM Computing Surveys, 57 | Container/namespace/cgroup hardening practices applied to isolating untrusted student sessions |
| 7 | Widjajarto et al. — *Live Migration Using Checkpoint and Restore in Userspace (CRIU): Usage Analysis of Network, Memory and CPU* | 2021 | Bulletin of Electrical Engineering and Informatics | Real checkpoint/restore costs for containers; candidate mechanism for future job-persistence work |
| 8 | Guilloteau, Bleuse, Cerf, Robu, Pagano, Rutten — *Autonomic Resource Harvesting in HPC: Control Methods and Their Reusability* | 2025 | ACM TAAS | Control-theory methods for dynamic resource harvesting without disturbing primary workloads |
| 9 | Mutka & Livny — *The Available Capacity of a Privately Owned Workstation Environment* | 1991 | Performance Evaluation | Models spare capacity of a personally-owned workstation over time; basis for idle-threshold/reservation sizing |
| 10 | Microsoft — *Advanced Settings Configuration in WSL* (.wslconfig reference) | 2024 | Microsoft Learn | Practical mechanism used to enforce the always-reserved co-tenancy slice |
| 11 | Mars, Tang, Hundt, Skadron, Soffa — *Bubble-Up: Increasing Utilization in Modern Warehouse Scale Computers via Sensible Co-locations* | 2011 | MICRO | Characterizes performance interference from co-locating workloads; methodology basis for measuring owner-side impact of co-tenancy |
| 12 | Lo, Cheng, Govindaraju, Ranganathan, Kozyrakis — *Heracles: Improving Resource Efficiency at Scale* | 2015 | ISCA | Feedback-controlled safe co-location of a best-effort workload alongside a latency-critical one; direct conceptual analogue of student job vs PC owner |
| 13 | Ghodsi, Zaharia, Hindman, Konwinski, Shenker, Stoica — *Dominant Resource Fairness: Fair Allocation of Multiple Resource Types* | 2011 | NSDI | Multi-resource fairness policy (used in Mesos); candidate Phase 2 scheduling upgrade |
| 14 | Dean & Ghemawat — *MapReduce: Simplified Data Processing on Large Clusters* | 2004 | OSDI | Origin of speculative/backup-task execution for straggler mitigation; considered for handling unpredictable node loss |
| 15 | Das, Gupta, Motivala — *SWIM: Scalable Weakly-consistent Infection-style Process Group Membership Protocol* | 2002 | IEEE DSN | Gossip-based decentralized membership/failure detection; considered for a decentralized architecture, ultimately set aside as too close to the guide's existing teaching focus (Cassandra-style gossip protocols) |
| 16 | McMahan, Moore, Ramage, Hampson, Agüera y Arcas — *Communication-Efficient Learning of Deep Networks from Decentralized Data* (FedAvg) | 2017 | AISTATS | Federated learning; designed for exactly this project's "intermittent, unreliable client" scenario (mobile devices ≈ lab PCs going busy/idle); candidate Phase 3 direction reframing ML training as the platform's native execution model |
| 17 | Mao, Schwarzkopf, Venkatakrishnan, Meng, Alizadeh — *Learning Scheduling Algorithms for Data Processing Clusters* (Decima) | 2019 | SIGCOMM | Reinforcement-learning-based cluster scheduling in place of hand-tuned heuristics; candidate Phase 3 direction for a learned scheduler |
| 18 | Gu, Lee, Zhang, Chowdhury, Shin — *Efficient Memory Disaggregation with Infiniswap* | 2017 | NSDI | Remote memory paging over RDMA; cited as the "true RAM sharing" research lineage but noted as infeasible on the prototype's commodity (non-RDMA) hardware |

## 12. Phase Plan

### Phase 1 — Base (non-negotiable core, prototype on 5 PCs)
- Master: Spring Boot API, Postgres schema, node registry, first-fit scheduler
- Agent: heartbeat loop, Docker session execution with WSL2-enforced resource caps
- Frontend: node availability view, resource-request form, embedded web terminal (xterm.js over WebSocket to `docker exec`)
- Storage: MinIO distributed mode across worker PCs, per-student bucket with 10GB quota, bucket mounted into session containers
- Networking: static IP for Master, scoped Windows Firewall rules, campus-LAN-only scope, basic auth
- Fault handling: heartbeat-timeout-based offline detection, kill-and-requeue on node loss (no checkpointing yet)

### Phase 2 — Product
- Dominant Resource Fairness-based scheduling (replacing first-fit)
- Admin/monitoring dashboard for live cluster state
- Real authentication and per-student quotas/session limits
- Application-level job persistence (student jobs checkpoint their own state, e.g. standard ML training checkpointing)

### Phase 3 — Research depth (one direction, explored even if results are imperfect/slow)
Two candidate directions, prioritizing whichever proves most buildable within the timeline:
1. **Contention characterization + feedback-controlled reservation** (Bubble-Up/Heracles-inspired): measure real owner-perceived impact at different reservation sizes; build a controller that dynamically adjusts the reserved slice instead of a static `.wslconfig` cap.
2. **Federated-learning-style distributed training** (FedAvg-inspired): reframe ML training jobs as federated rounds across available nodes, tolerant of nodes dropping mid-round by design, directly serving the project's original motivating use case (students training ML models).

## 13. Feasibility Study

- **Technical feasibility:** WSL2 + Docker resource capping, MinIO distributed mode, and Spring Boot/Postgres are all proven, existing technologies — no speculative/unavailable components required for Phase 1.
- **Economic feasibility:** zero additional hardware cost; reuses existing lab PCs and campus network infrastructure.
- **Operational feasibility:** requires lab-admin approval to install the Agent on lab PCs and permit the Master's network access on the campus LAN — an explicit dependency, not assumed away; a VM-based fallback demo is possible if real lab-PC access is delayed.

## 14. Non-Functional Requirements

- **Security:** container-level isolation (no host filesystem/network access beyond what's explicitly granted); no direct SSH/host access to physical lab PCs.
- **Performance:** target session-start latency in the low single-digit seconds for the prototype demo.
- **Scalability:** designed so additional worker PCs can join using the same Agent model without architectural change beyond the 5-PC prototype.
- **Availability:** campus-network scope only; does not require 24/7 uptime of all nodes — the pool naturally shrinks/grows with how many PCs are powered on.
- **Usability:** browser-based access only, no client software installation required for students.

## 15. Modules Breakdown

- **Student Module:** login, resource-request UI, live node availability view, embedded terminal, storage bucket view
- **Scheduler Module:** node registry, job/session queue, placement algorithm (first-fit → DRF)
- **Agent/Compute Module:** per-PC heartbeat, session execution, resource capping via WSL2/Docker
- **Storage Module:** MinIO distributed cluster, per-student bucket + quota enforcement, container-mount integration
- **Admin/Monitoring Module:** live cluster state view, session management, usage tracking (guide/admin facing)

## 16. Team and Technology Stack

- **Backend (Master):** Java, Spring Boot, PostgreSQL
- **Agent:** Python or Go (lightweight background service)
- **Frontend:** web-based portal (React or similar), xterm.js for embedded terminal
- **Isolation:** Docker Desktop with WSL2 backend, `.wslconfig` resource caps
- **Storage:** MinIO (distributed mode), S3-compatible API
- **Networking:** campus LAN, static IP for Master, scoped Windows Firewall rules

## 17. Networking Setup (Prototype)

- All 5 PCs on the same lab subnet; Master (PC-1) uses a static IP/DHCP reservation.
- Worker PCs make outbound-only connections to Master (poll for sessions, send heartbeats) — no inbound firewall rules needed on worker PCs.
- Master's port is scoped to the lab/campus subnet in Windows Firewall, not open to the public internet.
- Private IP addressing means the system is not reachable from outside the campus network by default; off-campus access (if ever needed) would require a college VPN, a cloud-hosted Master, or a temporary tunnel — explicitly out of scope for the prototype.
- To be confirmed with lab/network admin before Phase 1 build: whether the 5 PCs sit on a flat subnet or are segmented by VLAN, and whether client isolation is enabled on the relevant network.

## 18. Risks and Open Questions

| Risk | Mitigation |
|------|------------|
| Lab admin approval to install the Agent on real PCs | Get sign-off early; fall back to a VM-based demo cluster if delayed |
| Untrusted student code escaping container isolation | Restrict to Docker + WSL2 cgroup limits; no privileged containers |
| Docker Desktop requiring an active Windows session to run | Test auto-login or running Docker Engine directly inside WSL2 as a boot task, in week 1 |
| Co-tenancy causing visible owner-side slowdown | Empirically test at multiple reservation sizes before committing; fallback to idle-only harvesting if unacceptable |
| Small lab size limits demo scale | Emphasize per-node correctness and owner-impact measurements over raw node count |

## 19. Demo / Evaluation Plan

- Live demo on the 5-PC prototype: student starts a compute session, runs a real workload (e.g. ML training script) in the embedded terminal, uses persistent storage across a session restart.
- Metrics to report: session-start latency, resource-cap enforcement correctness, owner-perceived impact during co-tenant use (if Phase 3 contention study is completed), storage quota enforcement correctness.
