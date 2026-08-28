package com.jrblanco.boccantabria.ui.home.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_PUBLICATION_SKELETON: String = "home_skeleton"

/**
 * A placeholder shaped like the card it will become.
 *
 * The design document is explicit about this over a large spinner: a shape that matches the
 * content tells the reader what is coming, and the wait feels shorter for it. The pulse is slow
 * and faint on purpose — this is an official bulletin, not a loading screen competing for
 * attention.
 */
@Composable
fun PublicationCardSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = MIN_ALPHA,
        targetValue = MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_MILLIS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_PUBLICATION_SKELETON)
            .clearAndSetSemantics { },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BocTheme.elevation.level1),
    ) {
        Column(
            modifier = Modifier
                .padding(BocTheme.spacing.space4)
                .alpha(alpha),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        ) {
            Bar(widthFraction = 0.45f, height = LINE_SMALL)
            Bar(widthFraction = 1f, height = LINE_LARGE)
            Bar(widthFraction = 0.8f, height = LINE_LARGE)
            Bar(widthFraction = 0.3f, height = LINE_SMALL)
        }
    }
}

@Composable
private fun Bar(widthFraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(BocTheme.colors.surfaceStrong, MaterialTheme.shapes.extraSmall),
    )
}

/** The design document caps the placeholders at five: more reads as a broken screen. */
const val SKELETON_COUNT: Int = 5

private val LINE_SMALL = 12.dp
private val LINE_LARGE = 18.dp
private const val MIN_ALPHA = 0.45f
private const val MAX_ALPHA = 1f
private const val PULSE_MILLIS = 900
