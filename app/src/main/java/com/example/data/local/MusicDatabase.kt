package com.example.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val artworkUrl: String, // Can be a local drawable resource name or web URL
    val lyrics: String,
    val genre: String,
    val isTrending: Boolean = false,
    val isRecommended: Boolean = false,
    val isFavorite: Boolean = false,
    val audioUrl: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: Int,
    val songId: String
)

@Entity(tableName = "listening_stats")
data class ListeningStatEntity(
    @PrimaryKey val songId: String,
    val playCount: Int = 0,
    val listenSeconds: Int = 0,
    val lastPlayed: Long = System.currentTimeMillis()
)

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey val dateString: String, // Format: "yyyy-MM-dd"
    val timestamp: Long = System.currentTimeMillis()
)

// --- REVIEWS & RELATION MODELS ---

data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<SongEntity>
)

// --- DAO ---

@Dao
interface MusicDao {
    // Song queries
    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query OR album LIKE :query")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isTrending = 1")
    fun getTrendingSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isRecommended = 1")
    fun getRecommendedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: String, isFavorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongsCount(): Int

    @Query("DELETE FROM songs WHERE audioUrl IS NULL")
    suspend fun deleteNonLocalSongs()

    // Playlist queries
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    // Playlist-Song relation queries
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String)

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Int): Flow<PlaylistWithSongs>

    @Transaction
    @Query("SELECT * FROM playlists")
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>

    // Listening Stats queries
    @Query("SELECT * FROM listening_stats ORDER BY lastPlayed DESC")
    fun getAllListeningStats(): Flow<List<ListeningStatEntity>>

    @Query("SELECT * FROM listening_stats WHERE songId = :songId")
    suspend fun getStatForSong(songId: String): ListeningStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningStat(stat: ListeningStatEntity)

    @Transaction
    suspend fun incrementPlayStat(songId: String, addedSeconds: Int) {
        val existing = getStatForSong(songId)
        if (existing != null) {
            insertListeningStat(
                existing.copy(
                    playCount = existing.playCount + 1,
                    listenSeconds = existing.listenSeconds + addedSeconds,
                    lastPlayed = System.currentTimeMillis()
                )
            )
        } else {
            insertListeningStat(
                ListeningStatEntity(
                    songId = songId,
                    playCount = 1,
                    listenSeconds = addedSeconds,
                    lastPlayed = System.currentTimeMillis()
                )
            )
        }
    }

    // Listening History queries
    @Query("SELECT * FROM listening_history ORDER BY dateString DESC")
    fun getAllListeningHistory(): Flow<List<ListeningHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningHistory(history: ListeningHistoryEntity)
}

// --- DATABASE ---

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        ListeningStatEntity::class,
        ListeningHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "auralis_music_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
