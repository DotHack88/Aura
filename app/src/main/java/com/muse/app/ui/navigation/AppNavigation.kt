package com.muse.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.muse.app.ui.components.rememberDominantColor
import com.muse.app.ui.components.rememberAccentColor
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.muse.app.data.remote.FirestoreSyncService
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.model.PlayerMode
import com.muse.app.player.PlayerManager
import com.muse.app.ui.components.MiniPlayer
import com.muse.app.ui.home.HomeScreen
import com.muse.app.ui.home.NewReleasesScreen
import com.muse.app.ui.home.MoodsAndGenresScreen
import com.muse.app.ui.library.LibraryScreen
import com.muse.app.ui.lyrics.LyricsScreen
import com.muse.app.ui.player.FullPlayerScreen
import com.muse.app.ui.profile.ProfileScreen
import com.muse.app.ui.album.AlbumScreen
import com.muse.app.ui.artist.ArtistScreen
import com.muse.app.ui.search.SearchScreen
import com.muse.app.ui.library.PlaylistScreen
import com.muse.app.ui.profile.StatsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Search : Screen("search", "Cerca", Icons.Default.Search)
    object Library : Screen("library", "Libreria", Icons.Default.LibraryMusic)
    object Profile : Screen("profile", "Profilo", Icons.Default.Person)
}

@Composable
fun AppNavigation(
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    syncService: FirestoreSyncService
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playerState by playerManager.playerState.collectAsState()
    var isFullPlayerVisible by remember { mutableStateOf(false) }
    var isLyricsVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            playerManager.clearError()
        }
    }

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Search,
        Screen.Library,
        Screen.Profile
    )

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // Colori dinamici dalla copertina del brano in riproduzione
                val currentTrackUrl = playerState.currentTrack?.thumbnailUrl
                val rawDominant = rememberDominantColor(currentTrackUrl)
                val rawAccent  = rememberAccentColor(currentTrackUrl)

                val navDominant by animateColorAsState(
                    targetValue = if (rawDominant == Color.Transparent || playerState.currentTrack == null)
                        MaterialTheme.colorScheme.surface
                    else
                        rawDominant.copy(alpha = 0.55f),
                    animationSpec = tween(700),
                    label = "navBg"
                )
                val navAccent by animateColorAsState(
                    targetValue = if (rawAccent == Color.Transparent || playerState.currentTrack == null)
                        MaterialTheme.colorScheme.primary
                    else
                        rawAccent,
                    animationSpec = tween(700),
                    label = "navAccent"
                )

                if (currentRoute?.startsWith("album/") == false &&
                    currentRoute?.startsWith("artist/") == false &&
                    currentRoute?.startsWith("playlist/") == false &&
                    currentRoute?.startsWith("player") == false
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        modifier = Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x66000000), // 40% nero
                                    Color(0xD9000000), // 85% nero
                                    Color(0xFF000000)  // 100% nero
                                )
                            )
                        )
                    ) {
                        bottomNavItems.forEach { screen ->
                            val isSelected = currentRoute == screen.route || 
                                currentRoute?.startsWith(screen.route + "?") == true ||
                                (screen.route == Screen.Search.route && (currentRoute == "moods_and_genres" || currentRoute == "new_releases"))

                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        // Se è già selezionata, cliccarla di nuovo riporta alla radice della tab
                                        navController.navigate(screen.route) {
                                            popUpTo(screen.route) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Home.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = navAccent,
                                    selectedTextColor = navAccent,
                                    indicatorColor = navAccent.copy(alpha = 0.15f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            // Calcola l'altezza della NavigationBar per posizionare il MiniPlayer sopra di essa
            val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomBarHeight = if (currentRoute?.startsWith("album/") == false &&
                currentRoute?.startsWith("artist/") == false &&
                currentRoute?.startsWith("playlist/") == false &&
                currentRoute?.startsWith("player") == false) {
                80.dp + navBarHeight // altezza NavigationBar standard + inset sistema
            } else {
                navBarHeight
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(tween(200)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(200)) }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            musicRepository = musicRepository,
                            playerManager = playerManager,
                            onOpenSearch = { navController.navigate(Screen.Search.route) },
                            onNavigateToNewReleases = { navController.navigate("new_releases") },
                            onNavigateToMoodsAndGenres = { navController.navigate("moods_and_genres") },
                            onGenreClick = { genre -> navController.navigate("${Screen.Search.route}?query=$genre") },
                            onNavigateToArtist = { browseId -> navController.navigate("artist/$browseId") },
                            onNavigateToStats = { navController.navigate("stats") },
                            onNavigateToAlbum = { browseId, title, artist, cover ->
                                navController.navigate(
                                    "album/$browseId?title=${java.net.URLEncoder.encode(title, "UTF-8")}" +
                                    "&artist=${java.net.URLEncoder.encode(artist, "UTF-8")}" +
                                    "&cover=${java.net.URLEncoder.encode(cover, "UTF-8")}"
                                )
                            }
                        )
                    }
                    composable("moods_and_genres") {
                        MoodsAndGenresScreen(
                            onBack = { navController.navigateUp() },
                            onGenreClick = { genre -> navController.navigate("${Screen.Search.route}?query=$genre") }
                        )
                    }
                    composable("new_releases") {
                        NewReleasesScreen(
                            musicRepository = musicRepository,
                            onBack = { navController.navigateUp() },
                            onNavigateToAlbum = { browseId, title, artist, cover ->
                                navController.navigate(
                                    "album/$browseId?title=${java.net.URLEncoder.encode(title, "UTF-8")}" +
                                    "&artist=${java.net.URLEncoder.encode(artist, "UTF-8")}" +
                                    "&cover=${java.net.URLEncoder.encode(cover, "UTF-8")}"
                                )
                            },
                            onPlayAlbum = { album ->
                                val track = com.muse.app.domain.model.Track(
                                    id = album.id,
                                    title = album.title,
                                    artist = album.artist,
                                    thumbnailUrl = album.coverUrl,
                                    durationMs = 240_000L
                                )
                                playerManager.playTrack(track)
                            }
                        )
                    }
                    composable(
                        route = "${Screen.Search.route}?query={query}",
                        arguments = listOf(androidx.navigation.navArgument("query") { 
                            type = androidx.navigation.NavType.StringType
                            nullable = true 
                        })
                    ) { backStackEntry ->
                        val initialQuery = backStackEntry.arguments?.getString("query")
                        SearchScreen(
                            musicRepository = musicRepository,
                            playerManager = playerManager,
                            onNavigateToArtist = { browseId -> navController.navigate("artist/$browseId") },
                            onNavigateToAlbum = { browseId, title, artist, cover ->
                                navController.navigate(
                                    "album/$browseId?title=${java.net.URLEncoder.encode(title, "UTF-8")}" +
                                    "&artist=${java.net.URLEncoder.encode(artist, "UTF-8")}" +
                                    "&cover=${java.net.URLEncoder.encode(cover, "UTF-8")}"
                                )
                            },
                            onNavigateToPlaylist = { playlistId ->
                                navController.navigate("playlist/$playlistId")
                            },
                            onNavigateToMoodsAndGenres = { navController.navigate("moods_and_genres") },
                            onNavigateToNewReleases = { navController.navigate("new_releases") },
                            initialQuery = initialQuery
                        )
                    }
                    composable(Screen.Library.route) {
                        LibraryScreen(
                            musicRepository = musicRepository,
                            playerManager = playerManager,
                            onNavigateToPlaylist = { playlistId ->
                                navController.navigate("playlist/$playlistId")
                            },
                            onNavigateToArtist = { browseId -> 
                                navController.navigate("artist/$browseId") 
                            }
                        )
                    }
                    composable("playlist/{playlistId}") { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                        PlaylistScreen(
                            playlistId = playlistId,
                            musicRepository = musicRepository,
                            playerManager = playerManager,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Profile.route) {
                        ProfileScreen(
                            syncService = syncService,
                            musicRepository = musicRepository,
                            onNavigateToStats = { navController.navigate("stats") }
                        )
                    }
                    composable("stats") {
                        StatsScreen(
                            musicRepository = musicRepository,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("artist/{browseId}") { backStackEntry ->
                        val browseId = backStackEntry.arguments?.getString("browseId") ?: ""
                        com.muse.app.ui.artist.ArtistScreen(
                            browseId = browseId,
                            musicRepository = musicRepository,
                            playerManager = playerManager,
                            onBack = { navController.popBackStack() },
                            onNavigateToArtist = { artistBrowseId ->
                                navController.navigate("artist/$artistBrowseId")
                            },
                            onNavigateToAlbum = { albumBrowseId, title, artist, cover ->
                                navController.navigate(
                                    "album/$albumBrowseId?title=${java.net.URLEncoder.encode(title, "UTF-8")}" +
                                    "&artist=${java.net.URLEncoder.encode(artist, "UTF-8")}" +
                                    "&cover=${java.net.URLEncoder.encode(cover, "UTF-8")}"
                                )
                            }
                        )
                    }
                    composable(
                        route = "album/{browseId}?title={title}&artist={artist}&cover={cover}",
                        arguments = listOf(
                            androidx.navigation.navArgument("browseId") { type = androidx.navigation.NavType.StringType },
                            androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                            androidx.navigation.navArgument("artist") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                            androidx.navigation.navArgument("cover") { type = androidx.navigation.NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val browseId = backStackEntry.arguments?.getString("browseId") ?: ""
                        val title = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
                        val artist = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("artist") ?: "", "UTF-8")
                        val cover = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("cover") ?: "", "UTF-8")
                        AlbumScreen(
                            browseId = browseId,
                            albumTitle = title,
                            albumArtist = artist,
                            albumCoverUrl = cover,
                            musicRepository = musicRepository,
                            playerManager = playerManager,
                            onBack = { navController.popBackStack() },
                            onNavigateToSearch = { query ->
                                navController.navigate("${Screen.Search.route}?query=${java.net.URLEncoder.encode(query, "UTF-8")}")
                            }
                        )
                    }
                }

                // MiniPlayer posizionato esattamente sopra la Bottom Bar
                if (playerState.currentTrack != null && !isFullPlayerVisible && !isLyricsVisible) {
                    MiniPlayer(
                        playerState = playerState,
                        onExpand = { isFullPlayerVisible = true },
                        onTogglePlay = { playerManager.togglePlayPause() },
                        onNext = { playerManager.next() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomBarHeight)
                    )
                }
            }
        }

        // Full Player a schermo intero con animazione di scorrimento
        AnimatedVisibility(
            visible = isFullPlayerVisible && !isLyricsVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            FullPlayerScreen(
                playerManager = playerManager,
                musicRepository = musicRepository,
                onNavigateToLyrics = { isLyricsVisible = true },
                onNavigateToSearch = { query ->
                    isFullPlayerVisible = false
                    navController.navigate("${Screen.Search.route}?query=${java.net.URLEncoder.encode(query, "UTF-8")}")
                },
                onNavigateToAlbum = { browseId, title, artist, cover ->
                    isFullPlayerVisible = false
                    navController.navigate(
                        "album/$browseId?title=${java.net.URLEncoder.encode(title, "UTF-8")}" +
                        "&artist=${java.net.URLEncoder.encode(artist, "UTF-8")}" +
                        "&cover=${java.net.URLEncoder.encode(cover, "UTF-8")}"
                    )
                },
                onDismiss = { isFullPlayerVisible = false }
            )
        }

        // Schermata Lyrics Sincronizzati a schermo intero
        AnimatedVisibility(
            visible = isLyricsVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            LyricsScreen(
                playerManager = playerManager,
                onDismiss = { isLyricsVisible = false }
            )
        }
    }
}
