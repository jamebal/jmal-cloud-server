#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALLER="$SCRIPT_DIR/../install.sh"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

assert_file_contains() {
  file="$1"
  expected="$2"
  if ! grep -F "$expected" "$file" >/dev/null 2>&1; then
    echo "File content:" >&2
    [ -f "$file" ] && sed -n '1,120p' "$file" >&2
    fail "expected $file to contain: $expected"
  fi
}

make_fixture() {
  root="$1"
  mkdir -p "$root/archive-src" "$root/fake-bin" "$root/home"

  cat >"$root/archive-src/jmalcloud" <<'BIN'
#!/usr/bin/env sh
echo "fake jmalcloud"
BIN
  chmod +x "$root/archive-src/jmalcloud"
  tar -czf "$root/jmalcloud-test.tar.gz" -C "$root/archive-src" jmalcloud

  cat >"$root/fake-bin/curl" <<'CURL'
#!/usr/bin/env sh
set -eu

out=""
url=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -o)
      shift
      out="$1"
      ;;
    -*)
      ;;
    *)
      url="$1"
      ;;
  esac
  shift
done

[ -n "$url" ] || exit 2
[ -n "$out" ] || exit 3
printf '%s\n' "$url" >"$CURL_URL_LOG"
cp "$CURL_ARCHIVE" "$out"
CURL
  chmod +x "$root/fake-bin/curl"
}

run_installer() {
  root="$1"
  install_dir="$2"
  archive_url="$3"

  mkdir -p "$install_dir"
  HOME="$root/home" \
    PATH="$root/fake-bin:/usr/bin:/bin" \
    SHELL="/bin/zsh" \
    CURL_ARCHIVE="$root/jmalcloud-test.tar.gz" \
    CURL_URL_LOG="$root/curl-url.log" \
    JMALCLOUD_CLI_ARCHIVE_URL="$archive_url" \
    JMALCLOUD_CLI_INSTALL_DIR="$install_dir" \
    sh "$INSTALLER" >"$root/install.out" 2>&1
}

test_custom_archive_url_is_used() (
  root="$(mktemp -d)"
  trap 'rm -rf "$root"' EXIT INT TERM
  make_fixture "$root"

  url="https://cloud.example.com/s/jmalcloud-custom.tar.gz"
  run_installer "$root" "$root/bin" "$url"

  actual="$(cat "$root/curl-url.log")"
  [ "$actual" = "$url" ] || fail "expected custom archive URL, got $actual"
  [ -x "$root/bin/jmalcloud" ] || fail "expected installed jmalcloud binary"
)

test_home_local_bin_is_added_to_shell_profile() (
  root="$(mktemp -d)"
  trap 'rm -rf "$root"' EXIT INT TERM
  make_fixture "$root"

  run_installer "$root" "$root/home/.local/bin" "https://cloud.example.com/s/jmalcloud-custom.tar.gz"

  assert_file_contains "$root/home/.zshrc" 'export PATH="$HOME/.local/bin:$PATH"'
)

test_custom_archive_url_is_used
test_home_local_bin_is_added_to_shell_profile

echo "install.sh tests passed"
