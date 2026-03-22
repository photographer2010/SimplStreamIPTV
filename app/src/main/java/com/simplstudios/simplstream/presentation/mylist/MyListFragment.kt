package com.simplstudios.simplstream.presentation.mylist

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.simplstudios.simplstream.R
import com.simplstudios.simplstream.domain.model.Content
import com.simplstudios.simplstream.domain.model.WatchlistItem
import com.simplstudios.simplstream.presentation.common.ContentCardPresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Full-screen My List / Watchlist fragment
 * Shows all saved content in a grid layout with search filtering
 */
@AndroidEntryPoint
class MyListFragment : VerticalGridSupportFragment() {

    private val viewModel: MyListViewModel by viewModels()

    private lateinit var gridAdapter: ArrayObjectAdapter
    private var allItems: List<WatchlistItem> = emptyList()
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeState()
    }

    private fun setupUI() {
        title = "My List"

        // Fix Leanback green search orb — use brand blue
        searchAffordanceColor = resources.getColor(R.color.simpl_blue, null)

        // Grid presenter with 5 columns
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = 5
        setGridPresenter(gridPresenter)

        // Content adapter
        gridAdapter = ArrayObjectAdapter(ContentCardPresenter())
        adapter = gridAdapter

        // Click listener
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Content) {
                navigateToDetail(item)
            }
        }

        // Search — filter items by title
        setOnSearchClickedListener {
            // Navigate to search
            try {
                findNavController().navigate(R.id.action_mylist_to_search)
            } catch (e: Exception) {
                findNavController().navigateUp()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchlist.collect { items ->
                    allItems = items
                    displayItems(items)
                }
            }
        }
    }

    private fun displayItems(items: List<WatchlistItem>) {
        gridAdapter.clear()
        if (items.isEmpty()) {
            title = "My List (Empty)"
        } else {
            title = "My List (${items.size})"
            gridAdapter.addAll(0, items.map { it.toContent() })
        }
    }

    private fun navigateToDetail(content: Content) {
        val action = MyListFragmentDirections.actionMylistToDetail(
            contentId = content.id,
            mediaType = content.mediaType.name
        )
        findNavController().navigate(action)
    }
}
