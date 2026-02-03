package com.simplstudios.simplstream.presentation.player

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplstudios.simplstream.data.preferences.SessionManager
import com.simplstudios.simplstream.data.remote.dto.VideoStream
import com.simplstudios.simplstream.data.repository.StreamRepository
import com.simplstudios.simplstream.domain.model.Content
import com.simplstudios.simplstream.domain.model.MediaType
import com.simplstudios.simplstream.domain.repository.WatchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

/**
 * ViewModel for native ExoPlayer-based video playback
 * Uses SimplStream API to fetch direct stream URLs (no WebView, no ads!)
 */
@HiltViewModel
class NativePlayerViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(NativePlayerUiState())
    val uiState: StateFlow<NativePlayerUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<NativePlayerEvent>()
    val events: SharedFlow<NativePlayerEvent> = _events.asSharedFlow()
    
    private var profileId: Long = SessionManager.NO_PROFILE
    private var playbackArgs: NativePlaybackArgs? = null
    
    init {
        viewModelScope.launch {
            sessionManager.currentProfileId.collect { id ->
                profileId = id
            }
        }
    }
    
    fun initialize(args: NativePlaybackArgs) {
        playbackArgs = args
        
        _uiState.update {
            it.copy(
                contentId = args.contentId,
                title = args.title,
                mediaType = args.mediaType,
                seasonNumber = args.seasonNumber,
                episodeNumber = args.episodeNumber,
                episodeName = args.episodeName,
                resumePosition = args.resumePosition,
                posterUrl = args.posterUrl,
                isLoading = true
            )
        }
        
        // Fetch streams from API
        fetchStreams()
    }
    
    private fun fetchStreams() {
        val args = playbackArgs ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Pass title and imdbId to providers
            // Consumet uses title search, StremSRC uses IMDB ID
            val result = if (args.mediaType == MediaType.MOVIE) {
                streamRepository.getMovieStreams(
                    tmdbId = args.contentId, 
                    title = args.title,
                    imdbId = args.imdbId
                )
            } else {
                streamRepository.getTvStreams(
                    tmdbId = args.contentId,
                    season = args.seasonNumber ?: 1,
                    episode = args.episodeNumber ?: 1,
                    title = args.title,
                    imdbId = args.imdbId
                )
            }
            
            result.fold(
                onSuccess = { streams ->
                    if (streams.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "No streams available for this content"
                            )
                        }
                    } else {
                        // Get default stream (prefer saved provider or best quality)
                        val defaultIndex = findDefaultStreamIndex(streams)
                        
                        _uiState.update {
                            it.copy(
                                streams = streams,
                                currentStreamIndex = defaultIndex,
                                currentStream = streams[defaultIndex],
                                isLoading = false
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load streams"
                        )
                    }
                }
            )
        }
    }
    
    private fun findDefaultStreamIndex(streams: List<VideoStream>): Int {
        // TODO: Could add saved preferred provider logic here
        // For now, return first stream (already sorted by quality)
        return 0
    }
    
    fun switchStream(index: Int) {
        val streams = _uiState.value.streams
        if (index in streams.indices) {
            // Preserve current position for seamless stream switching
            val currentPos = _uiState.value.currentPosition
            _uiState.update {
                it.copy(
                    currentStreamIndex = index,
                    currentStream = streams[index],
                    isLoading = true,
                    error = null,
                    // Keep position for seamless switch (overrides resumePosition)
                    resumePosition = if (currentPos > 0) currentPos else it.resumePosition
                )
            }
        }
    }
    
    fun switchToStream(stream: VideoStream) {
        val index = _uiState.value.streams.indexOfFirst { it.id == stream.id }
        if (index >= 0) {
            switchStream(index)
        }
    }
    
    fun onStreamReady() {
        _uiState.update { it.copy(isLoading = false, isPlaying = true) }
    }
    
    fun onStreamError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
        // Auto-try next stream
        tryNextStream()
    }
    
    fun tryNextStream() {
        val currentIndex = _uiState.value.currentStreamIndex
        val nextIndex = currentIndex + 1
        
        if (nextIndex < _uiState.value.streams.size) {
            switchStream(nextIndex)
        } else {
            _uiState.update { it.copy(error = "All streams failed to load") }
        }
    }
    
    fun retryFetchStreams() {
        fetchStreams()
    }
    
    fun updateProgress(position: Long, duration: Long) {
        if (duration <= 0) return
        
        val progress = position.toFloat() / duration.toFloat()
        val isCompleted = progress >= 0.9f
        
        _uiState.update {
            it.copy(
                currentPosition = position,
                totalDuration = duration,
                progress = progress,
                isCompleted = isCompleted
            )
        }
        
        saveProgress(position, duration, isCompleted)
    }
    
    private var lastSavedPosition: Long = 0
    
    private fun saveProgress(position: Long, duration: Long, isCompleted: Boolean) {
        if (kotlin.math.abs(position - lastSavedPosition) < 10_000 && !isCompleted) return
        lastSavedPosition = position
        
        val args = playbackArgs ?: return
        if (profileId == SessionManager.NO_PROFILE) return
        
        viewModelScope.launch {
            val content = Content(
                id = args.contentId,
                title = args.title.substringBefore(" - "),
                overview = "",
                posterUrl = args.posterUrl,
                backdropUrl = null,
                voteAverage = 0f,
                releaseDate = null,
                mediaType = args.mediaType
            )
            
            watchHistoryRepository.addOrUpdateWatchHistory(
                profileId = profileId,
                content = content,
                seasonNumber = args.seasonNumber,
                episodeNumber = args.episodeNumber,
                episodeTitle = args.episodeName,
                watchPosition = position,
                totalDuration = duration,
                isCompleted = isCompleted
            )
        }
    }
    
    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }
    
    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }
    
    fun onPlaybackEnded() {
        _uiState.update { it.copy(isCompleted = true, isPlaying = false, showControls = true) }
        val state = _uiState.value
        if (state.totalDuration > 0) {
            saveProgress(state.totalDuration, state.totalDuration, true)
        }
    }

    /**
     * Check if this is a TV show episode with a potential next episode
     */
    fun hasNextEpisode(): Boolean {
        val args = playbackArgs ?: return false
        return args.mediaType == MediaType.TV && args.episodeNumber != null
    }

    /**
     * Navigate to play the next episode
     */
    fun playNextEpisode() {
        val args = playbackArgs ?: return
        if (args.mediaType != MediaType.TV || args.episodeNumber == null) return

        viewModelScope.launch {
            _events.emit(
                NativePlayerEvent.PlayNextEpisode(
                    contentId = args.contentId,
                    title = args.title.substringBefore(" - "),  // Get base show title
                    imdbId = args.imdbId,
                    seasonNumber = args.seasonNumber ?: 1,
                    episodeNumber = args.episodeNumber + 1,
                    posterUrl = args.posterUrl
                )
            )
        }
    }
    
    fun showControls() {
        _uiState.update { it.copy(showControls = true) }
    }
    
    fun hideControls() {
        _uiState.update { it.copy(showControls = false) }
    }
    
    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }
    
    fun exit() {
        val state = _uiState.value
        if (state.totalDuration > 0) {
            saveProgress(state.currentPosition, state.totalDuration, state.isCompleted)
        }
        
        viewModelScope.launch {
            _events.emit(NativePlayerEvent.Exit)
        }
    }
}

data class NativePlayerUiState(
    val contentId: Int = 0,
    val title: String = "",
    val mediaType: MediaType = MediaType.MOVIE,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeName: String? = null,
    val posterUrl: String? = null,
    val streams: List<VideoStream> = emptyList(),
    val currentStreamIndex: Int = 0,
    val currentStream: VideoStream? = null,
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val showControls: Boolean = true,
    val error: String? = null,
    val currentPosition: Long = 0,
    val totalDuration: Long = 0,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val resumePosition: Long = 0
) {
    /** Check if we can show "Next Episode" button */
    val canShowNextEpisode: Boolean get() = isCompleted && mediaType == MediaType.TV && episodeNumber != null
    val displayTitle: String get() = if (episodeName != null) {
        "$title - $episodeName"
    } else {
        title
    }
    val hasMultipleStreams: Boolean get() = streams.size > 1
    val formattedPosition: String get() = formatTime(currentPosition)
    val formattedDuration: String get() = formatTime(totalDuration)
    
    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        } else {
            "%d:%02d".format(minutes, seconds % 60)
        }
    }
}

sealed class NativePlayerEvent {
    data object Exit : NativePlayerEvent()

    data class PlayNextEpisode(
        val contentId: Int,
        val title: String,
        val imdbId: String?,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val posterUrl: String?
    ) : NativePlayerEvent()
}

@Parcelize
data class NativePlaybackArgs(
    val contentId: Int,
    val title: String,
    val mediaType: MediaType,
    val imdbId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeName: String? = null,
    val resumePosition: Long = 0,
    val posterUrl: String? = null
) : Parcelable
