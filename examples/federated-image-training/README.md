# IdleGrid FedAvg demo

This sample trains one CPU model on multiple lab PCs without worker-to-worker network access.
Each PC trains on its deterministic slice of `data/`, uploads a PyTorch checkpoint to the
IdleGrid Master over HTTP, and rank 0 downloads the peer updates and computes sample-weighted
FedAvg. The resulting `model.pt` is the shared model checkpoint.

The built-in dataset has 900 generated 64x64 images (300 each of circles, squares, and
triangles). Recreate it with `python3 generate_dataset.py`; the images are synthetic and
exist to demonstrate the workflow, not to claim real-world model quality.

Upload `idlegrid-fedavg-demo.zip` in the separate **Federated training** page. Choose 2–20
workers (limited by currently online and free PCs) and local epochs. The bundle must contain
`fedavg_train.py`, `fedavg_merge.py`, and a `data/<class-name>/*` directory. All workers must be able to reach the Master at
`http://<master-lan-ip>:9090`; they do not open a DDP rendezvous port or connect to each other.

After FedAvg, rank 0 trains the same model for the same epoch count on the full dataset as a
single-PC baseline. The report includes each worker's logs and sample count, aggregate loss
and accuracy, maximum parallel worker compute time, baseline time/metrics, and total elapsed
wall-clock time (which includes setup, transfers, aggregation, and baseline measurement).
Compare wall-clock elapsed time for the end-to-end result; the worker compute figure alone
does not include networking or coordination overhead.
