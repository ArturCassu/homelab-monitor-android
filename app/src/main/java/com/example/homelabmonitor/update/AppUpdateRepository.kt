package com.example.homelabmonitor.update

import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class AppUpdateRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val appContext = context.applicationContext
    private val updatesDirectory = File(appContext.cacheDir, UPDATES_DIRECTORY)

    fun currentState(): AppUpdateState {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return AppUpdateState(
            currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            currentVersionName = packageInfo.versionName.orEmpty(),
        )
    }

    suspend fun fetchLatest(): Result<AppUpdateManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_MANIFEST_URL)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Não foi possível consultar a atualização (HTTP ${response.code}).")
                }
                val payload = response.body?.string()
                    ?: error("O manifesto de atualização veio vazio.")
                AppUpdateManifestParser.parse(payload)
            }
        }
    }

    suspend fun downloadAndPrepare(manifest: AppUpdateManifest): Result<android.net.Uri> = withContext(Dispatchers.IO) {
        runCatching {
            validateDownloadUrl(manifest.apkUrl)
            require(manifest.sha256.matches(SHA256_PATTERN)) {
                "O manifesto de atualização tem um SHA-256 inválido."
            }

            updatesDirectory.mkdirs()
            cleanupCache()
            val temporaryFile = File(updatesDirectory, "homelab-monitor-${manifest.versionCode}.apk.part")
            val apkFile = File(updatesDirectory, "homelab-monitor-${manifest.versionCode}.apk")

            try {
                val request = Request.Builder().url(manifest.apkUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Não foi possível baixar a atualização (HTTP ${response.code}).")
                    }
                    val body = response.body ?: error("O APK de atualização veio vazio.")
                    if (body.contentLength() > MAX_APK_BYTES) {
                        error("O APK de atualização excede o limite de segurança.")
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    var totalBytes = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(temporaryFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                totalBytes += read
                                if (totalBytes > MAX_APK_BYTES) {
                                    error("O APK de atualização excede o limite de segurança.")
                                }
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                    }

                    val actualHash = digest.digest().toHex()
                    require(actualHash.equals(manifest.sha256, ignoreCase = true)) {
                        "A verificação SHA-256 do APK falhou."
                    }
                }

                check(temporaryFile.renameTo(apkFile)) { "Não foi possível preparar o APK para instalação." }
                FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    apkFile,
                )
            } finally {
                temporaryFile.delete()
            }
        }
    }

    fun cleanupCache() {
        updatesDirectory.listFiles()?.forEach { it.delete() }
    }

    private fun validateDownloadUrl(url: String) {
        val parsed = android.net.Uri.parse(url)
        require(parsed.scheme == "https" && parsed.host == "github.com") {
            "A atualização precisa ser baixada pelo GitHub via HTTPS."
        }
        require(
            parsed.path.orEmpty().startsWith(
                "/ArturCassu/homelab-monitor-android/releases/download/",
            ) && parsed.path.orEmpty().endsWith(".apk"),
        ) {
            "O manifesto aponta para um APK fora da release esperada."
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(Locale.US, byte) }

    companion object {
        const val LATEST_MANIFEST_URL =
            "https://raw.githubusercontent.com/ArturCassu/homelab-monitor-android/main/update/latest.json"

        private const val UPDATES_DIRECTORY = "updates"
        private const val BUFFER_SIZE = 16 * 1024
        private const val MAX_APK_BYTES = 100L * 1024L * 1024L
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}

internal object AppUpdateManifestParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun parse(payload: String): AppUpdateManifest = json.decodeFromString<AppUpdateManifest>(payload).also { manifest ->
        require(manifest.versionCode > 0) { "O versionCode da atualização é inválido." }
        require(manifest.versionName.isNotBlank()) { "O versionName da atualização está vazio." }
        require(manifest.apkUrl.isNotBlank()) { "A URL do APK de atualização está vazia." }
    }
}
