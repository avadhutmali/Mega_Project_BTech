# 🌐 IdleGrid - Comprehensive Technical Documentation

**IdleGrid** is a distributed cloud compute platform built specifically for university laboratory networks. It transforms idle, underutilized lab PCs into a powerful, decentralized compute cluster—allowing students to run intensive computational jobs across the network without interrupting the PC's primary user.

This document provides in-depth technical details about the system's architecture, APIs, and underlying mechanisms. It is designed to serve as a reference for project presentations and further development.

---

## 🏗️ 1. System Architecture & Components

The IdleGrid ecosystem is divided into four highly decoupled components.

### 1.1 Master Backend (`/backend`)
The brain of the cluster, responsible for orchestrating nodes and jobs. 
* **Technology:** Java 17, Spring Boot, Spring Web.
* **Storage (Phase 1):** In-memory data structures (`ConcurrentHashMap`) to store `Node` and `Job` states. This allows for lightning-fast prototyping and eliminates the need for database setup during initial demos.
* **Scheduling Algorithm:** Currently utilizes a **First-Fit algorithm**. When a job arrives requiring $X$ CPU and $Y$ RAM, the Master iterates through connected nodes and assigns the job to the first node that has $X$ CPU and $Y$ RAM currently available.

### 1.2 The Agent (`/agent`)
The worker process that runs silently in the background on each lab PC.
* **Technology:** Java 17, packaged as a self-contained `.exe` via `jpackage`.
* **Execution Engine:** Uses Docker Desktop via WSL2. 
* **Resource Isolation (Co-Tenancy):** The Agent achieves strict co-tenancy. When a job is assigned, it executes a Docker container. The resources of this container are strictly capped by the Windows PC's WSL2 `.wslconfig` mechanism. This ensures that a background job can never spike and cause the active PC owner's mouse to lag.
* **SSH Bridging:** The Agent can launch a temporary `sshd` container on the host PC and forward a specific port, allowing the user to SSH directly into the container running their job.

### 1.3 Auto-Installer (`/agent/installer`)
A zero-dependency, native Windows setup executable (`IdleGrid-Setup.exe`).
* **Technology:** Built entirely using Windows native scripting (PowerShell + Batch) and compiled using `IExpress` (a native Windows tool).
* **Flow:**
  1. Requests Administrator privileges.
  2. Enables WSL (Windows Subsystem for Linux v2) Windows features.
  3. Silently downloads and installs Docker Desktop.
  4. Automatically edits the Docker config to set hard resource limits based on the PC's total RAM and CPU.

### 1.4 Internal Dashboard (`/frontend`)
The control panel for submitting jobs and tracking the network.
* **Technology:** React, Vite, TailwindCSS.
* **Features:** Allows users to view all active nodes, filter out nodes that don't meet requirements, submit jobs with specific CPU/RAM constraints, and obtain SSH connection strings for running jobs.

---

## 📡 2. REST API Contract

The communication between the Agent, Dashboard, and Master is strictly governed by REST APIs. 

### 2.1 Node Management APIs (Agent ↔ Master)

**`POST /nodes/heartbeat`**
* **Who calls it:** The Agent (every few seconds).
* **Purpose:** Registers the node with the Master or updates its "Last Seen" timestamp and available resources.
* **Request Body:**
  ```json
  {
    "nodeId": "DB_LAB_2726",
    "ipAddress": "10.4.2.66",
    "cpuTotal": 8.0,
    "ramTotalMb": 16384,
    "cpuFree": 4.0,
    "ramFreeMb": 8192
  }
  ```

**`GET /nodes/summary`**
* **Who calls it:** The Dashboard.
* **Purpose:** Returns a list of all online nodes and their current available capacities to render the live network map.

### 2.2 Job Management APIs (Dashboard ↔ Master ↔ Agent)

**`POST /jobs/submit`**
* **Who calls it:** The Dashboard.
* **Purpose:** Submits a new job to the queue. The Master immediately attempts to schedule it.
* **Request Body:**
  ```json
  {
    "cpuReq": 2.0,
    "ramReqMb": 1024,
    "command": "/usr/sbin/sshd && sleep 3600",
    "targetNodeId": "DB_LAB_2726" // Optional: Force execution on a specific node
  }
  ```

**`GET /jobs/{id}/status`**
* **Who calls it:** The Dashboard (polling).
* **Purpose:** Retrieves the live status of a job (e.g., `QUEUED`, `ASSIGNED`, `RUNNING`, `COMPLETED`, `FAILED`).
* **Response:** Includes the assigned node ID and the SSH port mapping if applicable.

**`GET /jobs/agent/{nodeId}`**
* **Who calls it:** The Agent (long-polling/frequent polling).
* **Purpose:** The Agent asks the Master: "Do you have any jobs assigned to me that I haven't started yet?"

**`POST /jobs/{id}/complete`**
* **Who calls it:** The Agent.
* **Purpose:** Informs the Master that a job has finished executing (either successfully or with a failure), allowing the Master to free up the reserved resources on that node.

---

## ⚙️ 3. How the Co-Tenancy & Resource Capping Works (The "Secret Sauce")

The core problem IdleGrid solves is running heavy compute jobs on a PC while someone else is actively using it, without them noticing. 

We achieve this via a three-layered approach:

1. **The Target Image:** Jobs are executed inside a lightweight `ubuntu-worker` Docker container. This sandboxes the job so it cannot interfere with the host Windows file system.
2. **WSL2 Virtualization:** On modern Windows, Docker runs inside the Windows Subsystem for Linux (WSL2) lightweight utility VM. 
3. **The `.wslconfig` Cap:** During the Auto-Installer setup phase, the script checks the PC's total physical RAM and CPU cores. It then strictly limits the WSL2 VM to use *at most* 75% of the host's resources. Even if a student submits an infinite loop job that tries to consume 100% CPU and 32GB of RAM, the Windows OS will strictly enforce the WSL2 boundary, leaving the remaining 25% of resources perfectly free for the physical user to browse the web or code without lag.

---

## 🤖 4. CI/CD Pipeline (Cloud Build)

The project leverages **GitHub Actions** for fully automated Continuous Integration and Continuous Deployment.

* **Trigger:** Pushing code to the `main` branch.
* **Process:** 
  1. A `windows-latest` runner checks out the code.
  2. Maven packages the Spring Boot JAR.
  3. The `jpackage` tool bundles the Java Agent with a native Java Runtime Environment (JRE) into a standalone `IdleGridAgent.exe`, meaning target lab PCs do not need Java installed.
  4. The `Compile-Setup.bat` script leverages Windows `IExpress` to compile the `setup.ps1` PowerShell script into the `IdleGrid-Setup.exe` binary.
* **Output:** The new `IdleGridAgent-windows.zip` and `IdleGrid-Setup.exe` are automatically uploaded to the **GitHub Releases** page, tagged as `latest`. The public landing page always points directly to this `latest` release link, ensuring users always download the newest version.
