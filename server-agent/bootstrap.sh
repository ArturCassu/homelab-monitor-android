#!/bin/sh
set -eu

REPOSITORY=${HOMELAB_MONITOR_REPOSITORY:-https://github.com/ArturCassu/homelab-monitor-android}
TEMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/homelab-monitor-bootstrap.XXXXXX")
trap 'rm -rf "$TEMP_DIR"' EXIT INT TERM

command -v curl >/dev/null 2>&1 || { echo "curl é necessário para o bootstrap." >&2; exit 1; }
command -v tar >/dev/null 2>&1 || { echo "tar é necessário para o bootstrap." >&2; exit 1; }

echo "Baixando o Homelab Monitor de $REPOSITORY..."
curl --fail --silent --show-error --location "$REPOSITORY/archive/refs/heads/main.tar.gz" \
    | tar -xzf - -C "$TEMP_DIR" --strip-components=1

if [ ! -r /dev/tty ]; then
    echo "Este instalador é interativo e precisa de um terminal." >&2
    echo "Execute-o diretamente em uma sessão SSH/Tailscale com TTY." >&2
    exit 1
fi

exec sh "$TEMP_DIR/server-agent/homelab-monitor" install "$@" </dev/tty
