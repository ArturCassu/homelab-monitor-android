package com.example.homelabmonitor.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomelabSnapshot(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val host: String = "homelab",
    val online: Boolean = false,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
    @SerialName("observed_at_epoch_ms") val observedAtEpochMs: Long = 0,
    val cpu: CpuMetrics = CpuMetrics(),
    val memory: MemoryMetrics = MemoryMetrics(),
    val volumes: List<VolumeStatus> = emptyList(),
    val sensors: List<SensorReading> = emptyList(),
    val containers: ContainerStatus = ContainerStatus(),
    val immich: ImmichStatus = ImmichStatus(),
)

@Serializable
data class CpuMetrics(
    @SerialName("usage_percent") val usagePercent: Double = 0.0,
    @SerialName("load_1m") val load1m: Double? = null,
)

@Serializable
data class MemoryMetrics(
    @SerialName("used_bytes") val usedBytes: Long = 0,
    @SerialName("total_bytes") val totalBytes: Long = 0,
)

@Serializable
data class VolumeStatus(
    val name: String = "",
    @SerialName("used_bytes") val usedBytes: Long = 0,
    @SerialName("free_bytes") val freeBytes: Long = 0,
    @SerialName("total_bytes") val totalBytes: Long = 0,
)

@Serializable
data class SensorReading(
    val name: String = "",
    val value: Double? = null,
    val unit: String = "°C",
    val available: Boolean = true,
)

data class SensorGroup(
    val name: String,
    val value: Double?,
    val minimum: Double?,
    val maximum: Double?,
    val unit: String,
    val readingCount: Int,
)

@Serializable
data class ContainerStatus(
    val running: Int = 0,
    val stopped: Int = 0,
    val error: Int = 0,
    val items: List<ContainerItem> = emptyList(),
)

@Serializable
data class ContainerItem(
    val name: String = "",
    val state: String = "unknown",
    val health: String? = null,
)

@Serializable
data class ImmichStatus(
    val enabled: Boolean = false,
    val server: String = "unknown",
    val database: String = "unknown",
    val version: String? = null,
)

enum class AccentTheme(val key: String, val label: String) {
    GRAPHITE("graphite", "Grafite"),
    MINT("mint", "Menta"),
    AMBER("amber", "Âmbar"),
    VIOLET("violet", "Violeta"),
    ;

    companion object {
        fun fromKey(key: String?): AccentTheme = entries.firstOrNull { it.key == key } ?: GRAPHITE
    }
}

data class AppSettings(
    val endpoint: String = "",
    val token: String = "",
    val setupComplete: Boolean = false,
    val accentTheme: AccentTheme = AccentTheme.GRAPHITE,
)

data class MonitorUiState(
    val snapshot: HomelabSnapshot = HomelabSnapshot(),
    val settings: AppSettings = AppSettings(),
    val isRefreshing: Boolean = false,
    val isConnecting: Boolean = false,
    val lastError: String? = null,
    val setupError: String? = null,
)

enum class HealthState {
    HEALTHY,
    WARNING,
    ERROR,
    UNKNOWN,
}

fun HomelabSnapshot.healthState(fetchError: String? = null): HealthState {
    if (fetchError != null || !online) return HealthState.ERROR
    if (containers.error > 0) return HealthState.WARNING
    if (immich.enabled && immich.server.lowercase() !in setOf("healthy", "ok", "online")) return HealthState.WARNING
    if (immich.enabled && immich.database.lowercase() !in setOf("healthy", "ok", "online")) return HealthState.WARNING
    return HealthState.HEALTHY
}

fun HealthState.label(): String = when (this) {
    HealthState.HEALTHY -> "Saudável"
    HealthState.WARNING -> "Atenção"
    HealthState.ERROR -> "Offline"
    HealthState.UNKNOWN -> "Desconhecido"
}

fun HomelabSnapshot.ramUsagePercent(): Float = percentage(memory.usedBytes, memory.totalBytes)

fun VolumeStatus.usagePercent(): Float = percentage(usedBytes, totalBytes)

/**
 * Turns noisy hwmon readings such as Core 0/Core 1 into one useful card. The
 * raw readings remain available in the API; this is only a presentation model.
 */
fun HomelabSnapshot.sensorGroups(): List<SensorGroup> = sensors
    .filter { it.name.isNotBlank() }
    .groupBy { sensorGroupKey(it.name) }
    .map { (name, readings) ->
        val available = readings.filter { it.available && it.value != null }
        val values = available.mapNotNull { it.value }
        SensorGroup(
            name = name,
            value = values.averageOrNull(),
            minimum = values.minOrNull(),
            maximum = values.maxOrNull(),
            unit = available.firstOrNull()?.unit ?: readings.firstOrNull()?.unit ?: "°C",
            readingCount = readings.size,
        )
    }

private fun sensorGroupKey(name: String): String {
    val normalized = name.trim().replace(Regex("\\s+"), " ")
    return when {
        normalized.contains("gpu", ignoreCase = true) -> "GPU"
        Regex(".*\\bcore\\s*#?\\d+\\b.*", RegexOption.IGNORE_CASE).matches(normalized) -> "CPU cores"
        Regex(".*\\b(cpu\\s*)?(package|pkg|die)(\\s+id)?\\s*#?\\d*.*", RegexOption.IGNORE_CASE).matches(normalized) -> "CPU Package"
        normalized.contains("nvme", ignoreCase = true) -> "NVMe"
        else -> normalized.replace(Regex("\\s+(sensor|temp|temperature)?\\s*#?\\d+$", RegexOption.IGNORE_CASE), "").trim()
            .ifBlank { normalized }
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun percentage(used: Long, total: Long): Float {
    if (total <= 0) return 0f
    return (used.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (value >= 100 || value % 1.0 == 0.0) {
        "%.0f %s".format(Locale.US, value, units[unitIndex])
    } else {
        "%.1f %s".format(Locale.US, value, units[unitIndex])
    }
}

fun formatPercent(value: Double): String = "%.0f%%".format(Locale.US, value.coerceIn(0.0, 100.0))

fun formatTemperature(value: Double?, unit: String = "°C"): String = value?.let {
    "%.1f %s".format(Locale.US, it, unit)
} ?: "--"

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "--"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatObservedAt(epochMs: Long): String {
    if (epochMs <= 0) return "sem atualização"
    val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault())
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)
}
