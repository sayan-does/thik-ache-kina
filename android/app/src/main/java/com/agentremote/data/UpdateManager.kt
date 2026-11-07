package com.agentremote.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpToDate(val currentVersion: String) : UpdateStatus()
    data class UpdateAvailable(
        val info: UpdateInfo,
        val currentVersion: String
    ) : UpdateStatus()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateStatus()
    data class ReadyToInstall(
        val apkFile: File,
        val info: UpdateInfo
    ) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val GITHUB_REPO = "sayan-does/thik-ache-kina"
        const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

        fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
            val cleanLatest = latestTag.trim().removePrefix("v").split(".")
            val cleanCurrent = currentVersion.trim().removePrefix("v").split(".")
            val maxParts = maxOf(cleanLatest.size, cleanCurrent.size)
            for (i in 0 until maxParts) {
                val latestPart = cleanLatest.getOrNull(i)?.toIntOrNull() ?: 0
                val currentPart = cleanCurrent.getOrNull(i)?.toIntOrNull() ?: 0
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            return false
        }
    }

    suspend fun checkForUpdates(currentVersion: String): UpdateStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("User-Agent", "AgentRemote-Android")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateStatus.Error("GitHub API responded with code ${response.code}")
                }

                val bodyStr = response.body?.string() ?: return@withContext UpdateStatus.Error("Empty release data")
                val json = JSONObject(bodyStr)
                val tagName = json.optString("tag_name", "")
                val notes = json.optString("body", "No changelog provided.")
                val assets = json.optJSONArray("assets")

                var downloadUrl = ""
                var apkSize = 0L

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                if (tagName.isEmpty() || downloadUrl.isEmpty()) {
                    return@withContext UpdateStatus.Error("No APK asset found in latest release")
                }

                val info = UpdateInfo(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v"),
                    releaseNotes = notes,
                    downloadUrl = downloadUrl,
                    apkSize = apkSize
                )

                if (isNewerVersion(tagName, currentVersion)) {
                    UpdateStatus.UpdateAvailable(info, currentVersion)
                } else {
                    UpdateStatus.UpToDate(currentVersion)
                }
            }
        } catch (e: Exception) {
            UpdateStatus.Error(e.message ?: "Failed to connect to update server")
        }
    }

    suspend fun downloadUpdate(
        info: UpdateInfo,
        onProgress: (Float, Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!dir.exists()) dir.mkdirs()
        val outputFile = File(dir, "agent-remote-${info.versionName}.apk")

        if (outputFile.exists()) {
            outputFile.delete()
        }

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "AgentRemote-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download update: HTTP ${response.code}")
            }

            val responseBody = response.body ?: throw IllegalStateException("Download body is empty")
            val totalBytes = if (info.apkSize > 0) info.apkSize else responseBody.contentLength()
            var downloadedBytes = 0L

            responseBody.byteStream().use { input: InputStream ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes) else 0f
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }
        }

        outputFile
    }

    fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun installApk(file: File) {
        val authority = "${context.packageName}.provider"
        val apkUri = FileProvider.getUriForFile(context, authority, file)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}
