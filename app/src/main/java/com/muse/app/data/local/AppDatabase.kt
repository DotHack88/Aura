package com.muse.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val thumbnailUrl: String,
    val durationMs: Long,
    val viewsText: String? = null,
    val album: String? = null,
    val albumBrowseId: String? = null,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val orderIndex: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val trackId: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1
)

/** Artisti seguiti dall'utente */
@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String = "",
    val browseId: String? = null,
    val followedAt: Long = System.currentTimeMillis()
)

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY updatedAt DESC")
    fun getDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean): Int
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET coverUrl = :coverUrl WHERE id = :playlistId")
    suspend fun updatePlaylistCover(playlistId: String, coverUrl: String)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: String, name: String)

    @Query("SELECT * FROM playlists ORDER BY orderIndex ASC, createdAt ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("UPDATE playlists SET orderIndex = :orderIndex WHERE id = :playlistId")
    suspend fun updatePlaylistOrder(playlistId: String, orderIndex: Int)

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackToPlaylist(entry: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Int

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.orderIndex ASC
    """)
    fun getTracksForPlaylist(playlistId: String): Flow<List<TrackEntity>>
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: HistoryEntity)

    @Query("""
        SELECT t.*, h.lastPositionMs, h.playedAt 
        FROM history h
        INNER JOIN tracks t ON h.trackId = t.id
        ORDER BY h.playedAt DESC LIMIT 50
    """)
    fun getRecentHistory(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 1")
    suspend fun getLastPlayed(): HistoryEntity?

    @Query("SELECT * FROM history WHERE trackId = :trackId")
    suspend fun getHistoryEntry(trackId: String): HistoryEntity?

    @Query("""
        SELECT t.*, h.lastPositionMs, h.playedAt 
        FROM history h
        INNER JOIN tracks t ON h.trackId = t.id
        ORDER BY h.playCount DESC LIMIT :limit
    """)
    fun getTopPlayedTracks(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT SUM(playCount * 5000) FROM history")
    fun getTotalListeningTimeMs(): Flow<Long?>
}

@Dao
interface FollowedArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun followArtist(artist: FollowedArtistEntity)

    @Query("DELETE FROM followed_artists WHERE id = :artistId")
    suspend fun unfollowArtist(artistId: String)

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun getFollowedArtists(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT COUNT(*) FROM followed_artists WHERE id = :artistId")
    suspend fun isFollowing(artistId: String): Int
}

@Database(
    entities = [
        TrackEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        FollowedArtistEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun followedArtistDao(): FollowedArtistDao
}
