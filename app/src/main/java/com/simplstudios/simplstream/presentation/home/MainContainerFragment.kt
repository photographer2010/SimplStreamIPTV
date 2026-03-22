package com.simplstudios.simplstream.presentation.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.simplstudios.simplstream.R
import com.simplstudios.simplstream.presentation.foryou.ForYouFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main container fragment with Netflix-style header navigation
 * Replaces the old Leanback BrowseSupportFragment sidebar navigation
 */
@AndroidEntryPoint
class MainContainerFragment : Fragment() {

    private lateinit var contentContainer: FrameLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var navTabsContainer: LinearLayout
    
    private lateinit var tabHome: TextView
    private lateinit var tabMovies: TextView
    private lateinit var tabTvShows: TextView
    private lateinit var tabMyList: TextView
    private lateinit var tabForYou: TextView
    
    private lateinit var btnSearch: ImageView
    private lateinit var btnProfile: ImageView
    
    private var currentTab: NavTab = NavTab.HOME
    private var allTabs: List<TextView> = emptyList()

    // Tab history for smart back navigation
    private val tabHistory = ArrayDeque<NavTab>()

    // Track if header is visible for scroll behavior
    private var isHeaderVisible = true
    
    enum class NavTab {
        HOME, MOVIES, TV_SHOWS, MY_LIST, FOR_YOU
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_main_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bindViews(view)
        setupNavigation()
        setupKeyNavigation(view)
        setupBackNavigation()

        // Load home by default
        navigateToTab(NavTab.HOME)
    }
    
    private fun bindViews(view: View) {
        contentContainer = view.findViewById(R.id.content_container)
        headerContainer = view.findViewById(R.id.header_container)
        navTabsContainer = view.findViewById(R.id.nav_tabs_container)
        
        tabHome = view.findViewById(R.id.tab_home)
        tabMovies = view.findViewById(R.id.tab_movies)
        tabTvShows = view.findViewById(R.id.tab_tv_shows)
        tabMyList = view.findViewById(R.id.tab_my_list)
        tabForYou = view.findViewById(R.id.tab_for_you)
        
        btnSearch = view.findViewById(R.id.btn_search)
        btnProfile = view.findViewById(R.id.btn_profile)
        
        allTabs = listOf(tabHome, tabMovies, tabTvShows, tabMyList, tabForYou)
    }
    
    private fun setupNavigation() {
        // Tab click listeners
        tabHome.setOnClickListener { navigateToTab(NavTab.HOME) }
        tabMovies.setOnClickListener { navigateToTab(NavTab.MOVIES) }
        tabTvShows.setOnClickListener { navigateToTab(NavTab.TV_SHOWS) }
        tabMyList.setOnClickListener { navigateToTab(NavTab.MY_LIST) }
        tabForYou.setOnClickListener { navigateToTab(NavTab.FOR_YOU) }
        
        // Search button
        btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_main_to_search)
        }
        
        // Profile button - navigate to settings
        btnProfile.setOnClickListener {
            findNavController().navigate(R.id.action_main_to_settings)
        }
        
        // Focus change styling for tabs — no scale, just alpha
        allTabs.forEach { tab ->
            tab.setOnFocusChangeListener { v, hasFocus ->
                v.animate().alpha(if (hasFocus) 1f else 0.7f).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }

        // Focus change styling for icon buttons — no scale, just alpha + bg
        listOf(btnSearch, btnProfile).forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                v.animate().alpha(if (hasFocus) 1f else 0.7f).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                v.background = if (hasFocus) ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle_button_focus) else null
            }
        }
    }
    
    private fun setupKeyNavigation(view: View) {
        // Handle D-pad navigation to scroll through header
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // If content has focus and we press up, move focus to header
                        val currentFocus = view.findFocus()
                        if (currentFocus != null && !isViewInHeader(currentFocus)) {
                            showHeader()
                            tabHome.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // If header has focus and we press down, move to content
                        val currentFocus = view.findFocus()
                        if (currentFocus != null && isViewInHeader(currentFocus)) {
                            // Focus will naturally move to content
                            return@setOnKeyListener false
                        }
                    }
                }
            }
            false
        }
    }
    
    private fun isViewInHeader(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent == headerContainer) return true
            parent = parent.parent
        }
        return false
    }
    
    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (tabHistory.isNotEmpty()) {
                        // Go back to previous tab
                        val previousTab = tabHistory.removeLast()
                        navigateToTab(previousTab, addToHistory = false)
                    } else {
                        // No tab history — go back to profile selection
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )
    }

    private fun navigateToTab(tab: NavTab, addToHistory: Boolean = true) {
        if (currentTab == tab && childFragmentManager.fragments.isNotEmpty()) {
            return // Already on this tab
        }

        // Push current tab to history (avoid duplicates at the top)
        if (addToHistory && childFragmentManager.fragments.isNotEmpty()) {
            if (tabHistory.lastOrNull() != currentTab) {
                tabHistory.addLast(currentTab)
            }
            // Keep history reasonable — max 10 entries
            while (tabHistory.size > 10) tabHistory.removeFirst()
        }

        currentTab = tab
        updateTabStyles()
        
        val fragment = when (tab) {
            NavTab.HOME -> HomeNetflixFragment()
            NavTab.MOVIES -> MoviesFragment()
            NavTab.TV_SHOWS -> TvShowsFragment()
            NavTab.FOR_YOU -> ForYouFragment()
            NavTab.MY_LIST -> {
                // Navigate to existing MyList fragment via nav graph
                findNavController().navigate(R.id.action_main_to_mylist)
                return
            }
        }
        
        childFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fade_in_slow,
                R.anim.fade_out_slow
            )
            .replace(R.id.content_container, fragment)
            .commit()
    }
    
    private fun updateTabStyles() {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.simpl_blue)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        
        allTabs.forEachIndexed { index, tab ->
            val tabEnum = NavTab.values()[index]
            tab.setTextColor(if (currentTab == tabEnum) activeColor else inactiveColor)
        }
    }
    
    fun showHeader() {
        if (!isHeaderVisible) {
            isHeaderVisible = true
            headerContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }
    
    fun hideHeader() {
        if (isHeaderVisible) {
            isHeaderVisible = false
            headerContainer.animate()
                .translationY(-headerContainer.height.toFloat())
                .alpha(0f)
                .setDuration(200)
                .start()
        }
    }
    
    /**
     * Called by child fragments when user navigates to detail
     */
    fun navigateToDetail(contentId: Int, mediaType: String) {
        val action = MainContainerFragmentDirections.actionMainToDetail(
            contentId = contentId,
            mediaType = mediaType
        )
        findNavController().navigate(action)
    }
}
