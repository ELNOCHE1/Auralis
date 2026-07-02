package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.data.local.MusicDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.local.SongEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UserStats(
    val totalSongsPlayed: Int = 0,
    val totalMinutes: Int = 0,
    val favoriteGenre: String = "Ninguno"
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository
    private var playbackJob: Job? = null
    private var mediaPlayer: android.media.MediaPlayer? = null

    // Theme override state
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null) // null = system, true = dark, false = light
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    // Query states
    val searchQuery = MutableStateFlow("")

    init {
        val database = MusicDatabase.getDatabase(application)
        repository = MusicRepository(database.musicDao())

        viewModelScope.launch {
            repository.clearMockSongs()
            repository.seedDatabaseIfEmpty()
        }
    }

    // Streams of data
    val songs: StateFlow<List<SongEntity>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingSongs: StateFlow<List<SongEntity>> = repository.trendingSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recommendedSongs: StateFlow<List<SongEntity>> = repository.recommendedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<SongEntity>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsWithSongs = repository.playlistsWithSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search results combining query + database
    val searchResults: StateFlow<List<SongEntity>> = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allSongs
            } else {
                repository.searchSongs(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player States
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0) // in seconds
    val playbackProgress: StateFlow<Int> = _playbackProgress.asStateFlow()

    // Equalizer
    private val _bassLevel = MutableStateFlow(50f) // 0-100
    val bassLevel: StateFlow<Float> = _bassLevel.asStateFlow()

    private val _trebleLevel = MutableStateFlow(50f) // 0-100
    val trebleLevel: StateFlow<Float> = _trebleLevel.asStateFlow()

    // Playback notifications simulation trigger
    private val _notificationMessage = MutableStateFlow<String?>(null)
    val notificationMessage: StateFlow<String?> = _notificationMessage.asStateFlow()

    // Toast event stream
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Listener statistics
    val userStats: StateFlow<UserStats> = repository.allListeningStats
        .combine(songs) { stats, allSongs ->
            if (stats.isEmpty() || allSongs.isEmpty()) {
                UserStats(0, 0, "Ninguno")
            } else {
                val songMap = allSongs.associateBy { it.id }
                var totalPlays = 0
                var totalSecs = 0
                val genreCounts = mutableMapOf<String, Int>()

                stats.forEach { stat ->
                    totalPlays += stat.playCount
                    totalSecs += stat.listenSeconds
                    val song = songMap[stat.songId]
                    if (song != null) {
                        genreCounts[song.genre] = (genreCounts[song.genre] ?: 0) + stat.playCount
                    }
                }

                val favGenre = genreCounts.maxByOrNull { it.value }?.key ?: "Ninguno"
                UserStats(
                    totalSongsPlayed = totalPlays,
                    totalMinutes = totalSecs / 60,
                    favoriteGenre = favGenre
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserStats())

    // --- CONTROLLER ACTIONS ---

    fun toggleTheme() {
        _isDarkTheme.value = when (_isDarkTheme.value) {
            true -> false
            false -> true
            else -> false // default system to false/light override
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }

    fun selectSong(song: SongEntity) {
        releaseMediaPlayer()
        _currentSong.value = song
        _playbackProgress.value = 0
        _isPlaying.value = true
        _notificationMessage.value = "Sonando ahora: ${song.title} - ${song.artist}"
        
        // Save statistic
        viewModelScope.launch {
            repository.recordPlayback(song.id, 5) // Record 5 seconds listening unit initially
        }

        if (song.audioUrl != null) {
            try {
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(getApplication<Application>(), android.net.Uri.parse(song.audioUrl))
                    prepare()
                    start()
                    setOnCompletionListener {
                        nextSong()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch {
                    _toastMessage.emit("Error al reproducir el archivo local: ${e.localizedMessage}")
                }
                releaseMediaPlayer()
            }
        }
        
        startTimer()
    }

    fun togglePlayPause() {
        val song = _currentSong.value ?: return
        val nextPlaying = !_isPlaying.value
        _isPlaying.value = nextPlaying
        
        if (nextPlaying) {
            _notificationMessage.value = "Reanudado: ${song.title}"
            if (song.audioUrl != null && mediaPlayer == null) {
                try {
                    mediaPlayer = android.media.MediaPlayer().apply {
                        setDataSource(getApplication<Application>(), android.net.Uri.parse(song.audioUrl))
                        prepare()
                        seekTo(_playbackProgress.value * 1000)
                        start()
                        setOnCompletionListener {
                            nextSong()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    mediaPlayer?.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            startTimer()
        } else {
            _notificationMessage.value = "Pausado: ${song.title}"
            try {
                mediaPlayer?.pause()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stopTimer()
        }
    }

    fun nextSong() {
        val allList = songs.value
        if (allList.isEmpty()) return
        val current = _currentSong.value
        val nextIndex = if (current == null) {
            0
        } else {
            val idx = allList.indexOfFirst { it.id == current.id }
            if (idx == -1 || idx == allList.size - 1) 0 else idx + 1
        }
        selectSong(allList[nextIndex])
    }

    fun prevSong() {
        val allList = songs.value
        if (allList.isEmpty()) return
        val current = _currentSong.value
        val prevIndex = if (current == null) {
            0
        } else {
            val idx = allList.indexOfFirst { it.id == current.id }
            if (idx == -1 || idx == 0) allList.size - 1 else idx - 1
        }
        selectSong(allList[prevIndex])
    }

    fun seekTo(seconds: Int) {
        val current = _currentSong.value ?: return
        val targetSeconds = seconds.coerceIn(0, current.durationSeconds)
        _playbackProgress.value = targetSeconds
        try {
            mediaPlayer?.seekTo(targetSeconds * 1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            val willBeFavorite = !song.isFavorite
            repository.toggleFavorite(song.id, song.isFavorite)
            val msg = if (willBeFavorite) {
                "Añadido a favoritos: ${song.title}"
            } else {
                "Eliminado de favoritos: ${song.title}"
            }
            _toastMessage.emit(msg)
            _notificationMessage.value = msg
        }
    }

    // Equalizer
    fun setBass(value: Float) {
        _bassLevel.value = value
    }

    fun setTreble(value: Float) {
        _trebleLevel.value = value
    }

    // Playlist Operations
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
            val msg = "Playlist creada: $name"
            _toastMessage.emit(msg)
            _notificationMessage.value = msg
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            val playlist = playlists.value.find { it.id == playlistId }
            repository.deletePlaylist(playlistId)
            val msg = if (playlist != null) "Playlist eliminada: ${playlist.name}" else "Playlist eliminada"
            _toastMessage.emit(msg)
            _notificationMessage.value = msg
        }
    }

    fun addSongToPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            val playlist = playlists.value.find { it.id == playlistId }
            val song = songs.value.find { it.id == songId }
            val msg = if (playlist != null && song != null) {
                "Añadido '${song.title}' a la playlist '${playlist.name}'"
            } else {
                "Canción añadida a la playlist"
            }
            _toastMessage.emit(msg)
            _notificationMessage.value = msg
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            val playlist = playlists.value.find { it.id == playlistId }
            val song = songs.value.find { it.id == songId }
            val msg = if (playlist != null && song != null) {
                "Eliminado '${song.title}' de la playlist '${playlist.name}'"
            } else {
                "Canción eliminada de la playlist"
            }
            _toastMessage.emit(msg)
            _notificationMessage.value = msg
        }
    }

    fun clearNotification() {
        _notificationMessage.value = null
    }

    // Private helper timers
    private fun startTimer() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val song = _currentSong.value ?: return@launch
            val isReal = song.audioUrl != null && mediaPlayer != null
            while (_isPlaying.value) {
                delay(1000)
                val current = _currentSong.value ?: break
                if (isReal) {
                    val pos = try {
                        (mediaPlayer?.currentPosition ?: 0) / 1000
                    } catch (e: Exception) {
                        _playbackProgress.value
                    }
                    _playbackProgress.value = pos.coerceIn(0, current.durationSeconds)
                } else {
                    val progress = _playbackProgress.value
                    if (progress >= current.durationSeconds) {
                        // Record completed play
                        repository.recordPlayback(current.id, 10) // additional stats weights
                        nextSong()
                        break
                    } else {
                        _playbackProgress.value = progress + 1
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        playbackJob?.cancel()
        playbackJob = null
    }

    fun importLocalSong(uriString: String, title: String, artist: String, duration: Int) {
        viewModelScope.launch {
            val localSong = SongEntity(
                id = uriString,
                title = title,
                artist = artist,
                album = "Archivo Local",
                durationSeconds = duration,
                artworkUrl = "local_imported",
                lyrics = "[Canción local - Sin letra disponible]",
                genre = "Local",
                isTrending = false,
                isRecommended = true,
                isFavorite = false,
                audioUrl = uriString
            )
            repository.addSong(localSong)
            _toastMessage.emit("Canción importada con éxito: $title")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        releaseMediaPlayer()
    }
}
