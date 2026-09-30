package dev.peterdsp.poravia.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.peterdsp.poravia.R
import dev.peterdsp.poravia.theme.Space

/**
 * The common frame: a title, an optional back action, and a column that never
 * grows wider than a comfortable measure however wide the window is.
 *
 * A tablet or an unfolded inner screen gets two panes rather than one line of
 * text stretched across 1200 pixels, and that decision lives here so no screen
 * has to remember it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoraviaScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit) = {},
    constrainWidth: Boolean = true,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = contentWindowInsets,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = actions,
                // The shell's scaffold has already inset the content for the
                // status bar. A top bar that applies it again leaves a second
                // empty band the size of the status bar above every title.
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (constrainWidth) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH)) { content(padding) }
            }
        } else {
            content(padding)
        }
    }
}

/** A column of running text at a width someone can actually read. */
@Composable
fun ReadingColumn(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Space.readingWidth)
                .fillMaxWidth()
                .padding(horizontal = Space.x4),
            content = content,
        )
    }
}

private val MAX_CONTENT_WIDTH = androidx.compose.ui.unit.Dp(1200f)
