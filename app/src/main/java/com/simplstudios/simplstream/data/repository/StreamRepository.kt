package com.simplstudios.simplstream.data.repository

import android.util.Log
import com.simplstudios.simplstream.data.remote.api.ConsumetApi
import com.simplstudios.simplstream.data.remote.api.ConsumetSubtitle
import com.simplstudios.simplstream.data.remote.api.StremSrcApi
import com.simplstudios.simplstream.data.remote.api.StreamApi
import com.simplstudios.simplstream.data.remote.dto.SubtitleTrack
import com.simplstudios.simplstream.data.remote.dto.VideoStream
import com.simplstudios.simplstream.data.remote.extractor.WebViewExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching video streams from multiple providers
 *
 * Provider Priority:
 * 1. WebView Extractor - Sniffs m3u8 from embed providers (videasy/vidfast/111movies)
 * 2. StremSRC API (VidSRC) - Fallback if WebView extraction fails
 */
@Singleton
class StreamRepository @Inject constructor(
    private val streamApi: StreamApi,
    private val consumetApi: ConsumetApi,
    private val stremSrcApi: StremSrcApi,
    private val webViewExtractor: WebViewExtractor
) {

    companion object {
        private const val TAG = "StreamRepository"

        const val PROVIDER_WEBVIEW = "Default"
        const val PROVIDER_THIRDPARTY = "Third-Party S2"
    }

    /**
     * Fetch streams for a movie
     * Tries WebView extraction first, then StremSRC fallback
     */
    suspend fun getMovieStreams(
        tmdbId: Int,
        title: String? = null,
        imdbId: String? = null
    ): Result<List<VideoStream>> {
        val allStreams = mutableListOf<VideoStream>()
        var lastError: Exception? = null

        // PRIMARY: WebView m3u8 extraction (videasy/vidfast/111movies)
        Log.d(TAG, "Trying $PROVIDER_WEBVIEW (WebView extractor) for movie $tmdbId...")
        try {
            val result = webViewExtractor.extractMovieStream(tmdbId)
            if (result != null) {
                val subtitleTracks = result.subtitles.map { sub ->
                    SubtitleTrack(url = sub.url, language = sub.language, label = sub.label)
                }
                allStreams.add(
                    VideoStream(
                        id = "webview_${result.provider.name}_0",
                        url = result.m3u8Url,
                        quality = "auto",
                        provider = PROVIDER_WEBVIEW,
                        referer = result.referer,
                        isM3u8 = true,
                        subtitles = subtitleTracks
                    )
                )
                Log.d(TAG, "$PROVIDER_WEBVIEW: Found stream from ${result.provider.displayName} with ${subtitleTracks.size} subtitles")
            }
        } catch (e: Exception) {
            Log.w(TAG, "$PROVIDER_WEBVIEW failed: ${e.message}")
            lastError = e
        }

        // FALLBACK: StremSRC when imdbId available
        if (allStreams.isEmpty() && imdbId != null) {
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
        return if (allStreams.isNotEmpty()) {
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
    ): Result<List<VideoStream>> {
        val allStreams = mutableListOf<VideoStream>()
        var lastError: Exception? = null

        // PRIMARY: WebView m3u8 extraction
        Log.d(TAG, "Trying $PROVIDER_WEBVIEW for S${season}E${episode}...")
        try {
            val result = webViewExtractor.extractTvStream(tmdbId, season, episode)
            if (result != null) {
                val subtitleTracks = result.subtitles.map { sub ->
                    SubtitleTrack(url = sub.url, language = sub.language, label = sub.label)
                }
                allStreams.add(
                    VideoStream(
                        id = "webview_${result.provider.name}_0",
                        url = result.m3u8Url,
                        quality = "auto",
                        provider = PROVIDER_WEBVIEW,
                        referer = result.referer,
                        isM3u8 = true,
                        subtitles = subtitleTracks
                    )
                )
                Log.d(TAG, "$PROVIDER_WEBVIEW: Found stream from ${result.provider.displayName} with ${subtitleTracks.size} subtitles")
            }
        } catch (e: Exception) {
            Log.w(TAG, "$PROVIDER_WEBVIEW failed: ${e.message}")
            lastError = e
        }

        // FALLBACK: StremSRC when imdbId available
        if (allStreams.isEmpty() && imdbId != null) {
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
        return if (allStreams.isNotEmpty()) {
            Result.success(allStreams)
        } else {
            val errorMsg = "No streams available for S${season}E${episode}. Try again later."
            Result.failure(Exception(errorMsg))
        }
    }

    // ========== STREMSRC API (Third-Party S2 - Fallback) ==========

    private suspend fun fetchFromStremSrc(imdbId: String, isMovie: Boolean): List<VideoStream> =
        withContext(Dispatchers.IO) {
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

            streams.mapIndexed { index, stream ->
                val quality = when {
                    stream.title?.contains("1080p", ignoreCase = true) == true -> "1080p"
                    stream.title?.contains("720p", ignoreCase = true) == true -> "720p"
                    stream.title?.contains("480p", ignoreCase = true) == true -> "480p"
                    stream.title?.contains("360p", ignoreCase = true) == true -> "360p"
                    else -> "auto"
                }

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
     * Health check
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        true // WebView extractor doesn't need a health check
    }
}
