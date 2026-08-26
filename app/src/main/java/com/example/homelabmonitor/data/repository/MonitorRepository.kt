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
            require(settings.endpoint.isNotBlank()) { "Configure o host do homelab." }
            require(settings.token.isNotBlank()) { "Configure o token da API." }

            val request = Request.Builder()
                .url(metricsUrl(settings.endpoint))
                .header("Authorization", "Bearer ${settings.token}")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiResponseException(response.code)
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
            return "${EndpointConfig.normalize(endpoint)}/v1/metrics"
        }
    }
}

class ApiResponseException(val code: Int) : IOException("API respondeu HTTP $code")

fun userFacingConnectionError(throwable: Throwable): String = when (throwable) {
    is ApiResponseException -> when (throwable.code) {
        401, 403 -> "Token recusado pelo agente. Confira o token e tente novamente."
        404 -> "Agente encontrado, mas /v1/metrics não existe nesse endereço."
        in 500..599 -> "O agente respondeu com erro ${throwable.code}. Verifique o homelab."
        else -> "O agente respondeu HTTP ${throwable.code}."
    }
    is java.net.UnknownHostException -> "Não encontrei esse host. Confirme o Tailscale e o nome do servidor."
    is java.net.ConnectException -> "Não consegui conectar. Verifique se o agente está ativo e se a porta está liberada no Tailscale."
    is java.net.SocketTimeoutException -> "A conexão demorou demais. Confirme o Tailscale e tente novamente."
    is IllegalArgumentException -> throwable.message ?: "Endereço do homelab inválido."
    else -> throwable.message ?: "Não foi possível conectar ao homelab."
}
