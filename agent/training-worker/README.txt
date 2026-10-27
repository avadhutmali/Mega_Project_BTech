Pull the published image on every Agent PC before submitting a distributed run:

  docker pull ghcr.io/avadhutmali/idlegrid-pytorch-cpu:latest

If the GHCR package is not public yet, build this image locally instead:

  docker build -t idlegrid-pytorch-cpu:latest -f training-worker/Dockerfile training-worker

This CPU image contains Python, PyTorch CPU/Gloo, curl, and unzip. The training
container uses Docker host networking, so Docker Desktop 4.34+ must have host
networking enabled in Settings > Resources > Network on Windows PCs.
