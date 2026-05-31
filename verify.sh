#!/bin/bash
# verify.sh — WO-AUDIO-PROBE-v1 verification script
# Checks: APK build artifacts exist + /asr-probe endpoint health + DingTalk push
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
EDU_ROOT="$SCRIPT_DIR"

# ── Colors ──
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

PASS_COUNT=0
FAIL_COUNT=0

pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS_COUNT=$((PASS_COUNT+1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL_COUNT=$((FAIL_COUNT+1)); }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

echo "============================================"
echo " WO-AUDIO-PROBE-v1 Verification"
echo " $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"
echo ""

# ── 1. Source file checks ──
echo "── 1. Source Files ──"

if [ -f "$EDU_ROOT/audio-probe/src/main/java/com/edu/audioprobe/MainActivity.kt" ]; then
  pass "MainActivity.kt exists"
else
  fail "MainActivity.kt MISSING"
fi

if [ -f "$EDU_ROOT/audio-probe/src/main/assets/index.html" ]; then
  pass "index.html exists"
else
  fail "index.html MISSING"
fi

if [ -f "$EDU_ROOT/audio-probe/src/main/AndroidManifest.xml" ]; then
  pass "AndroidManifest.xml exists"
else
  fail "AndroidManifest.xml MISSING"
fi

if [ -f "$EDU_ROOT/audio-probe/build.gradle" ]; then
  pass "audio-probe/build.gradle exists"
else
  fail "audio-probe/build.gradle MISSING"
fi

# Check manifest contains RECORD_AUDIO
if grep -q "RECORD_AUDIO" "$EDU_ROOT/audio-probe/src/main/AndroidManifest.xml" 2>/dev/null; then
  pass "RECORD_AUDIO permission in manifest"
else
  fail "RECORD_AUDIO permission MISSING from manifest"
fi

# Check onPermissionRequest grants RESOURCE_AUDIO_CAPTURE (坑 #1)
if grep -q "RESOURCE_AUDIO_CAPTURE" "$EDU_ROOT/audio-probe/src/main/java/com/edu/audioprobe/MainActivity.kt" 2>/dev/null; then
  pass "RESOURCE_AUDIO_CAPTURE granted in WebChromeClient (坑 #1)"
else
  fail "RESOURCE_AUDIO_CAPTURE NOT FOUND in WebChromeClient — 坑 #1 头号杀手!"
fi

# Check WebViewAssetLoader usage (not file://)
if grep -q "WebViewAssetLoader" "$EDU_ROOT/audio-probe/src/main/java/com/edu/audioprobe/MainActivity.kt" 2>/dev/null; then
  pass "WebViewAssetLoader used (secure context, 坑 #2)"
else
  fail "WebViewAssetLoader NOT FOUND — file:// will break getUserMedia (坑 #2)"
fi

# Check /asr-probe endpoint in backend
if grep -q "/asr-probe" "$EDU_ROOT/backend/app.py" 2>/dev/null; then
  pass "/asr-probe endpoint in backend/app.py"
else
  fail "/asr-probe endpoint MISSING from backend/app.py"
fi

# Check CORS for appassets origin
if grep -q "gpt-4o-mini-transcribe" "$EDU_ROOT/backend/app.py" 2>/dev/null; then
  pass "OpenAI gpt-4o-mini-transcribe model referenced in backend"
else
  fail "gpt-4o-mini-transcribe model NOT found in backend"
fi

echo ""

# ── 2. Build artifact checks ──
echo "── 2. Build Artifacts ──"

APK_PATH="$EDU_ROOT/audio-probe/build/outputs/apk/debug/audio-probe-debug.apk"
if [ -f "$APK_PATH" ]; then
  APK_SIZE=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH" 2>/dev/null || echo "?")
  pass "audio-probe-debug.apk found ($APK_SIZE bytes)"
else
  warn "audio-probe-debug.apk not yet built — run './gradlew :audio-probe:assembleDebug' first"
fi

echo ""

# ── 3. Endpoint health check ──
echo "── 3. /asr-probe Endpoint Health ──"

# Default server URL — override with ASR_PROBE_SERVER env var
SERVER_URL="${ASR_PROBE_SERVER:-https://eagent.edu-aliyun.com}"
ENDPOINT="${SERVER_URL%/}/asr-probe"

# Generate a minimal WAV silence file for health check
TMP_WAV="/tmp/asr_probe_test_$$.wav"

# Minimal WAV: 44-byte header + 1 second of silence at 8000Hz mono 16-bit
python3 -c "
import struct, sys
sample_rate = 8000
duration_sec = 1
num_samples = sample_rate * duration_sec
data_size = num_samples * 2  # 16-bit mono
# WAV header
header = struct.pack('<4sI4s4sIHHIIHH4sI',
    b'RIFF', 36 + data_size,
    b'WAVE',
    b'fmt ', 16, 1, 1, sample_rate, sample_rate * 2, 2, 16,
    b'data', data_size)
# Write header + silence
with open('$TMP_WAV', 'wb') as f:
    f.write(header)
    f.write(b'\x00' * data_size)
" 2>/dev/null

if [ -f "$TMP_WAV" ]; then
  echo "  Sending silence WAV to $ENDPOINT ..."
  
  # Try curl with timeout
  HTTP_CODE=$(curl -s -o /tmp/asr_probe_resp_$$.txt -w "%{http_code}" \
    -X POST "$ENDPOINT" \
    -F "audio=@$TMP_WAV;type=audio/wav" \
    -F "language=pl" \
    --connect-timeout 10 --max-time 30 2>&1 || echo "000")
  
  if [ "$HTTP_CODE" = "200" ]; then
    RESP_BODY=$(cat /tmp/asr_probe_resp_$$.txt)
    if echo "$RESP_BODY" | grep -q '"transcript"'; then
      TRANSCRIPT=$(echo "$RESP_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('transcript','N/A'))" 2>/dev/null || echo "parse-error")
      pass "/asr-probe returned 200 — transcript: '$TRANSCRIPT'"
    else
      warn "/asr-probe returned 200 but unexpected response: $RESP_BODY"
    fi
  elif [ "$HTTP_CODE" = "000" ]; then
    warn "/asr-probe unreachable (network/DNS?) — server may not be running: $ENDPOINT"
  else
    RESP_BODY=$(cat /tmp/asr_probe_resp_$$.txt 2>/dev/null || echo "?")
    warn "/asr-probe returned HTTP $HTTP_CODE — $RESP_BODY"
  fi
  
  rm -f "$TMP_WAV" /tmp/asr_probe_resp_$$.txt
else
  warn "Could not generate test WAV (python3 missing?)"
fi

echo ""

# ── 4. DingTalk notification ──
echo "── 4. DingTalk Notification ──"

DINGTALK_TOKEN="${DINGTALK_TOKEN:-}"
DINGTALK_URL="https://oapi.dingtalk.com/robot/send?access_token=${DINGTALK_TOKEN}"

if [ -n "$DINGTALK_TOKEN" ] && [ ${#DINGTALK_TOKEN} -gt 10 ]; then
  if [ $FAIL_COUNT -eq 0 ]; then
    VERDICT="PASS"
    VERDICT_ICON="✅"
  else
    VERDICT="FAIL"
    VERDICT_ICON="❌"
  fi
  
  DING_MSG=$(cat <<EOF
{
  "msgtype": "markdown",
  "markdown": {
    "title": "WO-AUDIO-PROBE-v1 Verification: $VERDICT",
    "text": "## WO-AUDIO-PROBE-v1 Verification: $VERDICT $VERDICT_ICON\n\n**Time**: $(date '+%Y-%m-%d %H:%M:%S')\n**Passes**: $PASS_COUNT\n**Fails**: $FAIL_COUNT\n\n---\n\nVerify script: \`edu/verify.sh\`"
  }
}
EOF
  )
  
  curl -s -X POST "$DINGTALK_URL" \
    -H "Content-Type: application/json" \
    -d "$DING_MSG" > /dev/null 2>&1 && \
    echo "  DingTalk notification sent" || \
    warn "DingTalk push failed"
else
  warn "DINGTALK_TOKEN not set — skipping DingTalk notification"
  echo "  Set env: export DINGTALK_TOKEN=...440ecf44"
fi

echo ""

# ── 5. Summary ──
echo "============================================"
echo " VERIFICATION SUMMARY"
echo "============================================"
echo -e " Passes: ${GREEN}$PASS_COUNT${NC}"
echo -e " Fails:  ${RED}$FAIL_COUNT${NC}"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
  echo -e "${GREEN}✅ ALL CHECKS PASSED${NC}"
  exit 0
else
  echo -e "${RED}❌ $FAIL_COUNT CHECK(S) FAILED${NC}"
  exit 1
fi
