/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.androidfung.departureboard.ui.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

/**
 * A [Scene] that displays 2 panes on tablets / medium windows, or 3 panes on desktop / extra-large screens.
 * 
 * Layout configurations:
 * - 3-Pane (Desktop / EXTRA_LARGE): List Pane (30%) + Detail Pane (35%) + Supporting Pane (35%)
 * - 2-Pane (Tablet / Medium / Expanded): List Pane (45%) + Detail/Supporting Pane (55%)
 */
data class ListDetailScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val listEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>?,
    val supportingEntry: NavEntry<T>?,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOfNotNull(listEntry, detailEntry, supportingEntry)
    
    override val content: @Composable (() -> Unit) = {
        val windowWidthClass = com.androidfung.departureboard.ui.theme.LocalWindowWidthClass.current
        val isThreePaneMode = windowWidthClass == com.androidfung.departureboard.ui.theme.WindowWidthClass.EXTRA_LARGE &&
                detailEntry != null && supportingEntry != null

        CompositionLocalProvider(
            LocalIsTwoPaneMode provides true,
            LocalIsThreePaneMode provides isThreePaneMode
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isThreePaneMode) {
                    // 3-Pane Layout on Desktop (EXTRA_LARGE >= 1200dp)
                    // List: 30% | Middle Pane: 35% | Right Pane: 35%
                    Column(modifier = Modifier.weight(0.30f)) {
                        listEntry.Content()
                    }

                    CompositionLocalProvider(LocalBackButtonVisibility provides false) {
                        Column(modifier = Modifier.weight(0.35f)) {
                            val middleTransition = remember(detailEntry.contentKey) {
                                androidx.compose.animation.core.MutableTransitionState(false).apply {
                                    targetState = true
                                }
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visibleState = middleTransition,
                                enter = slideInHorizontally(
                                    initialOffsetX = { fullWidth -> (fullWidth * 0.4f).toInt() },
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 320,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                ) + androidx.compose.animation.fadeIn(
                                    animationSpec = androidx.compose.animation.core.tween(320)
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                detailEntry.Content()
                            }
                        }
                    }

                    CompositionLocalProvider(LocalBackButtonVisibility provides false) {
                        Column(modifier = Modifier.weight(0.35f)) {
                            val rightTransition = remember(supportingEntry.contentKey) {
                                androidx.compose.animation.core.MutableTransitionState(false).apply {
                                    targetState = true
                                }
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visibleState = rightTransition,
                                enter = slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 350,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                ) + androidx.compose.animation.fadeIn(
                                    animationSpec = androidx.compose.animation.core.tween(350)
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                supportingEntry.Content()
                            }
                        }
                    }
                } else {
                    // 2-Pane Layout on Tablets / Foldables / Expanded
                    val isUltraWide = windowWidthClass == com.androidfung.departureboard.ui.theme.WindowWidthClass.EXTRA_LARGE
                    val listWeight = if (isUltraWide) 0.50f else 0.45f
                    val detailWeight = if (isUltraWide) 0.50f else 0.55f

                    Column(modifier = Modifier.weight(listWeight)) {
                        listEntry.Content()
                    }

                    // Active right pane is whichever secondary entry is last in the backstack
                    val activeRightPane = supportingEntry ?: detailEntry
                    if (activeRightPane != null) {
                        CompositionLocalProvider(LocalBackButtonVisibility provides false) {
                            Column(modifier = Modifier.weight(detailWeight)) {
                                AnimatedContent(
                                    targetState = activeRightPane,
                                    contentKey = { entry -> entry.contentKey },
                                    transitionSpec = {
                                        slideInHorizontally(initialOffsetX = { it }) togetherWith
                                                slideOutHorizontally(targetOffsetX = { -it })
                                    }
                                ) { entry ->
                                    entry.Content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun listPane() = metadata {
            put(ListKey, true)
        }

        fun detailPane() = metadata {
            put(DetailKey, true)
        }

        fun supportingPane() = metadata {
            put(SupportingKey, true)
        }
    }

    object ListKey : NavMetadataKey<Boolean>
    object DetailKey : NavMetadataKey<Boolean>
    object SupportingKey : NavMetadataKey<Boolean>
}

val LocalBackButtonVisibility = compositionLocalOf { true }
val LocalIsTwoPaneMode = compositionLocalOf { false }
val LocalIsThreePaneMode = compositionLocalOf { false }

@Composable
fun <T : Any> rememberListDetailSceneStrategy(): ListDetailSceneStrategy<T> {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return remember(windowSizeClass) {
        ListDetailSceneStrategy(windowSizeClass)
    }
}

/**
 * An adaptive [SceneStrategy] that evaluates whether to display 1, 2, or 3 panes based on window width
 * and the active entries in the backstack.
 */
class ListDetailSceneStrategy<T : Any>(val windowSizeClass: WindowSizeClass) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            return null
        }

        val listEntry = entries.findLast { it.metadata.contains(ListDetailScene.ListKey) } ?: return null

        // All non-list entries ordered by their push sequence on the backstack
        val secondaryEntries = entries.filter { !it.metadata.contains(ListDetailScene.ListKey) }
        if (secondaryEntries.isEmpty()) {
            return null
        }

        // In 3-pane mode, middle pane is secondaryEntries[0] and right pane is secondaryEntries[1]
        // In 2-pane mode, active right pane is secondaryEntries.last() (top of backstack)
        val detailEntry = secondaryEntries.getOrNull(0)
        val supportingEntry = secondaryEntries.getOrNull(1)

        val sceneKey = listEntry.contentKey

        return ListDetailScene(
            key = sceneKey,
            previousEntries = entries.dropLast(1),
            listEntry = listEntry,
            detailEntry = detailEntry,
            supportingEntry = supportingEntry
        )
    }
}
