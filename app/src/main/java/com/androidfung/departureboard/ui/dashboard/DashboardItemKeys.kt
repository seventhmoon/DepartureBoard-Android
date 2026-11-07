package com.androidfung.departureboard.ui.dashboard

/**
 * Canonical item keys for LazyVerticalStaggeredGrid in the Dashboard.
 * Eliminates magic string checks during drag-and-drop reordering.
 */
object DashboardItemKeys {
    const val HEADER = "header"
    const val SEARCH_BAR = "search_bar"
    const val OFFLINE_BANNER = "offline_banner"
    const val EMPTY_STATE = "empty_state"
    const val QUICK_JUMP_PILLS = "quick_jump_pills"
    const val LOADING = "loading"
    const val SKELETON_PREFIX = "skeleton_"
    const val NATIVE_AD_PREFIX = "native_ad_card_"

    private val FIXED_ITEM_KEYS = setOf(
        HEADER,
        SEARCH_BAR,
        OFFLINE_BANNER,
        EMPTY_STATE,
        QUICK_JUMP_PILLS,
        LOADING
    )

    /**
     * Determines whether a grid item key corresponds to fixed/non-reorderable UI elements
     * (headers, search bar, skeletons, ads, or empty state) rather than station cards.
     */
    fun isNonStationKey(key: Any?): Boolean {
        if (key !is String) return false
        return key in FIXED_ITEM_KEYS ||
                key.startsWith(SKELETON_PREFIX) ||
                key.startsWith(NATIVE_AD_PREFIX)
    }
}
