package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    data class SuggestionResult(
        val artist: String,
        val lyrics: String,
        val album: String,
        val genre: String
    )

    suspend fun getLyricsAndArtist(filename: String): SuggestionResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return@withContext null
        }

        val prompt = """
            Eres un asistente experto en música de Auralis.
            Analiza el siguiente nombre de archivo de música: "$filename"
            Y determina:
            1. El artista o autor real.
            2. El álbum (si se conoce, o sugiere uno adecuado).
            3. El género musical principal (ej. Rock, Pop, Synthwave, Lofi, Acoustic, Space, Metal).
            4. La letra completa de la canción en español (o en su idioma original con excelente formato poético y saltos de línea). Si la canción es instrumental o desconocida, escribe una letra poética e inspiradora acorde al título.
            
            Debes responder ÚNICAMENTE con un objeto JSON válido, sin formato markdown (sin ```json), con la siguiente estructura exacta:
            {
              "artist": "Nombre del Artista",
              "lyrics": "Letra completa de la canción con saltos de línea...",
              "album": "Nombre del Álbum",
              "genre": "Género"
            }
        """.trimIndent()

        try {
            // Build the JSON body
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)
                
                // Add generationConfig to enforce JSON response
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.7)
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "$BASE_URL?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unsuccessful response from Gemini: ${response.code} ${response.message}")
                    return@withContext null
                }

                val bodyString = response.body?.string() ?: return@withContext null
                Log.d(TAG, "Gemini raw response: $bodyString")

                val root = JSONObject(bodyString)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            if (!text.isNullOrBlank()) {
                                // Parse the returned JSON text
                                val suggestionJson = JSONObject(text.trim())
                                val artist = suggestionJson.optString("artist", "Artista Desconocido")
                                val lyrics = suggestionJson.optString("lyrics", "")
                                val album = suggestionJson.optString("album", "Archivo Local")
                                val genre = suggestionJson.optString("genre", "Local")

                                return@withContext SuggestionResult(
                                    artist = artist,
                                    lyrics = lyrics,
                                    album = album,
                                    genre = genre
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from Gemini", e)
        }
        return@withContext null
    }
}
