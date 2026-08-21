package com.example.homelabmonitor.data.repository

import com.example.homelabmonitor.data.model.AppSettings
import com.example.homelabmonitor.data.model.HomelabSnapshot
import com.example.homelabmonitor.data.model.MockSnapshotFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

interface MonitorRepository {
    suspend fun fetchSnapshot(): Result<HomelabSnapshot>
}

class MockMonitorRepository : MonitorRepository {
    override suspend fun fetchSnapshot(): Result<HomelabSnapshot> =
        Result.success(MockSnapshotFactory.create())
}

class ConfigurationErrorRepository(private val message: String) : MonitorRepository {
    override suspend fun fetchSnapshot(): Result<HomelabSnapshot> = Result.failure(IllegalStateException(message))
}

class HttpMonitorRepository(
    private val settings: AppSettings,
    private val httpClient: OkHttpClient = defaultHttpClient,
    private val json: Json = apiJson,
) : MonitorRepository {
    override suspend fun fetchSnapshot(): Result<HomelabSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.endpoint.isNotBlank()) { "Configure o endpoint da API." }
            require(settings.token.isNotBlank()) { "Configure o token da API." }

            val request = Request.Builder()
                .url(metricsUrl(settings.endpoint))
                .header("Authorization", "Bearer ${settings.token}")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("API respondeu HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                require(body.isNotBlank()) { "API retornou uma resposta vazia." }
                json.decodeFromString<HomelabSnapshot>(body)
            }
        }
    }

    companion object {
        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        val apiJson: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun metricsUrl(endpoint: String): String {
            val normalized = endpoint.trim().trimEnd('/')
            return if (normalized.endsWith("/v1/metrics")) normalized else "$normalized/v1/metrics"
        }
    }
}
