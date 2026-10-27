# agent — runs on each lab PC

Owns: reporting this PC's available capacity to the Master, detecting user idle state,
and executing assigned jobs inside a resource-capped Docker container.

**Language: Java 17** (plain Java daemon + Maven, no Spring Boot — lighter for a background process).

---

## ⬇️ Download & Install

### Portable ZIP (recommended)

**[⬇ Download IdleGridAgent-windows.zip](IdleGridAgent-windows.zip)**

1. Extract the ZIP into a folder.
2. Edit `agent.properties` if the Master cannot be discovered automatically.
3. Double-click `run-agent.bat` and keep its window open.

The ZIP contains the standalone fat JAR, launcher, configuration, quick-start guide,
and `setup.ps1`. Install Java 17+ and Docker Desktop first; `setup.ps1` can help prepare
WSL2 and Docker on a lab PC.

---

## Quick start

### Prerequisites (per lab PC — run `setup.ps1` once)

> **The easiest way:** run `setup.ps1` (included in the ZIP) as Administrator — it
> handles all prerequisites automatically.

```powershell
# In an Administrator PowerShell, from the extracted IdleGridAgent folder:
Set-ExecutionPolicy Bypass -Scope Process -Force
.\setup.ps1
```

`setup.ps1` will:
- ✅ Enable WSL2 and Virtual Machine Platform Windows features
- ✅ Download and silently install Docker Desktop
- ✅ Configure Docker Desktop (WSL2 backend, resource limits, auto-start)
- ✅ Check Java 17+ is installed (with install instructions if missing)
- ✅ Run a container smoke test to confirm everything works

> A **reboot** is required after WSL2 features are first enabled.
> Re-run `setup.ps1` after rebooting — it is fully idempotent.

#### Manual prerequisites (if you prefer to set up by hand)

1. Java 17 or later: `java --version`
2. WSL2 enabled: `wsl --set-default-version 2`
3. Docker Desktop installed (WSL2 backend): `docker ps`

### Build

```bat
cd agent
.\mvnw.cmd package -q
```

> **First run only:** `mvnw.cmd` downloads Maven 3.9.6 (~10 MB) to your home directory
> (`~/.m2/wrapper/dists/`) and caches it. Every subsequent build uses the cached copy.
> No Maven installation needed on any lab PC or CI machine.

This produces `target/agent-1.0-SNAPSHOT-exec.jar` — a fat JAR with all dependencies.

### Package (Create Standalone ZIP)

To rebuild the portable Windows ZIP (fat JAR + `run-agent.bat` + config):

```bat
cd agent
.\package.bat
```

This creates `agent/IdleGridAgent-windows.zip`, ready to copy or share.


### Configure (optional)

Copy `src/main/resources/agent.properties` to the `agent/` directory and edit:

```properties
master.url=http://<master-ip>:9090
idle.threshold.seconds=300
```

Any property in the working-directory `agent.properties` overrides the JAR defaults.
A node ID (UUID) is auto-generated on first run and saved to `.node_id`.

The package includes the shared public JSONBin ID. The Master publishes its URL without an
API key, and Agents read the latest record without a key. Set `JSONBIN_MASTER_URL` on the
Master if automatic IP selection chooses the wrong network interface. If JSONBin cannot be reached, LAN discovery still runs;
if every discovery method fails, the agent exits with a config error instead of contacting
the wrong machine at `localhost`.

### Run

```bat
run-agent.bat
```

or directly:

```bat
java -jar agent-1.0-SNAPSHOT-exec.jar
```

Logs go to `logs/agent.log` (rolling, 10 MB × 3 files) and the console.

---

## What the agent does (three loops)

All loops run on a `ScheduledExecutorService` — timing stays reliable even if one
call blocks briefly.

| Loop | Interval | What it does |
|------|----------|--------------|
| **Heartbeat** | 3 s (default) | `POST /nodes/heartbeat` with nodeId, ip, cpuTotal, cpuFree (core equivalents), RAM totals/free, and idleState |
| **Idle check** | 10 s (default) | Calls `GetLastInputInfo` (JNA) + `query user`; transitions NodeState ACTIVE ↔ IDLE |
| **Job poll** | 5 s (default) | `GET /agent/{nodeId}/assignment`; on 200, starts docker container |
| **Container watchdog** | 15 s | `docker inspect` on running container; triggers FAILED report if container vanishes |
| **Storage poll** | 5 s | Pulls assigned file replicas into the node's `storage/` directory |

---

## Idle detection

Two complementary Windows signals are combined:

1. **`GetLastInputInfo`** (JNA → `user32.dll`) — milliseconds since last keyboard/mouse event.
   Same mechanism as BOINC, Condor, and Windows screensavers.
2. **`query user`** (shell) — whether any Windows session is in "Active" state.
   Catches the "watching a video without moving the mouse" case.

The detector records both transitions. Activity state is currently telemetry only; it
does not pause or terminate jobs, which continue under their container resource limits.

All transitions are logged to `logs/idle_transitions.log` for empirical threshold analysis
(the thesis "results" section — run this for a day on a real lab PC and study the data).

---

## Job execution

On assignment, the agent runs:

```
docker run -d
  --cpus=<cpuReq>
  --memory=<ramReqMb>m
  --label=idlegrid.session=<jobId>
  -P
  -v <workspace>:/workspace
  -w /workspace
  <image>
  sh -c "<command>"
```

- `-d` (detached) → Docker prints the container ID immediately so the watchdog can manage it.
- `-P` → publishes image-exposed ports on random host ports for the optional SSH workflow.
- `--label=idlegrid.session=<jobId>` → enables orphan cleanup on agent restart.

Output is streamed to `logs/sessions/<jobId>.log` and reported to the Master on completion.

---

## Co-tenancy

Idle detection records ACTIVE/IDLE transitions for observation. It does not evict a
running container when keyboard or mouse activity resumes: containers continue under
their Docker CPU and memory caps so the PC owner can keep using the machine.

**Demo metric:** compare owner-perceived responsiveness while a capped job runs with
normal use and while no job runs.

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
| POST | `/nodes/heartbeat` | Agent → Master | Register / refresh CPU, RAM, and storage capacity every 3 s |
| GET | `/agent/{nodeId}/assignment` | Agent → Master | Poll for assigned job |
| POST | `/jobs/{id}/complete` | Agent → Master | Report success or failure |
| GET | `/agent/{nodeId}/storage/assignment` | Agent → Master | Pull a file-replica task |
| GET | `/agent/storage/{key}/content` | Agent → Master | Download bytes for a replica |
| POST | `/agent/{nodeId}/storage/{key}/complete` | Agent → Master | Acknowledge replica placement |

The backend implements the assignment endpoint. After a container starts, the agent
also reports `POST /jobs/{id}/running` with the mapped SSH port and node ID.

For shared files, the agent polls `GET /agent/{nodeId}/storage/assignment`, downloads
the selected file from the Master, stores it under its configured storage directory,
and acknowledges the replica at `POST /agent/{nodeId}/storage/{key}/complete`.

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
    │   └── JobExecutor.java         # docker run -d, logs, watchdog handling, orphan cleanup
```

---

## Do not build yet

Checkpointing/persistence, terminal/SSH access into containers, multi-node job splitting,
MinIO storage, frontend integration. See `AGENTS.md` at repo root.
