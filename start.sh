#!/bin/bash
# Linux/macOS: (re)start HarkQuant Terminal on 127.0.0.1:${HQT_PORT:-8702}. PY=<python> to choose the interpreter.
cd "$(dirname "$0")"
PORT=${HQT_PORT:-8702}
PY=${PY:-$( [ -x .venv/bin/python ] && echo .venv/bin/python || echo python3 )}
for p in $(pgrep -f "hqterm[ ]serve --port $PORT"); do kill $p; done
sleep 1
setsid nohup $PY -m hqterm serve --port $PORT > /tmp/hqterm-$PORT.log 2>&1 &
for i in $(seq 60); do curl -s -m 2 --noproxy '*' http://127.0.0.1:$PORT/health >/dev/null && break; sleep 1; done
curl -s -m 3 --noproxy '*' http://127.0.0.1:$PORT/health; echo
