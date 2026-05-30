#!/usr/bin/env sh
set -eu

REPO="${JMALCLOUD_CLI_REPO:-jamebal/jmal-cloud-server}"
VERSION="${JMALCLOUD_CLI_VERSION:-latest}"
INSTALL_DIR="${JMALCLOUD_CLI_INSTALL_DIR:-}"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

detect_target() {
  os="$(uname -s)"
  arch="$(uname -m)"
  case "$os:$arch" in
    Linux:x86_64|Linux:amd64) echo "x86_64-unknown-linux-gnu" ;;
    Linux:aarch64|Linux:arm64) echo "aarch64-unknown-linux-gnu" ;;
    Darwin:x86_64|Darwin:amd64) echo "x86_64-apple-darwin" ;;
    Darwin:arm64|Darwin:aarch64) echo "aarch64-apple-darwin" ;;
    *)
      echo "unsupported platform: $os $arch" >&2
      exit 1
      ;;
  esac
}

default_install_dir() {
  if [ -w /usr/local/bin ]; then
    echo "/usr/local/bin"
  else
    echo "$HOME/.local/bin"
  fi
}

need curl
need tar
need mktemp

target="$(detect_target)"
archive="jmalcloud-${target}.tar.gz"
if [ "$VERSION" = "latest" ]; then
  url="https://github.com/${REPO}/releases/latest/download/${archive}"
else
  url="https://github.com/${REPO}/releases/download/${VERSION}/${archive}"
fi

if [ -z "$INSTALL_DIR" ]; then
  INSTALL_DIR="$(default_install_dir)"
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT INT TERM

echo "downloading $url"
curl -fsSL "$url" -o "$tmp/$archive"
tar -xzf "$tmp/$archive" -C "$tmp"

mkdir -p "$INSTALL_DIR"
install -m 0755 "$tmp/jmalcloud" "$INSTALL_DIR/jmalcloud"

echo "installed jmalcloud to $INSTALL_DIR/jmalcloud"
