package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.data.local.MusicDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.local.SongEntity
import com.example.data.local.ListeningHistoryEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class UserStats(
    val totalSongsPlayed: Int = 0,
    val totalMinutes: Int = 0,
    val favoriteGenre: String = "Ninguno"
)

data class StreakData(
    val currentStreak: Int = 0,
    val maxStreak: Int = 0
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean,
    val progress: Int,
    val maxProgress: Int,
    val category: String
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

    // Achievement SharedPreferences & Custom tracking flows
    private val prefs = application.getSharedPreferences("auralis_achievements_prefs", android.content.Context.MODE_PRIVATE)

    private val _sharesCount = MutableStateFlow(prefs.getInt("shares_count", 0))
    val sharesCount = _sharesCount.asStateFlow()

    private val _equalizerModified = MutableStateFlow(prefs.getBoolean("equalizer_modified", false))
    val equalizerModified = _equalizerModified.asStateFlow()

    private val _lyricsViewed = MutableStateFlow(prefs.getBoolean("lyrics_viewed", false))
    val lyricsViewed = _lyricsViewed.asStateFlow()

    private val _unlockedAchievementEvent = MutableSharedFlow<Achievement>()
    val unlockedAchievementEvent = _unlockedAchievementEvent.asSharedFlow()

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

    val listeningStreak: StateFlow<StreakData> = repository.allListeningHistory
        .map { history ->
            if (history.isEmpty()) return@map StreakData(0, 0)
            
            val dateStrings = history.map { it.dateString }.toSet()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            
            val today = Calendar.getInstance()
            val todayStr = sdf.format(today.time)
            
            val yesterday = Calendar.getInstance()
            yesterday.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = sdf.format(yesterday.time)
            
            val hasToday = dateStrings.contains(todayStr)
            val hasYesterday = dateStrings.contains(yesterdayStr)
            
            val currentStreak = if (!hasToday && !hasYesterday) {
                0
            } else {
                var streak = 0
                val checkCal = Calendar.getInstance()
                if (!hasToday && hasYesterday) {
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                }
                
                while (true) {
                    val dateStr = sdf.format(checkCal.time)
                    if (dateStrings.contains(dateStr)) {
                        streak++
                        checkCal.add(Calendar.DAY_OF_YEAR, -1)
                    } else {
                        break
                    }
                }
                streak
            }
            
            // Calculate max streak
            val sortedDates = dateStrings.mapNotNull { 
                try { sdf.parse(it) } catch (e: Exception) { null }
            }.sorted()
            
            var maxStreak = 0
            if (sortedDates.isNotEmpty()) {
                var tempStreak = 1
                maxStreak = 1
                for (i in 1 until sortedDates.size) {
                    val diffMs = sortedDates[i].time - sortedDates[i - 1].time
                    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
                    if (diffDays == 1L) {
                        tempStreak++
                        if (tempStreak > maxStreak) {
                            maxStreak = tempStreak
                        }
                    } else if (diffDays > 1L) {
                        tempStreak = 1
                    }
                }
            }
            
            StreakData(currentStreak, maxStreak)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakData(0, 0))

    private val mainStatsFlow = combine(userStats, listeningStreak) { stats, streak ->
        Pair(stats, streak)
    }

    private val databaseElementsFlow = combine(songs, favoriteSongs, playlists) { allSongs, favorites, allPlaylists ->
        Triple(allSongs, favorites, allPlaylists)
    }

    private val customAchievementsFlow = combine(sharesCount, equalizerModified, lyricsViewed) { shares, eqMod, lrcViewed ->
        Triple(shares, eqMod, lrcViewed)
    }

    val achievements: StateFlow<List<Achievement>> = combine(
        mainStatsFlow,
        databaseElementsFlow,
        customAchievementsFlow
    ) { statsAndStreak, dbElements, customData ->
        val (stats, streak) = statsAndStreak
        val (allSongs, favorites, allPlaylists) = dbElements
        val (shares, eqMod, lrcViewed) = customData
        
        listOf(
            Achievement(
                id = "first_play",
                title = "Melómano Novato",
                description = "Escuchá tu primera canción",
                iconName = "music",
                isUnlocked = stats.totalSongsPlayed >= 1,
                progress = stats.totalSongsPlayed.coerceAtMost(1),
                maxProgress = 1,
                category = "Escucha"
            ),
            Achievement(
                id = "collect_5",
                title = "Coleccionista",
                description = "Importá 5 canciones locales",
                iconName = "library",
                isUnlocked = allSongs.size >= 5,
                progress = allSongs.size.coerceAtMost(5),
                maxProgress = 5,
                category = "Biblioteca"
            ),
            Achievement(
                id = "first_favorite",
                title = "Crítico Musical",
                description = "Marcá una canción como favorita",
                iconName = "favorite",
                isUnlocked = favorites.isNotEmpty(),
                progress = if (favorites.isNotEmpty()) 1 else 0,
                maxProgress = 1,
                category = "Favoritos"
            ),
            Achievement(
                id = "first_playlist",
                title = "Creador",
                description = "Creá tu primera playlist",
                iconName = "playlist",
                isUnlocked = allPlaylists.isNotEmpty(),
                progress = if (allPlaylists.isNotEmpty()) 1 else 0,
                maxProgress = 1,
                category = "Playlists"
            ),
            Achievement(
                id = "streak_3",
                title = "Oyente Constante",
                description = "Racha de escucha de 3 días seguidos",
                iconName = "streak3",
                isUnlocked = streak.currentStreak >= 3,
                progress = streak.currentStreak.coerceAtMost(3),
                maxProgress = 3,
                category = "Rachas"
            ),
            Achievement(
                id = "streak_7",
                title = "Racha de Acero",
                description = "Racha de escucha de 7 días seguidos",
                iconName = "streak7",
                isUnlocked = streak.currentStreak >= 7,
                progress = streak.currentStreak.coerceAtMost(7),
                maxProgress = 7,
                category = "Rachas"
            ),
            Achievement(
                id = "marathon",
                title = "Maratón Musical",
                description = "Escuchá música por un total de 30 minutos",
                iconName = "marathon",
                isUnlocked = stats.totalMinutes >= 30,
                progress = stats.totalMinutes.coerceAtMost(30),
                maxProgress = 30,
                category = "Tiempo"
            ),
            Achievement(
                id = "explorer_playlists",
                title = "Explorador",
                description = "Creá 3 playlists personalizadas",
                iconName = "explorer",
                isUnlocked = allPlaylists.size >= 3,
                progress = allPlaylists.size.coerceAtMost(3),
                maxProgress = 3,
                category = "Playlists"
            ),
            Achievement(
                id = "elite_listener",
                title = "Melómano Centenario",
                description = "Escuchá un total de 100 canciones",
                iconName = "elite",
                isUnlocked = stats.totalSongsPlayed >= 100,
                progress = stats.totalSongsPlayed.coerceAtMost(100),
                maxProgress = 100,
                category = "Escucha"
            ),
            Achievement(
                id = "lyrics_view",
                title = "Amante de la Letra",
                description = "Visualizá la letra de una canción en el reproductor",
                iconName = "lyrics",
                isUnlocked = lrcViewed,
                progress = if (lrcViewed) 1 else 0,
                maxProgress = 1,
                category = "Funciones"
            ),
            Achievement(
                id = "eq_modify",
                title = "Ingeniero de Sonido",
                description = "Ajustá los graves o agudos en el ecualizador",
                iconName = "equalizer",
                isUnlocked = eqMod,
                progress = if (eqMod) 1 else 0,
                maxProgress = 1,
                category = "Funciones"
            ),
            Achievement(
                id = "social_share",
                title = "Conector Social",
                description = "Compartí una canción o playlist con tus amigos",
                iconName = "share",
                isUnlocked = shares >= 1,
                progress = shares.coerceAtMost(1),
                maxProgress = 1,
                category = "Social"
            )
        )
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            var isFirstEmission = true
            achievements.collect { list ->
                val notifiedSet = prefs.getStringSet("notified_achievements_set", emptySet()) ?: emptySet()
                val newlyUnlocked = list.filter { it.isUnlocked && !notifiedSet.contains(it.id) }
                
                if (isFirstEmission) {
                    val initialUnlockedIds = list.filter { it.isUnlocked }.map { it.id }.toSet()
                    if (initialUnlockedIds.isNotEmpty()) {
                        val merged = (notifiedSet + initialUnlockedIds)
                        prefs.edit().putStringSet("notified_achievements_set", merged).apply()
                    }
                    isFirstEmission = false
                } else {
                    if (newlyUnlocked.isNotEmpty()) {
                        val updatedSet = notifiedSet.toMutableSet()
                        newlyUnlocked.forEach { achievement ->
                            if (!notifiedSet.contains(achievement.id)) {
                                updatedSet.add(achievement.id)
                                _unlockedAchievementEvent.emit(achievement)
                            }
                        }
                        prefs.edit().putStringSet("notified_achievements_set", updatedSet).apply()
                    }
                }
            }
        }
    }

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
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                repository.recordListeningHistory(todayStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        recordEqualizerModified()
    }

    fun setTreble(value: Float) {
        _trebleLevel.value = value
        recordEqualizerModified()
    }

    fun recordShare() {
        val newVal = _sharesCount.value + 1
        _sharesCount.value = newVal
        prefs.edit().putInt("shares_count", newVal).apply()
    }

    fun recordEqualizerModified() {
        if (!_equalizerModified.value) {
            _equalizerModified.value = true
            prefs.edit().putBoolean("equalizer_modified", true).apply()
        }
    }

    fun recordLyricsViewed() {
        if (!_lyricsViewed.value) {
            _lyricsViewed.value = true
            prefs.edit().putBoolean("lyrics_viewed", true).apply()
        }
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

    fun importLocalSong(
        uriString: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        artworkUrl: String,
        lyrics: String,
        duration: Int
    ) {
        viewModelScope.launch {
            val localSong = SongEntity(
                id = uriString,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = duration,
                artworkUrl = artworkUrl,
                lyrics = lyrics,
                genre = genre,
                isTrending = false,
                isRecommended = true,
                isFavorite = false,
                audioUrl = uriString
            )
            repository.addSong(localSong)
            _toastMessage.emit("Canción importada con éxito: $title")
        }
    }

    fun updateSongDetails(
        songId: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        artworkUrl: String,
        lyrics: String
    ) {
        viewModelScope.launch {
            val existingSong = songs.value.find { it.id == songId }
            val updatedSong = SongEntity(
                id = songId,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = existingSong?.durationSeconds ?: 180,
                artworkUrl = artworkUrl,
                lyrics = lyrics,
                genre = genre,
                isTrending = existingSong?.isTrending ?: false,
                isRecommended = existingSong?.isRecommended ?: true,
                isFavorite = existingSong?.isFavorite ?: false,
                audioUrl = existingSong?.audioUrl ?: songId
            )
            repository.addSong(updatedSong)
            if (_currentSong.value?.id == songId) {
                _currentSong.value = updatedSong
            }
            _toastMessage.emit("Canción actualizada: $title")
        }
    }

    // --- Authentication State ---
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userCustomName = MutableStateFlow("")
    val userCustomName: StateFlow<String> = _userCustomName.asStateFlow()

    private val _userEmailOrPhone = MutableStateFlow("")
    val userEmailOrPhone: StateFlow<String> = _userEmailOrPhone.asStateFlow()

    private val _loginMethod = MutableStateFlow("") // "Gmail" or "Phone"
    val loginMethod: StateFlow<String> = _loginMethod.asStateFlow()

    private val _showLoginRequiredDialog = MutableStateFlow(false)
    val showLoginRequiredDialog: StateFlow<Boolean> = _showLoginRequiredDialog.asStateFlow()

    private var onLoginSuccessAction: (() -> Unit)? = null

    fun showLoginRequired(onSuccess: () -> Unit) {
        onLoginSuccessAction = onSuccess
        _showLoginRequiredDialog.value = true
    }

    fun dismissLoginRequired() {
        _showLoginRequiredDialog.value = false
        onLoginSuccessAction = null
    }

    fun login(customName: String, emailOrPhone: String, method: String) {
        _userCustomName.value = customName
        _userEmailOrPhone.value = emailOrPhone
        _loginMethod.value = method
        _isLoggedIn.value = true
        _showLoginRequiredDialog.value = false
        viewModelScope.launch {
            _toastMessage.emit("¡Bienvenido, $customName!")
        }
        onLoginSuccessAction?.invoke()
        onLoginSuccessAction = null
    }

    fun logout() {
        val prevName = _userCustomName.value
        _userCustomName.value = ""
        _userEmailOrPhone.value = ""
        _loginMethod.value = ""
        _isLoggedIn.value = false
        viewModelScope.launch {
            _toastMessage.emit("Sesión cerrada. ¡Hasta pronto, $prevName!")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        releaseMediaPlayer()
    }
}
