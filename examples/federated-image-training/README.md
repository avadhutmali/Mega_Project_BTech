# IdleGrid FedAvg demo

This sample trains one CPU model on multiple lab PCs without worker-to-worker network access.
Each PC trains on its deterministic slice of `data/`, uploads a PyTorch checkpoint to the
IdleGrid Master over HTTP, and rank 0 downloads the peer updates and computes sample-weighted
FedAvg. The resulting `model.pt` is the shared model checkpoint.

Upload `idlegrid-fedavg-demo.zip` in the separate **Federated training** page. Choose the
worker count and local epochs. The bundle must contain `fedavg_train.py`, `fedavg_merge.py`,
and a `data/<class-name>/*` directory. All workers must be able to reach the Master at
`http://<master-lan-ip>:9090`; they do not open a DDP rendezvous port or connect to each other.

The run report includes each worker's state, logs, start/end times, local training time,
sample count, loss and accuracy, plus the total wall-clock duration and aggregate metrics.
