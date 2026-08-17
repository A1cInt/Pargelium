package com.alcint.pargelium

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/A1cInt/Pargelium/releases/latest"

    fun checkForUpdates(context: Context, isManual: Boolean) {
        if (!isManual && !PrefsManager.getAutoUpdateEnabled()) return

        val lastCheck = PrefsManager.getLastUpdateCheckTime()
        val now = System.currentTimeMillis()
        if (!isManual && (now - lastCheck < 12 * 60 * 60 * 1000L)) return

        PrefsManager.saveLastUpdateCheckTime(now)

        if (isManual) {
            Toast.makeText(context, context.getString(R.string.checking_updates), Toast.LENGTH_SHORT).show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(GITHUB_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.optString("tag_name", "")
                    val body = json.optString("body", "")

                    val assets = json.optJSONArray("assets")
                    var apkUrl = ""
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name", "").endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    val cleanTag = tagName.removePrefix("v").replace("-release", "")
                    val cleanCurrent = (currentVersion ?: "").removePrefix("v")

                    if (cleanTag.isNotEmpty() && apkUrl.isNotEmpty() && isNewerVersion(cleanCurrent, cleanTag)) {
                        if (!isManual && PrefsManager.getSkippedVersion() == cleanTag) return@launch

                        withContext(Dispatchers.Main) {
                            showUpdateDialog(context, cleanTag, body, apkUrl, isManual)
                        }
                    } else if (isManual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.no_updates_found), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (isManual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (current.isBlank()) return true
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(currParts.size, latestParts.size)

        for (i in 0 until length) {
            val c = currParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun showUpdateDialog(context: Context, version: String, changelog: String, apkUrl: String, isManual: Boolean) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.update_available, version))
            .setMessage(changelog.takeIf { it.isNotBlank() } ?: context.getString(R.string.update_available_desc))
            .setPositiveButton(context.getString(R.string.action_download)) { _, _ ->
                downloadAndInstall(context, apkUrl, version)
            }
            .setNegativeButton(context.getString(R.string.action_cancel), null)
            .apply {
                if (!isManual) {
                    setNeutralButton(context.getString(R.string.action_skip)) { _, _ ->
                        PrefsManager.saveSkippedVersion(version)
                    }
                }
            }
            .show()
    }

    private fun downloadAndInstall(context: Context, url: String, version: String) {
        Toast.makeText(context, context.getString(R.string.downloading_update), Toast.LENGTH_SHORT).show()

        val fileName = "Pargelium_$version.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(context.getString(R.string.app_name) + " " + version)
            .setDescription(context.getString(R.string.downloading_update))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    ctx.unregisterReceiver(this)
                    installApk(ctx, file)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.install_failed), Toast.LENGTH_SHORT).show()
        }
    }
}