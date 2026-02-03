package com.simplstudios.simplstream.data.repository

import android.util.Log
import com.simplstudios.simplstream.data.remote.api.ConsumetApi
import com.simplstudios.simplstream.data.remote.api.ConsumetSubtitle
import com.simplstudios.simplstream.data.remote.api.StremSrcApi
import com.simplstudios.simplstream.data.remote.api.StreamApi
import com.simplstudios.simplstream.data.remote.dto.SubtitleTrack
import com.simplstudios.simplstream.data.remote.dto.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching video streams from multiple providers
 * 
 * Provider Priority:
 * 1. Consumet API (FlixHQ) - "SimplStream S1" - Primary, best for new releases
 * 2. StremSRC API (VidSRC) - "Third-Party S2" - Fallback, great for classic movies
 */
@Singleton
class StreamRepository @Inject constructor(
    private val streamApi: StreamApi,        // Legacy API (kept for compatibility)
    private val consumetApi: ConsumetApi,    // Consumet API - "SimplStream S1"
    private val stremSrcApi: StremSrcApi     // StremSRC API - "Third-Party S2"
) {
    
    companion object {
        private const val TAG = "StreamRepository"
        
        // Provider display names
        const val PROVIDER_SIMPLSTREAM = "SimplStream S1"
        const val PROVIDER_THIRDPARTY = "Third-Party S2"
    }
    
    /**
     * Fetch streams for a movie using all available providers
     * Falls back to StremSRC if Consumet fails
     * 
     * @param tmdbId TMDB movie ID
     * @param imdbId IMDB ID (optional, needed for StremSRC fallback)
     * @param title Movie title for searching FlixHQ
     * @return List of available streams from all providers
     */
    suspend fun getMovieStreams(
        tmdbId: Int, 
        title: String? = null,
        imdbId: String? = null
    ): Result<List<VideoStream>> = withContext(Dispatchers.IO) {
        val allStreams = mutableListOf<VideoStream>()
        var lastError: Exception? = null
        
        // Try Consumet API first (SimplStream S1)
        Log.d(TAG, "Trying $PROVIDER_SIMPLSTREAM (Consumet/FlixHQ)...")
        try {
            val consumetStreams = fetchFromConsumet(tmdbId, title)
            if (consumetStreams.isNotEmpty()) {
                allStreams.addAll(consumetStreams)
                Log.d(TAG, "$PROVIDER_SIMPLSTREAM: Found ${consumetStreams.size} streams")
            }
        } catch (e: Exception) {
            Log.w(TAG, "$PROVIDER_SIMPLSTREAM failed: ${e.message}")
            lastError = e
        }
        
        // Try StremSRC as fallback (Third-Party S2)
        if (imdbId != null) {
            Log.d(TAG, "Trying $PROVIDER_THIRDPARTY (StremSRC/VidSRC)...")
            try {
                val stremSrcStreams = fetchFromStremSrc(imdbId, isMovie = true)
                if (stremSrcStreams.isNotEmpty()) {
                    allStreams.addAll(stremSrcStreams)
                    Log.d(TAG, "$PROVIDER_THIRDPARTY: Found ${stremSrcStreams.size} streams")
                }
            } catch (e: Exception) {
                Log.w(TAG, "$PROVIDER_THIRDPARTY failed: ${e.message}")
                if (lastError == null) lastError = e
            }
        }
        
        // Return results
        if (allStreams.isNotEmpty()) {
            Log.d(TAG, "Total streams found: ${allStreams.size}")
            Result.success(allStreams)
        } else {
            val errorMsg = when {
                lastError?.message?.contains("Unable to resolve host") == true -> 
                    "No internet connection. Check your network and try again."
                lastError?.message?.contains("timeout") == true -> 
                    "Connection timeout. Servers may be busy, try again."
                else -> 
                    "No streams available. Try a different movie or check back later."
            }
            Log.e(TAG, "No streams found from any provider: $errorMsg")
            Result.failure(Exception(errorMsg))
        }
    }
    
    /**
     * Fetch streams for a movie (simplified overload)
     */
    suspend fun getMovieStreams(tmdbId: Int): Result<List<VideoStream>> {
        return getMovieStreams(tmdbId, null, null)
    }
    
    /**
     * Fetch streams for a TV show episode
     */
    suspend fun getTvStreams(
        tmdbId: Int, 
        season: Int, 
        episode: Int, 
        title: String? = null,
        imdbId: String? = null
    ): Result<List<VideoStream>> = withContext(Dispatchers.IO) {
        val allStreams = mutableListOf<VideoStream>()
        var lastError: Exception? = null
        
        // Try Consumet API first (SimplStream S1)
        Log.d(TAG, "Trying $PROVIDER_SIMPLSTREAM for S${season}E${episode}...")
        try {
            val consumetStreams = fetchTvFromConsumet(tmdbId, season, episode, title)
            if (consumetStreams.isNotEmpty()) {
                allStreams.addAll(consumetStreams)
                Log.d(TAG, "$PROVIDER_SIMPLSTREAM: Found ${consumetStreams.size} TV streams")
            }
        } catch (e: Exception) {
            Log.w(TAG, "$PROVIDER_SIMPLSTREAM failed: ${e.message}")
            lastError = e
        }
        
        // Try StremSRC as fallback (Third-Party S2)
        if (imdbId != null) {
            Log.d(TAG, "Trying $PROVIDER_THIRDPARTY for S${season}E${episode}...")
            try {
                val stremSrcId = "$imdbId:$season:$episode"
                val stremSrcStreams = fetchFromStremSrc(stremSrcId, isMovie = false)
                if (stremSrcStreams.isNotEmpty()) {
                    allStreams.addAll(stremSrcStreams)
                    Log.d(TAG, "$PROVIDER_THIRDPARTY: Found ${stremSrcStreams.size} TV streams")
                }
            } catch (e: Exception) {
                Log.w(TAG, "$PROVIDER_THIRDPARTY failed: ${e.message}")
                if (lastError == null) lastError = e
            }
        }
        
        // Return results
        if (allStreams.isNotEmpty()) {
            Result.success(allStreams)
        } else {
            val errorMsg = "No streams available for S${season}E${episode}. Try again later."
            Result.failure(Exception(errorMsg))
        }
    }
    
    // ========== CONSUMET API (SimplStream S1) ==========
    
    private suspend fun fetchFromConsumet(tmdbId: Int, title: String?): List<VideoStream> {
        val searchQuery = title ?: "movie $tmdbId"
        Log.d(TAG, "Consumet: Searching for '$searchQuery'")
        
        val searchResponse = consumetApi.search(searchQuery)
        val results = searchResponse.results ?: emptyList()
        
        if (results.isEmpty()) {
            throw Exception("No results found for '$searchQuery'")
        }
        
        // Find matching movie
        val movie = results.find { 
            it.type == "Movie" && (
                it.title.equals(title, ignoreCase = true) ||
                it.title.contains(title ?: "", ignoreCase = true)
            )
        } ?: results.firstOrNull { it.type == "Movie" }
            ?: throw Exception("Movie not found on FlixHQ")
        
        Log.d(TAG, "Consumet: Found '${movie.title}' (${movie.id})")
        
        // Get movie info
        val infoResponse = consumetApi.getInfo(movie.id)
        val episodes = infoResponse.episodes ?: emptyList()
        
        if (episodes.isEmpty()) {
            throw Exception("No streams available")
        }
        
        val episodeId = episodes.first().id
        
        // Try multiple servers IN PARALLEL for faster loading
        val servers = listOf("vidcloud", "upcloud", null) // null = default
        val allStreams = mutableListOf<VideoStream>()

        // Fetch all servers concurrently
        coroutineScope {
            val serverResults = servers.map { server ->
                async {
                    try {
                        Log.d(TAG, "Consumet: Trying server ${server ?: "default"}...")
                        val watchResponse = consumetApi.getWatchSources(episodeId, movie.id, server)
                        val sources = watchResponse.sources ?: emptyList()
                        val referer = watchResponse.headers?.Referer

                        // Extract subtitles from response
                        val subtitleTracks = watchResponse.subtitles?.mapNotNull { sub ->
                            convertSubtitle(sub)
                        } ?: emptyList()

                        Log.d(TAG, "Consumet: Found ${subtitleTracks.size} subtitle tracks")

                        sources.mapIndexed { index, source ->
                            VideoStream(
                                id = "consumet_${server ?: "default"}_$index",
                                url = source.url,
                                quality = source.quality ?: "auto",
                                provider = "$PROVIDER_SIMPLSTREAM (${server ?: "default"})",
                                referer = referer,
                                isM3u8 = source.isM3U8 ?: source.url.contains(".m3u8"),
                                subtitles = subtitleTracks
                            )
                        }.also { streams ->
                            if (streams.isNotEmpty()) {
                                Log.d(TAG, "Consumet: Server ${server ?: "default"} returned ${streams.size} streams")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Consumet: Server ${server ?: "default"} failed: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll()

            serverResults.forEach { streams -> allStreams.addAll(streams) }
        }

        if (allStreams.isEmpty()) {
            throw Exception("No playable streams from any server")
        }

        return allStreams
    }

    /**
     * Convert Consumet subtitle to our SubtitleTrack model
     */
    private fun convertSubtitle(sub: ConsumetSubtitle): SubtitleTrack? {
        val url = sub.url
        if (url.isBlank()) return null

        val lang = sub.lang ?: "Unknown"
        val label = getSubtitleLabel(lang)
        return SubtitleTrack(url = url, language = lang, label = label)
    }

    /**
     * Get human-readable label for subtitle language code
     */
    private fun getSubtitleLabel(lang: String): String {
        return when (lang.lowercase()) {
            "english", "en", "eng" -> "English"
            "spanish", "es", "spa" -> "Spanish"
            "french", "fr", "fra", "fre" -> "French"
            "german", "de", "deu", "ger" -> "German"
            "italian", "it", "ita" -> "Italian"
            "portuguese", "pt", "por" -> "Portuguese"
            "russian", "ru", "rus" -> "Russian"
            "japanese", "ja", "jpn" -> "Japanese"
            "korean", "ko", "kor" -> "Korean"
            "chinese", "zh", "zho", "chi" -> "Chinese"
            "arabic", "ar", "ara" -> "Arabic"
            "hindi", "hi", "hin" -> "Hindi"
            "dutch", "nl", "nld", "dut" -> "Dutch"
            else -> lang.replaceFirstChar { it.uppercase() }
        }
    }
    
    private suspend fun fetchTvFromConsumet(
        tmdbId: Int, 
        season: Int, 
        episode: Int, 
        title: String?
    ): List<VideoStream> {
        val searchQuery = title ?: "tv show $tmdbId"
        Log.d(TAG, "Consumet TV: Searching for '$searchQuery'")
        
        val searchResponse = consumetApi.search(searchQuery)
        val results = searchResponse.results ?: emptyList()
        
        Log.d(TAG, "Consumet TV: Found ${results.size} results")
        results.forEach { Log.d(TAG, "  - ${it.title} (${it.type}) id=${it.id}") }
        
        // Find TV show - more flexible matching
        val tvShow = results.find { 
            it.type == "TV Series" && it.title.equals(title, ignoreCase = true)
        } ?: results.find { 
            it.type == "TV Series" && (title?.let { t -> it.title.contains(t, ignoreCase = true) } == true)
        } ?: results.find {
            // Check if title words match
            it.type == "TV Series" && title?.split(" ")?.all { word -> 
                it.title.contains(word, ignoreCase = true) 
            } == true
        } ?: results.firstOrNull { it.type == "TV Series" }
            ?: throw Exception("TV show '$searchQuery' not found on FlixHQ")
        
        Log.d(TAG, "Consumet TV: Selected '${tvShow.title}' (${tvShow.id})")
        
        val infoResponse = consumetApi.getInfo(tvShow.id)
        val episodes = infoResponse.episodes ?: emptyList()
        
        Log.d(TAG, "Consumet TV: Found ${episodes.size} episodes")
        
        val targetEpisode = episodes.find { 
            it.season == season && it.number == episode 
        } ?: throw Exception("Episode S${season}E${episode} not found in ${episodes.size} episodes")
        
        Log.d(TAG, "Consumet TV: Found episode ${targetEpisode.id}")
        
        // Try multiple servers IN PARALLEL for faster loading
        val servers = listOf("vidcloud", "upcloud", null)
        val allStreams = mutableListOf<VideoStream>()

        // Fetch all servers concurrently
        coroutineScope {
            val serverResults = servers.map { server ->
                async {
                    try {
                        Log.d(TAG, "Consumet TV: Trying server ${server ?: "default"}...")
                        val watchResponse = consumetApi.getWatchSources(targetEpisode.id, tvShow.id, server)
                        val sources = watchResponse.sources ?: emptyList()
                        val referer = watchResponse.headers?.Referer

                        // Extract subtitles from response
                        val subtitleTracks = watchResponse.subtitles?.mapNotNull { sub ->
                            convertSubtitle(sub)
                        } ?: emptyList()

                        Log.d(TAG, "Consumet TV: Found ${subtitleTracks.size} subtitle tracks")

                        sources.mapIndexed { index, source ->
                            VideoStream(
                                id = "consumet_tv_${server ?: "default"}_$index",
                                url = source.url,
                                quality = source.quality ?: "auto",
                                provider = "$PROVIDER_SIMPLSTREAM (${server ?: "default"})",
                                referer = referer,
                                isM3u8 = source.isM3U8 ?: source.url.contains(".m3u8"),
                                subtitles = subtitleTracks
                            )
                        }.also { streams ->
                            if (streams.isNotEmpty()) {
                                Log.d(TAG, "Consumet TV: Server ${server ?: "default"} returned ${streams.size} streams")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Consumet TV: Server ${server ?: "default"} failed: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll()

            serverResults.forEach { streams -> allStreams.addAll(streams) }
        }

        if (allStreams.isEmpty()) {
            throw Exception("No playable streams for S${season}E${episode}")
        }

        return allStreams
    }
    
    // ========== STREMSRC API (Third-Party S2) ==========
    
    private suspend fun fetchFromStremSrc(imdbId: String, isMovie: Boolean): List<VideoStream> {
        Log.d(TAG, "StremSRC: Fetching for IMDB: $imdbId (isMovie=$isMovie)")
        
        val response = if (isMovie) {
            stremSrcApi.getMovieStreams(imdbId)
        } else {
            stremSrcApi.getTvStreams(imdbId)
        }
        
        val streams = response.streams ?: emptyList()
        
        if (streams.isEmpty()) {
            throw Exception("No streams from StremSRC")
        }
        
        return streams.mapIndexed { index, stream ->
            // Extract quality from title (e.g., "Fight Club - fsharetv (1080p)")
            val quality = when {
                stream.title?.contains("1080p", ignoreCase = true) == true -> "1080p"
                stream.title?.contains("720p", ignoreCase = true) == true -> "720p"
                stream.title?.contains("480p", ignoreCase = true) == true -> "480p"
                stream.title?.contains("360p", ignoreCase = true) == true -> "360p"
                else -> "auto"
            }
            
            // Get referer from behavior hints
            val referer = stream.behaviorHints?.proxyHeaders?.request?.referer
            
            VideoStream(
                id = "stremsrc_$index",
                url = stream.url,
                quality = quality,
                provider = PROVIDER_THIRDPARTY,
                referer = referer,
                isM3u8 = stream.url.contains(".m3u8")
            )
        }
    }
    
    /**
     * Health check for APIs
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            consumetApi.search("test")
            true
        } catch (e: Exception) {
            false
        }
    }
}
