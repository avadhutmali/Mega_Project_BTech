Pull the published image on every Agent PC before submitting a distributed run:

  docker pull ghcr.io/avadhutmali/idlegrid-pytorch-cpu@sha256:74e60bc071f7349b0f7d9656f2105d7238152133e52dc71377b08eaa51883221

The dashboards request the pinned digest. If the pull fails, do not submit a DDP run until
the image is available at that digest or the application defaults are changed to a published
tag. Building a local :latest tag will not satisfy the pinned digest reference.

This CPU image contains Python, PyTorch CPU/Gloo, curl, and unzip. The training
container uses Docker host networking, so Docker Desktop 4.34+ must have host
networking enabled in Settings > Resources > Network on Windows PCs.
