package com.jrblanco.boccantabria.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R

const val TAG_COMING_SOON: String = "coming_soon"

/**
 * What a destination that is not built yet shows.
 *
 * Shared rather than written twice so that the places it appears say the same thing in the same
 * voice. A destination that simply did nothing would read as a broken application.
 *
 * Now a thin wrapper over [IllustratedMessage] — same drawing, headline fixed to «Próximamente» and
 * no action. **Its signature and its test tag are unchanged on purpose**: instrumented tests that
 * have nothing to do with the saved list depend on both.
 */
@Composable
fun ComingSoonMessage(
    iconRes: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    IllustratedMessage(
        iconRes = iconRes,
        title = stringResource(R.string.coming_soon),
        description = description,
        modifier = modifier.testTag(TAG_COMING_SOON),
    )
}
