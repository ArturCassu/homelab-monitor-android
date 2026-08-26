#!/bin/sh
set -eu

PORT=8099
PORT_SET=false
IMMICH=true
IMMICH_SET=false
NONINTERACTIVE=false

while [ "$#" -gt 0 ]; do
    case "$1" in
        --port)
            [ "$#" -ge 2 ] || { echo "--port precisa de um valor." >&2; exit 2; }
            PORT=$2
            PORT_SET=true
            shift 2
            ;;
        --immich)
            [ "$#" -ge 2 ] || { echo "--immich precisa de true ou false." >&2; exit 2; }
            case "$2" in true|false) IMMICH=$2 ;; *) echo "--immich aceita true ou false." >&2; exit 2 ;; esac
            IMMICH_SET=true
            shift 2
            ;;
        --non-interactive) NONINTERACTIVE=true; shift ;;
        --help|-h)
            echo "Uso: sudo sh install.sh [--non-interactive] [--port PORTA] [--immich true|false]"
            exit 0
            ;;
        *) echo "Argumento desconhecido: $1" >&2; exit 2 ;;
    esac
done

if [ "$(id -u)" -ne 0 ]; then
    echo "Execute como root: sudo sh install.sh" >&2
    exit 1
fi

valid_port() {
    case "$1" in ''|*[!0-9]*) return 1 ;; esac
    [ "$1" -ge 1 ] && [ "$1" -le 65535 ]
}
valid_port "$PORT" || { echo "Porta inválida: $PORT" >&2; exit 2; }

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
bind_ip=$(tailscale ip -4 2>/dev/null || true)
if [ -z "$bind_ip" ]; then
    echo "Não foi possível obter o IPv4 do Tailscale. Conecte o servidor ao tailnet e tente novamente." >&2
    exit 1
fi

docker_bin=$(command -v docker || true)
has_docker=false
if [ "$docker_bin" = "/usr/bin/docker" ]; then
    has_docker=true
fi

getent group homelab-metrics >/dev/null 2>&1 || groupadd --system homelab-metrics
if ! id homelab-metrics >/dev/null 2>&1; then
    useradd --system --gid homelab-metrics --home-dir /nonexistent --shell /usr/sbin/nologin homelab-metrics
fi

operator_user=${SUDO_USER:-}
if [ -n "$operator_user" ] && [ "$operator_user" != root ]; then
    usermod -a -G homelab-metrics "$operator_user"
fi

install -d -o root -g root -m 0755 /opt/homelab-metrics
install -d -o root -g root -m 0755 /opt/homelab-metrics/package
install -m 0755 "$script_dir/homelab_metrics.py" /opt/homelab-metrics/homelab_metrics.py
install -m 0755 "$script_dir/homelab-monitor" /opt/homelab-metrics/package/homelab-monitor
install -m 0755 "$script_dir/install.sh" /opt/homelab-metrics/package/install.sh
install -m 0755 "$script_dir/docker-snapshot.sh" /opt/homelab-metrics/package/docker-snapshot.sh
install -m 0755 "$script_dir/homelab_metrics.py" /opt/homelab-metrics/package/homelab_metrics.py
install -d -o root -g root -m 0755 /opt/homelab-metrics/package/systemd
install -m 0644 "$script_dir/systemd/homelab-metrics.service" /opt/homelab-metrics/package/systemd/homelab-metrics.service
install -m 0644 "$script_dir/systemd/homelab-metrics-docker-snapshot.service" /opt/homelab-metrics/package/systemd/homelab-metrics-docker-snapshot.service
install -m 0644 "$script_dir/systemd/homelab-metrics-docker-snapshot.timer" /opt/homelab-metrics/package/systemd/homelab-metrics-docker-snapshot.timer
install -d -o root -g root -m 0755 /usr/local/libexec
install -o root -g root -m 0750 "$script_dir/docker-snapshot.sh" /usr/local/libexec/homelab-metrics-docker-snapshot
install -o root -g root -m 0755 "$script_dir/homelab-monitor" /usr/local/bin/homelab-monitor

install -d -o root -g homelab-metrics -m 0750 /etc/homelab-metrics
token_file=/etc/homelab-metrics/token
if [ ! -s "$token_file" ]; then
    umask 077
    /usr/bin/openssl rand -hex 32 > "$token_file"
fi
chown root:homelab-metrics "$token_file"
chmod 0640 "$token_file"

volumes_file=/etc/homelab-metrics/volumes.conf
if [ ! -s "$volumes_file" ]; then
    printf '%s\n' / /srv/storage /mnt/windows-ssd > "$volumes_file"
fi
chown root:root "$volumes_file"
chmod 0644 "$volumes_file"

existing_immich=$(sed -n 's/^ENABLE_IMMICH=//p' /etc/homelab-metrics/agent.env 2>/dev/null | head -n 1 || true)
existing_port=$(sed -n 's/^PORT=//p' /etc/homelab-metrics/agent.env 2>/dev/null | head -n 1 || true)
if [ "$IMMICH_SET" = false ] && [ "$existing_immich" = true -o "$existing_immich" = false ]; then IMMICH=$existing_immich; fi
if [ "$PORT_SET" = false ] && valid_port "${existing_port:-}"; then PORT=$existing_port; fi

printf 'BIND_IP=%s\nPORT=%s\nENABLE_IMMICH=%s\n' "$bind_ip" "$PORT" "$IMMICH" > /etc/homelab-metrics/agent.env
chown root:root /etc/homelab-metrics/agent.env
chmod 0644 /etc/homelab-metrics/agent.env

install -o root -g root -m 0644 "$script_dir/systemd/homelab-metrics.service" /etc/systemd/system/homelab-metrics.service
install -o root -g root -m 0644 "$script_dir/systemd/homelab-metrics-docker-snapshot.service" /etc/systemd/system/homelab-metrics-docker-snapshot.service
install -o root -g root -m 0644 "$script_dir/systemd/homelab-metrics-docker-snapshot.timer" /etc/systemd/system/homelab-metrics-docker-snapshot.timer

systemctl daemon-reload
systemctl enable --now homelab-metrics.service
if [ "$has_docker" = true ]; then
    systemctl enable --now homelab-metrics-docker-snapshot.timer
else
    systemctl disable --now homelab-metrics-docker-snapshot.timer >/dev/null 2>&1 || true
    echo "Aviso: Docker não encontrado. O agente foi instalado, mas as métricas de containers ficarão indisponíveis."
fi

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
    ufw allow in on tailscale0 to any port "$PORT" proto tcp comment 'homelab metrics via Tailscale' >/dev/null
fi

if command -v curl >/dev/null 2>&1; then
    response_file=$(mktemp)
    trap 'rm -f "$response_file"' EXIT
    status=$(curl --silent --show-error --max-time 10 --output "$response_file" --write-out '%{http_code}' \
        -H "Authorization: Bearer $(cat "$token_file")" -H 'Accept: application/json' \
        "http://${bind_ip}:${PORT}/v1/metrics" || true)
    if [ "$status" != 200 ]; then
        echo "A API não respondeu 200 (HTTP ${status}). Consulte: homelab-monitor logs" >&2
        exit 1
    fi
fi

echo "✓ Agente instalado em http://${bind_ip}:${PORT}"
echo "✓ Immich: $([ "$IMMICH" = true ] && echo habilitado || echo desabilitado)"
echo "✓ Comando disponível: homelab-monitor status"
echo "Token protegido em /etc/homelab-metrics/token; para exibi-lo intencionalmente, use: homelab-monitor token"
