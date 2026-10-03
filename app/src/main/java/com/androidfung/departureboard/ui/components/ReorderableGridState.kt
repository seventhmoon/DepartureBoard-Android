package com.androidfung.departureboard.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
 * State and gesture controller for drag-and-drop reordering inside a LazyVerticalGrid.
 */
class ReorderableGridState(
    val gridState: LazyGridState,
    private val scope: CoroutineScope,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    val dragOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    private var initialItemPosition: Offset? = null

    fun onDragStart(offset: Offset) {
        val hitItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val x = offset.x.toInt()
            val y = offset.y.toInt()
            x in item.offset.x..(item.offset.x + item.size.width) &&
                    y in item.offset.y..(item.offset.y + item.size.height)
        } ?: return

        // Skip non-station items like header
        if (hitItem.key == "header" || hitItem.key == "loading" || hitItem.key == "empty_state") {
            return
        }

        draggingKey = hitItem.key
        initialItemPosition = Offset(hitItem.offset.x.toFloat(), hitItem.offset.y.toFloat())
    }

    fun onDrag(dragAmount: Offset) {
        if (draggingKey == null) return

        scope.launch {
            dragOffset.snapTo(dragOffset.value + dragAmount)

            val currentDraggedItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }
                ?: return@launch

            val targetCenterX = currentDraggedItem.offset.x + currentDraggedItem.size.width / 2 + dragOffset.value.x
            val targetCenterY = currentDraggedItem.offset.y + currentDraggedItem.size.height / 2 + dragOffset.value.y

            val targetItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.key != draggingKey &&
                        item.key != "header" &&
                        item.key != "loading" &&
                        item.key != "empty_state" &&
                        targetCenterX.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                        targetCenterY.toInt() in item.offset.y..(item.offset.y + item.size.height)
            }

            if (targetItem != null) {
                // Header is at index 0, so station cards start at index 1 in the grid layout
                val fromGridIndex = currentDraggedItem.index
                val toGridIndex = targetItem.index

                val fromStationIndex = fromGridIndex - 1
                val toStationIndex = toGridIndex - 1

                if (fromStationIndex >= 0 && toStationIndex >= 0 && fromStationIndex != toStationIndex) {
                    onMove(fromStationIndex, toStationIndex)
                    // Reset offset relative to target
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
fun rememberReorderableGridState(
    gridState: LazyGridState = rememberLazyGridState(),
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderableGridState {
    val scope = rememberCoroutineScope()
    return remember(gridState, onMove) {
        ReorderableGridState(gridState, scope, onMove)
    }
}

/**
 * Modifier applied to the LazyVerticalGrid to detect long-press drag gestures.
 */
fun Modifier.reorderableGrid(state: ReorderableGridState): Modifier = this.pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset) },
        onDrag = { _, dragAmount -> state.onDrag(dragAmount) },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragCancel() }
    )
}

/**
 * Modifier applied to each reorderable station card item.
 */
fun Modifier.reorderableItem(state: ReorderableGridState, key: Any): Modifier {
    val isDragging = state.draggingKey == key
    return this
        .zIndex(if (isDragging) 2f else 1f)
        .graphicsLayer {
            if (isDragging) {
                translationX = state.dragOffset.value.x
                translationY = state.dragOffset.value.y
                scaleX = 1.04f
                scaleY = 1.04f
                shadowElevation = 16f
            }
        }
}
