package com.simplstudios.simplstream.presentation.detail

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.simplstudios.simplstream.R
import com.simplstudios.simplstream.domain.model.*
import com.simplstudios.simplstream.presentation.player.NativePlaybackArgs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Netflix-style Detail Fragment with modern UI
 * Features:
 * - Full-width hero backdrop
 * - Glass morphism info panels  
 * - Circular cast photos
 * - Landscape episode cards
 * - Smooth D-pad navigation
 */
@AndroidEntryPoint
class DetailNetflixFragment : Fragment() {
    
    private val viewModel: DetailViewModel by viewModels()
    private val args: DetailNetflixFragmentArgs by navArgs()
    
    // Views
    private lateinit var scrollView: View
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var contentContainer: View
    private lateinit var backdropImage: ImageView
    private lateinit var backButton: ImageView
    private lateinit var posterImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var ratingText: TextView
    private lateinit var yearText: TextView
    private lateinit var runtimeText: TextView
    private lateinit var qualityBadge: TextView
    private lateinit var genresText: TextView
    private lateinit var playButton: Button
    private lateinit var watchlistButton: Button
    private lateinit var resumeContainer: View
    private lateinit var resumeProgress: ProgressBar
    private lateinit var resumeLabel: TextView
    private lateinit var overviewText: TextView
    private lateinit var directorContainer: View
    private lateinit var directorText: TextView
    private lateinit var seasonsContainer: View
    private lateinit var seasonSpinner: TextView
    private lateinit var episodesLoading: ProgressBar
    private lateinit var episodesRecycler: RecyclerView
    private lateinit var castContainer: View
    private lateinit var castRecycler: RecyclerView
    private lateinit var recommendationsContainer: View
    private lateinit var recommendationsRecycler: RecyclerView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_detail_netflix, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bindViews(view)
        setupNavigation()
        setupButtons()
        observeState()
        observeEvents()
        
        // Load content
        val mediaType = MediaType.valueOf(args.mediaType)
        viewModel.loadContent(args.contentId, mediaType)
    }
    
    private fun bindViews(view: View) {
        scrollView = view.findViewById(R.id.scroll_view)
        loadingView = view.findViewById(R.id.loading_view)
        errorView = view.findViewById(R.id.error_view)
        contentContainer = view.findViewById(R.id.content_container)
        backdropImage = view.findViewById(R.id.backdrop_image)
        backButton = view.findViewById(R.id.back_button)
        posterImage = view.findViewById(R.id.poster_image)
        titleText = view.findViewById(R.id.title_text)
        ratingText = view.findViewById(R.id.rating_text)
        yearText = view.findViewById(R.id.year_text)
        runtimeText = view.findViewById(R.id.runtime_text)
        qualityBadge = view.findViewById(R.id.quality_badge)
        genresText = view.findViewById(R.id.genres_text)
        playButton = view.findViewById(R.id.play_button)
        watchlistButton = view.findViewById(R.id.watchlist_button)
        resumeContainer = view.findViewById(R.id.resume_container)
        resumeProgress = view.findViewById(R.id.resume_progress)
        resumeLabel = view.findViewById(R.id.resume_label)
        overviewText = view.findViewById(R.id.overview_text)
        directorContainer = view.findViewById(R.id.director_container)
        directorText = view.findViewById(R.id.director_text)
        seasonsContainer = view.findViewById(R.id.seasons_container)
        seasonSpinner = view.findViewById(R.id.season_spinner)
        episodesLoading = view.findViewById(R.id.episodes_loading)
        episodesRecycler = view.findViewById(R.id.episodes_recycler)
        castContainer = view.findViewById(R.id.cast_container)
        castRecycler = view.findViewById(R.id.cast_recycler)
        recommendationsContainer = view.findViewById(R.id.recommendations_container)
        recommendationsRecycler = view.findViewById(R.id.recommendations_recycler)
    }
    
    private fun setupNavigation() {
        // Back button
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // Back button focus handling — no scale, just alpha
        backButton.setOnFocusChangeListener { v, hasFocus ->
            v.animate().alpha(if (hasFocus) 1f else 0.7f).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
        
        // D-pad back key
        view?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                findNavController().navigateUp()
                true
            } else false
        }
    }
    
    private fun setupButtons() {
        // Play button
        playButton.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.canResume) {
                viewModel.resumeWatching()
            } else if (state.isMovie) {
                viewModel.playMovie()
            } else {
                state.currentSeasonDetail?.episodes?.firstOrNull()?.let {
                    viewModel.playEpisode(it)
                }
            }
        }
        
        // Watchlist button
        watchlistButton.setOnClickListener {
            viewModel.toggleWatchlist()
        }
        
        // Focus handling for buttons — no scale, just alpha
        listOf(playButton, watchlistButton).forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                v.animate().alpha(if (hasFocus) 1f else 0.8f).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }
        
        // Request initial focus
        playButton.requestFocus()
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    loadingView.isVisible = state.isLoading
                    errorView.isVisible = state.error != null && !state.isLoading
                    contentContainer.isVisible = !state.isLoading && state.error == null
                    
                    if (!state.isLoading && state.error == null) {
                        updateUI(state)
                    }
                    
                    // Episodes loading state
                    episodesLoading.isVisible = state.isLoadingEpisodes
                }
            }
        }
    }
    
    private fun updateUI(state: DetailUiState) {
        // Backdrop
        backdropImage.load(state.backdropUrl) {
            crossfade(300)
            placeholder(R.drawable.bg_card)
        }
        
        // Poster with rounded corners
        posterImage.load(state.posterUrl) {
            crossfade(200)
            placeholder(R.drawable.bg_card)
        }
        
        // Text content
        titleText.text = state.title
        ratingText.text = state.rating
        yearText.text = state.year ?: ""
        genresText.text = state.genres
        overviewText.text = state.overview
        
        // Runtime (movies) or season info (TV)
        runtimeText.text = state.movieDetail?.runtimeDisplay 
            ?: state.tvShowDetail?.seasonCountDisplay 
            ?: ""
        
        // Director (for movies)
        val directors = state.movieDetail?.directors
        if (!directors.isNullOrEmpty()) {
            directorContainer.isVisible = true
            directorText.text = directors.joinToString(", ") { it.name }
        } else {
            directorContainer.isVisible = false
        }
        
        // Watchlist button state
        updateWatchlistButton(state.isInWatchlist)
        
        // Resume progress
        if (state.canResume) {
            playButton.text = getString(R.string.action_resume)
            resumeContainer.isVisible = true
            val progressPercent = (state.resumeProgress * 100).toInt()
            resumeProgress.progress = progressPercent
            resumeLabel.text = "$progressPercent% watched"
        } else {
            playButton.text = getString(R.string.action_play)
            resumeContainer.isVisible = false
        }
        
        // Cast section
        val cast = state.movieDetail?.cast ?: state.tvShowDetail?.cast ?: emptyList()
        if (cast.isNotEmpty()) {
            castContainer.isVisible = true
            setupCastRecycler(cast)
        } else {
            castContainer.isVisible = false
        }
        
        // TV Show specific - seasons and episodes
        if (state.isTvShow) {
            seasonsContainer.isVisible = true
            setupSeasonsAndEpisodes(state)
        } else {
            seasonsContainer.isVisible = false
        }
        
        // Recommendations
        val recommendations = state.movieDetail?.recommendations 
            ?: state.tvShowDetail?.recommendations 
            ?: emptyList()
        if (recommendations.isNotEmpty()) {
            recommendationsContainer.isVisible = true
            setupRecommendationsRecycler(recommendations)
        } else {
            recommendationsContainer.isVisible = false
        }
    }
    
    private fun updateWatchlistButton(isInWatchlist: Boolean) {
        watchlistButton.text = if (isInWatchlist) {
            getString(R.string.action_remove_from_list)
        } else {
            getString(R.string.action_add_to_list)
        }
        
        // Update icon
        val iconRes = if (isInWatchlist) R.drawable.ic_check else R.drawable.ic_add
        watchlistButton.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
    }
    
    private fun setupCastRecycler(cast: List<CastMember>) {
        castRecycler.layoutManager = LinearLayoutManager(
            requireContext(), 
            LinearLayoutManager.HORIZONTAL, 
            false
        )
        castRecycler.adapter = CastNetflixAdapter(cast)
    }
    
    private fun setupSeasonsAndEpisodes(state: DetailUiState) {
        val tvShow = state.tvShowDetail ?: return
        
        // Season selector
        val selectedSeason = tvShow.seasons.find { it.seasonNumber == state.selectedSeasonNumber }
        seasonSpinner.text = selectedSeason?.name ?: "Season ${state.selectedSeasonNumber}"
        
        // Season spinner click
        seasonSpinner.setOnClickListener {
            showSeasonPicker(tvShow.seasons, state.selectedSeasonNumber)
        }
        
        // D-pad enter on season spinner
        seasonSpinner.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && 
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                showSeasonPicker(tvShow.seasons, state.selectedSeasonNumber)
                true
            } else false
        }
        
        // Season spinner focus — no scale
        seasonSpinner.setOnFocusChangeListener { v, hasFocus ->
            v.animate().alpha(if (hasFocus) 1f else 0.8f).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
        
        // Episodes (horizontal scroll)
        state.currentSeasonDetail?.let { season ->
            episodesRecycler.isVisible = true
            episodesRecycler.layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            episodesRecycler.adapter = EpisodeNetflixAdapter(season.episodes) { episode ->
                viewModel.playEpisode(episode)
            }
        }
    }
    
    private fun showSeasonPicker(seasons: List<Season>, currentSeason: Int) {
        val seasonNames = seasons.map { it.name }.toTypedArray()
        val currentIndex = seasons.indexOfFirst { it.seasonNumber == currentSeason }
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.SimplStreamDialogTheme)
            .setTitle("Select Season")
            .setSingleChoiceItems(seasonNames, currentIndex) { dlg, which ->
                val selected = seasons[which]
                viewModel.selectSeason(selected.seasonNumber)
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        // D-pad support
        dialog.setOnShowListener {
            dialog.listView?.let { listView ->
                listView.isFocusable = true
                listView.post {
                    listView.setSelection(currentIndex.coerceAtLeast(0))
                    listView.requestFocus()
                }
            }
        }
        dialog.show()
    }
    
    private fun setupRecommendationsRecycler(recommendations: List<Content>) {
        recommendationsRecycler.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        recommendationsRecycler.adapter = RecommendationNetflixAdapter(recommendations) { content ->
            val action = DetailNetflixFragmentDirections.actionDetailSelf(
                contentId = content.id,
                mediaType = content.mediaType.name
            )
            findNavController().navigate(action)
        }
    }
    
    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    try {
                        when (event) {
                            is DetailEvent.PlayContent -> {
                                if (event.sources.isNotEmpty()) {
                                    navigateToPlayer(event)
                                } else {
                                    Toast.makeText(
                                        requireContext(), 
                                        "No video sources available", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            is DetailEvent.AddedToWatchlist -> {
                                Toast.makeText(
                                    requireContext(), 
                                    R.string.added_to_watchlist, 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is DetailEvent.RemovedFromWatchlist -> {
                                Toast.makeText(
                                    requireContext(), 
                                    R.string.removed_from_watchlist, 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            requireContext(), 
                            "Error: ${e.message}", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    
    private fun navigateToPlayer(event: DetailEvent.PlayContent) {
        try {
            val args = NativePlaybackArgs(
                contentId = event.contentId,
                title = event.title,
                mediaType = event.mediaType,
                imdbId = event.imdbId,
                seasonNumber = event.seasonNumber,
                episodeNumber = event.episodeNumber,
                episodeName = event.episodeName,
                resumePosition = event.resumePosition,
                posterUrl = event.posterUrl
            )

            val action = DetailNetflixFragmentDirections.actionDetailToPlayer(args)
            findNavController().navigate(action)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(), 
                "Navigation error: ${e.message}", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

// ===================== ADAPTERS =====================

/**
 * Netflix-style Cast Adapter with circular photos
 */
class CastNetflixAdapter(
    private val cast: List<CastMember>
) : RecyclerView.Adapter<CastNetflixAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: ImageView = view.findViewById(R.id.profile_image)
        val nameText: TextView = view.findViewById(R.id.name_text)
        val characterText: TextView = view.findViewById(R.id.character_text)
        val focusRing: View = view.findViewById(R.id.focus_ring)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cast_netflix, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val castMember = cast[position]
        
        // Circular profile image
        holder.profileImage.load(castMember.profileUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_card)
            error(R.drawable.bg_card)
            transformations(CircleCropTransformation())
        }
        
        holder.nameText.text = castMember.name
        holder.characterText.text = castMember.character ?: ""
        
        // Focus handling — clean, no border
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            holder.focusRing.isVisible = hasFocus
            val scale = if (hasFocus) 1.02f else 1f
            v.animate().scaleX(scale).scaleY(scale).alpha(if (hasFocus) 1f else 0.8f).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }

    override fun getItemCount() = cast.size
}

/**
 * Netflix-style Episode Adapter with landscape cards
 */
class EpisodeNetflixAdapter(
    private val episodes: List<Episode>,
    private val onEpisodeClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeNetflixAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContainer: CardView = view.findViewById(R.id.card_container)
        val stillImage: ImageView = view.findViewById(R.id.still_image)
        val episodeBadge: TextView = view.findViewById(R.id.episode_badge)
        val runtimeBadge: TextView = view.findViewById(R.id.runtime_badge)
        val playOverlay: View = view.findViewById(R.id.play_overlay)
        val watchProgress: ProgressBar = view.findViewById(R.id.watch_progress)
        val episodeCode: TextView = view.findViewById(R.id.episode_code)
        val episodeName: TextView = view.findViewById(R.id.episode_name)
        val episodeOverview: TextView = view.findViewById(R.id.episode_overview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode_netflix, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val episode = episodes[position]

        // Episode still image
        holder.stillImage.load(episode.stillUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_card)
            error(R.drawable.bg_card)
        }

        // Episode badge
        holder.episodeBadge.text = "E${episode.episodeNumber}"

        // Runtime badge
        episode.runtimeDisplay?.let {
            holder.runtimeBadge.text = it
            holder.runtimeBadge.isVisible = true
        } ?: run {
            holder.runtimeBadge.isVisible = false
        }

        // Episode info
        holder.episodeCode.text = "S${String.format("%02d", episode.seasonNumber)} • E${String.format("%02d", episode.episodeNumber)}"
        holder.episodeName.text = episode.name
        holder.episodeOverview.text = episode.overview ?: ""
        holder.episodeOverview.isVisible = !episode.overview.isNullOrBlank()

        // Click handler
        holder.itemView.setOnClickListener { onEpisodeClick(episode) }

        // D-pad focus handling — clip to outline to prevent black rect on scale
        holder.cardContainer.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        holder.cardContainer.clipToOutline = true
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            holder.playOverlay.isVisible = hasFocus

            val scale = if (hasFocus) 1.03f else 1f
            v.animate()
                .scaleX(scale).scaleY(scale)
                .alpha(if (hasFocus) 1f else 0.85f)
                .setDuration(120)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        // D-pad enter key
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                onEpisodeClick(episode)
                true
            } else false
        }
    }

    override fun getItemCount() = episodes.size
}

/**
 * Netflix-style Recommendation Adapter with landscape cards
 */
class RecommendationNetflixAdapter(
    private val recommendations: List<Content>,
    private val onContentClick: (Content) -> Unit
) : RecyclerView.Adapter<RecommendationNetflixAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContainer: CardView = view.findViewById(R.id.card_container)
        val backdropImage: ImageView = view.findViewById(R.id.backdrop_image)
        val ratingText: TextView = view.findViewById(R.id.rating_text)
        val typeIndicator: View = view.findViewById(R.id.type_indicator)
        val titleText: TextView = view.findViewById(R.id.title_text)
        val metadataText: TextView = view.findViewById(R.id.metadata_text)
        val matchText: TextView = view.findViewById(R.id.match_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation_netflix, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val content = recommendations[position]
        
        // Backdrop image
        holder.backdropImage.load(content.backdropUrl ?: content.posterUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_card)
            error(R.drawable.bg_card)
        }
        
        // Rating
        holder.ratingText.text = content.ratingDisplay
        
        // Type indicator color
        val indicatorColor = when (content.mediaType) {
            MediaType.MOVIE -> Color.parseColor("#3B82F6") // Blue
            MediaType.TV -> Color.parseColor("#10B981")    // Green
        }
        holder.typeIndicator.setBackgroundColor(indicatorColor)
        
        // Title and metadata
        holder.titleText.text = content.title
        holder.metadataText.text = buildString {
            content.year?.let { append(it) }
            append(" • ")
            append(if (content.mediaType == MediaType.MOVIE) "Movie" else "Series")
        }
        
        // Show match percentage for high-rated content (simulated)
        if (content.voteAverage >= 7.5f) {
            val matchPercent = (70 + (content.voteAverage * 3)).toInt().coerceAtMost(99)
            holder.matchText.text = "$matchPercent% Match"
            holder.matchText.isVisible = true
        } else {
            holder.matchText.isVisible = false
        }
        
        // Click handler
        holder.itemView.setOnClickListener { onContentClick(content) }
        
        // Focus handling — clip to outline to prevent black rect on scale
        holder.cardContainer.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        holder.cardContainer.clipToOutline = true
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.03f else 1f
            v.animate()
                .scaleX(scale).scaleY(scale)
                .alpha(if (hasFocus) 1f else 0.85f)
                .setDuration(120)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    override fun getItemCount() = recommendations.size
}
