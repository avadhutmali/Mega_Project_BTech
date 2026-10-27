# Running the backend (Master server)

## Prerequisites

- JDK 17+ installed
- No Maven install needed — `mvnw.cmd` downloads it automatically on first run

## Run it

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Starts on **`http://localhost:9090`**

The backend listens on **port 9090**, configured in `application.properties`.

## Enable JSONBin Master discovery

The configured rendezvous bin is public, so the Master and Agents use it without API keys.
The default bin ID is configured; override it with `JSONBIN_BIN_ID` if using another bin.
If the Master has multiple network interfaces, set `JSONBIN_MASTER_URL` to the address
lab Agents can reach.

Example in PowerShell when auto-detection selects the wrong network interface:

```powershell
$env:JSONBIN_MASTER_URL = "http://<master-ip>:9090"
.\mvnw.cmd spring-boot:run
```

## Test it end to end with curl (no Agent needed yet)

1. Simulate a node heartbeat:
```powershell
curl -X POST http://localhost:9090/nodes/heartbeat `
  -H "Content-Type: application/json" `
  -d '{"nodeId":"pc-1","ip":"192.168.1.101","cpuTotal":8,"cpuFree":4,"ramTotalMb":8192,"ramFreeMb":4096,"diskTotalMb":512000,"diskFreeMb":256000,"status":"ONLINE"}'
```

2. Check it registered:
```powershell
curl http://localhost:9090/nodes/summary
```

## Upload and retrieve a file

The dashboard has a **Storage** page. From PowerShell, you can also upload a file:

```powershell
curl.exe -X POST http://localhost:9090/storage/files -F "file=@C:\path\to\file.zip"
```

Save the returned `key`. Retrieve the same file later with:

```powershell
curl.exe -L -o file.zip http://localhost:9090/storage/files/<key>/content
```

The Master stores the primary copy under `backend/storage`; online Agents copy it into
their `storage/` directory until there are two worker replicas or no eligible disk remains.

3. Submit a job:
```powershell
curl -X POST http://localhost:9090/jobs/submit `
  -H "Content-Type: application/json" `
  -d '{"cpuReq":1,"ramReqMb":512,"command":"echo hello"}'
```
Note the `jobId` in the response.

4. Within ~3 seconds (the scheduler tick), check its status — it should move
   from `QUEUED` to `ASSIGNED`:
```powershell
curl http://localhost:9090/jobs/<jobId>/status
```

5. Simulate the Agent picking it up:
```powershell
curl http://localhost:9090/agent/pc-1/assignment
```
This should return the job and flip it to `RUNNING`.

6. Simulate the Agent reporting completion:
```powershell
curl -X POST http://localhost:9090/jobs/<jobId>/complete `
  -H "Content-Type: application/json" `
  -d '{"result":"hello","success":true}'
```

7. Confirm it's `DONE` and the node's capacity was freed back up:
```powershell
curl http://localhost:9090/jobs/<jobId>/status
curl http://localhost:9090/nodes/summary
```

If all seven steps work, the full scheduling pipeline is proven — before a
single real Agent or lab PC is involved.

## Simulating a node going offline

Just stop sending heartbeats for that `nodeId` (i.e. don't run step 1 again).
After ~15 seconds the scheduler will mark it `OFFLINE`, and if it had a job
`ASSIGNED`/`RUNNING`, that job goes back to `QUEUED` automatically.
