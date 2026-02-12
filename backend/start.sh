#!/bin/bash
# Startup script for Voice Chat Backend

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# Activate venv
if [ ! -d "venv" ]; then
    echo "❌ Virtual environment not found!"
    echo "Run: python3 -m venv venv && source venv/bin/activate && pip install -r requirements.txt"
    exit 1
fi

source venv/bin/activate

# Parse arguments
PORT=${1:-9000}
HOST=${2:-127.0.0.1}

echo "════════════════════════════════════════════════════════════"
echo "🚀 Voice Chat Backend Starting..."
echo "════════════════════════════════════════════════════════════"
echo ""
echo "📍 Server: http://$HOST:$PORT"
echo "📚 API Docs: http://$HOST:$PORT/docs"
echo "❤️  Health: curl http://$HOST:$PORT/health"
echo ""
echo "Press Ctrl+C to stop"
echo "════════════════════════════════════════════════════════════"
echo ""

# Start server
python -m uvicorn app.main:app --host "$HOST" --port "$PORT" --reload
