package com.simplstudios.simplstream.domain.repository

import com.simplstudios.simplstream.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository for AI-powered personalized recommendations
 * 
 * This repository combines:
 * - User watch history analysis
 * - TMDB similar/recommended content
 * - Trending content filtered by preferences
 * - Genre-based discovery
 */
interface RecommendationRepository {
    
    /**
     * Get all personalized recommendation sections for the For You page
     */
    suspend fun getForYouSections(profileId: Long): Result<List<RecommendationSection>>
    
    /**
     * Get the user's computed taste profile
     */
    suspend fun getTasteProfile(profileId: Long): Result<TasteProfile>
    
    /**
     * Get recommendations based on a specific watched content
     * "Because you watched Breaking Bad..."
     */
    suspend fun getRecommendationsForContent(
        profileId: Long,
        contentId: Int,
        mediaType: MediaType
    ): Result<List<RecommendedContent>>
    
    /**
     * Get top personalized picks
     */
    suspend fun getTopPicksForYou(profileId: Long, limit: Int = 20): Result<List<RecommendedContent>>
    
    /**
     * Get trending content filtered by user preferences
     */
    suspend fun getTrendingForYou(profileId: Long, limit: Int = 20): Result<List<RecommendedContent>>
    
    /**
     * Get hidden gems - lesser known but highly rated in preferred genres
     */
    suspend fun getHiddenGems(profileId: Long, limit: Int = 15): Result<List<RecommendedContent>>
    
    /**
     * Get new releases in preferred genres
     */
    suspend fun getNewReleasesForYou(profileId: Long, limit: Int = 15): Result<List<RecommendedContent>>
    
    /**
     * Get binge-worthy series recommendations
     */
    suspend fun getBingeWorthy(profileId: Long, limit: Int = 10): Result<List<RecommendedContent>>
    
    /**
     * Get spotlight content for hero section
     */
    suspend fun getSpotlightContent(profileId: Long): Result<SpotlightContent?>
    
    /**
     * Refresh taste profile based on latest watch history
     */
    suspend fun refreshTasteProfile(profileId: Long): Result<TasteProfile>
    
    /**
     * Observe taste profile changes
     */
    fun observeTasteProfile(profileId: Long): Flow<TasteProfile?>
}
