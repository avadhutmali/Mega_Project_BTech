# IdleGrid Public Download Page

Hello! 👋 
This site provides the public download for the IdleGrid Windows Agent and quick setup instructions for campus lab PCs.

## Download artifact

The current download is the portable agent ZIP at `public/downloads/IdleGridAgent-windows.zip`.
`agent/package.bat` updates this static copy whenever it rebuilds the agent ZIP.
The package includes `run-agent.bat`; it checks prerequisites, installs missing Java 17+,
WSL2, and Docker Desktop with Administrator permission, then starts the Agent. If everything
is ready, the Agent starts directly. Docker Desktop auto-start is enabled at Windows sign-in.

## 🛠 What you need to build:
The direct download URL is `/downloads/IdleGridAgent-windows.zip`. The landing page uses a standard browser download link so it can be served by Vite locally and as a static Vercel asset.
