#!/usr/bin/env bash
# scripts/doctor.sh — single-source health check for the Claude/NeoMud env.
# Default mode: PASS/FAIL summary, exit 0 iff all pass.
# -v: verbose, prints versions and raw command output for failed checks.
# -q: quiet, only print failures (useful for the SessionStart hook).

set -u  # don't set -e — we want to keep going after individual failures

VERBOSE=0
QUIET=0
case "${1:-}" in
  -v|--verbose) VERBOSE=1 ;;
  -q|--quiet)   QUIET=1 ;;
  -h|--help)
    cat <<EOF
usage: scripts/doctor.sh [-v|-q]
  (no flag)   one-line PASS/FAIL per check
  -v          verbose: print versions + paths + raw output for failures
  -q          quiet: only print failures (used by SessionStart hook)
exit 0 iff every check passes.
EOF
    exit 0 ;;
esac

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || { echo "doctor: cannot cd to repo root"; exit 2; }

PASS_COUNT=0
FAIL_COUNT=0
FAILURES=()

# Color only when stdout is a TTY (so hook output stays clean)
if [[ -t 1 ]]; then
  C_OK=$'\033[32m'; C_FAIL=$'\033[31m'; C_DIM=$'\033[2m'; C_RST=$'\033[0m'
else
  C_OK=""; C_FAIL=""; C_DIM=""; C_RST=""
fi

# emit "PASS name" or "FAIL name -- detail [fix]"
report() {
  local status="$1" name="$2" detail="${3:-}" fix="${4:-}"
  if [[ "$status" == "PASS" ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    [[ "$QUIET" == "1" ]] && return
    if [[ "$VERBOSE" == "1" && -n "$detail" ]]; then
      printf "%sPASS%s %-32s %s%s%s\n" "$C_OK" "$C_RST" "$name" "$C_DIM" "$detail" "$C_RST"
    else
      printf "%sPASS%s %s\n" "$C_OK" "$C_RST" "$name"
    fi
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    FAILURES+=("$name")
    printf "%sFAIL%s %-32s %s\n" "$C_FAIL" "$C_RST" "$name" "$detail"
    [[ -n "$fix" ]] && printf "      %sfix:%s %s\n" "$C_DIM" "$C_RST" "$fix"
  fi
}

# helper: check a binary exists, optionally with a version constraint
check_bin() {
  local name="$1" min_major="${2:-}" version_cmd="${3:-}"
  local path
  path="$(command -v "$name" 2>/dev/null || true)"
  if [[ -z "$path" ]]; then
    report FAIL "tool:$name" "not on PATH" "brew install $name"
    return
  fi
  if [[ -n "$min_major" && -n "$version_cmd" ]]; then
    local v_out v_major
    v_out="$(eval "$version_cmd" 2>&1 | head -1)"
    v_major="$(echo "$v_out" | grep -oE '[0-9]+' | head -1)"
    if [[ -z "$v_major" ]] || [[ "$v_major" -lt "$min_major" ]]; then
      report FAIL "tool:$name" "$v_out (need >= $min_major)" "brew upgrade $name"
      return
    fi
    report PASS "tool:$name" "$path  $v_out"
  else
    report PASS "tool:$name" "$path"
  fi
}

check_env() {
  local name="$1"
  local val="${!name:-}"
  if [[ -z "$val" ]]; then
    report FAIL "env:$name" "unset" "set in .claude/settings.local.json env block"
  else
    local masked="$val"
    case "$name" in *KEY*|*TOKEN*|*SECRET*) masked="${val:0:4}...${val: -4} (len=${#val})" ;; esac
    report PASS "env:$name" "$masked"
  fi
}

# --- 1. Tools on PATH ---
check_bin node 20 'node -v'
check_bin npm  10 'npm -v'
check_bin npx  ""  ''
check_bin uv   ""  ''
check_bin uvx  ""  ''
check_bin jq   ""  ''
check_bin git  ""  ''
check_bin unzip "" ''

# Java + JAVA_HOME consistency
if command -v java >/dev/null 2>&1; then
  J_VER="$(java -version 2>&1 | head -1)"
  case "$J_VER" in
    *\"21*) report PASS "tool:java" "$(command -v java)  $J_VER" ;;
    *)      report FAIL "tool:java" "$J_VER (need 21)" "JAVA_HOME=\$(/usr/libexec/java_home -v 21)" ;;
  esac
else
  report FAIL "tool:java" "not on PATH" "ensure JAVA_HOME bin is on PATH"
fi

# gradle wrapper present + runs
if [[ -x ./gradlew ]]; then
  if [[ "$VERBOSE" == "1" ]]; then
    GW_OUT="$(./gradlew --version 2>&1 | grep -E 'Gradle|Kotlin' | head -3 | tr '\n' ' ')"
    [[ -n "$GW_OUT" ]] && report PASS "tool:gradlew" "$GW_OUT" || report FAIL "tool:gradlew" "no version output" "check JAVA_HOME"
  else
    report PASS "tool:gradlew" "./gradlew exists"
  fi
else
  report FAIL "tool:gradlew" "./gradlew missing or not executable" "chmod +x ./gradlew"
fi

# --- 2. Env vars ---
check_env JAVA_HOME
check_env ANDROID_HOME
check_env NANO_BANANA_API_KEY
check_env GEMINI_API_KEY
check_env ELEVENLABS_API_KEY

# --- 3. .mcp.json valid + lists servers ---
if [[ -f .mcp.json ]]; then
  if jq -e . .mcp.json >/dev/null 2>&1; then
    SERVERS="$(jq -r '.mcpServers | keys | join(", ")' .mcp.json)"
    report PASS "mcp:.mcp.json" "[$SERVERS]"
  else
    report FAIL "mcp:.mcp.json" "invalid JSON" "jq . .mcp.json  # to see error"
  fi
else
  report FAIL "mcp:.mcp.json" "missing at repo root" "create .mcp.json with mcpServers block"
fi

# --- 4. Playwright browsers cache ---
PW_CACHE="$HOME/Library/Caches/ms-playwright"
if [[ -d "$PW_CACHE" ]] && [[ -n "$(ls -A "$PW_CACHE" 2>/dev/null)" ]]; then
  report PASS "mcp:playwright-browsers" "$PW_CACHE"
else
  report FAIL "mcp:playwright-browsers" "cache empty/missing" "npx playwright install"
fi

# --- 5. claude mcp list (live state) ---
if command -v claude >/dev/null 2>&1; then
  MCP_LIST="$(claude mcp list 2>&1)"
  if [[ "$VERBOSE" == "1" ]]; then
    while IFS= read -r line; do printf "      %s\n" "$line"; done <<< "$MCP_LIST"
  fi
  CONNECTED="$(echo "$MCP_LIST" | grep -cE 'connected|Connected|✓')"
  FAILED="$(echo "$MCP_LIST" | grep -cE 'failed|Failed|✗|error')"
  if [[ "$FAILED" -gt 0 ]]; then
    BAD="$(echo "$MCP_LIST" | grep -E 'failed|Failed|✗|error' | head -3 | tr '\n' '; ')"
    report FAIL "mcp:runtime-state" "$FAILED failed, $CONNECTED ok -- $BAD" "claude mcp list  # then restart session"
  else
    report PASS "mcp:runtime-state" "$CONNECTED servers connected"
  fi
else
  report FAIL "mcp:runtime-state" "claude CLI not on PATH" "ensure ~/.local/bin is on PATH"
fi

# --- 6. Repo health: settings.local.json gitignored + not tracked ---
if [[ -f .claude/settings.local.json ]]; then
  if git ls-files --error-unmatch .claude/settings.local.json >/dev/null 2>&1; then
    report FAIL "repo:settings.local-tracked" "tracked by git (would commit secrets!)" "git rm --cached .claude/settings.local.json && add to .gitignore"
  else
    if grep -qE '(^|/)\.claude/settings\.local\.json' .gitignore 2>/dev/null; then
      report PASS "repo:settings.local-safe" "gitignored, untracked"
    else
      report FAIL "repo:settings.local-safe" "not in .gitignore" "echo '.claude/settings.local.json' >> .gitignore"
    fi
  fi
else
  report PASS "repo:settings.local-safe" "no settings.local.json (no local secrets)"
fi

# --- summary ---
echo
TOTAL=$((PASS_COUNT + FAIL_COUNT))
if [[ "$FAIL_COUNT" -eq 0 ]]; then
  printf "%sdoctor: %d/%d OK%s\n" "$C_OK" "$PASS_COUNT" "$TOTAL" "$C_RST"
  exit 0
else
  printf "%sdoctor: %d FAIL, %d PASS (of %d)%s\n" "$C_FAIL" "$FAIL_COUNT" "$PASS_COUNT" "$TOTAL" "$C_RST"
  printf "        failed: %s\n" "${FAILURES[*]}"
  exit 1
fi
