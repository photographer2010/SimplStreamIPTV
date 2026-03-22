package com.simplstudios.simplstream.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplstudios.simplstream.data.preferences.SessionManager
import com.simplstudios.simplstream.domain.model.Content
import com.simplstudios.simplstream.domain.model.Genre
import com.simplstudios.simplstream.domain.model.WatchHistory
import com.simplstudios.simplstream.domain.model.WatchlistItem
import com.simplstudios.simplstream.domain.repository.ContentRepository
import com.simplstudios.simplstream.domain.repository.WatchHistoryRepository
import com.simplstudios.simplstream.domain.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun List<Content>.filterForKids(): List<Content> = filter { it.isKidsSafe }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val watchlistRepository: WatchlistRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val _selectedContent = MutableStateFlow<Content?>(null)
    val selectedContent: StateFlow<Content?> = _selectedContent.asStateFlow()
    
    private var isKidsMode = false

    init {
        observeKidsMode()
        observeUserContent()
        loadContent()
    }

    private fun observeKidsMode() {
        viewModelScope.launch {
            sessionManager.isKidsProfile.collect { isKids ->
                isKidsMode = isKids
                _uiState.update { it.copy(isKidsProfile = isKids) }
            }
        }
    }
    
    private fun observeUserContent() {
        viewModelScope.launch {
            sessionManager.currentProfileId
                .filter { it != SessionManager.NO_PROFILE }
                .collectLatest { profileId ->
                    // Observe continue watching
                    launch {
                        watchHistoryRepository.getContinueWatching(profileId, 15)
                            .collect { history ->
                                _uiState.update { it.copy(continueWatching = history) }
                            }
                    }
                    
                    // Observe watchlist
                    launch {
                        watchlistRepository.getWatchlistLimited(profileId, 15)
                            .collect { watchlist ->
                                _uiState.update { it.copy(myList = watchlist) }
                            }
                    }
                }
        }
    }
    
    fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Load all content in parallel
                val trendingDeferred = async { contentRepository.getTrending() }
                val popularMoviesDeferred = async { contentRepository.getPopularMovies() }
                val topRatedMoviesDeferred = async { contentRepository.getTopRatedMovies() }
                val nowPlayingDeferred = async { contentRepository.getNowPlayingMovies() }
                val popularTvDeferred = async { contentRepository.getPopularTvShows() }
                val topRatedTvDeferred = async { contentRepository.getTopRatedTvShows() }
                val onTheAirDeferred = async { contentRepository.getOnTheAirTvShows() }
                val movieGenresDeferred = async { contentRepository.getMovieGenres() }
                val tvGenresDeferred = async { contentRepository.getTvGenres() }
                
                val trending = trendingDeferred.await()
                val popularMovies = popularMoviesDeferred.await()
                val topRatedMovies = topRatedMoviesDeferred.await()
                val nowPlaying = nowPlayingDeferred.await()
                val popularTv = popularTvDeferred.await()
                val topRatedTv = topRatedTvDeferred.await()
                val onTheAir = onTheAirDeferred.await()
                val movieGenres = movieGenresDeferred.await()
                val tvGenres = tvGenresDeferred.await()
                
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        featuredContent = trending.getOrNull()?.items?.take(5) ?: emptyList(),
                        trending = trending.getOrNull()?.items ?: emptyList(),
                        popularMovies = popularMovies.getOrNull()?.items ?: emptyList(),
                        topRatedMovies = topRatedMovies.getOrNull()?.items ?: emptyList(),
                        nowPlayingMovies = nowPlaying.getOrNull()?.items ?: emptyList(),
                        popularTvShows = popularTv.getOrNull()?.items ?: emptyList(),
                        topRatedTvShows = topRatedTv.getOrNull()?.items ?: emptyList(),
                        onTheAirTvShows = onTheAir.getOrNull()?.items ?: emptyList(),
                        movieGenres = movieGenres.getOrNull() ?: emptyList(),
                        tvGenres = tvGenres.getOrNull() ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "Failed to load content"
                    )
                }
            }
        }
    }
    
    fun setSelectedContent(content: Content?) {
        _selectedContent.value = content
    }
    
    fun refreshContent() {
        loadContent()
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isKidsProfile: Boolean = false,

    // User-specific content
    val continueWatching: List<WatchHistory> = emptyList(),
    val myList: List<WatchlistItem> = emptyList(),

    // Featured & Trending
    val featuredContent: List<Content> = emptyList(),
    val trending: List<Content> = emptyList(),

    // Movies
    val popularMovies: List<Content> = emptyList(),
    val topRatedMovies: List<Content> = emptyList(),
    val nowPlayingMovies: List<Content> = emptyList(),

    // TV Shows
    val popularTvShows: List<Content> = emptyList(),
    val topRatedTvShows: List<Content> = emptyList(),
    val onTheAirTvShows: List<Content> = emptyList(),

    // Genres
    val movieGenres: List<Genre> = emptyList(),
    val tvGenres: List<Genre> = emptyList()
) {
    private fun List<Content>.kidsFilter(): List<Content> =
        if (isKidsProfile) filter { it.isKidsSafe } else this

    val hasContinueWatching: Boolean get() = continueWatching.isNotEmpty()
    val hasMyList: Boolean get() = myList.isNotEmpty()
    val filteredFeaturedContent: List<Content> get() = featuredContent.kidsFilter()
    val currentFeatured: Content? get() = filteredFeaturedContent.firstOrNull()

    val contentRows: List<ContentRow> get() = buildList {
        if (hasContinueWatching) {
            add(ContentRow.ContinueWatchingRow(continueWatching))
        }
        if (hasMyList) {
            add(ContentRow.MyListRow(myList.map { it.toContent() }))
        }
        val t = trending.kidsFilter()
        if (t.isNotEmpty()) add(ContentRow.SimpleRow("Trending Now", t))
        val pm = popularMovies.kidsFilter()
        if (pm.isNotEmpty()) add(ContentRow.SimpleRow("Popular Movies", pm))
        val trm = topRatedMovies.kidsFilter()
        if (trm.isNotEmpty()) add(ContentRow.SimpleRow("Top Rated Movies", trm))
        val np = nowPlayingMovies.kidsFilter()
        if (np.isNotEmpty()) add(ContentRow.SimpleRow("Now Playing", np))
        val ptv = popularTvShows.kidsFilter()
        if (ptv.isNotEmpty()) add(ContentRow.SimpleRow("Popular TV Shows", ptv))
        val trtv = topRatedTvShows.kidsFilter()
        if (trtv.isNotEmpty()) add(ContentRow.SimpleRow("Top Rated TV Shows", trtv))
        val ota = onTheAirTvShows.kidsFilter()
        if (ota.isNotEmpty()) add(ContentRow.SimpleRow("On The Air", ota))
    }
}

sealed class ContentRow {
    abstract val title: String
    
    data class ContinueWatchingRow(
        val items: List<WatchHistory>,
        override val title: String = "Continue Watching"
    ) : ContentRow()
    
    data class MyListRow(
        val items: List<Content>,
        override val title: String = "My List"
    ) : ContentRow()
    
    data class SimpleRow(
        override val title: String,
        val items: List<Content>
    ) : ContentRow()
}
