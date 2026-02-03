package com.simplstudios.simplstream.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from the TMDB Embed API streams endpoint
 */
@JsonClass(generateAdapter = true)
data class StreamResponse(
    @Json(name = "success")
    val success: Boolean? = null,
    
    @Json(name = "streams")
    val streams: List<StreamDto>? = null,
    
    @Json(name = "error")
    val error: String? = null,
    
    @Json(name = "count")
    val count: Int? = null,
    
    @Json(name = "tmdbId")
    val tmdbId: String? = null
)

/**
 * Individual stream object from the API
 * Example:
 * {
 *   "title": "Fight Club - 1080p [MP4Hydra #2]",
 *   "url": "https://stream.url/video.mp4",
 *   "quality": "1080p",
 *   "provider": "mp4hydra",
 *   "headers": { "User-Agent": "Mozilla/5.0" }
 * }
 */
@JsonClass(generateAdapter = true)
data class StreamDto(
    @Json(name = "title")
    val title: String? = null,
    
    @Json(name = "name")
    val name: String? = null,
    
    @Json(name = "url")
    val url: String,
    
    @Json(name = "quality")
    val quality: String? = null,
    
    @Json(name = "provider")
    val provider: String? = null,
    
    @Json(name = "headers")
    val headers: Map<String, String>? = null,
    
    @Json(name = "size")
    val size: String? = null,
    
    @Json(name = "codec")
    val codec: String? = null
) {
    /** Get the display title - API returns either 'title' or 'name' */
    fun getDisplayTitle(): String = title ?: name ?: "Stream"
    
    /** Check if this stream has a valid direct URL (not encoded) */
    fun hasValidUrl(): Boolean {
        val trimmedUrl = url.trim()
        val isValid = trimmedUrl.startsWith("http://", ignoreCase = true) || 
                      trimmedUrl.startsWith("https://", ignoreCase = true)
        android.util.Log.d("StreamDto", "URL check: provider=$provider, valid=$isValid, url=${trimmedUrl.take(60)}")
        return isValid
    }
}

/**
 * Subtitle track info
 */
data class SubtitleTrack(
    val url: String,
    val language: String,
    val label: String = language
)

/**
 * Domain model for a video stream
 */
data class VideoStream(
    val id: String,
    val title: String = "",
    val url: String,
    val quality: String,
    val provider: String,
    val headers: Map<String, String> = emptyMap(),
    val isHls: Boolean,
    val referer: String? = null,
    val isM3u8: Boolean = false,
    val subtitles: List<SubtitleTrack> = emptyList()
) {
    companion object {
        /**
         * Create VideoStream from legacy StreamDto (old TMDB Embed API)
         */
        fun fromDto(dto: StreamDto): VideoStream? {
            // Only accept streams with valid direct URLs (not encoded)
            if (!dto.hasValidUrl()) {
                android.util.Log.d("VideoStream", "Rejecting stream: ${dto.provider} - URL doesn't start with http")
                return null
            }
            
            val url = dto.url.trim()
            android.util.Log.d("VideoStream", "Accepting stream: ${dto.provider} - ${dto.quality} - ${url.take(50)}")
            return VideoStream(
                id = "${dto.provider}_${dto.quality}_${url.hashCode()}",
                title = dto.getDisplayTitle(),
                url = url,
                quality = dto.quality ?: "Unknown",
                provider = dto.provider ?: "Unknown",
                headers = dto.headers ?: emptyMap(),
                isHls = url.contains(".m3u8", ignoreCase = true),
                referer = dto.headers?.get("Referer"),
                isM3u8 = url.contains(".m3u8", ignoreCase = true)
            )
        }
    }
    
    /**
     * Secondary constructor for Consumet API streams
     */
    constructor(
        id: String,
        url: String,
        quality: String,
        provider: String,
        referer: String?,
        isM3u8: Boolean,
        subtitles: List<SubtitleTrack> = emptyList()
    ) : this(
        id = id,
        title = "$provider - $quality",
        url = url,
        quality = quality,
        provider = provider,
        headers = if (referer != null) mapOf("Referer" to referer) else emptyMap(),
        isHls = isM3u8 || url.contains(".m3u8", ignoreCase = true),
        referer = referer,
        isM3u8 = isM3u8,
        subtitles = subtitles
    )
    
    /**
     * Get display name for server selection UI
     */
    fun getDisplayName(): String {
        val qualityBadge = when {
            quality.contains("2160") || quality.contains("4k", ignoreCase = true) -> "4K"
            quality.contains("1080") -> "1080p"
            quality.contains("720") -> "720p"
            quality.contains("480") -> "480p"
            else -> quality
        }
        return "$provider - $qualityBadge"
    }
    
    /**
     * Get quality priority for sorting (higher = better)
     */
    fun getQualityPriority(): Int {
        return when {
            quality.contains("2160") || quality.contains("4k", ignoreCase = true) -> 5
            quality.contains("1080") -> 4
            quality.contains("720") -> 3
            quality.contains("480") -> 2
            quality.contains("360") -> 1
            else -> 0
        }
    }
}
