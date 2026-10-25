package com.androidfung.departureboard.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Backwards-compatibility forwarding function for [com.androidfung.departureboard.ui.detail.DetailPaneScaffold].
 */
@Composable
fun DetailPaneScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
    headerLeading: (@Composable () -> Unit)? = null,
    headerTitle: (@Composable ColumnScope.() -> Unit)? = null,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(PaddingValues) -> Unit
) {
    com.androidfung.departureboard.ui.detail.DetailPaneScaffold(
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        headerLeading = headerLeading,
        headerTitle = headerTitle,
        headerActions = headerActions,
        content = content
    )
}
