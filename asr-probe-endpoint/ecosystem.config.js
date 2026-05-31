// PM2 ecosystem config for edu-asr-probe
// WO-AUDIO-PROBE-ENDPOINT-FIX
module.exports = {
  apps: [{
    name: 'edu-asr-probe',
    script: 'server.py',
    interpreter: '/opt/edu/asr-probe-endpoint/venv/bin/python',
    interpreter_args: '',
    cwd: '/opt/edu/asr-probe-endpoint',
    env: {
      ASR_PROBE_TOKEN: 'ab75e14a101c8779',
      ASR_PROBE_PORT: '8095',
      OPENAI_API_KEY: process.env.OPENAI_API_KEY
    },
    // Restart if it crashes
    autorestart: true,
    max_restarts: 10,
    // Logging
    out_file: '/dev/null',
    error_file: '/dev/null',
    merge_logs: true,
    // No watch in production
    watch: false
  }]
};
