package com.example.data

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MusicRepository(private val musicDao: MusicDao) {

    val allSongs: Flow<List<SongEntity>> = musicDao.getAllSongs()
    val trendingSongs: Flow<List<SongEntity>> = musicDao.getTrendingSongs()
    val recommendedSongs: Flow<List<SongEntity>> = musicDao.getRecommendedSongs()
    val favoriteSongs: Flow<List<SongEntity>> = musicDao.getFavoriteSongs()
    val allPlaylists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
    val allListeningStats: Flow<List<ListeningStatEntity>> = musicDao.getAllListeningStats()
    val playlistsWithSongs: Flow<List<PlaylistWithSongs>> = musicDao.getAllPlaylistsWithSongs()

    fun searchSongs(query: String): Flow<List<SongEntity>> {
        return musicDao.searchSongs("%$query%")
    }

    suspend fun toggleFavorite(songId: String, isCurrentlyFavorite: Boolean) {
        musicDao.updateFavoriteStatus(songId, !isCurrentlyFavorite)
    }

    suspend fun createPlaylist(name: String): Long {
        return musicDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Int) {
        musicDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: String) {
        musicDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        musicDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getPlaylistWithSongs(playlistId: Int): Flow<PlaylistWithSongs> {
        return musicDao.getPlaylistWithSongs(playlistId)
    }

    suspend fun recordPlayback(songId: String, durationSeconds: Int) {
        musicDao.incrementPlayStat(songId, durationSeconds)
    }

    suspend fun addSong(song: SongEntity) {
        musicDao.insertSongs(listOf(song))
    }

    suspend fun clearMockSongs() {
        musicDao.deleteNonLocalSongs()
    }

    suspend fun seedDatabaseIfEmpty() {
        // Do not seed default internet/background songs, only leave local music.
        // We can optionally seed standard local playlists.
    }
}
