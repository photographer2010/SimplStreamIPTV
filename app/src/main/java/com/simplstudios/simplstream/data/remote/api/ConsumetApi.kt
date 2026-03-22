package com.simplstudios.simplstream.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API interface for Consumet API (FlixHQ provider)
 * Hosted on Vercel: https://simplstream-consumet.vercel.app
 * 
 * Flow:
 * 1. Search movie/show by title to get FlixHQ ID
 * 2. Get info to retrieve episode ID
 * 3. Get watch sources with direct m3u8 URLs
 */
interface ConsumetApi {
    
    /**
     * Search for movies/shows
     * @param query Search query (movie/show title)
     * @return Search results with FlixHQ IDs
     */
    @GET("movies/flixhq/{query}")
    suspend fun search(
        @Path("query") query: String
    ): ConsumetSearchResponse
    
    /**
     * Get movie/show info including episode IDs
     * @param id FlixHQ ID (e.g., "movie/watch-zootopia-2-138460")
     */
    @GET("movies/flixhq/info")
    suspend fun getInfo(
        @Query("id") id: String
    ): ConsumetInfoResponse
    
    /**
     * Get watch sources (direct stream URLs)
     * @param episodeId Episode ID from info response
     * @param mediaId FlixHQ ID (same as info id)
     * @param server Server to use (vidcloud, upcloud, mixdrop)
     */
    @GET("movies/flixhq/watch")
    suspend fun getWatchSources(
        @Query("episodeId") episodeId: String,
        @Query("mediaId") mediaId: String,
        @Query("server") server: String? = null
    ): ConsumetWatchResponse
}

// DTOs for Consumet API responses

data class ConsumetSearchResponse(
    val currentPage: Int? = null,
    val hasNextPage: Boolean? = null,
    val results: List<ConsumetSearchResult>? = null
)

data class ConsumetSearchResult(
    val id: String,           // e.g., "movie/watch-zootopia-2-138460"
    val title: String,
    val url: String? = null,
    val image: String? = null,
    val releaseDate: String? = null,
    val type: String? = null, // "Movie" or "TV Series"
    val seasons: Int? = null  // For TV shows
)

data class ConsumetInfoResponse(
    val id: String,
    val title: String,
    val url: String? = null,
    val cover: String? = null,
    val image: String? = null,
    val description: String? = null,
    val type: String? = null,
    val releaseDate: String? = null,
    val genres: List<String>? = null,
    val duration: String? = null,
    val rating: Double? = null,
    val episodes: List<ConsumetEpisode>? = null
)

data class ConsumetEpisode(
    val id: String,           // Episode ID needed for watch endpoint
    val title: String? = null,
    val number: Int? = null,
    val season: Int? = null,
    val url: String? = null
)

data class ConsumetWatchResponse(
    val message: String? = null,  // Error message when watch fails (e.g. "Something went wrong...")
    val headers: ConsumetHeaders? = null,
    val sources: List<ConsumetSource>? = null,
    val subtitles: List<ConsumetSubtitle>? = null
)

data class ConsumetHeaders(
    val Referer: String? = null
)

data class ConsumetSource(
    val url: String,
    val isM3U8: Boolean? = null,
    val quality: String? = null
)

data class ConsumetSubtitle(
    val url: String,
    val lang: String? = null
)
