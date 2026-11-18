# PyTorch DDP and IdleGrid Distributed Training

A learning guide to the multi-PC image-training feature in IdleGrid. It introduces data-parallel training, explains how PyTorch DistributedDataParallel (DDP) works, then follows one training run through this repository's dashboard, Spring Boot Master, Agents, Docker containers, and model download.

> **Current implementation:** CPU training with PyTorch's Gloo backend; 2 or 3 PCs; one training process per PC; TCP host networking. The run requires every worker to stay connected until training finishes. It is a demonstration workflow, not a fault-tolerant production trainer.

## 1. What problem does DDP solve?

Training a model means repeatedly taking a batch of examples, calculating model predictions, measuring the error, calculating gradients, and updating the model weights. A single PC processes one set of batches at a time. Data-parallel training uses multiple processes to process different batches in parallel.

Suppose a training set has 900 images and there are three workers:

- Each worker starts with the same model structure and initial weights.
- Each worker processes a different subset of examples in each epoch.
- Each calculates gradients from its own subset.
- DDP synchronizes those gradients among the workers.
- Each worker applies the same optimizer update, keeping the model copies aligned.

At the end, the ranks have the same learned parameters, so rank zero can write the final checkpoint. There is no separate end-of-run merge of three independently trained models.

### Important: DDP does not split the dataset for you

`DistributedDataParallel` synchronizes model gradients. It does **not** divide or distribute input data. The training program must do that, commonly with `torch.utils.data.DistributedSampler`. PyTorch documents this distinction directly in its [DDP API](https://docs.pytorch.org/docs/stable/generated/torch.nn.parallel.DistributedDataParallel.html).

In IdleGrid, each worker downloads and extracts the same uploaded ZIP. The sample training script uses `DistributedSampler` so each rank gets a different set of sample indices. IdleGrid does not physically split one ZIP into per-PC archives.

## 2. DDP versus nearby ideas

| Idea | What is distributed | How this IdleGrid demo relates |
|---|---|---|
| Data-parallel DDP | Same model on each worker; different training batches; gradients synchronized each step | **Implemented** |
| Model parallelism | Different parts of one model reside on different workers | Not implemented |
| Federated Averaging (FedAvg) | Workers train local models, then aggregate weights at communication rounds | Not implemented; DDP synchronizes gradients during training |
| MapReduce-style data processing | Independent tasks process data and combine task outputs | Not the model-update protocol used here |

DDP is a good fit for this controlled lab-network demo because the workers are expected to be online together and communicate directly. It is less forgiving of workers disappearing than methods designed around intermittent clients.

## 3. The DDP vocabulary

- **Process:** one running copy of the Python training program.
- **Node:** one machine participating in the distributed job. In this IdleGrid demo, a node is one Agent PC.
- **Rank:** a unique number for a process in the whole training group, from `0` to `WORLD_SIZE - 1`.
- **World size:** total number of participating processes. IdleGrid launches one process per PC, so it equals the worker-PC count.
- **Local rank:** process number on one machine. With one process per PC, it is `0` on each PC.
- **Process group:** the group of ranks that communicates through PyTorch distributed operations.
- **Backend:** communication implementation. This demo uses **Gloo**, suitable for CPU workers communicating over Ethernet.
- **Coordinator / rendezvous address:** the IP and TCP port at which ranks find and coordinate with one another.
- **Collective / all-reduce:** a communication operation that combines a tensor value across every rank. DDP uses gradient reductions so every worker can take a consistent optimizer step.
- **Sampler:** code that gives each rank its own sample indices. The example uses `DistributedSampler`.

### Two different “masters” in this project

These names refer to different processes:

1. **IdleGrid Master:** the Spring Boot service on port 9090. It schedules jobs, stores the submitted training bundle, reports run state, and receives the model artifact.
2. **PyTorch DDP coordinator:** rank zero's Agent PC, listening on the configured DDP port (29500 by default). The other ranks connect directly to that PC for PyTorch rendezvous and gradient communication.

The Python workers do not send gradient tensors through the Spring Boot REST API. The backend starts the workers and stores files; PyTorch's Gloo processes communicate peer-to-peer over the lab network.

## 4. What happens during one IdleGrid training run?

```mermaid
sequenceDiagram
    participant U as User / Dashboard
    participant M as IdleGrid Master :9090
    participant A0 as Agent rank 0
    participant A1 as Agent rank 1
    participant A2 as Agent rank 2 (optional)
    participant P as PyTorch Gloo group

    U->>M: Upload ZIP + command + worker/resource settings
    M->>M: Validate bundle; select all workers as a group
    M->>A0: Assign rank 0 Docker job
    M->>A1: Assign rank 1 Docker job
    opt 3-worker run
        M->>A2: Assign rank 2 Docker job
    end
    A0->>M: Download same ZIP bundle
    A1->>M: Download same ZIP bundle
    opt 3-worker run
        A2->>M: Download same ZIP bundle
    end
    A0->>P: torchrun rank 0 starts on coordinator IP:29500
    A1->>P: Connect as rank 1
    opt 3-worker run
        A2->>P: Connect as rank 2
    end
    P->>P: Forward/backward on local batches; reduce gradients
    P->>P: Each rank applies synchronized optimizer updates
    A0->>M: Upload rank-zero model artifact
    U->>M: Poll run status and download artifact
```

### Step-by-step lifecycle

1. **The user submits the form.** The browser posts multipart form data to `POST /distributed-training/runs`. Fields include the ZIP bundle, Python command, worker count, CPU and RAM request per PC, Docker image, and output filename.
2. **The Master validates inputs.** Current limits include 2–3 workers, a positive CPU request, at least 512 MB requested RAM per worker, a Python entrypoint, and a safe output filename. Spring limits uploads to 100 MB. The ZIP validator limits expanded content to 1 GB and rejects unsafe paths.
3. **The Master performs gang placement.** It selects enough online PCs with an IP address and requested capacity, excluding nodes with an active job. The selection and creation of all child jobs use the same scheduler lock so ordinary scheduling cannot interleave midway through placement. If enough PCs are unavailable, submission fails rather than silently starting only part of a DDP group.
4. **The Master creates one Agent job per rank.** It assigns ranks starting at 0, selects rank 0's node IP as the PyTorch coordinator, and sets each container to use Docker host networking.
5. **Each Agent pulls its assignment.** Agents poll `GET /agent/{nodeId}/assignment`. The Agent ensures the requested Docker image exists locally, pulling it if needed, then starts the worker container with the requested CPU/RAM caps.
6. **Each worker downloads the bundle.** The container uses `curl` to fetch the same ZIP from the Master, extracts it under `/workspace/idlegrid-bundle`, and launches `torchrun`.
7. **PyTorch initializes the group.** Every rank uses the same world size, coordinator IP, and coordinator port. If a rank cannot connect, all participants may wait until the process-group timeout or fail.
8. **The user script trains.** `DistributedSampler` selects samples per rank. DDP synchronizes gradients during backward passes; each rank performs the optimizer step.
9. **Rank zero writes and uploads the model.** The sample writes `model.pt` beside `train.py`. After `torchrun` succeeds, rank zero posts it to the Master using `POST /distributed-training/runs/{runId}/artifact`.
10. **The dashboard reports completion.** The run reaches `COMPLETED` only if all child jobs report done and the expected artifact exists. The browser can then download it from the artifact endpoint.

## 5. How the sample training program works

The repository example is [`examples/distributed-image-training/train.py`](../examples/distributed-image-training/train.py). In simplified form, the key parts are:

```python
import torch.distributed as dist
from torch.nn.parallel import DistributedDataParallel as DDP
from torch.utils.data import DataLoader, DistributedSampler

# torchrun provides rank and world-size environment values.
dist.init_process_group(backend="gloo")
rank = dist.get_rank()
world_size = dist.get_world_size()

# Each rank can read the same folder, but the sampler selects its own indices.
sampler = DistributedSampler(
    dataset,
    num_replicas=world_size,
    rank=rank,
    shuffle=True,
)
loader = DataLoader(dataset, batch_size=32, sampler=sampler)

model = DDP(MyModel())
for epoch in range(epochs):
    sampler.set_epoch(epoch)
    for images, labels in loader:
        optimizer.zero_grad(set_to_none=True)
        loss = loss_fn(model(images), labels)
        loss.backward()       # DDP synchronizes gradients across ranks
        optimizer.step()      # each rank applies the same update

if rank == 0:
    torch.save(model.module.state_dict(), "model.pt")

dist.destroy_process_group()
```

This snippet is illustrative; the repository example supplies the full CNN, image loading, optimizer, loss, log output, and checkpoint dictionary.

### Why `sampler.set_epoch(epoch)` matters

When shuffling is enabled, calling `set_epoch` gives the sampler a new deterministic shuffle for each epoch. Without it, ranks can repeat the same ordering across epochs.

### Dataset size and batches

All ranks should construct compatible datasets with the same class-to-label mapping and ordering. If dataset size is not divisible by world size, `DistributedSampler` may pad indices so ranks process equal numbers of examples. That can mean some examples are repeated in an epoch. `drop_last` choices and batch sizes affect the exact number of samples processed.

The sample dataset has 90 generated images across three classes, divisible by both 2 and 3 workers. Each rank therefore gets a balanced number of indices in the demo.

### Metrics in the sample

The example prints rank-local loss and accuracy. These are useful for showing each worker is active, but they are **not a globally aggregated validation metric**. A formal experiment should aggregate metrics across ranks (for example, sum correct counts and sample counts with an all-reduce) and should use a separate validation set.

## 6. `torchrun` settings used by IdleGrid

For every child worker, the backend constructs a command equivalent to:

```bash
torchrun \
  --nnodes=<worker-count> \
  --nproc-per-node=1 \
  --node-rank=<rank> \
  --master-addr=<rank-0-agent-lan-ip> \
  --master-port=29500 \
  --max-restarts=0 \
  train.py --epochs 2 --batch-size 32
```

- `--nnodes` is the total number of Agent PCs in the group.
- `--nproc-per-node=1` starts one Python rank in each container/PC.
- `--node-rank` distinguishes each worker in the group.
- `--master-addr` is the LAN IP of the Agent selected for rank zero, not the Spring Boot Master address.
- `--master-port` is the PyTorch rendezvous port; all peers must be able to reach it.
- `--max-restarts=0` means failed ranks are not restarted automatically.

PyTorch's [torchrun documentation](https://docs.pytorch.org/docs/stable/elastic/run.html) describes the multi-node launcher and rank environment. The DDP API also requires initializing the process group before wrapping the model, and the user script is responsible for data sharding. For CPU workers, PyTorch's [distributed backend guidance](https://docs.pytorch.org/docs/stable/distributed.html) recommends Gloo.

## 7. What to put in the training ZIP

The archive should have `train.py` at its root, plus any modules and dataset files imported by the script. For the example's `ImageFolder` loader:

```text
training.zip
├── train.py
├── model.py                 # optional, if imported by train.py
└── data/
    ├── circle/
    │   ├── image_001.png
    │   └── ...
    ├── square/
    │   └── ...
    └── triangle/
        └── ...
```

`ImageFolder` treats each immediate subfolder under `data/` as a class label. PNG and JPEG files are supported by the installed image library. Keep the ZIP paths relative and avoid wrapping all files in an extra outer directory unless the command points to that nested entrypoint; the working directory after extraction is the archive root.

The Master keeps a run's bundle on its local filesystem, in a run-specific directory under the configured training directory. By default, this is under the operating system's temporary directory as `idlegrid-training-runs/<runId>`. Workers fetch that ZIP from the Master. This is separate from the shared Storage page, which has its own persistent Master file store and access keys.

## 8. Hardware and network prerequisites

### Every Agent PC

- Windows with Docker Desktop using the WSL2 backend.
- Docker Desktop 4.34 or newer for Docker Desktop host networking.
- The same compatible CPU PyTorch training image available locally.
- Enough live free CPU and RAM for the submitted request.
- A running IdleGrid Agent connected to the same Master.

The worker image recipe is [`agent/training-worker/Dockerfile`](../agent/training-worker/Dockerfile). The Agent ZIP includes this recipe, not the built image layers. The dashboard uses the digest supplied for the lab demo; the earlier `latest` reference returned `not found`. Pull the pinned digest on every Agent PC and verify it before starting DDP:

```powershell
# Run from the extracted Agent directory
docker pull ghcr.io/avadhutmali/idlegrid-pytorch-cpu@sha256:74e60bc071f7349b0f7d9656f2105d7238152133e52dc71377b08eaa51883221
```

The Agent checks for the image locally before trying `docker pull`, so a local build under the exact image name can be used while the GHCR tag is unavailable.

### Network paths

1. Every Agent needs HTTP access to the Master (default port 9090) to poll for jobs and download the bundle.
2. Rank zero's container must accept the PyTorch rendezvous connection on TCP port 29500.
3. All rank containers need direct TCP connectivity to rank zero and to the other peers for Gloo collectives. Allow the needed traffic in Windows Firewall and the campus network.
4. Enable Docker Desktop host networking in **Settings → Resources → Network** on each Windows PC.

Heartbeat success only proves that an Agent can contact the backend. It does **not** prove that worker-to-worker TCP connections work. Wi-Fi client isolation and separate VLANs can break DDP while the dashboard still shows all nodes online.

## 9. Running the project's demo

1. Start the Spring Boot backend on the Master PC, normally `http://<master-ip>:9090`.
2. Start the dashboard with `VITE_MASTER_URL` set to the Master URL if it is not local.
3. Install/configure the Agent on every worker PC and confirm each appears online.
4. Enable Docker Desktop host networking and check firewall/network reachability.
5. Pull the pinned worker image digest on every Agent PC and verify it is available locally.
6. Open the dashboard's **Distributed training** page.
7. Upload [`idlegrid-ddp-image-demo.zip`](../examples/distributed-image-training/idlegrid-ddp-image-demo.zip).
8. Use `train.py --epochs 2 --batch-size 32`, choose 2 or 3 workers, and request resources that every selected PC can provide.
9. Submit, inspect each rank's status/log tail, and wait for `COMPLETED`.
10. Download the `model.pt` artifact.

The generated shape dataset is a systems smoke-test: it demonstrates process-group startup, sample distribution, gradient synchronization, and artifact return. It is not a benchmark of useful image recognition or a claim that distributed execution is faster than one PC.

## 10. Useful local checks

### Check Docker image availability on each PC

```powershell
  docker image inspect ghcr.io/avadhutmali/idlegrid-pytorch-cpu@sha256:74e60bc071f7349b0f7d9656f2105d7238152133e52dc71377b08eaa51883221
```

If it is missing and GHCR has a published public image, `docker pull` should work. Otherwise build the image locally using the command above.

### Check an Agent can reach the Master

```powershell
Test-NetConnection <master-ip> -Port 9090
```

### Check rank-zero port reachability

While a training run is starting, test from another participating PC:

```powershell
Test-NetConnection <rank-zero-agent-ip> -Port 29500
```

A test may report closed before rank zero's process is listening. Check again while torchrun has started; if it remains unreachable, verify host networking, the coordinator IP, and firewall rules.

### Local one-machine DDP sanity check

After building the image and ensuring the bundle is in a mounted/current directory, `torchrun --standalone --nnodes=1 --nproc-per-node=2 train.py ...` can test the Python DDP script on one machine. This checks the model/data code, not IdleGrid gang placement or cross-PC networking.

## 11. Reading run status and troubleshooting

| Symptom | Likely cause | What to check |
|---|---|---|
| Submission says insufficient workers | Too few online nodes meet each worker's CPU/RAM request, or nodes already have jobs | Node page, active jobs, and per-worker resource requests |
| Agent reports image pull failure or `not found` | The pinned digest is unavailable/private or the PC cannot reach GHCR | Run the exact `docker pull` command from `QUICKSTART.txt` and verify the package visibility/digest |
| One rank stays `ASSIGNED`/`STARTING` | Agent is offline, busy, or not polling; Docker image startup is still in progress | Agent logs, heartbeat timestamp, Docker status, node/job assignment |
| Process group times out | Wrong coordinator IP/port, a firewall/VLAN/client-isolation rule, or host networking not enabled | Test TCP 29500, confirm rank 0 IP, enable Docker Desktop host networking |
| Training says file/class folder missing | ZIP layout does not match the training script's paths | Put `train.py` at archive root and use the expected `data/<class>/...` tree |
| Run fails after a PC becomes active or drops off | Agent evicted the container or node heartbeat expired; DDP needs all ranks | Keep the selected PCs available for the whole run; restart a new run after fixing the cause |
| Workers finish but no artifact appears | Rank zero did not save the exact output filename/path or could not upload it to the Master | Save the configured output beside `train.py`; check rank-zero logs and Master reachability |
| Run says `COMPLETED` but learning metric is poor | Tiny synthetic dataset/model, too few epochs, or training configuration issue | Treat the sample as connectivity proof; use a suitable real dataset and evaluate with validation data |

Run records are held in memory and the bundle/artifact live on the Master filesystem. A Master restart loses run metadata, even if temporary files remain on disk. DDP jobs use `--max-restarts=0`; the group does not recover through checkpoints.

## 12. Performance and evaluation notes

DDP is not automatically faster. It is most useful when the compute work saved by parallel batches exceeds the communication and coordination cost. Small models, small batches, slow LAN links, and large gradients can make a multi-PC run slower than one PC.

For a fair comparison, record:

- Same model, dataset, preprocessing, global batch size, and epoch count.
- One-worker elapsed time versus 2-worker and 3-worker elapsed time.
- CPU usage, memory use, and approximate network utilization per PC.
- Training/validation metrics aggregated across ranks.
- Whether all ranks completed and whether rank zero returned the expected model file.

In this project, changing worker count while holding per-rank batch size constant changes the global batch size. To keep a comparison fair, either adjust local batch size so the total stays constant or explicitly report the changed global batch size. The prototype currently logs per-rank metrics but does not provide a benchmarking or metric-aggregation dashboard.

## 13. Current scope and what is not implemented

- CPU-only worker image; no GPU device passthrough or NCCL configuration.
- One process/rank per PC; no multiple GPUs or multiple processes per node.
- Only 2–3 workers are accepted by the current backend.
- The same bundle is downloaded by every rank; there is no data cache or remote dataset mount in the DDP path.
- User code must already be DDP-aware. The backend does not convert a single-process Python script automatically.
- No elasticity, checkpoint restart, fault-tolerant membership, or partial-group operation.
- No post-hoc model merge. Gradient synchronization occurs during the training loop.
- DDP run records are not durable across Master restarts.
- GPU training, privacy-preserving federated learning, and model/data sharding are separate future designs, not aliases for this DDP flow.

## 14. Suggested extension path

For a classroom prototype, the next improvements should preserve correctness before adding scale:

1. Publish and verify the exact worker image tag, then automate image-build status reporting.
2. Add a preflight action to test Master access, peer reachability, and port availability before reserving nodes.
3. Add cancel-all behavior and mark the whole worker group failed when one rank dies.
4. Persist run metadata and artifacts beyond the process temp directory.
5. Aggregate evaluation metrics across ranks and record elapsed time and global batch size.
6. Add checkpoint save/restart only after defining how checkpoints are transferred and which workers can resume.
7. Explore GPU-aware scheduling only after image, host driver, device exposure, and NCCL networking requirements are designed and tested.

## 15. References

- [PyTorch `DistributedDataParallel` API](https://docs.pytorch.org/docs/stable/generated/torch.nn.parallel.DistributedDataParallel.html)
- [PyTorch distributed communication package and backend guidance](https://docs.pytorch.org/docs/stable/distributed.html)
- [PyTorch `torchrun` launcher](https://docs.pytorch.org/docs/stable/elastic/run.html)
- [PyTorch multi-node DDP tutorial](https://docs.pytorch.org/tutorials/intermediate/ddp_series_multinode.html)
- [IdleGrid example training script](../examples/distributed-image-training/train.py)
- [IdleGrid backend DDP service](../backend/src/main/java/com/idlegrid/backend/service/DistributedTrainingService.java)
- [IdleGrid DDP controller/API](../backend/src/main/java/com/idlegrid/backend/controller/DistributedTrainingController.java)
- [IdleGrid DDP dashboard](../frontend/src/pages/DistributedTrainingPage.jsx)
- [IdleGrid CPU/Gloo Docker image](../agent/training-worker/Dockerfile)
