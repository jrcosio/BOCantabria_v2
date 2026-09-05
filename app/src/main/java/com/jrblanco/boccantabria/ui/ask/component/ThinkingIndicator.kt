package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_THINKING: String = "ask_thinking"

/**
 * Three dots that say the assistant is working.
 *
 * The answer cannot stream — a strict schema and progressive text do not go together — so this is what
 * the wait has to say (D-306).
 *
 * **It animates for ever, and that breaks a test that does not know.** An endless animation stops the
 * composition ever reaching idle, and `assertIsDisplayed()` waits for idle: it **hangs** instead of
 * failing. Any instrumented test that walks past this must drive the clock by hand —
 * `mainClock.autoAdvance = false` plus `advanceTimeByFrame()` — exactly as the loading skeleton needs
 * (011 research.md D-326).
 */
@Composable
fun ThinkingIndicator(
    label: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "thinking")

    Surface(
        color = BocTheme.colors.surfaceSoft,
        contentColor = BocTheme.colors.textMuted,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .testTag(TAG_THINKING)
            .semantics { contentDescription = label },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BocTheme.spacing.space4,
                vertical = BocTheme.spacing.space3,
            ),
            horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(DOTS) { index ->
                val alpha by transition.animateFloat(
                    initialValue = MIN_ALPHA,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(DURATION_MILLIS, delayMillis = index * STAGGER_MILLIS),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$index",
                )
                Surface(
                    color = BocTheme.colors.aiAccent,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(DOT_SIZE)
                        .alpha(alpha),
                ) {}
            }
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val DOTS = 3
private const val MIN_ALPHA = 0.25f
private const val DURATION_MILLIS = 500
private const val STAGGER_MILLIS = 150
private val DOT_SIZE = 6.dp
