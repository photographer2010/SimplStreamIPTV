package com.simplstudios.simplstream.presentation.foryou

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
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.simplstudios.simplstream.R
import com.simplstudios.simplstream.domain.model.*
import com.simplstudios.simplstream.presentation.home.MainContainerFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * For You Fragment - AI-Powered Personalized Recommendations
 * 
 * A beautiful, Netflix-style personalized content discovery experience.
 * Features:
 * - Spotlight hero section for top recommendation
 * - Dynamic sections based on viewing history
 * - Match percentages for each recommendation
 * - Genre-based discovery sections
 * - Smooth D-pad navigation
 */
@AndroidEntryPoint
class ForYouFragment : Fragment() {
    
    private val viewModel: ForYouViewModel by viewModels()
    
    // Views
    private lateinit var scrollView: View
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var contentContainer: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    
    // Spotlight views
    private lateinit var spotlightContainer: FrameLayout
    private lateinit var spotlightBackdrop: ImageView
    private lateinit var spotlightTagline: TextView
    private lateinit var spotlightTitle: TextView
    private lateinit var spotlightMatch: TextView
    private lateinit var spotlightRating: TextView
    private lateinit var spotlightYear: TextView
    private lateinit var spotlightPlayButton: Button
    private lateinit var spotlightInfoButton: Button
    
    // Header views
    private lateinit var headerSection: View
    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: ImageView
    
    // Sections container
    private lateinit var sectionsContainer: LinearLayout
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_for_you, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bindViews(view)
        setupButtons()
        observeState()
        observeEvents()
    }
    
    private fun bindViews(view: View) {
        scrollView = view.findViewById(R.id.scroll_view)
        loadingView = view.findViewById(R.id.loading_view)
        errorView = view.findViewById(R.id.error_view)
        contentContainer = view.findViewById(R.id.content_container)
        errorText = view.findViewById(R.id.error_text)
        retryButton = view.findViewById(R.id.retry_button)
        
        // Spotlight
        spotlightContainer = view.findViewById(R.id.spotlight_container)
        spotlightBackdrop = view.findViewById(R.id.spotlight_backdrop)
        spotlightTagline = view.findViewById(R.id.spotlight_tagline)
        spotlightTitle = view.findViewById(R.id.spotlight_title)
        spotlightMatch = view.findViewById(R.id.spotlight_match)
        spotlightRating = view.findViewById(R.id.spotlight_rating)
        spotlightYear = view.findViewById(R.id.spotlight_year)
        spotlightPlayButton = view.findViewById(R.id.spotlight_play_button)
        spotlightInfoButton = view.findViewById(R.id.spotlight_info_button)
        
        // Header
        headerSection = view.findViewById(R.id.header_section)
        subtitleText = view.findViewById(R.id.subtitle_text)
        refreshButton = view.findViewById(R.id.refresh_button)
        
        // Sections
        sectionsContainer = view.findViewById(R.id.sections_container)
    }
    
    private fun setupButtons() {
        // Retry button
        retryButton.setOnClickListener {
            viewModel.loadRecommendations()
        }
        
        // Refresh button
        refreshButton.setOnClickListener {
            viewModel.refreshRecommendations()
        }
        
        // Focus handling for refresh button
        refreshButton.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.1f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
        }
        
        // Spotlight button focus handling
        listOf(spotlightPlayButton, spotlightInfoButton).forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.05f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            }
        }
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }
    
    private fun updateUI(state: ForYouState) {
        // View visibility
        loadingView.isVisible = state.isLoading
        errorView.isVisible = state.error != null && !state.isLoading
        contentContainer.isVisible = !state.isLoading && state.error == null
        
        // Error state
        if (state.error != null) {
            errorText.text = state.error
            return
        }
        
        // Spotlight section
        state.spotlight?.let { spotlight ->
            spotlightContainer.isVisible = true
            updateSpotlight(spotlight)
        } ?: run {
            spotlightContainer.isVisible = false
        }
        
        // Update subtitle with taste info
        state.tasteProfile?.let { profile ->
            val topGenres = profile.preferredGenres.take(3).joinToString(", ") { it.genreName }
            if (topGenres.isNotEmpty()) {
                subtitleText.text = "Based on your love for $topGenres"
            } else {
                subtitleText.text = "Personalized recommendations just for you"
            }
        } ?: run {
            subtitleText.text = "Personalized recommendations just for you"
        }
        
        // Update sections
        updateSections(state.sections)
        
        // Show refresh animation
        if (state.isRefreshing) {
            refreshButton.animate()
                .rotation(refreshButton.rotation + 360f)
                .setDuration(800)
                .start()
        }
    }
    
    private fun updateSpotlight(spotlight: SpotlightContent) {
        val content = spotlight.content
        
        // Backdrop
        spotlightBackdrop.load(content.content.backdropUrl ?: content.content.posterUrl) {
            crossfade(300)
            placeholder(R.drawable.bg_card)
        }
        
        // Tagline
        spotlightTagline.text = spotlight.tagline
        
        // Title
        spotlightTitle.text = content.content.title
        
        // Match percentage
        spotlightMatch.text = content.matchDisplay
        spotlightMatch.setTextColor(
            when {
                content.matchScore >= 90 -> Color.parseColor("#22C55E") // Green
                content.matchScore >= 75 -> Color.parseColor("#EAB308") // Yellow
                else -> Color.parseColor("#6B7280") // Gray
            }
        )
        
        // Rating
        spotlightRating.text = content.content.ratingDisplay
        
        // Year
        spotlightYear.text = content.content.year ?: ""
        
        // Button actions
        spotlightPlayButton.setOnClickListener {
            viewModel.onContentClicked(content.content)
        }
        
        spotlightInfoButton.setOnClickListener {
            viewModel.onContentClicked(content.content)
        }
    }
    
    private fun updateSections(sections: List<RecommendationSection>) {
        sectionsContainer.removeAllViews()
        
        sections.forEach { section ->
            val sectionView = layoutInflater.inflate(
                R.layout.item_for_you_section,
                sectionsContainer,
                false
            )
            
            // Section icon
            val iconView = sectionView.findViewById<ImageView>(R.id.section_icon)
            iconView.setImageResource(getSectionIcon(section.icon))
            iconView.setColorFilter(getSectionIconColor(section.type))
            
            // Section title
            val titleView = sectionView.findViewById<TextView>(R.id.section_title)
            titleView.text = section.title
            
            // Section subtitle
            val subtitleView = sectionView.findViewById<TextView>(R.id.section_subtitle)
            if (!section.subtitle.isNullOrBlank()) {
                subtitleView.text = section.subtitle
                subtitleView.visibility = View.VISIBLE
            } else if (!section.reason.isNullOrBlank()) {
                subtitleView.text = section.reason
                subtitleView.visibility = View.VISIBLE
            } else {
                subtitleView.visibility = View.GONE
            }
            
            // RecyclerView
            val recyclerView = sectionView.findViewById<RecyclerView>(R.id.section_recycler)
            recyclerView.layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            recyclerView.adapter = RecommendationCardAdapter(section.items) { recommended ->
                viewModel.onContentClicked(recommended.content)
            }
            
            sectionsContainer.addView(sectionView)
        }
    }
    
    private fun getSectionIcon(icon: RecommendationIcon): Int = when (icon) {
        RecommendationIcon.SPARKLE -> R.drawable.ic_sparkle
        RecommendationIcon.FIRE -> R.drawable.ic_trending
        RecommendationIcon.STAR -> R.drawable.ic_star
        RecommendationIcon.HEART -> R.drawable.ic_heart
        RecommendationIcon.CLOCK -> R.drawable.ic_clock
        RecommendationIcon.GEM -> R.drawable.ic_sparkle // Use sparkle as gem
        RecommendationIcon.TROPHY -> R.drawable.ic_star // Use star as trophy
        RecommendationIcon.NEW_TAG -> R.drawable.ic_new_releases
        RecommendationIcon.PLAY_CIRCLE -> R.drawable.ic_play_arrow
        RecommendationIcon.FILM -> R.drawable.ic_movie
    }
    
    private fun getSectionIconColor(type: RecommendationType): Int = when (type) {
        RecommendationType.BECAUSE_YOU_WATCHED -> Color.parseColor("#EC4899") // Pink
        RecommendationType.TOP_PICKS -> Color.parseColor("#2563EB") // Blue
        RecommendationType.TRENDING_FOR_YOU -> Color.parseColor("#F97316") // Orange
        RecommendationType.NEW_RELEASES -> Color.parseColor("#22C55E") // Green
        RecommendationType.HIDDEN_GEMS -> Color.parseColor("#8B5CF6") // Purple
        RecommendationType.GENRE_SPOTLIGHT -> Color.parseColor("#06B6D4") // Cyan
        RecommendationType.BINGE_WORTHY -> Color.parseColor("#EF4444") // Red
        RecommendationType.CONTINUE_WATCHING -> Color.parseColor("#F59E0B") // Amber
        RecommendationType.SIMILAR_TO_LIKED -> Color.parseColor("#EC4899") // Pink
        RecommendationType.CRITICS_CHOICE -> Color.parseColor("#EAB308") // Yellow
    }
    
    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ForYouEvent.NavigateToDetail -> {
                            // Navigate via parent container
                            (parentFragment as? MainContainerFragment)?.navigateToDetail(
                                event.contentId,
                                event.mediaType.name
                            )
                        }
                        is ForYouEvent.ShowError -> {
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }
                        is ForYouEvent.RefreshComplete -> {
                            Toast.makeText(requireContext(), "Recommendations updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Adapter for personalized recommendation cards
 */
class RecommendationCardAdapter(
    private val items: List<RecommendedContent>,
    private val onItemClick: (RecommendedContent) -> Unit
) : RecyclerView.Adapter<RecommendationCardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContainer: CardView = view.findViewById(R.id.card_container)
        val backdropImage: ImageView = view.findViewById(R.id.backdrop_image)
        val matchBadge: TextView = view.findViewById(R.id.match_badge)
        val statusBadge: TextView = view.findViewById(R.id.status_badge)
        val typeIndicator: View = view.findViewById(R.id.type_indicator)
        val ratingText: TextView = view.findViewById(R.id.rating_text)
        val titleText: TextView = view.findViewById(R.id.title_text)
        val matchReason: TextView = view.findViewById(R.id.match_reason)
        val focusBorder: View = view.findViewById(R.id.focus_border)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val content = item.content
        
        // Backdrop
        holder.backdropImage.load(content.backdropUrl ?: content.posterUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_card)
            error(R.drawable.bg_card)
        }
        
        // Match badge with color coding
        holder.matchBadge.text = item.matchDisplay
        val matchColor = when {
            item.matchScore >= 90 -> "#22C55E" // Bright green
            item.matchScore >= 80 -> "#4ADE80" // Light green
            item.matchScore >= 70 -> "#EAB308" // Yellow
            else -> "#6B7280" // Gray
        }
        holder.matchBadge.background.setTint(Color.parseColor(matchColor))
        
        // Status badge (NEW or TRENDING)
        when {
            item.isNewRelease -> {
                holder.statusBadge.text = "NEW"
                holder.statusBadge.background.setTint(Color.parseColor("#EF4444"))
                holder.statusBadge.visibility = View.VISIBLE
            }
            item.isTrending -> {
                holder.statusBadge.text = "TRENDING"
                holder.statusBadge.background.setTint(Color.parseColor("#F97316"))
                holder.statusBadge.visibility = View.VISIBLE
            }
            else -> {
                holder.statusBadge.visibility = View.GONE
            }
        }
        
        // Type indicator color
        val indicatorColor = when (content.mediaType) {
            MediaType.MOVIE -> Color.parseColor("#3B82F6") // Blue
            MediaType.TV -> Color.parseColor("#10B981")    // Green
        }
        holder.typeIndicator.setBackgroundColor(indicatorColor)
        
        // Rating
        holder.ratingText.text = content.ratingDisplay
        
        // Title
        holder.titleText.text = content.title
        
        // Match reason text
        val reasonText = buildString {
            val mediaTypeStr = if (content.mediaType == MediaType.MOVIE) "Movie" else "Series"
            append(mediaTypeStr)
            content.year?.let {
                append(" • ")
                append(it)
            }
            item.matchReasons.firstOrNull()?.let {
                append(" • ")
                append(it.description)
            }
        }
        holder.matchReason.text = reasonText
        
        // Click handler
        holder.itemView.setOnClickListener { onItemClick(item) }
        
        // D-pad focus handling
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            holder.focusBorder.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            
            val scale = if (hasFocus) 1.06f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            holder.cardContainer.cardElevation = if (hasFocus) 12f else 0f
        }
        
        // D-pad enter key
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                onItemClick(item)
                true
            } else false
        }
    }

    override fun getItemCount() = items.size
}
