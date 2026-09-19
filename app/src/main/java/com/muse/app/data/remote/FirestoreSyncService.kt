package com.muse.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.muse.app.domain.model.Playlist
import com.muse.app.domain.model.Track
import kotlinx.coroutines.tasks.await

class FirestoreSyncService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun syncLastPlayback(track: Track, positionMs: Long, durationMs: Long) {
        val uid = getCurrentUserId() ?: return
        val playbackData = mapOf(
            "trackId" to track.id,
            "title" to track.title,
            "artist" to track.artist,
            "thumbnailUrl" to track.thumbnailUrl,
            "positionMs" to positionMs,
            "durationMs" to durationMs,
            "lastPlayedAt" to System.currentTimeMillis()
        )
        firestore.collection("users").document(uid)
            .collection("history").document("last_played")
            .set(playbackData, SetOptions.merge())
            .await()
    }

    suspend fun getLastPlayback(): Map<String, Any>? {
        val uid = getCurrentUserId() ?: return null
        val doc = firestore.collection("users").document(uid)
            .collection("history").document("last_played")
            .get()
            .await()
        return doc.data
    }

    suspend fun saveFavorite(track: Track, isFavorite: Boolean) {
        val uid = getCurrentUserId() ?: return
        val favRef = firestore.collection("users").document(uid)
            .collection("favorites").document(track.id)

        if (isFavorite) {
            val data = mapOf(
                "id" to track.id,
                "title" to track.title,
                "artist" to track.artist,
                "thumbnailUrl" to track.thumbnailUrl,
                "durationMs" to track.durationMs,
                "timestamp" to System.currentTimeMillis()
            )
            favRef.set(data).await()
        } else {
            favRef.delete().await()
        }
    }

    suspend fun syncPlaylist(playlist: Playlist) {
        val uid = getCurrentUserId() ?: return
        val playlistData = mapOf(
            "id" to playlist.id,
            "name" to playlist.name,
            "coverUrl" to playlist.coverUrl,
            "createdAt" to playlist.createdAt,
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("users").document(uid)
            .collection("playlists").document(playlist.id)
            .set(playlistData, SetOptions.merge())
            .await()
    }

    suspend fun deletePlaylist(playlistId: String) {
        val uid = getCurrentUserId() ?: return
        firestore.collection("users").document(uid)
            .collection("playlists").document(playlistId)
            .delete()
            .await()
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: Track) {
        val uid = getCurrentUserId() ?: return
        val data = mapOf(
            "id" to track.id,
            "title" to track.title,
            "artist" to track.artist,
            "thumbnailUrl" to track.thumbnailUrl,
            "durationMs" to track.durationMs,
            "addedAt" to System.currentTimeMillis()
        )
        firestore.collection("users").document(uid)
            .collection("playlists").document(playlistId)
            .collection("tracks").document(track.id)
            .set(data)
            .await()
    }

    suspend fun pullFavorites(): List<Track> {
        val uid = getCurrentUserId() ?: return emptyList()
        val result = firestore.collection("users").document(uid)
            .collection("favorites")
            .get()
            .await()

        return result.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: return@mapNotNull null
            val title = doc.getString("title") ?: ""
            val artist = doc.getString("artist") ?: ""
            val thumbnailUrl = doc.getString("thumbnailUrl") ?: ""
            val durationMs = doc.getLong("durationMs") ?: 0L
            
            Track(
                id = id,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                durationMs = durationMs,
                isFavorite = true
            )
        }
    }
    suspend fun pullPlaylists(): List<Playlist> {
        val uid = getCurrentUserId() ?: return emptyList()
        val result = firestore.collection("users").document(uid)
            .collection("playlists")
            .get()
            .await()

        return result.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: return@mapNotNull null
            val name = doc.getString("name") ?: ""
            val coverUrl = doc.getString("coverUrl")
            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            Playlist(id = id, name = name, coverUrl = coverUrl, createdAt = createdAt)
        }
    }

    suspend fun pullPlaylistTracks(playlistId: String): List<Track> {
        val uid = getCurrentUserId() ?: return emptyList()
        val result = firestore.collection("users").document(uid)
            .collection("playlists").document(playlistId)
            .collection("tracks")
            .get()
            .await()

        return result.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: return@mapNotNull null
            val title = doc.getString("title") ?: ""
            val artist = doc.getString("artist") ?: ""
            val thumbnailUrl = doc.getString("thumbnailUrl") ?: ""
            val durationMs = doc.getLong("durationMs") ?: 0L
            
            Track(
                id = id,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                durationMs = durationMs
            )
        }
    }

    suspend fun pullHistory(): List<Map<String, Any>> {
        // La history è un po' più complessa da scaricare interamente. 
        // Per ora usiamo "last_played" e potremmo avere una collection "history_tracks" in futuro.
        // Simuliamo l'ottenimento dell'history principale o dell'ultimo playback
        val uid = getCurrentUserId() ?: return emptyList()
        val doc = firestore.collection("users").document(uid)
            .collection("history").document("last_played")
            .get()
            .await()
            
        val data = doc.data
        return if (data != null) listOf(data) else emptyList()
    }
}
