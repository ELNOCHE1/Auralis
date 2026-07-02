package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistWithSongs
import com.example.data.local.SongEntity
import com.example.ui.viewmodel.MusicPlayerViewModel

@Composable
fun PlaylistsScreen(
    viewModel: MusicPlayerViewModel,
    onSongSelect: (SongEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlistsWithSongs by viewModel.playlistsWithSongs.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    var selectedPlaylistForDetail by remember { mutableStateOf<PlaylistWithSongs?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (playlistsWithSongs.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "No Playlists",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.empty_playlists),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("create_playlist_empty_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.create_playlist_dialog_title))
                }
            }
        } else {
            // Playlists List
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Mis Playlists",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .testTag("create_playlist_header_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Crear",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    items(playlistsWithSongs) { playlistWithSongs ->
                        PlaylistCard(
                            playlistWithSongs = playlistWithSongs,
                            onClick = { selectedPlaylistForDetail = playlistWithSongs },
                            onDeleteClick = { viewModel.deletePlaylist(playlistWithSongs.playlist.id) }
                        )
                    }
                }
            }
        }

        // --- CREATE DIALOG ---
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text(text = stringResource(R.string.create_playlist_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        placeholder = { Text(text = stringResource(R.string.playlist_name_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlist_name_input")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (playlistNameInput.isNotBlank()) {
                                viewModel.createPlaylist(playlistNameInput)
                                playlistNameInput = ""
                                showCreateDialog = false
                            }
                        },
                        modifier = Modifier.testTag("playlist_confirm_create_btn")
                    ) {
                        Text(text = stringResource(R.string.btn_create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(text = stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        // --- PLAYLIST DETAILS DIALOG ---
        selectedPlaylistForDetail?.let { currentDetail ->
            // Re-fetch detail dynamically from the active list to reflect real-time deletes
            val dynamicDetail = playlistsWithSongs.firstOrNull { it.playlist.id == currentDetail.playlist.id }
            if (dynamicDetail == null) {
                selectedPlaylistForDetail = null
            } else {
                PlaylistSongsDialog(
                    playlistWithSongs = dynamicDetail,
                    onDismiss = { selectedPlaylistForDetail = null },
                    onSongPlay = { song ->
                        onSongSelect(song)
                        selectedPlaylistForDetail = null
                    },
                    onRemoveSong = { songId ->
                        viewModel.removeSongFromPlaylist(dynamicDetail.playlist.id, songId)
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlistWithSongs: PlaylistWithSongs,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("playlist_card_${playlistWithSongs.playlist.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            // Playlist Art Simulation
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (playlistWithSongs.songs.isNotEmpty()) {
                            getArtGradient(playlistWithSongs.songs.first().artworkUrl)
                        } else {
                            getArtGradient("default")
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlistWithSongs.playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                val count = playlistWithSongs.songs.size
                Text(
                    text = if (count == 1) "1 canción" else "$count canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            IconButton(
                onClick = {
                    val songListText = playlistWithSongs.songs.mapIndexed { index, song ->
                        "${index + 1}. ${song.title} - ${song.artist}"
                    }.joinToString("\n")
                    
                    val shareText = "🎶 ¡Mira mi playlist en Auralis! 🎶\n" +
                            "Nombre: ${playlistWithSongs.playlist.name}\n" +
                            "Canciones (${playlistWithSongs.songs.size}):\n" +
                            (if (songListText.isEmpty()) "[Sin canciones aún]" else songListText) +
                            "\n\n¡Creado con Auralis!"
                    
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Playlist compartida: ${playlistWithSongs.playlist.name}")
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir Playlist"))
                },
                modifier = Modifier.testTag("share_playlist_${playlistWithSongs.playlist.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Compartir playlist",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("delete_playlist_${playlistWithSongs.playlist.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun PlaylistSongsDialog(
    playlistWithSongs: PlaylistWithSongs,
    onDismiss: () -> Unit,
    onSongPlay: (SongEntity) -> Unit,
    onRemoveSong: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .testTag("playlist_songs_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlistWithSongs.playlist.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Canciones guardadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            val songListText = playlistWithSongs.songs.mapIndexed { index, song ->
                                "${index + 1}. ${song.title} - ${song.artist}"
                            }.joinToString("\n")
                            
                            val shareText = "🎶 ¡Mira mi playlist en Auralis! 🎶\n" +
                                    "Nombre: ${playlistWithSongs.playlist.name}\n" +
                                    "Canciones (${playlistWithSongs.songs.size}):\n" +
                                    (if (songListText.isEmpty()) "[Sin canciones aún]" else songListText) +
                                    "\n\n¡Creado con Auralis!"
                            
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Playlist compartida: ${playlistWithSongs.playlist.name}")
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir Playlist"))
                        },
                        modifier = Modifier.testTag("dialog_share_playlist_${playlistWithSongs.playlist.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Song List
                if (playlistWithSongs.songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no agregaste canciones a esta playlist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(playlistWithSongs.songs) { song ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSongPlay(song) }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val shareIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Compartiendo canción en Auralis")
                                            putExtra(android.content.Intent.EXTRA_TEXT, "🎵 ¡Escuchá esta canción en Auralis! 🎵\nTítulo: ${song.title}\nArtista: ${song.artist}\nÁlbum: ${song.album}")
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir canción"))
                                    },
                                    modifier = Modifier.testTag("share_song_from_dialog_${song.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Compartir canción",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveSong(song.id) },
                                    modifier = Modifier.testTag("remove_song_${song.id}_from_playlist")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RemoveCircle,
                                        contentDescription = "Quitar",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
