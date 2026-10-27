# IdleGrid DDP image-classification demo

Create a ZIP with this layout:

```text
train.py
data/
  cats/*.jpg
  dogs/*.jpg
```

`train.py` must initialize `torch.distributed` with the `gloo` backend, wrap the model
with `DistributedDataParallel`, and use `DistributedSampler`. IdleGrid launches one
`torchrun` node on every selected PC. Each node receives the same ZIP; the sampler assigns
different samples to each rank. Rank zero must write the final state to `model.pt` beside
`train.py` before exiting successfully.

The example `train.py` in this directory satisfies that contract. Submit this command in
the dashboard:

```text
train.py --epochs 2 --batch-size 32
```

This is CPU/Gloo training. Each Agent PC needs the `idlegrid-pytorch-cpu:latest` image
built from `agent/training-worker/Dockerfile`, Docker Desktop host networking enabled, and
TCP connectivity to the other selected PCs on the lab network.
