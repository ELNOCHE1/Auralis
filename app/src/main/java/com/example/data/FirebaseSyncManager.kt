package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first

class FirebaseSyncManager(private val context: Context, private val repository: MusicRepository) {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val currentUser get() = auth.currentUser
    val currentUid: String? get() = currentUser?.uid

    /**
     * Sincroniza el perfil del usuario en Firestore.
     */
    suspend fun syncProfile(displayName: String, emailOrPhone: String, loginMethod: String) {
        val uid = currentUid ?: return
        try {
            val profile = hashMapOf(
                "uid" to uid,
                "displayName" to displayName,
                "emailOrPhone" to emailOrPhone,
                "loginMethod" to loginMethod,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("users").document(uid)
                .set(profile, SetOptions.merge())
                .await()
            Log.d("FirebaseSync", "Perfil de usuario sincronizado correctamente para UID: $uid")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error al sincronizar el perfil", e)
        }
    }

    /**
     * Sube las playlists locales actuales a Firestore.
     */
    suspend fun pushPlaylistsToFirestore() {
        val uid = currentUid ?: return
        try {
            val playlistsWithSongs = repository.playlistsWithSongs.first()
            val playlistsList = playlistsWithSongs.map { pWithS ->
                val songsList = pWithS.songs.map { song ->
                    hashMapOf(
                        "id" to song.id,
                        "title" to song.title,
                        "artist" to song.artist,
                        "album" to song.album,
                        "durationSeconds" to song.durationSeconds,
                        "artworkUrl" to song.artworkUrl,
                        "lyrics" to song.lyrics,
                        "genre" to song.genre
                    )
                }
                hashMapOf(
                    "id" to pWithS.playlist.id,
                    "name" to pWithS.playlist.name,
                    "createdAt" to pWithS.playlist.createdAt,
                    "songs" to songsList
                )
            }

            db.collection("users").document(uid)
                .collection("playlists")
                .document("all_playlists")
                .set(hashMapOf("playlists" to playlistsList))
                .await()
            Log.d("FirebaseSync", "Playlists subidas a Firestore para UID: $uid")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error al subir las playlists", e)
        }
    }

    /**
     * Descarga las playlists de Firestore y las combina con la base de datos local (Room).
     */
    suspend fun pullPlaylistsFromFirestore() {
        val uid = currentUid ?: return
        try {
            val doc = db.collection("users").document(uid)
                .collection("playlists")
                .document("all_playlists")
                .get()
                .await()

            if (doc.exists()) {
                val playlistsData = doc.get("playlists") as? List<Map<String, Any>> ?: return
                for (pData in playlistsData) {
                    val name = pData["name"] as? String ?: continue
                    val createdAt = pData["createdAt"] as? Long ?: System.currentTimeMillis()
                    
                    val existingPlaylists = repository.allPlaylists.first()
                    var localPlaylistId = existingPlaylists.find { it.name == name }?.id
                    if (localPlaylistId == null) {
                        localPlaylistId = repository.createPlaylist(name).toInt()
                    }

                    val songsList = pData["songs"] as? List<Map<String, Any>> ?: emptyList()
                    for (sData in songsList) {
                        val songId = sData["id"] as? String ?: continue
                        val title = sData["title"] as? String ?: ""
                        val artist = sData["artist"] as? String ?: ""
                        val album = sData["album"] as? String ?: ""
                        val durationSeconds = (sData["durationSeconds"] as? Number)?.toInt() ?: 180
                        val artworkUrl = sData["artworkUrl"] as? String ?: ""
                        val lyrics = sData["lyrics"] as? String ?: ""
                        val genre = sData["genre"] as? String ?: ""

                        val existingSongs = repository.allSongs.first()
                        if (existingSongs.none { it.id == songId }) {
                            repository.addSong(
                                SongEntity(
                                    id = songId,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    durationSeconds = durationSeconds,
                                    artworkUrl = artworkUrl,
                                    lyrics = lyrics,
                                    genre = genre
                                )
                            )
                        }
                        
                        repository.addSongToPlaylist(localPlaylistId, songId)
                    }
                }
                Log.d("FirebaseSync", "Playlists descargadas de Firestore e integradas localmente")
            }
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error al descargar playlists de Firestore", e)
        }
    }

    /**
     * Sube el historial de escucha (para las rachas) a Firestore.
     */
    suspend fun pushListeningHistory() {
        val uid = currentUid ?: return
        try {
            val history = repository.allListeningHistory.first()
            val dateStrings = history.map { it.dateString }
            
            db.collection("users").document(uid)
                .collection("history")
                .document("listening_history")
                .set(hashMapOf("dates" to dateStrings))
                .await()
            Log.d("FirebaseSync", "Historial de escucha subido a Firestore")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error al subir el historial de escucha", e)
        }
    }

    /**
     * Descarga el historial de escucha de Firestore y lo combina localmente.
     */
    suspend fun pullListeningHistory() {
        val uid = currentUid ?: return
        try {
            val doc = db.collection("users").document(uid)
                .collection("history")
                .document("listening_history")
                .get()
                .await()

            if (doc.exists()) {
                val dates = doc.get("dates") as? List<String> ?: return
                for (dateStr in dates) {
                    repository.recordListeningHistory(dateStr)
                }
                Log.d("FirebaseSync", "Historial de escucha integrado correctamente")
            }
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error al descargar el historial de escucha", e)
        }
    }

    /**
     * Sincroniza el estado de logros desbloqueados (parámetros de SharedPreferences) a Firestore.
     */
    suspend fun pushAchievementsState(sharesCount: Int, equalizerModified: Boolean, lyricsViewed: Boolean) {
        val uid = currentUid ?: return
        try {
            val data = hashMapOf(
                "sharesCount" to sharesCount,
                "equalizerModified" to equalizerModified,
                "lyricsViewed" to lyricsViewed,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("users").document(uid)
                .collection("achievements")
                .document("state")
                .set(data, SetOptions.merge())
                .await()
            Log.d("FirebaseSync", "Estado de logros subido a Firestore")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error al subir estado de logros", e)
        }
    }

    /**
     * Descarga el estado de logros de Firestore y actualiza SharedPreferences.
     */
    suspend fun pullAchievementsState(prefs: android.content.SharedPreferences): Map<String, Any>? {
        val uid = currentUid ?: return null
        try {
            val doc = db.collection("users").document(uid)
                .collection("achievements")
                .document("state")
                .get()
                .await()

            if (doc.exists()) {
                val sharesCount = (doc.get("sharesCount") as? Number)?.toInt() ?: 0
                val equalizerModified = doc.getBoolean("equalizerModified") ?: false
                val lyricsViewed = doc.getBoolean("lyricsViewed") ?: false

                prefs.edit().apply {
                    putInt("shares_count", sharesCount)
                    putBoolean("equalizer_modified", equalizerModified)
                    putBoolean("lyrics_viewed", lyricsViewed)
                    apply()
                }
                Log.d("FirebaseSync", "Estado de logros descargado e integrado localmente")
                return mapOf(
                    "sharesCount" to sharesCount,
                    "equalizerModified" to equalizerModified,
                    "lyricsViewed" to lyricsViewed
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error al descargar el estado de logros", e)
        }
        return null
    }

    /**
     * Descarga todos los datos de Firestore al iniciar sesión.
     */
    suspend fun pullAllData(prefs: android.content.SharedPreferences) {
        pullPlaylistsFromFirestore()
        pullListeningHistory()
        pullAchievementsState(prefs)
    }

    /**
     * Sube todos los datos locales a Firestore.
     */
    suspend fun pushAllData(sharesCount: Int, equalizerModified: Boolean, lyricsViewed: Boolean) {
        syncProfile(
            auth.currentUser?.displayName ?: "Usuario de Auralis",
            auth.currentUser?.email ?: auth.currentUser?.phoneNumber ?: "Invitado",
            "Firebase"
        )
        pushPlaylistsToFirestore()
        pushListeningHistory()
        pushAchievementsState(sharesCount, equalizerModified, lyricsViewed)
    }
}
