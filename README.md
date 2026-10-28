# IdleGrid

IdleGrid is a campus-lab compute prototype. A Spring Boot Master tracks lab PCs, schedules containerized jobs, manages shared files with worker replicas, and coordinates CPU-first PyTorch DDP runs. Java Agents on Windows PCs report capacity and run Docker containers; a React dashboard provides monitoring and submission screens.

## Project documentation

- [Detailed project and technical guide](DETAILED_README.md) — architecture, workflows, APIs, setup, limitations, demo plan, and report/PPT outline.
- [Backend API and setup](backend/README.md)
- [Backend run instructions](backend/HOW_TO_RUN.md)
- [Agent setup and configuration](agent/README.md)
- [Project motivation and roadmap context](docs/PROJECT_CONTEXT.md)
- [PyTorch DDP example](examples/distributed-image-training/README.md)

## Quick start

Start the Master on port 9090:

```bash
cd backend
./mvnw spring-boot:run
```

Start the internal dashboard:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Extract the Windows Agent ZIP, configure `agent.properties` if automatic Master discovery is unavailable, and start `run-agent.bat`. See the detailed guide for Docker/WSL2, DDP networking, and storage setup.

## Current scope

This is a prototype for a trusted lab network. Node/job/booking/training-run control state is in memory; there is no authentication or database. Shared file bytes and a JSON index persist on the Master filesystem. Multi-node DDP needs direct TCP connectivity and an available worker image; a public GHCR package alone does not guarantee the required image tag has been published.
