package com.simplstudios.simplstream.data.remote.api

import com.simplstudios.simplstream.data.remote.dto.StreamResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API interface for SimplStream's TMDB Embed API
 * Hosted on Render: https://simplstream-api.onrender.com
 */
interface StreamApi {
    
    /**
     * Get aggregated streams from all providers for a movie
     * @param tmdbId The TMDB ID of the movie
     */
    @GET("api/streams/movie/{tmdbId}")
    suspend fun getMovieStreams(
        @Path("tmdbId") tmdbId: Int
    ): StreamResponse
    
    /**
     * Get aggregated streams from all providers for a TV show episode
     * API uses: /api/streams/series/{tmdbId}?s={season}&e={episode}
     * @param tmdbId The TMDB ID of the TV show
     * @param s Season number
     * @param e Episode number
     */
    @GET("api/streams/series/{tmdbId}")
    suspend fun getTvStreams(
        @Path("tmdbId") tmdbId: Int,
        @Query("s") s: Int,
        @Query("e") e: Int
    ): StreamResponse
    
    /**
     * Get streams from a specific provider for a movie
     * @param provider Provider name (4khdhub, moviesmod, mp4hydra, vidzee, vixsrc, uhdmovies)
     * @param tmdbId The TMDB ID of the movie
     */
    @GET("api/streams/{provider}/movie/{tmdbId}")
    suspend fun getMovieStreamsFromProvider(
        @Path("provider") provider: String,
        @Path("tmdbId") tmdbId: Int
    ): StreamResponse
    
    /**
     * Get streams from a specific provider for a TV show episode
     * @param provider Provider name (4khdhub, moviesmod, mp4hydra, vidzee, vixsrc, uhdmovies)
     * @param tmdbId The TMDB ID of the TV show
     * @param s Season number
     * @param e Episode number
     */
    @GET("api/streams/{provider}/series/{tmdbId}")
    suspend fun getTvStreamsFromProvider(
        @Path("provider") provider: String,
        @Path("tmdbId") tmdbId: Int,
        @Query("s") s: Int,
        @Query("e") e: Int
    ): StreamResponse
    
    /**
     * Health check endpoint
     */
    @GET("api/health")
    suspend fun healthCheck(): HealthResponse
}

/**
 * Health check response
 * API returns: {"ok":true,"service":"tmdb-embed-api","time":"..."}
 */
data class HealthResponse(
    val ok: Boolean? = null,
    val service: String? = null,
    val time: String? = null
)
