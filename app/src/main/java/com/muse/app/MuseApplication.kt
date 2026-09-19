package com.muse.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.muse.app.cast.CastManager
import com.muse.app.data.local.AppDatabase
import com.muse.app.data.local.download.OfflineManager
import com.muse.app.data.remote.FirestoreSyncService
import com.muse.app.data.remote.YoutubeApiService
import com.muse.app.data.repository.MusicRepository
import com.muse.app.player.PlayerManager
import com.muse.app.player.NewPipeStreamExtractor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MuseApplication : Application(), coil.ImageLoaderFactory {

    lateinit var database: AppDatabase
        private set

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var playerManager: PlayerManager
        private set

    lateinit var syncService: FirestoreSyncService
        private set

    // User-Agent browser-like usato sia per Retrofit che per Coil (immagini)
    // YouTube blocca le richieste prive di User-Agent con un 403 silenzioso
    private val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

    // -----------------------------------------------------------------------
    // Coil SingletonImageLoader.Factory
    // Coil chiama questo metodo automaticamente quando accede al singleton.
    // Configura un OkHttpClient con User-Agent e cookie per evitare 403 YouTube.
    // -----------------------------------------------------------------------
    override fun newImageLoader(): ImageLoader {
        val imageOkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    // GDPR consent cookie per evitare redirect ad una pagina di consenso
                    .header(
                        "Cookie",
                        "SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJpdCAD"
                    )
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // 20% della RAM disponibile
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB
                    .build()
            }
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Inizializza NewPipeExtractor per l'estrazione URL stream YouTube
        NewPipeStreamExtractor.init(applicationContext)

        // Inizializza Google Cast SDK
        CastManager.init(applicationContext)

        // Room Local Database
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN viewsText TEXT")
            }
        }
        
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "muse_music.db"
        ).addMigrations(MIGRATION_5_6, MIGRATION_6_7)
         .fallbackToDestructiveMigration().build()

        // OkHttpClient & Retrofit for YouTube Data API
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val youtubeApi = retrofit.create(YoutubeApiService::class.java)

        // Firebase Sync Service
        syncService = FirestoreSyncService()

        // Offline download manager
        val offlineManager = OfflineManager(applicationContext, database)
        offlineManager.restoreFailedDownloads() // Ripristina download interrotti al riavvio

        // Repository
        musicRepository = MusicRepository(
            youtubeApi = youtubeApi,
            trackDao = database.trackDao(),
            historyDao = database.historyDao(),
            playlistDao = database.playlistDao(),
            followedArtistDao = database.followedArtistDao(),
            syncService = syncService,
            offlineManager = offlineManager
        )

        // Player Manager
        playerManager = PlayerManager(this, musicRepository)
    }
}
