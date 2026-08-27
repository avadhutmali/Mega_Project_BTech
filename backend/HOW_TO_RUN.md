# Running the backend (Master server)

## Prerequisites

- JDK 17+ installed
- No Maven install needed — `mvnw.cmd` downloads it automatically on first run

## Run it

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Starts on **`http://localhost:8081`**

> **Note:** Port 8080 is occupied by Oracle TNS Listener on this machine.
> The backend is permanently configured to use **port 8081** in `application.properties`.

## Test it end to end with curl (no Agent needed yet)

1. Simulate a node heartbeat:
```powershell
curl -X POST http://localhost:8081/nodes/heartbeat `
  -H "Content-Type: application/json" `
  -d '{"nodeId":"pc-1","ip":"192.168.1.101","cpuFree":80,"ramFreeMb":4096}'
```

2. Check it registered:
```powershell
curl http://localhost:8081/nodes/summary
```

3. Submit a job:
```powershell
curl -X POST http://localhost:8081/jobs/submit `
  -H "Content-Type: application/json" `
  -d '{"cpuReq":10,"ramReqMb":512,"command":"echo hello"}'
```
Note the `jobId` in the response.

4. Within ~3 seconds (the scheduler tick), check its status — it should move
   from `QUEUED` to `ASSIGNED`:
```powershell
curl http://localhost:8081/jobs/<jobId>/status
```

5. Simulate the Agent picking it up:
```powershell
curl http://localhost:8081/agent/pc-1/assignment
```
This should return the job and flip it to `RUNNING`.

6. Simulate the Agent reporting completion:
```powershell
curl -X POST http://localhost:8081/jobs/<jobId>/complete `
  -H "Content-Type: application/json" `
  -d '{"result":"hello","success":true}'
```

7. Confirm it's `DONE` and the node's capacity was freed back up:
```powershell
curl http://localhost:8081/jobs/<jobId>/status
curl http://localhost:8081/nodes/summary
```

If all seven steps work, the full scheduling pipeline is proven — before a
single real Agent or lab PC is involved.

## Simulating a node going offline

Just stop sending heartbeats for that `nodeId` (i.e. don't run step 1 again).
After ~15 seconds the scheduler will mark it `OFFLINE`, and if it had a job
`ASSIGNED`/`RUNNING`, that job goes back to `QUEUED` automatically.
