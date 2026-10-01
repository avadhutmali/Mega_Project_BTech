# Ubuntu Worker Image for IdleGrid

This image is used by the IdleGrid agents when a user requests an interactive SSH session or a generic compute job.

## Features
- **OS**: Ubuntu 22.04
- **Tools**: Python 3, curl, nano, ping
- **SSH**: OpenSSH Server running on port 22
- **Testing Setup**: The user `student` is created with **no password** (`PermitEmptyPasswords yes`), meaning you can SSH into it instantly without credentials during testing.

## How to build and push to GHCR (GitHub Container Registry)

To avoid Docker Hub rate limits, push this image to your GitHub repository's container registry.

### 1. Login to GHCR
First, create a GitHub Personal Access Token (PAT) with `read:packages` and `write:packages` scopes.
Then login on your machine:
```bash
echo "YOUR_GITHUB_TOKEN" | docker login ghcr.io -u avadhutmali --password-stdin
```


### 2. Build the image
```bash
cd images/ubuntu-worker
docker build -t ghcr.io/avadhutmali/idlegrid-ubuntu-worker:latest .
```

### 3. Push the image
```bash
docker push ghcr.io/avadhutmali/idlegrid-ubuntu-worker:latest
```

### 4. Make the package public
Go to your GitHub repository -> Packages -> `idlegrid-ubuntu-worker` -> Package Settings -> Change visibility to **Public**.
This ensures the Windows Agents can pull the image without needing to login to Docker!
