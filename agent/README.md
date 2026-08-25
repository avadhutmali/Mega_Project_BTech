# agent — runs on each lab PC

Owns: reporting this PC's available capacity to the Master, detecting user idle state,
and executing assigned jobs inside a resource-capped Docker container.

**Language: Java 17** (plain Java daemon + Maven, no Spring Boot — lighter for a background process).

---

## Quick start

### Prerequisites (per lab PC — set up once)

1. Java 17 or later: `java --version`
2. Maven 3.8+: `mvn --version`
3. WSL2 enabled and Docker Desktop installed (WSL2 backend, not Hyper-V)
4. Docker running: `docker ps`

### Build

```bat
cd agent
mvn package -q
```

This produces `target/agent-1.0-SNAPSHOT-exec.jar` — a fat JAR with all dependencies.

### Configure (optional)

Copy `src/main/resources/agent.properties` to the `agent/` directory and edit:

```properties
master.url=http://<master-ip>:8080
idle.threshold.seconds=300
```

Any property in the working-directory `agent.properties` overrides the JAR defaults.
A node ID (UUID) is auto-generated on first run and saved to `.node_id`.

### Run

```bat
run.bat
```

or directly:

```bat
java -jar target\agent-1.0-SNAPSHOT-exec.jar
```

Logs go to `logs/agent.log` (rolling, 10 MB × 3 files) and the console.

---

## What the agent does (three loops)

All loops run on a `ScheduledExecutorService` — timing stays reliable even if one
call blocks briefly.

| Loop | Interval | What it does |
|------|----------|--------------|
| **Heartbeat** | 3 s (default) | `POST /nodes/heartbeat` with nodeId, ip, cpuFree%, ramFreeMb, diskFreeMb, idleState |
| **Idle check** | 10 s (default) | Calls `GetLastInputInfo` (JNA) + `query user`; transitions NodeState ACTIVE ↔ IDLE |
| **Job poll** | 5 s (default) | `GET /agent/{nodeId}/assignment`; on 200, starts docker container |

---

## Idle detection

Two complementary Windows signals are combined:

1. **`GetLastInputInfo`** (JNA → `user32.dll`) — milliseconds since last keyboard/mouse event.
   Same mechanism as BOINC, Condor, and Windows screensavers.
2. **`query user`** (shell) — whether any Windows session is in "Active" state.
   Catches the "watching a video without moving the mouse" case.

**Transition asymmetry (by design):**
- `ACTIVE → IDLE`: only after idle threshold elapses (default 300 s). *Slow departure.*
- `IDLE → ACTIVE`: instantly on any input. *Fast return — container must die before user notices.*

All transitions are logged to `logs/idle_transitions.log` for empirical threshold analysis
(the thesis "results" section — run this for a day on a real lab PC and study the data).

---

## Job execution

On assignment, the agent runs:

```
docker run -d --rm
  --cpus=<cpuReq>
  --memory=<ramReqMb>m
  --network=none
  --label=idlegrid.session=<jobId>
  -v <workspace>:/workspace
  -w /workspace
  <image>
  sh -c "<command>"
```

- `-d` (detached) → Docker prints the container ID immediately so we can kill it on eviction.
- `--network=none` → containers cannot make outbound connections (security).
- `--label=idlegrid.session=<jobId>` → enables orphan cleanup on agent restart.

Output is streamed to `logs/sessions/<jobId>.log` and reported to the Master on completion.

---

## Eviction (IDLE → ACTIVE while a job is running)

1. `EvictionManager` receives the IDLE → ACTIVE state transition.
2. Calls `docker kill <containerId>` — kills the container immediately (~100 ms).
3. Reports `POST /jobs/{id}/complete` with `{ success: false, result: "evicted" }`.
4. Master requeues the job.

**Demo metric:** time between mouse movement and container death — measure this for the
evaluation section.

---

## Orphan cleanup on restart (Step 10)

On startup, before registering as ONLINE, the agent runs:

```
docker ps -a -q --filter label=idlegrid.session
```

Any matching containers (left by a crash) are killed and removed. This prevents silent
zombie containers consuming CPU/RAM without the Master knowing.

---

## API contract (agent side)

| Method | Path | Direction | Purpose |
|--------|------|-----------|---------|
| POST | `/nodes/heartbeat` | Agent → Master | Register / refresh node every 3 s |
| GET | `/agent/{nodeId}/assignment` | Agent → Master | Poll for assigned job |
| POST | `/jobs/{id}/complete` | Agent → Master | Report success, failure, or eviction |

> **Backend note:** `GET /agent/{nodeId}/assignment` needs to be added to the backend.
> The agent handles 404 gracefully until it exists. Coordinate with Avadhut.

---

## Project layout

```
agent/
├── pom.xml                          # Maven build — Java 17, JNA, org.json, shade plugin
├── run.bat                          # Windows launcher
├── src/main/resources/
│   └── agent.properties             # Default config (override in working dir)
└── src/main/java/com/idlegrid/agent/
    ├── AgentMain.java               # Entry point — wires and starts all loops
    ├── config/AgentConfig.java      # Reads config, resolves node ID
    ├── util/HttpUtil.java           # Shared Java 11 HttpClient wrapper
    ├── heartbeat/HeartbeatService.java
    ├── resources/ResourceMonitor.java  # CPU (MXBean) + RAM (wmic) + disk (FileStore)
    ├── idle/
    │   ├── NodeState.java           # ACTIVE/IDLE state machine + listener interface
    │   ├── User32Ext.java           # JNA: GetLastInputInfo from user32.dll
    │   ├── Kernel32Ext.java         # JNA: GetTickCount from kernel32.dll
    │   └── IdleDetector.java        # Combines both signals, drives NodeState
    ├── job/
    │   ├── JobModel.java            # Assignment POJO
    │   ├── JobPoller.java           # Polls Master for assignments
    │   └── JobExecutor.java         # docker run -d, logs, evict, orphan cleanup
    └── eviction/
        └── EvictionManager.java     # NodeState listener → triggers JobExecutor.evict()
```

---

## Do not build yet

Checkpointing/persistence, terminal/SSH access into containers, multi-node job splitting,
MinIO storage, frontend integration. See `AGENTS.md` at repo root.
