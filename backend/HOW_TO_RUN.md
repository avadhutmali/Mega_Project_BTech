# Running this locally (before lab PC access)

## Prerequisites

- JDK 17+ installed
- Maven (or just use the included `mvnw` wrapper if you generate one via
  `mvn -N io.takari:maven:wrapper` — not included here to keep the zip small)

## Run it

```bash
mvn spring-boot:run
```

Starts on `http://localhost:8080`.

## Test it end to end with curl (no Agent needed yet)

1. Simulate a node heartbeat:
```bash
curl -X POST http://localhost:8080/nodes/heartbeat \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"pc-1","ip":"192.168.1.101","cpuFree":1,"ramFreeMb":2048}'
```

2. Check it registered:
```bash
curl http://localhost:8080/nodes/summary
```

3. Submit a job:
```bash
curl -X POST http://localhost:8080/jobs/submit \
  -H "Content-Type: application/json" \
  -d '{"cpuReq":1,"ramReqMb":1024,"command":"echo hello"}'
```
Note the `jobId` in the response.

4. Within ~3 seconds (the scheduler tick), check its status — it should move
   from `QUEUED` to `ASSIGNED`:
```bash
curl http://localhost:8080/jobs/<jobId>/status
```

5. Simulate the Agent picking it up:
```bash
curl http://localhost:8080/agent/pc-1/assignment
```
This should return the job and flip it to `RUNNING`.

6. Simulate the Agent reporting completion:
```bash
curl -X POST http://localhost:8080/jobs/<jobId>/complete \
  -H "Content-Type: application/json" \
  -d '{"result":"hello","success":true}'
```

7. Confirm it's `DONE` and the node's capacity was freed back up:
```bash
curl http://localhost:8080/jobs/<jobId>/status
curl http://localhost:8080/nodes/summary
```

If all seven steps work, the full scheduling pipeline is proven — before a
single real Agent or lab PC is involved.

## Simulating a node going offline

Just stop sending heartbeats for that `nodeId` (i.e. don't run step 1 again).
After ~15 seconds the scheduler will mark it `OFFLINE`, and if it had a job
`ASSIGNED`/`RUNNING`, that job goes back to `QUEUED` automatically.
