#!/usr/bin/env bash
# =============================================================================
# build-installer.sh  —  Builds IdleGridAgent-Setup.exe from source
# Run from the agent/ directory:
#   bash installer/build-installer.sh
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$AGENT_DIR/installer"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║  IdleGrid Agent — Installer Build Script     ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# ── 1. Check prerequisites ──────────────────────────────────────────────────
echo "[1/4] Checking prerequisites..."

if ! command -v makensis &>/dev/null; then
    echo "  NSIS not found. Installing..."
    sudo apt-get install -y nsis
fi
echo "  OK: makensis $(makensis /VERSION 2>&1 || true)"

# ── 2. Build fat JAR ─────────────────────────────────────────────────────────
echo ""
echo "[2/4] Building fat JAR..."
cd "$AGENT_DIR"
./mvnw package -q -DskipTests
JAR=$(ls target/agent-*-exec.jar | head -1)
echo "  OK: $JAR ($(du -sh "$JAR" | cut -f1))"

# ── 3. Assemble installer staging directory ──────────────────────────────────
echo ""
echo "[3/4] Staging installer files..."
STAGE="$OUT_DIR/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE"

cp "$JAR"                    "$STAGE/agent-1.0-SNAPSHOT-exec.jar"
cp "$AGENT_DIR/agent.properties" "$STAGE/"
cp "$AGENT_DIR/setup.ps1"        "$STAGE/"
cp "$OUT_DIR/LICENSE.txt"        "$STAGE/"
cp "$OUT_DIR/installer.nsi"      "$STAGE/"

echo "  Staged files:"
ls -lh "$STAGE/"

# ── 4. Compile NSIS installer ────────────────────────────────────────────────
echo ""
echo "[4/4] Compiling NSIS installer..."
cd "$STAGE"
makensis installer.nsi

EXE="$STAGE/IdleGridAgent-Setup.exe"
if [ -f "$EXE" ]; then
    # Copy final EXE to agent root
    cp "$EXE" "$AGENT_DIR/IdleGridAgent-Setup.exe"
    SIZE=$(du -sh "$AGENT_DIR/IdleGridAgent-Setup.exe" | cut -f1)
    echo ""
    echo "╔══════════════════════════════════════════════════════╗"
    echo "║  BUILD SUCCESS                                        ║"
    echo "║                                                       ║"
    echo "║  Output: agent/IdleGridAgent-Setup.exe  ($SIZE)       ║"
    echo "║                                                       ║"
    echo "║  Copy this single file to any Windows lab PC and     ║"
    echo "║  double-click it to install.                         ║"
    echo "╚══════════════════════════════════════════════════════╝"
    echo ""
else
    echo "ERROR: NSIS build failed — installer.exe not found"
    exit 1
fi
