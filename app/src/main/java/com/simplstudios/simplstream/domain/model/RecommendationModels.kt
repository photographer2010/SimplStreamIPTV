package com.simplstudios.simplstream.domain.model

/**
 * AI-powered recommendation models for the "For You" experience
 * 
 * The recommendation engine analyzes:
 * - Watch history (what you've watched)
 * - Watch progress (what you've completed vs abandoned)
 * - Watchlist items (what you want to watch)
 * - Genre preferences (inferred from history)
 * - Time patterns (when you watch)
 * - Rating preferences (quality threshold)
 */

/**
 * A curated section of personalized content
 */
data class RecommendationSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val type: RecommendationType,
    val items: List<RecommendedContent>,
    val reason: String? = null, // "Because you watched..."
    val icon: RecommendationIcon = RecommendationIcon.SPARKLE,
    val priority: Int = 0 // Higher = more important
)

/**
 * Individual recommended content with match metadata
 */
data class RecommendedContent(
    val content: Content,
    val matchScore: Int, // 0-100 match percentage
    val matchReasons: List<MatchReason>,
    val sourceContentTitle: String? = null, // "Because you watched X"
    val isNewRelease: Boolean = false,
    val isTrending: Boolean = false
) {
    val matchDisplay: String get() = "$matchScore% Match"
    val hasHighMatch: Boolean get() = matchScore >= 85
    val hasMediumMatch: Boolean get() = matchScore >= 70
}

/**
 * Reason why content was recommended
 */
data class MatchReason(
    val type: MatchReasonType,
    val description: String,
    val weight: Float // How much this reason contributed
)

enum class MatchReasonType {
    GENRE_MATCH,           // Same genre as watched content
    SIMILAR_TO_WATCHED,    // TMDB "similar" content
    SAME_DIRECTOR,         // Same director as liked content
    SAME_CAST,             // Shared cast members
    TRENDING_IN_GENRE,     // Popular in preferred genre
    HIGHLY_RATED,          // High TMDB rating
    NEW_RELEASE,           // Recently released
    WATCHLIST_BASED,       // Related to watchlist items
    CONTINUE_SERIES,       // Next season/related show
    MOOD_MATCH            // Matches viewing time patterns
}

enum class RecommendationType {
    BECAUSE_YOU_WATCHED,   // "Because you watched Breaking Bad"
    TOP_PICKS,             // Personalized top picks
    TRENDING_FOR_YOU,      // Trending in your genres
    NEW_RELEASES,          // New in genres you like
    CONTINUE_WATCHING,     // Resume what you started
    HIDDEN_GEMS,           // Lesser known but matching
    GENRE_SPOTLIGHT,       // Deep dive into a genre
    SIMILAR_TO_LIKED,      // Similar to highly rated
    BINGE_WORTHY,          // Series you might binge
    CRITICS_CHOICE         // Award winners in your genres
}

enum class RecommendationIcon {
    SPARKLE,       // AI/personalized
    FIRE,          // Trending
    STAR,          // Top rated
    HEART,         // Because you loved
    CLOCK,         // Continue watching
    GEM,           // Hidden gem
    TROPHY,        // Award winner
    NEW_TAG,       // New release
    PLAY_CIRCLE,   // Ready to watch
    FILM           // Genre spotlight
}

/**
 * User's taste profile built from watch history
 */
data class TasteProfile(
    val profileId: Long,
    val preferredGenres: List<GenrePreference>,
    val preferredActors: List<PersonPreference>,
    val preferredDirectors: List<PersonPreference>,
    val avgRatingThreshold: Float, // Minimum rating user tends to watch
    val preferredMediaType: MediaType?, // Prefers movies or TV
    val preferredRuntime: RuntimePreference?,
    val completionRate: Float, // How often user finishes content
    val watchTimePreference: WatchTimePreference,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class GenrePreference(
    val genreId: Int,
    val genreName: String,
    val watchCount: Int,
    val completionRate: Float, // How often finished in this genre
    val avgRating: Float, // Avg rating of watched in this genre
    val affinity: Float // 0.0-1.0 how much user likes this genre
)

data class PersonPreference(
    val personId: Int,
    val name: String,
    val watchCount: Int,
    val affinity: Float
)

data class RuntimePreference(
    val preferShort: Boolean, // Under 90min movies
    val preferLong: Boolean,  // Over 2hr movies
    val preferShortEpisodes: Boolean, // Under 30min episodes
    val preferLongEpisodes: Boolean   // Over 45min episodes
)

data class WatchTimePreference(
    val morningWatcher: Boolean,   // 6am-12pm
    val afternoonWatcher: Boolean, // 12pm-6pm
    val eveningWatcher: Boolean,   // 6pm-10pm
    val nightOwl: Boolean,         // 10pm-2am
    val weekendBinger: Boolean     // Watches more on weekends
)

/**
 * For You page state
 */
data class ForYouState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sections: List<RecommendationSection> = emptyList(),
    val tasteProfile: TasteProfile? = null,
    val spotlight: SpotlightContent? = null,
    val error: String? = null,
    val lastRefreshed: Long = 0
)

/**
 * Featured spotlight content at top of For You
 */
data class SpotlightContent(
    val content: RecommendedContent,
    val tagline: String, // "Perfect for you tonight"
    val gradient: SpotlightGradient
)

enum class SpotlightGradient {
    BLUE_PURPLE,
    RED_ORANGE,
    GREEN_TEAL,
    PINK_PURPLE,
    GOLD_ORANGE
}
