package com.muse.app.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.muse.app.domain.model.Track
import com.muse.app.player.NewPipeStreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.io.File

object WhatsAppShareManager {

    private const val TAG = "WhatsAppShareManager"
    private const val CLIP_DURATION_SECONDS = 30

    /**
     * Generates a 30-second video clip from the track currently playing
     * using FFmpeg (download + mux audio+video streams), then shares it
     * to WhatsApp status.
     */
    suspend fun shareToWhatsApp(
        context: Context,
        track: Track,
        videoId: String, // ID del video YouTube effettivamente in riproduzione (può essere diverso da track.id)
        startMs: Long,
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val startSec = (startMs / 1000).coerceAtLeast(0)

            onProgress("Estrazione stream video...")

            val videoUrl = getVideoStreamUrl(videoId)
            if (videoUrl == null) {
                withContext(Dispatchers.Main) {
                    onError("Impossibile estrarre lo stream del video. Riprova tra qualche secondo.")
                }
                return@withContext
            }

            onProgress("Elaborazione video in corso (10-30s)...")

            val videoDir = File(context.cacheDir, "videos").also { it.mkdirs() }
            val outputFile = File(videoDir, "whatsapp_status_${System.currentTimeMillis()}.mp4")

            val ffmpegCmd = buildString {
                append("-user_agent \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\" ")
                append("-ss $startSec -i \"$videoUrl\" ")
                append("-t $CLIP_DURATION_SECONDS ")
                // Copia il video, formatta l'audio (il videoUrl contiene già l'audio)
                append("-c:v copy -c:a aac -b:a 128k ")
                append("-y \"${outputFile.absolutePath}\"")
            }

            val session = FFmpegKit.execute(ffmpegCmd)

            if (!ReturnCode.isSuccess(session.returnCode)) {
                val errorLog = session.allLogsAsString.takeLast(100)
                Log.e(TAG, "FFmpeg failed: ${session.allLogsAsString}")
                withContext(Dispatchers.Main) {
                    onError("Errore FFmpeg: $errorLog")
                }
                return@withContext
            }

            if (!outputFile.exists() || outputFile.length() < 1024) {
                withContext(Dispatchers.Main) {
                    onError("File video non generato correttamente. Riprova.")
                }
                return@withContext
            }

            onProgress("Video pronto! Apertura WhatsApp...")

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.whatsapp")
                }
                try {
                    context.startActivity(intent)
                    onSuccess()
                } catch (e: Exception) {
                    val generic = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(generic, "Condividi video").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    onSuccess()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore: ${e.message}", e)
            onError("Errore imprevisto: ${e.message}")
        }
    }

    private suspend fun getVideoStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val pageUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(pageUrl)
            extractor.fetchPage()

            val progressive = extractor.videoStreams
                .filter {
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    !it.content.isNullOrEmpty() &&
                    !it.isVideoOnly
                }
                .sortedByDescending { it.height }

            if (progressive.isNotEmpty()) {
                return@withContext (progressive.firstOrNull { it.height <= 720 } ?: progressive.first()).content
            }

            val videoOnly = extractor.videoOnlyStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrEmpty() }
                .sortedByDescending { it.height }

            return@withContext (videoOnly.firstOrNull { it.height <= 720 } ?: videoOnly.firstOrNull())?.content
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video stream: ${e.message}")
            return@withContext null
        }
    }

    fun cleanOldClips(context: Context) {
        File(context.cacheDir, "videos").listFiles()?.forEach { it.delete() }
    }
}
