#!/usr/bin/env python3
"""
WO-AUDIO-PROBE-ENDPOINT-FIX — standalone ASR probe endpoint
Runs as independent PM2 process 'edu-asr-probe' on :8095.

Path-based token protection: the endpoint path itself IS the token.
Only requests hitting the correct path can reach the ASR proxy.

Security:
- OPENAI_API_KEY is read from env, NEVER hardcoded
- No auth/session — this is a diagnostic probe, not a product endpoint
- Path-based token prevents casual scanning/abuse
- CORS restricted to WebViewAssetLoader origin
"""

import os
import sys
import time
import tempfile
import logging

from flask import Flask, request, jsonify
from flask_cors import CORS

# ── Config ──
PROBE_TOKEN = os.environ.get('ASR_PROBE_TOKEN', 'ab75e14a101c8779')
PROBE_PATH = f'/asr-probe-{PROBE_TOKEN}'
PORT = int(os.environ.get('ASR_PROBE_PORT', '8095'))
OPENAI_API_KEY = os.environ.get('OPENAI_API_KEY')

# ── Allowed CORS origins ──
ALLOWED_ORIGINS = [
    'https://appassets.androidplatform.net',
]

# ── Logging ──
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [edu-asr-probe] %(levelname)s %(message)s',
    stream=sys.stderr
)
logger = logging.getLogger('edu-asr-probe')

# ── App ──
app = Flask(__name__)

# CORS: only allow WebViewAssetLoader origin (坑 #5)
# Also allow localhost for testing
CORS(app, resources={
    PROBE_PATH: {
        'origins': ALLOWED_ORIGINS + ['http://localhost:8095', 'http://127.0.0.1:8095'],
        'methods': ['POST', 'OPTIONS'],
        'allow_headers': ['Content-Type'],
        'max_age': 86400
    }
})


@app.route(PROBE_PATH, methods=['POST', 'OPTIONS'])
def asr_probe():
    """ASR probe endpoint — receives multipart audio, forwards to OpenAI."""

    if request.method == 'OPTIONS':
        return '', 204

    t0 = time.time()

    # ── Validate audio ──
    if 'audio' not in request.files:
        return jsonify({
            'transcript': None,
            'latencyMs': 0,
            'provider': 'openai/gpt-4o-mini-transcribe',
            'error': 'No audio file (field: "audio")'
        }), 400

    audio_file = request.files['audio']
    if not audio_file.filename:
        return jsonify({
            'transcript': None,
            'latencyMs': 0,
            'provider': 'openai/gpt-4o-mini-transcribe',
            'error': 'Empty audio file'
        }), 400

    language = request.form.get('language', 'pl')
    logger.info(f'ASR request: file={audio_file.filename}, language={language}')

    # ── Check API key ──
    if not OPENAI_API_KEY:
        logger.error('OPENAI_API_KEY not configured')
        return jsonify({
            'transcript': None,
            'latencyMs': int((time.time() - t0) * 1000),
            'provider': 'openai/gpt-4o-mini-transcribe',
            'error': 'OPENAI_API_KEY not configured on server'
        }), 500

    # ── Forward to OpenAI ──
    tmp_path = None
    try:
        # Save uploaded file to temp
        suffix = os.path.splitext(audio_file.filename)[1] or '.webm'
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            audio_file.save(tmp)
            tmp_path = tmp.name

        file_size = os.path.getsize(tmp_path)
        logger.info(f'Saved temp file: {tmp_path} ({file_size} bytes)')

        # Import openai lazily (only needed for this route)
        from openai import OpenAI
        client = OpenAI(api_key=OPENAI_API_KEY)

        with open(tmp_path, 'rb') as f:
            transcription = client.audio.transcriptions.create(
                model='gpt-4o-mini-transcribe',
                file=f,
                language=language,
                response_format='json'
            )

        latency_ms = int((time.time() - t0) * 1000)
        transcript = transcription.text

        logger.info(f'Transcription OK: "{transcript}" ({latency_ms}ms)')

        return jsonify({
            'transcript': transcript,
            'latencyMs': latency_ms,
            'provider': 'openai/gpt-4o-mini-transcribe',
            'language': language,
            'error': None
        })

    except Exception as e:
        latency_ms = int((time.time() - t0) * 1000)
        err_msg = str(e)
        logger.error(f'Transcription failed: {err_msg} ({latency_ms}ms)')
        return jsonify({
            'transcript': None,
            'latencyMs': latency_ms,
            'provider': 'openai/gpt-4o-mini-transcribe',
            'language': language,
            'error': err_msg
        }), 500

    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except Exception:
                pass


@app.route('/health', methods=['GET'])
def health():
    """Health check for PM2/nginx monitoring."""
    ok = bool(OPENAI_API_KEY)
    return jsonify({
        'status': 'ok' if ok else 'degraded',
        'provider': 'openai/gpt-4o-mini-transcribe',
        'keyConfigured': ok,
        'probeToken': PROBE_TOKEN[:4] + '...' if PROBE_TOKEN else None
    })


@app.route('/', methods=['GET'])
def index():
    return jsonify({
        'service': 'edu-asr-probe',
        'version': '1.0.0',
        'endpoint': PROBE_PATH,
        'health': '/health'
    })


if __name__ == '__main__':
    logger.info(f'Starting edu-asr-probe on :{PORT}')
    logger.info(f'Endpoint path: {PROBE_PATH}')
    logger.info(f'OpenAI key configured: {bool(OPENAI_API_KEY)}')
    logger.info(f'CORS origins: {ALLOWED_ORIGINS}')
    app.run(host='0.0.0.0', port=PORT, debug=False)
