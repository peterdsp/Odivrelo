package dev.peterdsp.odivrelo.ui.adaptive

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import dev.peterdsp.odivrelo.theme.Space
import kotlinx.coroutines.flow.map

/** The hinge, in the terms a layout actually needs. */
@Immutable
data class FoldState(
    val orientation: FoldingFeature.Orientation,
    val state: FoldingFeature.State,
    val isSeparating: Boolean,
    val occludes: Boolean,
    /** Window-relative bounds, in pixels. */
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val isVertical: Boolean get() = orientation == FoldingFeature.Orientation.VERTICAL
    val isHorizontal: Boolean get() = orientation == FoldingFeature.Orientation.HORIZONTAL
    val widthPx: Int get() = right - left
    val heightPx: Int get() = bottom - top

    val isTabletop: Boolean
        get() = isHorizontal && state == FoldingFeature.State.HALF_OPENED

    val isBook: Boolean
        get() = isVertical && state == FoldingFeature.State.HALF_OPENED
}

/**
 * Everything the layout is allowed to know about the window.
 *
 * Every value here is measured. There is no device model, no hard-coded hinge
 * width and no "if the width is exactly this then it must be a fold" anywhere,
 * because a layout built on a guess about hardware breaks on the next device.
 */
@Immutable
data class OdivreloWindow(
    val widthClass: WindowWidthSizeClass,
    val heightClass: WindowHeightSizeClass,
    val fold: FoldState?,
) {
    /** Compact is one pane. Medium and expanded are list and detail together. */
    val paneCount: Int get() = if (widthClass == WindowWidthSizeClass.Compact) 1 else 2

    val isCompact: Boolean get() = widthClass == WindowWidthSizeClass.Compact

    /** A short window (landscape phone) needs a rail rather than a bottom bar. */
    val usesRail: Boolean
        get() = widthClass != WindowWidthSizeClass.Compact ||
            heightClass == WindowHeightSizeClass.Compact

    val label: String
        get() = when (widthClass) {
            WindowWidthSizeClass.Compact -> "compact"
            WindowWidthSizeClass.Medium -> "medium"
            else -> "expanded"
        }

    val postureLabel: String
        get() = when {
            fold == null -> "flat"
            fold.isTabletop -> "tabletop"
            fold.isBook -> "book"
            fold.state == FoldingFeature.State.HALF_OPENED -> "half-opened"
            else -> "flat-with-hinge"
        }
}

val LocalOdivreloWindow = staticCompositionLocalOf {
    OdivreloWindow(WindowWidthSizeClass.Compact, WindowHeightSizeClass.Medium, null)
}

/**
 * Observes the real window: its size class from Material 3 and its folding
 * features from `WindowInfoTracker`.
 *
 * The tracker is lifecycle-aware through `collectAsStateWithLifecycle`, so a
 * backgrounded activity is not kept awake watching a hinge it cannot see.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberOdivreloWindow(activity: Activity): OdivreloWindow {
    val sizeClass = calculateWindowSizeClass(activity)
    val foldFlow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { info ->
                info.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                    ?.let { feature ->
                        FoldState(
                            orientation = feature.orientation,
                            state = feature.state,
                            isSeparating = feature.isSeparating,
                            occludes = feature.occlusionType == FoldingFeature.OcclusionType.FULL,
                            left = feature.bounds.left,
                            top = feature.bounds.top,
                            right = feature.bounds.right,
                            bottom = feature.bounds.bottom,
                        )
                    }
            }
    }
    val fold by foldFlow.collectAsStateWithLifecycle(initialValue = null)
    return OdivreloWindow(
        widthClass = sizeClass.widthSizeClass,
        heightClass = sizeClass.heightSizeClass,
        fold = fold,
    )
}

/**
 * List and detail side by side, split at the hinge when there is one.
 *
 * When a vertical fold separates the window, the two panes are placed either
 * side of it and the hinge's own width becomes the gap, so nothing is ever
 * drawn into the seam. Without a fold the list takes a fixed, readable share
 * and the detail takes the rest.
 *
 * The container's position in the window is measured rather than assumed,
 * because a navigation rail moves the content's origin and a hinge is reported
 * in window coordinates.
 */
@Composable
fun TwoPaneLayout(
    modifier: Modifier = Modifier,
    fold: FoldState?,
    listPane: @Composable (Modifier) -> Unit,
    detailPane: @Composable (Modifier) -> Unit,
) {
    var originX by remember { mutableStateOf(0) }
    var containerWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            originX = bounds.left.toInt()
            containerWidth = coordinates.size.width
        },
    ) {
        val separatingVertical = fold != null && fold.isVertical && fold.isSeparating
        val hingeStart = if (separatingVertical) fold.left - originX else 0
        val hingeEnd = if (separatingVertical) fold.right - originX else 0
        val hingeUsable = separatingVertical &&
            hingeStart > MIN_PANE_PX && containerWidth - hingeEnd > MIN_PANE_PX

        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            if (hingeUsable) {
                val listWidth = with(density) { hingeStart.toDp() }
                val gap = with(density) { (hingeEnd - hingeStart).toDp() }
                listPane(Modifier.width(listWidth).fillMaxHeight())
                Spacer(Modifier.width(gap).fillMaxHeight())
                detailPane(Modifier.weight(1f).fillMaxHeight())
            } else {
                listPane(
                    Modifier
                        .weight(LIST_WEIGHT)
                        .widthIn(min = MIN_LIST_WIDTH)
                        .fillMaxHeight(),
                )
                Spacer(Modifier.width(Space.x4))
                detailPane(Modifier.weight(DETAIL_WEIGHT).fillMaxHeight())
            }
        }
    }
}

/**
 * A single column that steps around a horizontal hinge.
 *
 * In a tabletop posture the seam sits across the middle of the screen. Text and
 * controls drawn into it are physically unreadable, so the column is split and
 * the hinge's own height becomes a gap. On a flat device this is an ordinary
 * column with no extra spacing at all.
 */
@Composable
fun FoldAwareColumn(
    modifier: Modifier = Modifier,
    fold: FoldState?,
    above: @Composable () -> Unit,
    below: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var originY by remember { mutableStateOf(0) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            originY = coordinates.boundsInWindow().top.toInt()
        },
    ) {
        val separating = fold != null && fold.isHorizontal && fold.isSeparating
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                above()
            }
            if (separating) {
                Spacer(Modifier.height(with(density) { fold.heightPx.toDp() }).fillMaxWidth())
            }
            Box(Modifier.fillMaxWidth()) { below() }
        }
    }
}

/**
 * Padding that keeps content clear of a separating hinge in a single-pane
 * layout, for surfaces such as dialogs and bottom sheets that cannot be split.
 */
@Composable
fun Modifier.avoidFold(fold: FoldState?): Modifier {
    if (fold == null || !fold.isSeparating) return this
    val density = LocalDensity.current
    return if (fold.isHorizontal) {
        padding(bottom = with(density) { fold.heightPx.toDp() } + Space.x2)
    } else {
        padding(end = with(density) { fold.widthPx.toDp() } + Space.x2)
    }
}

private const val LIST_WEIGHT = 0.42f
private const val DETAIL_WEIGHT = 0.58f
private val MIN_LIST_WIDTH: Dp = 300.dp

/**
 * Below this, a pane is not a pane. A hinge close to an edge (a phone unfolded
 * into a nearly square window with an off-centre seam) falls back to the
 * proportional split rather than producing a sliver nothing fits in.
 */
private const val MIN_PANE_PX = 200
