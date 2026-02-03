package com.simplstudios.simplstream.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simplstudios.simplstream.R
import com.simplstudios.simplstream.domain.model.Content
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * TV Shows tab - shows only TV content
 */
@AndroidEntryPoint
class TvShowsFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels({ requireParentFragment() })
    
    private lateinit var rowsContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_category_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        rowsContainer = view.findViewById(R.id.rows_container)
        observeState()
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!state.isLoading) {
                        updateContent(state)
                    }
                }
            }
        }
    }
    
    private fun updateContent(state: HomeUiState) {
        rowsContainer.removeAllViews()
        
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
            addContentRow("Currently Airing", state.onTheAirTvShows)
        }
        
        // Trending (filter to TV only)
        val trendingTv = state.trending.filter { 
            it.mediaType == com.simplstudios.simplstream.domain.model.MediaType.TV 
        }
        if (trendingTv.isNotEmpty()) {
            addContentRow("Trending TV Shows", trendingTv)
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
}
