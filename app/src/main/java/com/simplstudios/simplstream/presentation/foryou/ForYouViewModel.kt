package com.simplstudios.simplstream.presentation.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplstudios.simplstream.data.preferences.SessionManager
import com.simplstudios.simplstream.domain.model.*
import com.simplstudios.simplstream.domain.repository.RecommendationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the "For You" personalized recommendations page
 * 
 * Features:
 * - Loads personalized recommendation sections
 * - Manages spotlight content for hero display
 * - Handles pull-to-refresh for fresh recommendations
 * - Tracks taste profile for debugging/display
 */
@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ForYouState())
    val uiState: StateFlow<ForYouState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<ForYouEvent>()
    val events = _events.asSharedFlow()
    
    init {
        loadRecommendations()
    }
    
    /**
     * Load all personalized recommendation sections
     */
    fun loadRecommendations() {
        viewModelScope.launch {
            val profileId = sessionManager.currentProfileId.first()
            if (profileId == SessionManager.NO_PROFILE) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = "Please select a profile to see recommendations"
                )}
                return@launch
            }
            
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Load spotlight content first for hero
            val spotlightResult = recommendationRepository.getSpotlightContent(profileId)
            val spotlight = spotlightResult.getOrNull()
            
            // Load taste profile (for display purposes)
            val tasteProfile = recommendationRepository.getTasteProfile(profileId).getOrNull()
            
            // Load all recommendation sections
            val sectionsResult = recommendationRepository.getForYouSections(profileId)
            
            sectionsResult.fold(
                onSuccess = { sections ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        sections = sections,
                        spotlight = spotlight,
                        tasteProfile = tasteProfile,
                        lastRefreshed = System.currentTimeMillis(),
                        error = null
                    )}
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load recommendations"
                    )}
                }
            )
        }
    }
    
    /**
     * Refresh recommendations (pull-to-refresh or manual refresh)
     */
    fun refreshRecommendations() {
        viewModelScope.launch {
            val profileId = sessionManager.currentProfileId.first()
            if (profileId == SessionManager.NO_PROFILE) return@launch
            
            _uiState.update { it.copy(isRefreshing = true) }
            
            // First refresh the taste profile
            recommendationRepository.refreshTasteProfile(profileId)
            
            // Then reload all sections
            val sectionsResult = recommendationRepository.getForYouSections(profileId)
            val spotlightResult = recommendationRepository.getSpotlightContent(profileId)
            val tasteProfile = recommendationRepository.getTasteProfile(profileId).getOrNull()
            
            sectionsResult.fold(
                onSuccess = { sections ->
                    _uiState.update { it.copy(
                        isRefreshing = false,
                        sections = sections,
                        spotlight = spotlightResult.getOrNull(),
                        tasteProfile = tasteProfile,
                        lastRefreshed = System.currentTimeMillis()
                    )}
                    _events.emit(ForYouEvent.RefreshComplete)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isRefreshing = false) }
                    _events.emit(ForYouEvent.ShowError(error.message ?: "Refresh failed"))
                }
            )
        }
    }
    
    /**
     * Navigate to content detail
     */
    fun onContentClicked(content: Content) {
        viewModelScope.launch {
            _events.emit(ForYouEvent.NavigateToDetail(content.id, content.mediaType))
        }
    }
    
    /**
     * Log content interaction for improving recommendations
     */
    fun onContentViewed(content: Content) {
        // Could be used to improve recommendations in the future
        // e.g., tracking what the user looks at but doesn't click
    }
    
    /**
     * Get formatted last refresh time
     */
    fun getLastRefreshDisplay(): String {
        val lastRefreshed = _uiState.value.lastRefreshed
        if (lastRefreshed == 0L) return "Never"
        
        val now = System.currentTimeMillis()
        val diff = now - lastRefreshed
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> "${diff / 86400_000}d ago"
        }
    }
}

/**
 * Events emitted by ForYouViewModel
 */
sealed class ForYouEvent {
    data class NavigateToDetail(val contentId: Int, val mediaType: MediaType) : ForYouEvent()
    data class ShowError(val message: String) : ForYouEvent()
    object RefreshComplete : ForYouEvent()
}
