package com.example.homelabmonitor

import com.example.homelabmonitor.data.model.HealthState
import com.example.homelabmonitor.data.model.HomelabSnapshot
import com.example.homelabmonitor.data.model.formatBytes
import com.example.homelabmonitor.data.model.formatDuration
import com.example.homelabmonitor.data.model.healthState
import com.example.homelabmonitor.data.model.ramUsagePercent
import com.example.homelabmonitor.data.model.usagePercent
import com.example.homelabmonitor.data.repository.HttpMonitorRepository
import com.example.homelabmonitor.data.repository.EndpointConfig
import com.example.homelabmonitor.update.AppUpdateManifestParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesMetricsPayloadAndComputesState() {
        val snapshot = json.decodeFromString<HomelabSnapshot>(
            """
            {
              "schema_version": 1,
              "host": "homelab",
              "online": true,
              "uptime_seconds": 90061,
              "observed_at_epoch_ms": 1730000000000,
              "cpu": {"usage_percent": 27.5, "load_1m": 0.80},
              "memory": {"used_bytes": 50, "total_bytes": 100},
              "volumes": [{"name": "/srv", "used_bytes": 75, "free_bytes": 25, "total_bytes": 100}],
              "sensors": [{"name": "CPU Package", "value": 51.5, "unit": "°C", "available": true}],
              "containers": {"running": 4, "stopped": 1, "error": 0},
              "immich": {"server": "healthy", "database": "healthy", "version": "test"}
            }
            """.trimIndent(),
        )

        assertEquals("homelab", snapshot.host)
        assertEquals(27.5, snapshot.cpu.usagePercent, 0.001)
        assertEquals(0.5f, snapshot.ramUsagePercent(), 0.001f)
        assertEquals(0.75f, snapshot.volumes.single().usagePercent(), 0.001f)
        assertEquals(HealthState.HEALTHY, snapshot.healthState())
        assertTrue(snapshot.immich.database == "healthy")
    }

    @Test
    fun containerErrorRaisesWarning() {
        val snapshot = HomelabSnapshot(
            online = true,
            containers = com.example.homelabmonitor.data.model.ContainerStatus(error = 1),
        )

        assertEquals(HealthState.WARNING, snapshot.healthState())
    }

    @Test
    fun disabledImmichDoesNotMakeHostUnhealthy() {
        val snapshot = HomelabSnapshot(
            online = true,
            immich = com.example.homelabmonitor.data.model.ImmichStatus(enabled = false),
        )

        assertEquals(HealthState.HEALTHY, snapshot.healthState())
    }

    @Test
    fun formatsValuesForCompactCards() {
        assertEquals("1 KiB", formatBytes(1024))
        assertEquals("1.5 MiB", formatBytes(1_572_864))
        assertEquals("1d 1h", formatDuration(90_000))
    }

    @Test
    fun normalizesMetricsEndpointWithoutDuplicatingPath() {
        assertEquals("http://homelab:8099/v1/metrics", HttpMonitorRepository.metricsUrl("http://homelab:8099"))
        assertEquals("http://homelab:8099/v1/metrics", HttpMonitorRepository.metricsUrl("http://homelab:8099/v1/metrics/"))
        assertEquals("http://homelab:8099/v1/metrics", HttpMonitorRepository.metricsUrl("homelab"))
        assertEquals("https://homelab:443/v1/metrics", HttpMonitorRepository.metricsUrl("https://homelab"))
    }

    @Test
    fun validatesAndNormalizesHostsWithOptionalPort() {
        val defaultPort = EndpointConfig.parse("100.64.10.20").getOrThrow()
        assertEquals("http://100.64.10.20:8099", defaultPort.baseUrl)
        assertTrue(defaultPort.usedDefaultPort)

        val customPort = EndpointConfig.parse("http://homelab:9100/v1/metrics/").getOrThrow()
        assertEquals("http://homelab:9100", customPort.baseUrl)
        assertEquals(9100, customPort.port)

        assertTrue(EndpointConfig.parse("http://bad host").isFailure)
        assertTrue(EndpointConfig.parse("http://homelab:99999").isFailure)
    }

    @Test
    fun parsesPublicUpdateManifest() {
        val manifest = AppUpdateManifestParser.parse(
            """
            {
              "version_code": 3,
              "version_name": "0.2.0",
              "apk_url": "https://github.com/ArturCassu/homelab-monitor-android/releases/download/v0.2.0/HomelabMonitor-debug.apk",
              "sha256": "951ac02a466acf18fa4dcd6d091a164b015996cb3eaa5ada45103ec1af553fc4",
              "notes": "teste"
            }
            """.trimIndent(),
        )

        assertEquals(3L, manifest.versionCode)
        assertEquals("0.2.0", manifest.versionName)
        assertTrue(manifest.apkUrl.endsWith("HomelabMonitor-debug.apk"))
    }
}
