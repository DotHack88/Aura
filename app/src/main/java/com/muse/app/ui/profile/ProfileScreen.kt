package com.muse.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import com.google.firebase.auth.FirebaseAuth
import com.muse.app.data.remote.FirestoreSyncService
import com.muse.app.ui.components.MuseThumbnail
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    syncService: FirestoreSyncService,
    musicRepository: com.muse.app.data.repository.MusicRepository,
    onNavigateToStats: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var syncInfo by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val topPlayedTracks by musicRepository.getTopPlayedTracks(10).collectAsState(initial = emptyList<com.muse.app.domain.model.Track>())

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            try {
                // Esegue subito il pull al login/avvio
                musicRepository.syncBidirectional()
                
                val lastPlayback = syncService.getLastPlayback()
                if (lastPlayback != null) {
                    val title = lastPlayback["title"] as? String ?: ""
                    val pos = (lastPlayback["positionMs"] as? Long) ?: 0L
                    syncInfo = "Sincronizzato e pronto. Ultimo brano: \"$title\""
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Profilo & Sincronizzazione",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (currentUser != null) {
            // Profilo Utente Connesso
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentUser?.email ?: "Utente Muse",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = Color.Green,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sincronizzazione Cloud Attiva",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Green)
                )
            }

            syncInfo?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }


            var isSyncing by remember { mutableStateOf(false) }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToStats,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Le mie Statistiche (Aura Stats)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        isSyncing = true
                        try {
                            musicRepository.syncBidirectional()
                            syncInfo = "Sincronizzazione completata (Preferiti, Playlist, Statistiche)."
                        } catch (e: Exception) {
                            syncInfo = "Errore durante la sincronizzazione."
                        } finally {
                            isSyncing = false
                        }
                    }
                },
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSyncing) "Sincronizzazione..." else "Sincronizza Ora")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    auth.signOut()
                    currentUser = null
                    syncInfo = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disconnetti")
            }

        } else {
            // Form di Accesso / Registrazione
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRegisterMode) "Crea un Account Muse" else "Accedi a Muse",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (email.isNotBlank() && password.isNotBlank()) {
                                errorMessage = null
                                if (isRegisterMode) {
                                    auth.createUserWithEmailAndPassword(email.trim(), password)
                                        .addOnSuccessListener {
                                            currentUser = auth.currentUser
                                        }
                                        .addOnFailureListener {
                                            errorMessage = it.localizedMessage ?: "Errore nella registrazione"
                                        }
                                } else {
                                    auth.signInWithEmailAndPassword(email.trim(), password)
                                        .addOnSuccessListener {
                                            currentUser = auth.currentUser
                                        }
                                        .addOnFailureListener {
                                            errorMessage = it.localizedMessage ?: "Errore nell'accesso"
                                        }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRegisterMode) "Registrati" else "Accedi")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
                        Text(
                            if (isRegisterMode) "Hai già un account? Accedi"
                            else "Non hai un account? Registrati"
                        )
                    }
                }
            }
        }

        // Sezione Statistiche di Ascolto
        if (topPlayedTracks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "I tuoi brani più ascoltati",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            topPlayedTracks.forEachIndexed { index, track: com.muse.app.domain.model.Track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(24.dp)
                    )
                    MuseThumbnail(
                        url = track.thumbnailUrl,
                        contentDescription = track.title,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
