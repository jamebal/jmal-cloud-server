#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
WORKFLOW="$REPO_ROOT/.github/workflows/jmalcloud-cli-release.yml"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

if [ ! -f "$WORKFLOW" ]; then
  fail "workflow 不存在: $WORKFLOW"
fi

x86_linux_entry="$(
  awk '
    /^[[:space:]]*-[[:space:]]*os:/ {
      if (entry ~ /target:[[:space:]]*x86_64-unknown-linux-gnu/) {
        print entry
        found = 1
        exit
      }
      entry = $0 ORS
      next
    }
    entry != "" {
      entry = entry $0 ORS
    }
    END {
      if (!found && entry ~ /target:[[:space:]]*x86_64-unknown-linux-gnu/) {
        print entry
      }
    }
  ' "$WORKFLOW"
)"

if [ -z "$x86_linux_entry" ]; then
  fail "release matrix 缺少 x86_64-unknown-linux-gnu"
fi

printf '%s\n' "$x86_linux_entry" | grep -F "use_cross: true" >/dev/null 2>&1 \
  || fail "x86_64-unknown-linux-gnu 必须使用 cross 构建，避免依赖 runner 的新版 glibc"

grep -F "cross build --manifest-path cli/jmal-cloud-cli/Cargo.toml --release --target" "$WORKFLOW" >/dev/null 2>&1 \
  || fail "workflow 缺少 cross build 命令"

echo "release workflow tests passed"
