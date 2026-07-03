package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.SongEntity
import com.example.ui.viewmodel.MusicPlayerViewModel

@Composable
fun HomeScreen(
    viewModel: MusicPlayerViewModel,
    onSongSelect: (SongEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val recommendedSongs by viewModel.recommendedSongs.collectAsState()
    val trendingSongs by viewModel.trendingSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    val context = LocalContext.current
    // Check if locale is es-AR to render Argentine greeting
    val locale = context.resources.configuration.locales[0]
    val greeting = if (locale.country == "AR") {
        "¡Hola, che! ¿Qué escuchamos hoy?"
    } else {
        "¡Hola! ¿Qué escuchamos hoy?"
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importTitle by remember { mutableStateOf("") }
    var importArtist by remember { mutableStateOf("") }
    var importAlbum by remember { mutableStateOf("") }
    var importGenre by remember { mutableStateOf("") }
    var importLyrics by remember { mutableStateOf("") }
    var importArtworkUrl by remember { mutableStateOf("bg_retro") }
    var importDuration by remember { mutableStateOf(180) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val retriever = MediaMetadataRetriever()
            var title = "Canción Local"
            var artist = "Artista Desconocido"
            var album = "Archivo Local"
            var genre = "Local"
            var duration = 180
            try {
                retriever.setDataSource(context, it)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Canción Local"
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Artista Desconocido"
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Archivo Local"
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "Local"
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durationStr != null) {
                    duration = durationStr.toInt() / 1000
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val displayNameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIdx != -1) {
                            val filename = c.getString(displayNameIdx)
                            title = filename?.substringBeforeLast(".") ?: "Canción Local"
                        }
                    }
                }
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }

            // Set states to trigger the Edit / Import dialog
            importUri = it
            importTitle = title
            importArtist = artist
            importAlbum = album
            importGenre = genre
            importDuration = duration
            importArtworkUrl = when (genre.lowercase()) {
                "retro", "synthwave" -> "bg_synthwave"
                "acoustic", "folk", "acoustic guitar" -> "bg_acoustic"
                "lofi", "chill" -> "bg_lofi"
                "rock", "metal" -> "bg_rock"
                "space", "ambient" -> "bg_space"
                else -> "bg_retro"
            }
            
            importLyrics = """
                🎵 [Intro Instrumental]
                
                [Estrofa 1]
                Escuchando esta melodía de $artist...
                El viento sopla en silencio,
                mientras suena la canción en mi rincón.
                $title es el pulso de este momento.
                
                [Coro]
                Oh, $title nos hace soñar,
                deja que el ritmo te lleve hoy.
                Bajo las estrellas vamos a cantar,
                con Auralis, directo al corazón.
                
                [Estrofa 2]
                El camino se llena de color,
                la música borra la oscuridad.
                $artist le pone todo el alma y valor,
                viviendo cada nota con libertad.
                
                [Coro]
                Oh, $title nos hace soñar,
                deja que el ritmo te lleve hoy.
                Bajo las estrellas vamos a cantar,
                con Auralis, directo al corazón.
                
                [Outro]
                Sigue sonando... $title...
                Conectando almas en Auralis Connect.
                🎵 [Fin de la canción]
            """.trimIndent()
            
            showImportDialog = true
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentPadding = PaddingValues(bottom = 120.dp) // extra padding so player doesn't hide contents
    ) {
        // Hero Header Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Greeting and Import Button in a Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    Button(
                        onClick = { launcher.launch(arrayOf("audio/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("import_local_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Cargar Audio",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Importar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Header with "Descubrí algo nuevo" and "Ver todo"
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "Descubrí algo nuevo",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Ver todo",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    )
                }

                // 16:9 Hero Card (Mix Semanal / Pulsos Urbanos)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF004A77),
                                    Color(0xFF1D2433)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    // Absolute top right circle decor
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 20.dp, y = (-20).dp)
                            .size(128.dp)
                            .border(8.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    )

                    // Texts aligned bottom left
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = "MIX SEMANAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "Pulsos Urbanos",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "Basado en lo que escuchaste ayer",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        }

        // Carousel of Recommended Songs
        item {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.title_recommended),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recommendedSongs, key = { it.id }) { song ->
                        RecommendedSongCard(
                            song = song,
                            onClick = { onSongSelect(song) },
                            isCurrent = song.id == currentSong?.id
                        )
                    }
                }
            }
        }

        // Trends Title
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = "Trending",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.title_trends),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }

        // Trends List
        items(trendingSongs, key = { it.id }) { song ->
            TrendingSongRow(
                song = song,
                onPlayClick = { onSongSelect(song) },
                onFavoriteToggle = { viewModel.toggleFavorite(song) },
                isCurrent = song.id == currentSong?.id
            )
        }
    }

    if (showImportDialog && importUri != null) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = "Detalles de Canción Local",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Verifica y edita la información de tu archivo de música local antes de guardarlo en tu biblioteca de Auralis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = importTitle,
                        onValueChange = { importTitle = it },
                        label = { Text("Título de la canción") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("import_title_field")
                    )

                    OutlinedTextField(
                        value = importArtist,
                        onValueChange = { importArtist = it },
                        label = { Text("Autor / Artista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("import_artist_field")
                    )

                    OutlinedTextField(
                        value = importAlbum,
                        onValueChange = { importAlbum = it },
                        label = { Text("Álbum") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("import_album_field")
                    )

                    OutlinedTextField(
                        value = importGenre,
                        onValueChange = { importGenre = it },
                        label = { Text("Género") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("import_genre_field")
                    )

                    Text(
                        text = "Estilo de Arte Visual:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val artworkOptions = listOf(
                        "bg_retro" to "Retro Pink",
                        "bg_synthwave" to "Synthwave",
                        "bg_acoustic" to "Acoustic",
                        "bg_lofi" to "Lofi",
                        "bg_rock" to "Rock",
                        "bg_space" to "Space"
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        artworkOptions.forEach { (key, name) ->
                            val isSelected = importArtworkUrl == key
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(getArtGradient(key))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { importArtworkUrl = key }
                                    .testTag("import_art_option_$key")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = importLyrics,
                        onValueChange = { importLyrics = it },
                        label = { Text("Letra de la canción") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("import_lyrics_field"),
                        maxLines = 15
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importLocalSong(
                            uriString = importUri.toString(),
                            title = importTitle,
                            artist = importArtist,
                            album = importAlbum,
                            genre = importGenre,
                            artworkUrl = importArtworkUrl,
                            lyrics = importLyrics,
                            duration = importDuration
                        )
                        showImportDialog = false
                    },
                    modifier = Modifier.testTag("confirm_import_btn")
                ) {
                    Text("Importar canción", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun RecommendedSongCard(
    song: SongEntity,
    onClick: () -> Unit,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick)
            .testTag("recommended_card_${song.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Fake Album Art using localized stylized gradients
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getArtGradient(song.artworkUrl)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TrendingSongRow(
    song: SongEntity,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onPlayClick)
            .padding(8.dp)
            .testTag("trend_row_${song.id}")
    ) {
        // Thumbnail Art
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(getArtGradient(song.artworkUrl)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = song.title.take(1),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Titles
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Favorite Toggle
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("favorite_toggle_${song.id}")
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper gradients to simulate distinct gorgeous album arts without remote dependencies
fun getArtGradient(artworkKey: String): Brush {
    return when (artworkKey) {
        "bg_retro" -> Brush.sweepGradient(listOf(Color(0xFFFF007F), Color(0xFF7000FF), Color(0xFFFF007F)))
        "bg_synthwave" -> Brush.verticalGradient(listOf(Color(0xFF2E0854), Color(0xFF9B00E8)))
        "bg_acoustic" -> Brush.radialGradient(listOf(Color(0xFFEAB308), Color(0xFF78350F)))
        "bg_lofi" -> Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF4F46E5)))
        "bg_rock" -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFF000000)))
        "bg_space" -> Brush.radialGradient(listOf(Color(0xFFEC4899), Color(0xFF0F172A)))
        else -> Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
    }
}
