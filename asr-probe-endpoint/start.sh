#!/bin/bash
# WO-AUDIO-PROBE-ENDPOINT-FIX
# PM2 startup wrapper for edu-asr-probe
# Sources OPENAI_API_KEY from server-v7/.env before starting

set -e

# Source the OpenAI API key from WonderBear's config
if [ -f /opt/wonderbear/server-v7/.env ]; then
  export $(grep '^OPENAI_API_KEY=' /opt/wonderbear/server-v7/.env | head -1)
fi

# Ensure token is set
export ASR_PROBE_TOKEN="${ASR_PROBE_TOKEN:-ab75e14a101c8779}"
export ASR_PROBE_PORT="${ASR_PROBE_PORT:-8095}"

exec /opt/edu/asr-probe-endpoint/venv/bin/python /opt/edu/asr-probe-endpoint/server.py
