package com.muse.app.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersionName: String,
    val releaseNotes: String,
    val downloadUrl: String
)

class UpdateManager(private val context: Context) {

    // Sostituisci con il tuo owner/repo su GitHub (es. "emanuele/muse")
    private val repoOwner = "DotHack88"
    private val repoName = "Aura"

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val tagName = json.getString("tag_name") // e.g. "v1.0.2"
                val body = json.optString("body", "Nuova versione disponibile")
                
                val currentVersionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                
                // Pulisce le stringhe (es. toglie la "v" iniziale se presente)
                val cleanLatest = tagName.replace(Regex("[^0-9.]"), "")
                val cleanCurrent = currentVersionName?.replace(Regex("[^0-9.]"), "") ?: "0.0.0"

                // Confronto rudimentale basato su stringhe, ideale sarebbe usare versionCode (ma GitHub releases usa tag)
                // Usiamo compareTo per le versioni semantiche
                val isNewer = compareVersions(cleanLatest, cleanCurrent) > 0

                if (isNewer) {
                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.getString("name")
                        if (assetName.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    if (downloadUrl.isNotEmpty()) {
                        return@withContext UpdateInfo(
                            hasUpdate = true,
                            latestVersionName = tagName,
                            releaseNotes = body,
                            downloadUrl = downloadUrl
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Errore durante il check dell'aggiornamento", e)
        }
        return@withContext null
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".")
        val parts2 = v2.split(".")
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrNull(i)?.toIntOrNull() ?: 0
            val p2 = parts2.getOrNull(i)?.toIntOrNull() ?: 0
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(updateInfo.downloadUrl)
        val request = DownloadManager.Request(uri)
            .setTitle("Muse Update ${updateInfo.latestVersionName}")
            .setDescription("Scaricamento del nuovo aggiornamento in corso...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MuseUpdate.apk")

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    installApk(downloadId, downloadManager)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installApk(downloadId: Long, downloadManager: DownloadManager) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Errore durante l'avvio dell'installazione", e)
        }
    }
}
