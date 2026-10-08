# 🌐 IdleGrid

**IdleGrid** is a distributed cloud compute platform built for university laboratory networks. It transforms idle, underutilized lab PCs into a powerful, decentralized compute cluster—allowing students to run intensive computational jobs across the network **without interrupting the PC's primary user**.

By leveraging Docker and WSL2's native resource capping (`.wslconfig`), IdleGrid achieves strict "co-tenancy." A job can run in the background with a hard limit on CPU and RAM, ensuring the PC's physical owner experiences zero performance degradation.

---

## 🏗️ Architecture & Components

The IdleGrid ecosystem is divided into four main components:

### 1. Master Backend (`/backend`)
The brain of the cluster. Built with **Java & Spring Boot**.
* **Node Management:** Tracks which lab PCs are currently online via heartbeats and monitors their available CPU/RAM capacity.
* **Job Scheduling:** Receives job requests (with specific CPU/RAM requirements) and assigns them to the most suitable available node using a first-fit algorithm.
* **State Tracking:** Maintains an in-memory ledger of all queued, running, and completed jobs.

### 2. The Agent (`/agent`)
The worker process that runs silently on each lab PC. Built with **Java** and packaged as a native Windows executable (`.exe`).
* **Heartbeats:** Continuously pings the Master backend to announce its presence and available hardware resources.
* **Job Execution:** When assigned a job, it pulls the requested Docker image and executes the job command.
* **Resource Isolation:** Job containers are strictly constrained by the lab PC's WSL2 configuration, ensuring the local user is never disrupted.
* **Auto-Installer (`agent/installer`):** Includes a zero-dependency, native Windows setup executable (`IdleGrid-Setup.exe`) that automatically enables WSL2 and installs Docker Desktop in the background.

### 3. Internal Dashboard (`/frontend`)
The control panel for submitting jobs. Built with **React, Vite, and TailwindCSS**.
* **Live Node Map:** See a real-time list of all connected lab PCs, their IP addresses, and their current resource availability.
* **Job Submission:** A clean form to submit arbitrary commands or scripts, specifying exactly how much CPU and RAM the job requires.
* **Job Tracking:** Watch jobs move from `QUEUED` to `ASSIGNED` to `RUNNING` or `FAILED`, and grab SSH connection strings to connect directly to running jobs.

### 4. Public Portal (`/public-landing-page`)
The public-facing showcase website. Built with **React & Vite**.
* **User Onboarding:** Explains the IdleGrid concept to students and lab administrators.
* **1-Click Download:** Provides a direct link to download the latest `IdleGrid-Setup.exe` from GitHub Releases so anyone can contribute their PC to the grid.

---

## 🚀 CI/CD Automation (GitHub Actions)

IdleGrid utilizes a fully automated cloud build pipeline via **GitHub Actions** (`.github/workflows/release.yml`). 

Whenever code is pushed to the `main` branch, the pipeline wakes up a cloud Windows server and automatically:
1. Compiles the Agent Java code into a fat JAR.
2. Uses `jpackage` to bundle the Agent with a native JRE into a standalone `.exe`.
3. Packages the Agent into a distributable `.zip`.
4. Uses Windows `IExpress` to compile the `IdleGrid-Setup.exe` auto-installer.
5. Publishes all of these artifacts directly to the **GitHub Releases** page under the `latest` tag.

---

## 🛠️ Local Development & Setup

### Prerequisites
* Java 17+
* Node.js & npm (for frontends)
* Maven

### Running the Backend
```bash
cd backend
mvn spring-boot:run
# The backend will start on http://localhost:9090
```

### Running the Internal Dashboard
```bash
cd frontend
npm install
npm run dev
# Open http://localhost:5173
```

### Building the Agent Locally (Windows Only)
If you want to manually build the agent executables on your local machine instead of waiting for GitHub Actions:
```cmd
cd agent
package.bat
```
This will output the `IdleGridAgent-windows.zip` in the `agent/dist/` folder.

To manually build the Installer EXE:
```cmd
cd agent/installer
Compile-Setup.bat
```
This will generate `IdleGrid-Setup.exe`.

---

## 🔒 Current Scope (Phase 1 Prototype)
This repository currently represents the Phase 1 Proof-of-Concept. 
* **In-Memory Only:** Job and node states are stored in-memory in the backend for simplicity during demos. (No external database).
* **No Authentication:** The API and frontend are open to demonstrate the core scheduling and execution loops. 
* **Focus:** The primary goal of this milestone is to successfully demonstrate a distributed Docker job running on a lab PC without disrupting an active user.
