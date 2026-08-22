package com.example.homelabmonitor.update

import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateManifest(
    @SerialName("version_code") val versionCode: Long,
    @SerialName("version_name") val versionName: String,
    @SerialName("apk_url") val apkUrl: String,
    val sha256: String,
    val notes: String? = null,
)

data class AppUpdateState(
    val currentVersionCode: Long,
    val currentVersionName: String,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val available: AppUpdateManifest? = null,
    val message: String? = null,
    val error: String? = null,
    val installerUri: Uri? = null,
)
