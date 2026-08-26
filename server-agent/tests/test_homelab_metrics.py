import importlib.util
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "homelab_metrics.py"
SPEC = importlib.util.spec_from_file_location("homelab_metrics", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class MetricsAgentTest(unittest.TestCase):
    def test_cpu_usage_is_bounded(self):
        self.assertAlmostEqual(
            MODULE.usage_from_cpu_samples((100, 40), (200, 80)),
            60.0,
        )
        self.assertAlmostEqual(
            MODULE.usage_from_cpu_samples((100, 40), (200, 60)),
            80.0,
        )

    def test_docker_snapshot_classifies_states(self):
        snapshot = MODULE.parse_docker_snapshot(
            """immich_server\trunning\tUp 1 hour (healthy)\tghcr.io/immich-app/immich-server:v1.132.0
immich_postgres\trunning\tUp 1 hour (healthy)\tpostgres:14
old_job\texited\tExited (0) 2 hours ago\tbusybox:latest
broken\texited\tExited (1) 2 hours ago\tbusybox:latest
"""
        )
        self.assertEqual(snapshot["running"], 2)
        self.assertEqual(snapshot["stopped"], 1)
        self.assertEqual(snapshot["error"], 1)
        self.assertEqual(MODULE.immich_status(snapshot, True)["server"], "healthy")
        self.assertEqual(MODULE.immich_status(snapshot, True)["database"], "healthy")
        self.assertEqual(MODULE.immich_status(snapshot, True)["version"], "v1.132.0")

    def test_immich_can_be_disabled_without_affecting_other_metrics(self):
        snapshot = MODULE.parse_docker_snapshot(
            "immich_server\trunning\tUp 1 hour (healthy)\timmich-server:v1\n"
        )
        immich = MODULE.immich_status(snapshot, False)
        self.assertFalse(immich["enabled"])
        self.assertEqual(immich["server"], "unknown")

    def test_missing_docker_snapshot_is_unavailable(self):
        with tempfile.TemporaryDirectory() as directory:
            collector = MODULE.MetricsCollector(
                Path(directory) / "volumes.conf",
                Path(directory) / "missing.tsv",
            )
            self.assertFalse(collector._docker()["available"])

    def test_http_endpoint_requires_token_and_allows_only_get(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            volumes = root / "volumes.conf"
            volumes.write_text("/\n", encoding="utf-8")
            docker = root / "docker.tsv"
            docker.write_text("container\trunning\tUp 1 minute\timage:latest\n", encoding="utf-8")
            handler = type("TestMetricsHandler", (MODULE.MetricsHandler,), {})
            handler.collector = type(
                "FixedCollector",
                (),
                {"snapshot": lambda _self: {"online": True, "host": "test"}},
            )()
            handler.token = "t" * 64
            server = MODULE.ThreadingHTTPServer(("127.0.0.1", 0), handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            url = f"http://127.0.0.1:{server.server_port}/v1/metrics"
            try:
                with self.assertRaises(urllib.error.HTTPError) as unauthorized:
                    urllib.request.urlopen(url, timeout=3)
                self.assertEqual(unauthorized.exception.code, 401)
                unauthorized.exception.close()

                request = urllib.request.Request(url, headers={"Authorization": f"Bearer {handler.token}"})
                with urllib.request.urlopen(request, timeout=3) as response:
                    payload = json.load(response)
                self.assertTrue(payload["online"])

                write_request = urllib.request.Request(url, method="POST")
                with self.assertRaises(urllib.error.HTTPError) as method_error:
                    urllib.request.urlopen(write_request, timeout=3)
                self.assertEqual(method_error.exception.code, 405)
                method_error.exception.close()
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=3)


if __name__ == "__main__":
    unittest.main()
