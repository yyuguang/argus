#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${ARGUS_BASE_URL:-http://localhost:8900}"
TOKEN="${ARGUS_INTERNAL_TOKEN:-argustest}"
APP_NAME="${ARGUS_APP_NAME:-argus-server}"
ENVIRONMENT="${ARGUS_ENVIRONMENT:-local}"
HOST_NAME="${ARGUS_HOST:-$(hostname)}"
DEFAULT_LOG_FILE="$(cd "$(dirname "$0")/.." && pwd)/logs/argus-error.log"
LOG_FILE="${ARGUS_LOG_FILE:-${1:-${DEFAULT_LOG_FILE}}}"
MODE="${ARGUS_MONITOR_MODE:-file}"
ONCE="${ARGUS_MONITOR_ONCE:-0}"
TAIL_FROM_BEGINNING="${ARGUS_TAIL_FROM_BEGINNING:-0}"
FLUSH_SECONDS="${ARGUS_ERROR_FLUSH_SECONDS:-2}"
CONTEXT_LINES="${ARGUS_CONTEXT_LINES:-20}"
PUSH_RETRIES="${ARGUS_PUSH_RETRIES:-30}"
PUSH_RETRY_INTERVAL_SECONDS="${ARGUS_PUSH_RETRY_INTERVAL_SECONDS:-2}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/phase2-argus-monitor.sh
  scripts/phase2-argus-monitor.sh /path/to/argus-error.log

说明：
  这个脚本只做运维侧错误日志监控，默认监听 logs/argus-error.log。
  它不会主动请求任何错误接口，也不会构造模拟错误数据。
  真实请求触发 controller 抛错后，脚本捕获实际 ERROR 日志块并推送到 Argus 分析。
  脚本不会内置 className/methodName/lineNumber 字段，这些字段由 Argus 后端从真实异常栈解析。

常用环境变量：
  ARGUS_BASE_URL              Argus 服务地址，默认 http://localhost:8900
  ARGUS_INTERNAL_TOKEN        内部接口 Token，默认 argustest
  ARGUS_APP_NAME              被监控应用名，默认 argus-server
  ARGUS_ENVIRONMENT           环境，默认 local
  ARGUS_MONITOR_MODE          file 或 stdin，默认 file
  ARGUS_LOG_FILE              错误日志路径，默认 ./logs/argus-error.log
  ARGUS_MONITOR_ONCE=1        捕获并推送一次后退出，便于测试
  ARGUS_TAIL_FROM_BEGINNING=1 从文件开头读取，默认只监听新增日志
  ARGUS_PUSH_RETRIES          推送失败重试次数，默认 30
  ARGUS_PUSH_RETRY_INTERVAL_SECONDS 推送失败重试间隔秒数，默认 2
USAGE
}

if [[ "${LOG_FILE}" == "-h" || "${LOG_FILE}" == "--help" || "${MODE}" == "-h" || "${MODE}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${MODE}" != "stdin" && "${MODE}" != "file" ]]; then
  echo "[phase2-monitor] unsupported ARGUS_MONITOR_MODE=${MODE}, use stdin or file" >&2
  exit 2
fi

TAIL_LINES="0"
if [[ "${TAIL_FROM_BEGINNING}" == "1" ]]; then
  TAIL_LINES="+1"
fi

echo "[phase2-monitor] mode=${MODE}"
echo "[phase2-monitor] baseUrl=${BASE_URL}"
echo "[phase2-monitor] appName=${APP_NAME}"
echo "[phase2-monitor] token=${TOKEN}"
echo "[phase2-monitor] monitorMode=${MODE}"
if [[ "${MODE}" == "file" ]]; then
  echo "[phase2-monitor] logFile=${LOG_FILE}"
  if [[ ! -e "${LOG_FILE}" ]]; then
    echo "[phase2-monitor] log file not found yet, waiting for creation: ${LOG_FILE}"
  fi
else
  echo "[phase2-monitor] input=stdin"
fi
echo "[phase2-monitor] once=${ONCE}"

PY_MONITOR="$(mktemp)"
cleanup() {
  rm -f "${PY_MONITOR}"
}
trap cleanup EXIT

cat > "${PY_MONITOR}" <<'PY'
import datetime as dt
import hashlib
import json
import re
import select
import socket
import sys
import time
import urllib.error
import urllib.request
from collections import deque

base_url, token, app_name, env, host, once, flush_seconds, context_lines, mode, push_retries, push_retry_interval = sys.argv[1:12]
flush_seconds = float(flush_seconds)
push_retries = int(push_retries)
push_retry_interval = float(push_retry_interval)
context = deque(maxlen=int(context_lines))
current = []
last_error_at = 0.0

LOG_START_RE = re.compile(r"^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}")
TRACE_RE = re.compile(r"\btraceId[=:]\s*([A-Za-z0-9._:-]+)")
REQUEST_ID_RE = re.compile(r"\brequestId[=:]\s*([A-Za-z0-9._:-]+)")
HTTP_RE = re.compile(r"\b(GET|POST|PUT|DELETE|PATCH)\s+(/[^\s?]+[^\s]*)")
URI_RE = re.compile(r"\b(?:uri|url|path|requestUri)[=:]\s*([^\s,]+)")

def is_error_start(line):
    return (
        " ERROR " in line
        or "] ERROR" in line
        or "\tERROR\t" in line
        or "Exception:" in line
        or "Error:" in line
    )

def is_stack_or_context(line):
    stripped = line.strip()
    if not stripped:
        return True
    if stripped.startswith(("at ", "Caused by:", "Suppressed:", "... ")):
        return True
    if re.match(r"^[a-zA-Z_$][\w.$]*(Exception|Error)(:|\b)", stripped):
        return True
    return not LOG_START_RE.match(line)

def find_first(pattern, lines):
    for line in lines:
        match = pattern.search(line)
        if match:
            return match.group(1)
    return None

def extract_message(lines):
    for line in lines:
        if is_error_start(line):
            return line.strip()[:1000]
    return lines[0].strip()[:1000] if lines else "应用错误日志"

def extract_stack(lines):
    stack = []
    started = False
    for line in lines:
        stripped = line.strip()
        if re.match(r"^[a-zA-Z_$][\w.$]*(Exception|Error)(:|\b)", stripped):
            started = True
        if started:
            stack.append(line)
    return "\n".join(stack).strip() or "\n".join(lines).strip()

def extract_interface(lines):
    for line in lines:
        match = HTTP_RE.search(line)
        if match:
            return f"{match.group(1)} {match.group(2)}"
    uri = find_first(URI_RE, lines)
    return uri

def post_error(lines):
    now = dt.datetime.now().replace(microsecond=0)
    raw_block = "\n".join(lines)
    digest = hashlib.sha256(raw_block.encode("utf-8", errors="replace")).hexdigest()[:16]
    log_id = f"phase2-tail-{app_name}-{int(time.time())}-{digest}"
    trace_id = find_first(TRACE_RE, lines) or log_id
    request_id = find_first(REQUEST_ID_RE, lines)
    interface_ref = extract_interface(lines)

    entry = {
        "appName": app_name,
        "logId": log_id,
        "logTime": now.isoformat(),
        "logLevel": "ERROR",
        "message": extract_message(lines),
        "stackTrace": extract_stack(lines),
        "traceId": trace_id,
        "logSource": "APP_LOG",
        "environment": env,
        "host": host or socket.gethostname(),
        "businessKey": request_id,
        "interfaceRef": interface_ref,
        "requestInfo": {
            "traceId": trace_id,
            "requestId": request_id,
            "interfaceRef": interface_ref,
            "source": "phase2-argus-monitor.sh",
        },
        "contextLogs": lines[-50:],
    }

    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/api/v1/internal/error-logs",
        data=json.dumps(entry, ensure_ascii=False).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "X-Argus-Token": token,
        },
        method="POST",
    )
    for attempt in range(push_retries + 1):
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                body = response.read().decode("utf-8")
                try:
                    payload = json.loads(body)
                except json.JSONDecodeError:
                    payload = {}
                if payload.get("code") not in (0, "0", None):
                    print(f"[phase2-monitor] push rejected logId={log_id} traceId={trace_id} response={body}", flush=True)
                    return False
                print(f"[phase2-monitor] pushed logId={log_id} traceId={trace_id} response={body}", flush=True)
                return True
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            print(f"[phase2-monitor] push failed status={error.code} logId={log_id} body={body}", flush=True)
            return False
        except urllib.error.URLError as error:
            if attempt >= push_retries:
                print(f"[phase2-monitor] push failed after retries logId={log_id} error={error}", flush=True)
                return False
            print(f"[phase2-monitor] push retry {attempt + 1}/{push_retries} logId={log_id} error={error}", flush=True)
            time.sleep(push_retry_interval)
    return False

def flush_current():
    global current
    if not current:
        return
    lines = current
    current = []
    post_error(lines)
    if once == "1":
        sys.exit(0)

while True:
    ready, _, _ = select.select([sys.stdin], [], [], flush_seconds)
    if not ready:
        if current and time.time() - last_error_at >= flush_seconds:
            flush_current()
        continue

    line = sys.stdin.readline()
    if line == "":
        time.sleep(0.2)
        continue
    line = line.rstrip("\n")

    if is_error_start(line):
        flush_current()
        current = list(context) + [line]
        last_error_at = time.time()
        continue

    if current:
        if is_stack_or_context(line):
            current.append(line)
            last_error_at = time.time()
        else:
            flush_current()
            context.append(line)
        continue

    context.append(line)
PY

if [[ "${MODE}" == "file" ]]; then
  tail -n "${TAIL_LINES}" -F "${LOG_FILE}" | python3 "${PY_MONITOR}" \
    "${BASE_URL}" "${TOKEN}" "${APP_NAME}" "${ENVIRONMENT}" "${HOST_NAME}" \
    "${ONCE}" "${FLUSH_SECONDS}" "${CONTEXT_LINES}" "${MODE}" "${PUSH_RETRIES}" "${PUSH_RETRY_INTERVAL_SECONDS}"
else
  python3 "${PY_MONITOR}" \
    "${BASE_URL}" "${TOKEN}" "${APP_NAME}" "${ENVIRONMENT}" "${HOST_NAME}" \
    "${ONCE}" "${FLUSH_SECONDS}" "${CONTEXT_LINES}" "${MODE}" "${PUSH_RETRIES}" "${PUSH_RETRY_INTERVAL_SECONDS}"
fi
