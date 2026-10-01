package com.muse.app.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object CoverUtils {
    /**
     * Salva un'immagine selezionata dalla galleria nella memoria interna dell'app,
     * restituendo l'URI del file locale (file://...).
     */
    fun savePlaylistCoverLocally(context: Context, playlistId: String, uri: Uri): String? {
        return try {
            val coversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
            val destFile = File(coversDir, "${playlistId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}