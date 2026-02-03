package com.simplstudios.simplstream.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * StremSRC API - Stremio addon that extracts from VidSRC + PStream
 * Uses IMDB IDs to fetch direct MP4/m3u8 streams
 * 
 * API Format: /stream/{type}/{imdbId}.json
 * 
 * Great for classic movies, serves as fallback when Consumet fails
 */
interface StremSrcApi {
    
    /**
     * Get streams for a movie by IMDB ID
     * @param imdbId IMDB ID (e.g., "tt0137523" for Fight Club)
     * @return StremSrcResponse with available streams
     */
    @GET("stream/movie/{imdbId}.json")
    suspend fun getMovieStreams(@Path("imdbId") imdbId: String): StremSrcResponse
    
    /**
     * Get streams for a TV show episode
     * @param imdbId IMDB ID with season:episode (e.g., "tt0944947:1:1" for GoT S1E1)
     * @return StremSrcResponse with available streams
     */
    @GET("stream/series/{imdbId}.json")
    suspend fun getTvStreams(@Path("imdbId") imdbId: String): StremSrcResponse
}

/**
 * Response from StremSRC API
 */
data class StremSrcResponse(
    val streams: List<StremSrcStream>?
)

/**
 * Individual stream from StremSRC
 */
data class StremSrcStream(
    val title: String?,           // e.g., "Fight Club - fsharetv (1080p)"
    val url: String,              // Direct video URL (MP4 or m3u8)
    val behaviorHints: BehaviorHints?
)

/**
 * Behavior hints containing headers and other metadata
 */
data class BehaviorHints(
    val proxyHeaders: ProxyHeaders?,
    val notWebReady: Boolean?
)

/**
 * Proxy headers for the stream request
 */
data class ProxyHeaders(
    val request: RequestHeaders?
)

/**
 * Request headers (referer, user-agent, etc.)
 */
data class RequestHeaders(
    val referer: String?
)
