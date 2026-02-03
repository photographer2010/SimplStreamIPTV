package com.simplstudios.simplstream.presentation.home

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.simplstudios.simplstream.R
import com.simplstudios.simplstream.domain.model.Content
import com.simplstudios.simplstream.domain.model.MediaType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Netflix-style Home Fragment with Hero Carousel and Landscape Cards
 */
@AndroidEntryPoint
class HomeNetflixFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels({ requireParentFragment() })
    
    // Hero views
    private lateinit var heroBackdrop: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroRating: TextView
    private lateinit var heroYear: TextView
    private lateinit var heroGenre: TextView
    private lateinit var heroDescription: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnMoreInfo: Button
    private lateinit var dotsIndicator: LinearLayout
    
    // Content rows container
    private lateinit var rowsContainer: LinearLayout
    
    // Hero carousel state
    private var featuredContent: List<Content> = emptyList()
    private var currentHeroIndex = 0
    private val heroHandler = Handler(Looper.getMainLooper())
    private val heroRotateRunnable = Runnable { rotateHero() }
    private val HERO_ROTATE_DELAY = 8000L // 8 seconds

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home_netflix, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bindViews(view)
        setupHeroButtons()
        observeState()
    }
    
    private fun bindViews(view: View) {
        heroBackdrop = view.findViewById(R.id.hero_backdrop)
        heroTitle = view.findViewById(R.id.hero_title)
        heroRating = view.findViewById(R.id.hero_rating)
        heroYear = view.findViewById(R.id.hero_year)
        heroGenre = view.findViewById(R.id.hero_genre)
        heroDescription = view.findViewById(R.id.hero_description)
        btnPlay = view.findViewById(R.id.btn_play)
        btnMoreInfo = view.findViewById(R.id.btn_more_info)
        dotsIndicator = view.findViewById(R.id.dots_indicator)
        rowsContainer = view.findViewById(R.id.rows_container)
    }
    
    private fun setupHeroButtons() {
        // Focus handling for hero buttons
        listOf(btnPlay, btnMoreInfo).forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
        }
        
        btnPlay.setOnClickListener {
            val content = featuredContent.getOrNull(currentHeroIndex) ?: return@setOnClickListener
            navigateToDetail(content)
        }
        
        btnMoreInfo.setOnClickListener {
            val content = featuredContent.getOrNull(currentHeroIndex) ?: return@setOnClickListener
            navigateToDetail(content)
        }
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!state.isLoading) {
                        updateHeroCarousel(state.featuredContent)
                        updateContentRows(state)
                    }
                }
            }
        }
    }
    
    private fun updateHeroCarousel(featured: List<Content>) {
        featuredContent = featured.take(5)
        if (featuredContent.isEmpty()) return
        
        currentHeroIndex = 0
        displayHeroContent(featuredContent[currentHeroIndex])
        setupDotsIndicator()
        startHeroRotation()
    }
    
    private fun displayHeroContent(content: Content) {
        // Load backdrop
        heroBackdrop.load(content.backdropUrl ?: content.posterUrl) {
            crossfade(300)
            placeholder(R.drawable.bg_card)
            error(R.drawable.bg_card)
        }
        
        heroTitle.text = content.title
        heroRating.text = content.ratingDisplay
        heroYear.text = content.year ?: ""
        heroGenre.text = when (content.mediaType) {
            MediaType.MOVIE -> "Movie"
            MediaType.TV -> "TV Series"
        }
        heroDescription.text = content.overview
        
        // Update dots
        updateDotsIndicator()
    }
    
    private fun setupDotsIndicator() {
        dotsIndicator.removeAllViews()
        
        featuredContent.forEachIndexed { index, _ ->
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(8, 8).apply {
                    marginEnd = 8
                }
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_current_server_dot)
                alpha = if (index == currentHeroIndex) 1f else 0.3f
            }
            dotsIndicator.addView(dot)
        }
    }
    
    private fun updateDotsIndicator() {
        for (i in 0 until dotsIndicator.childCount) {
            dotsIndicator.getChildAt(i)?.alpha = if (i == currentHeroIndex) 1f else 0.3f
        }
    }
    
    private fun rotateHero() {
        if (featuredContent.isEmpty()) return
        
        currentHeroIndex = (currentHeroIndex + 1) % featuredContent.size
        
        // Animate transition
        heroBackdrop.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                displayHeroContent(featuredContent[currentHeroIndex])
                heroBackdrop.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
            .start()
        
        // Schedule next rotation
        heroHandler.postDelayed(heroRotateRunnable, HERO_ROTATE_DELAY)
    }
    
    private fun startHeroRotation() {
        heroHandler.removeCallbacks(heroRotateRunnable)
        heroHandler.postDelayed(heroRotateRunnable, HERO_ROTATE_DELAY)
    }
    
    private fun stopHeroRotation() {
        heroHandler.removeCallbacks(heroRotateRunnable)
    }
    
    private fun updateContentRows(state: HomeUiState) {
        rowsContainer.removeAllViews()
        
        // Continue Watching (if any)
        if (state.hasContinueWatching) {
            val continueItems = state.continueWatching.map { it.toContent() }
            addContentRow("Continue Watching", continueItems)
        }
        
        // My List (if any)
        if (state.hasMyList) {
            val myListItems = state.myList.map { it.toContent() }
            addContentRow("My List", myListItems)
        }
        
        // Trending
        if (state.trending.isNotEmpty()) {
            addContentRow("Trending Now", state.trending)
        }
        
        // Popular Movies
        if (state.popularMovies.isNotEmpty()) {
            addContentRow("Popular Movies", state.popularMovies)
        }
        
        // Top Rated Movies
        if (state.topRatedMovies.isNotEmpty()) {
            addContentRow("Top Rated Movies", state.topRatedMovies)
        }
        
        // Now Playing
        if (state.nowPlayingMovies.isNotEmpty()) {
            addContentRow("Now Playing", state.nowPlayingMovies)
        }
        
        // Popular TV Shows
        if (state.popularTvShows.isNotEmpty()) {
            addContentRow("Popular TV Shows", state.popularTvShows)
        }
        
        // Top Rated TV Shows
        if (state.topRatedTvShows.isNotEmpty()) {
            addContentRow("Top Rated TV Shows", state.topRatedTvShows)
        }
        
        // On The Air
        if (state.onTheAirTvShows.isNotEmpty()) {
            addContentRow("On The Air", state.onTheAirTvShows)
        }
    }
    
    private fun addContentRow(title: String, items: List<Content>) {
        val rowView = layoutInflater.inflate(R.layout.item_content_row, rowsContainer, false)
        
        val titleText = rowView.findViewById<TextView>(R.id.row_title)
        val recyclerView = rowView.findViewById<RecyclerView>(R.id.row_recycler)
        
        titleText.text = title
        
        recyclerView.layoutManager = LinearLayoutManager(
            requireContext(), 
            LinearLayoutManager.HORIZONTAL, 
            false
        )
        recyclerView.adapter = LandscapeCardAdapter(items) { content ->
            navigateToDetail(content)
        }
        
        rowsContainer.addView(rowView)
    }
    
    private fun navigateToDetail(content: Content) {
        (parentFragment as? MainContainerFragment)?.navigateToDetail(
            contentId = content.id,
            mediaType = content.mediaType.name
        )
    }
    
    override fun onResume() {
        super.onResume()
        startHeroRotation()
    }
    
    override fun onPause() {
        super.onPause()
        stopHeroRotation()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        stopHeroRotation()
    }
}

/**
 * Adapter for landscape content cards
 */
class LandscapeCardAdapter(
    private val items: List<Content>,
    private val onItemClick: (Content) -> Unit
) : RecyclerView.Adapter<LandscapeCardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardImage: ImageView = view.findViewById(R.id.card_image)
        val titleText: TextView = view.findViewById(R.id.title_text)
        val metadataText: TextView = view.findViewById(R.id.metadata_text)
        val ratingText: TextView = view.findViewById(R.id.rating_text)
        val typeIndicator: View = view.findViewById(R.id.type_indicator)
        val focusBorder: View = view.findViewById(R.id.focus_border)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_landscape_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val content = items[position]
        
        // Load backdrop image (landscape)
        holder.cardImage.load(content.backdropUrl ?: content.posterUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_card)
            error(R.drawable.bg_card)
        }
        
        holder.titleText.text = content.title
        holder.metadataText.text = buildString {
            content.year?.let { append(it) }
            append(" • ")
            append(if (content.mediaType == MediaType.MOVIE) "Movie" else "Series")
        }
        holder.ratingText.text = content.ratingDisplay
        
        // Type indicator color
        val indicatorColor = when (content.mediaType) {
            MediaType.MOVIE -> Color.parseColor("#3B82F6") // Blue
            MediaType.TV -> Color.parseColor("#10B981")    // Green
        }
        holder.typeIndicator.setBackgroundColor(indicatorColor)
        
        // Focus handling
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            holder.focusBorder.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            
            val scale = if (hasFocus) 1.08f else 1.0f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(150)
                .start()
            
            // Lift card when focused
            v.elevation = if (hasFocus) 16f else 0f
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(content)
        }
    }

    override fun getItemCount() = items.size
}
