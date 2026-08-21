package com.example.homelabmonitor

import com.example.homelabmonitor.data.model.HealthState
import com.example.homelabmonitor.data.model.HomelabSnapshot
import com.example.homelabmonitor.data.model.formatBytes
import com.example.homelabmonitor.data.model.formatDuration
import com.example.homelabmonitor.data.model.healthState
import com.example.homelabmonitor.data.model.ramUsagePercent
import com.example.homelabmonitor.data.model.usagePercent
import com.example.homelabmonitor.data.repository.HttpMonitorRepository
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
    fun formatsValuesForCompactCards() {
        assertEquals("1 KiB", formatBytes(1024))
        assertEquals("1.5 MiB", formatBytes(1_572_864))
        assertEquals("1d 1h", formatDuration(90_000))
    }

    @Test
    fun normalizesMetricsEndpointWithoutDuplicatingPath() {
        assertEquals("http://homelab:8099/v1/metrics", HttpMonitorRepository.metricsUrl("http://homelab:8099"))
        assertEquals("http://homelab:8099/v1/metrics", HttpMonitorRepository.metricsUrl("http://homelab:8099/v1/metrics/"))
    }
}
