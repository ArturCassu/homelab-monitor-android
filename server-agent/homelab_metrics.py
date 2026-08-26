#!/usr/bin/env python3
"""Small read-only HTTP metrics agent for the homelab.

The process is intentionally dependency-free. It reads host metrics from
/proc and /sys, configured filesystem statistics, and a short-lived Docker
snapshot written by a separate root-owned systemd helper.
"""

from __future__ import annotations

import argparse
import hmac
import json
import logging
import os
from pathlib import Path
import re
import socket
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


LOGGER = logging.getLogger("homelab-metrics")
EXITED_RE = re.compile(r"exited\s*\((-?\d+)\)", re.IGNORECASE)


class MetricsUnavailable(RuntimeError):
    """Raised when an essential host metric cannot be collected."""


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip()


def read_cpu_totals() -> tuple[int, int]:
    line = Path("/proc/stat").read_text(encoding="utf-8").splitlines()[0]
    fields = line.split()
    if len(fields) < 8 or fields[0] != "cpu":
        raise MetricsUnavailable("cpu data unavailable")
    values = [int(value) for value in fields[1:]]
    total = sum(values)
    idle = values[3] + (values[4] if len(values) > 4 else 0)
    return total, idle


def usage_from_cpu_samples(previous: tuple[int, int], current: tuple[int, int]) -> float:
    total_delta = current[0] - previous[0]
    idle_delta = current[1] - previous[1]
    if total_delta <= 0:
        return 0.0
    return max(0.0, min(100.0, (1.0 - idle_delta / total_delta) * 100.0))


def read_memory() -> tuple[int, int]:
    values: dict[str, int] = {}
    for line in Path("/proc/meminfo").read_text(encoding="utf-8").splitlines():
        key, _, raw_value = line.partition(":")
        if not _:
            continue
        number = raw_value.strip().split()[0]
        values[key] = int(number) * 1024

    total = values.get("MemTotal")
    available = values.get("MemAvailable")
    if total is None or available is None:
        raise MetricsUnavailable("memory data unavailable")
    return max(0, total - available), total


def read_sensors() -> list[dict[str, object]]:
    sensors: list[dict[str, object]] = []
    for hwmon_path in sorted(Path("/sys/class/hwmon").glob("hwmon*")):
        if not hwmon_path.is_dir():
            continue
        try:
            device_name = read_text(hwmon_path / "name")
        except OSError:
            device_name = hwmon_path.name

        for input_path in sorted(hwmon_path.glob("temp*_input")):
            try:
                millidegrees = int(read_text(input_path))
                label_path = input_path.with_name(input_path.name.replace("_input", "_label"))
                label = read_text(label_path) if label_path.exists() else device_name
                sensors.append(
                    {
                        "name": label or device_name,
                        "value": round(millidegrees / 1000.0, 1),
                        "unit": "°C",
                        "available": True,
                    }
                )
            except (OSError, ValueError):
                continue
    return sensors[:32]


def read_volumes(path: Path) -> list[str]:
    if not path.exists():
        return ["/"]
    volumes: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        value = line.strip()
        if value and not value.startswith("#") and value not in volumes:
            volumes.append(value)
    return volumes or ["/"]


def collect_volumes(paths: list[str]) -> list[dict[str, object]]:
    volumes: list[dict[str, object]] = []
    for raw_path in paths:
        path = Path(raw_path)
        try:
            stat = os.statvfs(path)
            total = stat.f_blocks * stat.f_frsize
            free = stat.f_bavail * stat.f_frsize
            volumes.append(
                {
                    "name": raw_path,
                    "used_bytes": max(0, total - free),
                    "free_bytes": free,
                    "total_bytes": total,
                }
            )
        except OSError:
            LOGGER.warning("volume unavailable: %s", raw_path)
    return volumes


def parse_health(status: str) -> str | None:
    lowered = status.lower()
    if "(unhealthy)" in lowered:
        return "unhealthy"
    if "(healthy)" in lowered:
        return "healthy"
    if "(starting)" in lowered:
        return "starting"
    return None


def parse_docker_snapshot(text: str) -> dict[str, object]:
    items: list[dict[str, object]] = []
    running = 0
    stopped = 0
    errors = 0

    for line in text.splitlines():
        parts = line.split("\t", 3)
        if len(parts) != 4:
            continue
        name, state, status, image = (part.strip() for part in parts)
        if not name:
            continue

        normalized_state = state.lower() or "unknown"
        health = parse_health(status)
        exit_match = EXITED_RE.search(status)
        exit_code = int(exit_match.group(1)) if exit_match else None
        is_error = (
            health == "unhealthy"
            or normalized_state == "dead"
            or (exit_code is not None and exit_code != 0)
        )

        if normalized_state == "running":
            running += 1
        elif is_error:
            errors += 1
        else:
            stopped += 1

        items.append(
            {
                "name": name,
                "state": normalized_state,
                "health": health,
                "image": image,
            }
        )

    return {
        "running": running,
        "stopped": stopped,
        "error": errors,
        "items": items[:100],
        "available": True,
    }


def image_version(image: str) -> str | None:
    last_component = image.rsplit("/", 1)[-1]
    if ":" not in last_component:
        return None
    tag = last_component.rsplit(":", 1)[-1].strip()
    return tag or None


def immich_status(containers: dict[str, object], enabled: bool) -> dict[str, object]:
    if not enabled:
        return {"enabled": False, "server": "unknown", "database": "unknown", "version": None}
    items = containers.get("items", [])
    if not isinstance(items, list):
        return {"enabled": True, "server": "unknown", "database": "unknown", "version": None}

    server: dict[str, object] | None = None
    database: dict[str, object] | None = None
    for item in items:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", "")).lower()
        if name in {"immich_server", "immich-server"} or name.startswith("immich_server-"):
            server = item
        if name in {"immich_postgres", "immich-postgres"} or name.startswith("immich_postgres-"):
            database = item

    def status_for(item: dict[str, object] | None) -> str:
        if item is None:
            return "unknown"
        health = item.get("health")
        state = str(item.get("state", "unknown")).lower()
        if health in {"healthy", "unhealthy", "starting"}:
            return str(health)
        return "online" if state == "running" else "offline"

    version = image_version(str(server.get("image", ""))) if server else None
    return {"enabled": True, "server": status_for(server), "database": status_for(database), "version": version}


class MetricsCollector:
    def __init__(self, volumes_file: Path, docker_snapshot: Path, immich_enabled: bool = True):
        self.volume_paths = read_volumes(volumes_file)
        self.docker_snapshot = docker_snapshot
        self.immich_enabled = immich_enabled
        self._cpu_lock = threading.Lock()
        self._previous_cpu: tuple[int, int] | None = None

    def _cpu_usage(self) -> float:
        with self._cpu_lock:
            current = read_cpu_totals()
            previous = self._previous_cpu
            self._previous_cpu = current
            if previous is None:
                baseline = current
                time.sleep(0.1)
                current = read_cpu_totals()
                self._previous_cpu = current
                return usage_from_cpu_samples(baseline, current)
            return usage_from_cpu_samples(previous, current)

    def _docker(self) -> dict[str, object]:
        try:
            stat = self.docker_snapshot.stat()
            if time.time() - stat.st_mtime > 120:
                raise OSError("docker snapshot is stale")
            return parse_docker_snapshot(self.docker_snapshot.read_text(encoding="utf-8"))
        except (OSError, UnicodeError):
            return {
                "running": 0,
                "stopped": 0,
                "error": 0,
                "items": [],
                "available": False,
            }

    def snapshot(self) -> dict[str, object]:
        try:
            uptime = float(read_text(Path("/proc/uptime")).split()[0])
            used_memory, total_memory = read_memory()
            cpu_usage = self._cpu_usage()
            load_1m = os.getloadavg()[0]
        except (OSError, ValueError, IndexError, MetricsUnavailable) as exc:
            raise MetricsUnavailable("essential host metrics unavailable") from exc

        containers = self._docker()
        return {
            "schema_version": 1,
            "host": socket.gethostname(),
            "online": True,
            "uptime_seconds": int(uptime),
            "observed_at_epoch_ms": int(time.time() * 1000),
            "cpu": {"usage_percent": round(cpu_usage, 1), "load_1m": round(load_1m, 2)},
            "memory": {"used_bytes": used_memory, "total_bytes": total_memory},
            "volumes": collect_volumes(self.volume_paths),
            "sensors": read_sensors(),
            "containers": containers,
            "immich": immich_status(containers, self.immich_enabled),
        }


class MetricsHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "HomelabMetrics/1"
    collector: MetricsCollector
    token: str

    def _access_log(self, status: int, started: float) -> None:
        path = urlsplit(self.path).path
        LOGGER.info("%s %s %d %.1fms", self.command, path, status, (time.monotonic() - started) * 1000)

    def _send_json(self, status: int, payload: dict[str, object], allow: str | None = None) -> None:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        if allow:
            self.send_header("Allow", allow)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _authorized(self) -> bool:
        header = self.headers.get("Authorization", "")
        scheme, separator, supplied = header.partition(" ")
        if separator != " " or scheme != "Bearer":
            return False
        return bool(supplied.strip()) and hmac.compare_digest(supplied.strip(), self.token)

    def do_GET(self) -> None:  # noqa: N802
        started = time.monotonic()
        status = HTTPStatus.NOT_FOUND
        try:
            if urlsplit(self.path).path != "/v1/metrics":
                self._send_json(status, {"error": "not_found"})
                return
            if not self._authorized():
                status = HTTPStatus.UNAUTHORIZED
                self._send_json(status, {"error": "unauthorized"})
                return
            try:
                payload = self.collector.snapshot()
            except MetricsUnavailable:
                status = HTTPStatus.SERVICE_UNAVAILABLE
                self._send_json(status, {"error": "metrics_unavailable"})
                return
            status = HTTPStatus.OK
            self._send_json(status, payload)
        finally:
            self._access_log(int(status), started)

    def _method_not_allowed(self) -> None:
        started = time.monotonic()
        status = HTTPStatus.METHOD_NOT_ALLOWED
        try:
            self._send_json(status, {"error": "method_not_allowed"}, allow="GET")
        finally:
            self._access_log(int(status), started)

    do_HEAD = _method_not_allowed  # noqa: N815
    do_POST = _method_not_allowed  # noqa: N815
    do_PUT = _method_not_allowed  # noqa: N815
    do_PATCH = _method_not_allowed  # noqa: N815
    do_DELETE = _method_not_allowed  # noqa: N815
    do_OPTIONS = _method_not_allowed  # noqa: N815

    def log_message(self, _format: str, *_args: object) -> None:
        # The explicit access log above never includes request headers.
        return


def load_token(path: Path) -> str:
    token = read_text(path)
    if len(token) < 32:
        raise ValueError("token must contain at least 32 characters")
    return token


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", required=True, help="address to bind, normally the Tailscale IPv4")
    parser.add_argument("--port", type=int, default=8099)
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--volumes-file", type=Path, required=True)
    parser.add_argument("--docker-snapshot", type=Path, required=True)
    parser.add_argument("--immich-enabled", choices=("true", "false"), default="true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    token = load_token(args.token_file)
    collector = MetricsCollector(
        args.volumes_file,
        args.docker_snapshot,
        immich_enabled=args.immich_enabled == "true",
    )
    MetricsHandler.collector = collector
    MetricsHandler.token = token

    server = ThreadingHTTPServer((args.bind, args.port), MetricsHandler)
    server.daemon_threads = True
    LOGGER.info("listening on %s:%d", args.bind, args.port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
