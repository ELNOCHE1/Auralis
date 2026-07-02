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

    suspend fun seedDatabaseIfEmpty() {
        if (musicDao.getSongsCount() == 0) {
            val defaultSongs = listOf(
                SongEntity(
                    id = "song_1",
                    title = "Vientos del Sur",
                    artist = "Aura Mística",
                    album = "Ecos de la Pampa",
                    durationSeconds = 195,
                    artworkUrl = "bg_retro",
                    lyrics = """[Intro - Melodía suave de guitarra y flauta]

En las noches frías de este invierno eterno,
siento el susurro de un viento tierno.
Cruza la cordillera, llega hasta tu balcón,
llevando el eco de mi triste canción.

[Estribillo]
Vientos del sur, decile que la extraño,
que cada minuto lejos me hace daño.
Vientos del sur, lleven este cantar,
bajo el cielo estrellado, hasta su mirar.

[Verso 2]
Caminando por calles de adoquín mojado,
recuerdo aquel tango que bailamos de lado.
Tu risa flotaba como humo en el café,
una promesa hermosa que nunca olvidaré.

[Estribillo]
Vientos del sur, decile que la extraño,
que cada minuto lejos me hace daño.
Vientos del sur, lleven este cantar,
bajo el cielo estrellado, hasta su mirar.

[Outro - Solo de bandoneón suave]
Vientos del sur...
Hasta su mirar...""",
                    genre = "Folklore/Fusión",
                    isTrending = true,
                    isRecommended = true
                ),
                SongEntity(
                    id = "song_2",
                    title = "Neon Nights",
                    artist = "Hyperdrive",
                    album = "Retroverse",
                    durationSeconds = 210,
                    artworkUrl = "bg_synthwave",
                    lyrics = """[Instrumental Intro - Heavy synthesizer arpeggios]

[Verse 1]
Grid lines glowing in the deep dark night,
Chasing the horizon in the digital light.
Vapor trails on a grid of chrome,
In this cyber city, we are never alone.

[Chorus]
Neon nights, keep on glowing,
In the stream, our colors are showing.
Synthesized dreams, electric eyes,
Underneath the purple grid-lit skies.

[Verse 2]
Speed of sound, feel the bass in your chest,
This retro drive will never let you rest.
FM waveforms floating on the breeze,
Lost inside these 80s fantasies.

[Chorus]
Neon nights, keep on glowing,
In the stream, our colors are showing.
Synthesized dreams, electric eyes,
Underneath the purple grid-lit skies.

[Guitar/Synth Solo]

[Outro - Fade out with synth drum beat]""",
                    genre = "Synthwave",
                    isTrending = true,
                    isRecommended = false
                ),
                SongEntity(
                    id = "song_3",
                    title = "Café y Melancolía",
                    artist = "Sebastián Cruz",
                    album = "Historias de Invierno",
                    durationSeconds = 165,
                    artworkUrl = "bg_acoustic",
                    lyrics = """[Intro - Guitarra acústica melódica]

La lluvia golpea la ventana sin cesar,
y en este pocillo de café te vuelvo a buscar.
El humo dibuja tu silueta al subir,
un dulce fantasma que me ayuda a vivir.

[Estribillo]
Café caliente en una tarde gris,
con gusto a la nostalgia que me hace feliz.
Vení sentate un rato, conversemos los dos,
aunque sea un recuerdo, quiero oír tu voz.

[Verso 2]
Las hojas doradas caen sobre el cordón,
mientras yo rasgueo esta vieja canción.
Buenos Aires duerme bajo el temporal,
y nuestro café se vuelve inmortal.

[Estribillo]
Café caliente en una tarde gris,
con gusto a la nostalgia que me hace feliz.
Vení sentate un rato, conversemos los dos,
aunque sea un recuerdo, quiero oír tu voz.

[Outro - Notas finales de guitarra apagándose]
Tu voz... en mi café.""",
                    genre = "Acústico/Indie",
                    isTrending = false,
                    isRecommended = true
                ),
                SongEntity(
                    id = "song_4",
                    title = "Oasis de Lluvia",
                    artist = "Lofi Luna",
                    album = "Study Beats Vol. 1",
                    durationSeconds = 145,
                    artworkUrl = "bg_lofi",
                    lyrics = """[Instrumental track - No vocal lyrics]
[Sonido ambiental de lluvia de fondo]
[Suaves acordes de piano eléctrico Fender Rhodes]
[Ritmo relajado de batería de baja fidelidad - Lofi]
[Solo de saxofón lejano con filtro cálido]
[Transición suave de vinilo crujiendo]
[Acordes finales flotantes]""",
                    genre = "Lofi Chill",
                    isTrending = false,
                    isRecommended = true
                ),
                SongEntity(
                    id = "song_5",
                    title = "Tormenta de Distorsión",
                    artist = "Los Fósiles",
                    album = "Gritando al Silencio",
                    durationSeconds = 245,
                    artworkUrl = "bg_rock",
                    lyrics = """[Intro - Batería potente y riff de guitarra distorsionado]

Ruedan las piedras en el callejón,
estalla el grito de mi generación.
No hay más mentiras que nos puedan calmar,
hoy las guitarras van a reventar.

[Estribillo]
¡Es una tormenta de distorsión!
¡Que quema las venas de tu corazón!
No mires atrás, no pidas perdón,
¡esto es puro rock, es revolución!

[Verso 2]
En los sótanos oscuros del centro ayer,
sentimos el fuego que empieza a nacer.
Cables cruzados, valvulares al cien,
gritando verdades que te hacen sentir bien.

[Estribillo]
¡Es una tormenta de distorsión!
¡Que quema las venas de tu corazón!
No mires atrás, no pidas perdón,
¡esto es puro rock, es revolución!

[Solo de Guitarra Eléctrica Rápido e Intenso]

[Outro - Acorde final sostenido con acople]
¡Revolución!""",
                    genre = "Indie Rock",
                    isTrending = true,
                    isRecommended = false
                ),
                SongEntity(
                    id = "song_6",
                    title = "Eclipse Solar",
                    artist = "Starlight Odyssey",
                    album = "Cosmos",
                    durationSeconds = 230,
                    artworkUrl = "bg_space",
                    lyrics = """[Intro - Sonidos espaciales y pulsos analógicos]

Dancing in the shadow of a dying star,
Trying to remember who we really are.
The moon aligns, blocking out the sun,
Our journey through the cosmos has just begun.

[Chorus]
In the eclipse, we lose our way,
Turning the midnight into day.
A perfect alignment of space and time,
We collide in a rhythm sublime.

[Verse 2]
Floating through nebulas of pink and gold,
Chasing the stories that were never told.
Solar flares dancing in your dark blue eyes,
As we break through the limits of gravity's skies.

[Chorus]
In the eclipse, we lose our way,
Turning the midnight into day.
A perfect alignment of space and time,
We collide in a rhythm sublime.

[Outro - Slow synth pad fading into deep space silence]""",
                    genre = "Dream Pop / Space Rock",
                    isTrending = false,
                    isRecommended = true
                )
            )
            musicDao.insertSongs(defaultSongs)
        }
    }
}
