# IdleGrid Public Download Page

Hello! 👋 
This site provides the public download for the IdleGrid Windows Agent and quick setup instructions for campus lab PCs.

## Download artifact

The current download is the portable agent ZIP at `public/downloads/IdleGridAgent-windows.zip`.
`agent/package.bat` updates this static copy whenever it rebuilds the agent ZIP.
The package includes `run-agent.bat`; it requests Administrator permission and runs
`wsl --install` and the Docker Desktop winget install command every time, then starts the
Agent. Java 17+ must be installed separately.

## 🛠 What you need to build:
The direct download URL is `/downloads/IdleGridAgent-windows.zip`. The landing page uses a standard browser download link so it can be served by Vite locally and as a static Vercel asset.
