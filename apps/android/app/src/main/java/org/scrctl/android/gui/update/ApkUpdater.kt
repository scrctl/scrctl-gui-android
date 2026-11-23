package org.scrctl.android.gui.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A non-success response while downloading an update APK; localized by the UI. */
class UpdateDownloadHttpException(val statusCode: Int) :
    java.io.IOException("Download failed with HTTP $statusCode")

class InvalidUpdateApk : java.io.IOException()

class OlderUpdateApk(val candidate: Long, val installed: Long) : java.io.IOException()

/** Downloads a release APK and hands it to the system package installer. */
class ApkUpdater(private val context: Context) {
    suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "ScrctlGui-update.apk")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ScrctlGui-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw UpdateDownloadHttpException(code)
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        target
    }

    suspend fun install(file: File) {
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val candidate = packageManager.getPackageArchiveInfo(file.path, 0)
                ?.takeIf { it.packageName == context.packageName }
                ?: throw InvalidUpdateApk()
            val installed = packageManager.getPackageInfo(context.packageName, 0)
            val candidateVersion = PackageInfoCompat.getLongVersionCode(candidate)
            val installedVersion = PackageInfoCompat.getLongVersionCode(installed)
            if (candidateVersion < installedVersion) {
                throw OlderUpdateApk(candidateVersion, installedVersion)
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
