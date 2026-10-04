package com.androidfung.departureboard.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State and gesture controller for drag-and-drop reordering inside a LazyVerticalStaggeredGrid.
 */
class ReorderableStaggeredGridState(
    val staggeredGridState: LazyStaggeredGridState,
    private val scope: CoroutineScope,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    val dragOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    private var initialItemPosition: Offset? = null

    fun onDragStart(offset: Offset) {
        val hitItem = staggeredGridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val x = offset.x.toInt()
            val y = offset.y.toInt()
            x in item.offset.x..(item.offset.x + item.size.width) &&
                    y in item.offset.y..(item.offset.y + item.size.height)
        } ?: return

        // Skip non-station items like header, quick_jump_pills, loading, or empty_state
        if (hitItem.key == "header" || hitItem.key == "quick_jump_pills" || hitItem.key == "loading" || hitItem.key == "empty_state") {
            return
        }

        draggingKey = hitItem.key
        initialItemPosition = Offset(hitItem.offset.x.toFloat(), hitItem.offset.y.toFloat())
    }

    fun onDrag(dragAmount: Offset) {
        if (draggingKey == null) return

        scope.launch {
            dragOffset.snapTo(dragOffset.value + dragAmount)

            val currentDraggedItem = staggeredGridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }
                ?: return@launch

            val targetCenterX = currentDraggedItem.offset.x + currentDraggedItem.size.width / 2 + dragOffset.value.x
            val targetCenterY = currentDraggedItem.offset.y + currentDraggedItem.size.height / 2 + dragOffset.value.y

            val targetItem = staggeredGridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.key != draggingKey &&
                        item.key != "header" &&
                        item.key != "quick_jump_pills" &&
                        item.key != "loading" &&
                        item.key != "empty_state" &&
                        targetCenterX.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                        targetCenterY.toInt() in item.offset.y..(item.offset.y + item.size.height)
            }

            if (targetItem != null) {
                // Find station indices by accounting for header and quick-jump items
                val visibleKeys = staggeredGridState.layoutInfo.visibleItemsInfo
                val fromGridIndex = currentDraggedItem.index
                val toGridIndex = targetItem.index

                val headerOffset = if (visibleKeys.any { it.key == "quick_jump_pills" }) 2 else 1
                val fromStationIndex = fromGridIndex - headerOffset
                val toStationIndex = toGridIndex - headerOffset

                if (fromStationIndex >= 0 && toStationIndex >= 0 && fromStationIndex != toStationIndex) {
                    onMove(fromStationIndex, toStationIndex)
                    dragOffset.snapTo(Offset.Zero)
                }
            }
        }
    }

    fun onDragEnd() {
        scope.launch {
            dragOffset.animateTo(Offset.Zero)
            draggingKey = null
            initialItemPosition = null
        }
    }

    fun onDragCancel() {
        scope.launch {
            dragOffset.snapTo(Offset.Zero)
            draggingKey = null
            initialItemPosition = null
        }
    }
}

@Composable
fun rememberReorderableStaggeredGridState(
    staggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderableStaggeredGridState {
    val scope = rememberCoroutineScope()
    return remember(staggeredGridState, onMove) {
        ReorderableStaggeredGridState(staggeredGridState, scope, onMove)
    }
}

/**
 * Modifier applied to the LazyVerticalStaggeredGrid to detect long-press drag gestures.
 */
fun Modifier.reorderableStaggeredGrid(state: ReorderableStaggeredGridState): Modifier = this.pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset) },
        onDrag = { _, dragAmount -> state.onDrag(dragAmount) },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragCancel() }
    )
}

/**
 * Modifier applied to each reorderable station card item in the staggered grid.
 */
fun Modifier.reorderableStaggeredItem(state: ReorderableStaggeredGridState, key: Any): Modifier {
    val isDragging = state.draggingKey == key
    return this
        .zIndex(if (isDragging) 10f else 1f)
        .graphicsLayer {
            if (isDragging) {
                translationX = state.dragOffset.value.x
                translationY = state.dragOffset.value.y
                scaleX = 1.04f
                scaleY = 1.04f
                shadowElevation = 24f
            }
        }
}
