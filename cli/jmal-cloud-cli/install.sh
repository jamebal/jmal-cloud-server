#!/usr/bin/env sh
set -eu

REPO="${JMALCLOUD_CLI_REPO:-jamebal/jmal-cloud-server}"
VERSION="${JMALCLOUD_CLI_VERSION:-latest}"
INSTALL_DIR="${JMALCLOUD_CLI_INSTALL_DIR:-}"
ARCHIVE_URL="${JMALCLOUD_CLI_ARCHIVE_URL:-}"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少必要命令: $1" >&2
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
      echo "不支持的平台: $os $arch" >&2
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

download_url() {
  archive="$1"
  if [ -n "$ARCHIVE_URL" ]; then
    echo "$ARCHIVE_URL"
  elif [ "$VERSION" = "latest" ]; then
    echo "https://github.com/${REPO}/releases/latest/download/${archive}"
  else
    echo "https://github.com/${REPO}/releases/download/${VERSION}/${archive}"
  fi
}

path_contains_dir() {
  dir="$1"
  case ":$PATH:" in
    *":$dir:"*) return 0 ;;
    *) return 1 ;;
  esac
}

shell_profile() {
  shell_path="${SHELL:-}"
  shell_name="${shell_path##*/}"
  case "$shell_name" in
    zsh)
      echo "$HOME/.zshrc"
      return
      ;;
    bash)
      echo "$HOME/.bashrc"
      return
      ;;
  esac

  if [ -f "$HOME/.zshrc" ]; then
    echo "$HOME/.zshrc"
  elif [ -f "$HOME/.bashrc" ]; then
    echo "$HOME/.bashrc"
  else
    echo "$HOME/.profile"
  fi
}

path_export_value() {
  dir="$1"
  if [ "$dir" = "$HOME/.local/bin" ]; then
    echo "\$HOME/.local/bin"
  else
    echo "$dir"
  fi
}

ensure_path() {
  dir="$1"
  if path_contains_dir "$dir"; then
    return
  fi

  profile="$(shell_profile)"
  export_value="$(path_export_value "$dir")"
  export_line="export PATH=\"$export_value:\$PATH\""

  if [ -f "$profile" ] && grep -F "$export_line" "$profile" >/dev/null 2>&1; then
    echo "$dir 已经在 $profile 中配置"
  else
    if [ -s "$profile" ]; then
      printf '\n' >>"$profile"
    fi
    {
      echo "# jmalcloud 安装脚本自动添加"
      echo "$export_line"
    } >>"$profile"
    echo "已将 $dir 写入 $profile"
  fi

  echo "请执行 'source $profile' 或重新打开终端，然后直接使用 jmalcloud"
}

need curl
need tar
need mktemp
need install

target="$(detect_target)"
archive="jmalcloud-${target}.tar.gz"
url="$(download_url "$archive")"

if [ -z "$INSTALL_DIR" ]; then
  INSTALL_DIR="$(default_install_dir)"
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT INT TERM

echo "正在下载 $url"
curl -fsSL "$url" -o "$tmp/$archive"
tar -xzf "$tmp/$archive" -C "$tmp"

if [ ! -f "$tmp/jmalcloud" ]; then
  echo "压缩包中没有 jmalcloud 二进制文件" >&2
  exit 1
fi

mkdir -p "$INSTALL_DIR"
install -m 0755 "$tmp/jmalcloud" "$INSTALL_DIR/jmalcloud"
ensure_path "$INSTALL_DIR"

echo "jmalcloud 已安装到 $INSTALL_DIR/jmalcloud"
