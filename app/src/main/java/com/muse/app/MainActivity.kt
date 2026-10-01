package com.muse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.muse.app.ui.navigation.AppNavigation
import com.muse.app.ui.theme.MuseTheme
import com.muse.app.updater.UpdateManager
import com.muse.app.updater.UpdateInfo
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val app = application as MuseApplication

        setContent {
            MuseTheme {
                val context = LocalContext.current
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                
                LaunchedEffect(Unit) {
                    val manager = UpdateManager(context)
                    updateInfo = manager.checkForUpdate()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        musicRepository = app.musicRepository,
                        playerManager = app.playerManager,
                        syncService = app.syncService
                    )
                    
                    updateInfo?.let { info ->
                        AlertDialog(
                            onDismissRequest = { updateInfo = null },
                            title = { Text("Nuovo aggiornamento disponibile") },
                            text = { Text("Versione ${info.latestVersionName} è disponibile.\n\n${info.releaseNotes}") },
                            confirmButton = {
                                TextButton(onClick = {
                                    val manager = UpdateManager(context)
                                    manager.downloadAndInstallUpdate(info)
                                    updateInfo = null
                                }) {
                                    Text("Aggiorna")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateInfo = null }) {
                                    Text("Ignora")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
