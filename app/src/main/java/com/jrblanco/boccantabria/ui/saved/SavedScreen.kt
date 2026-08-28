package com.jrblanco.boccantabria.ui.saved

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.ComingSoonMessage

/** As with search: a real destination, honest about not being built yet. */
@Composable
fun SavedScreen(modifier: Modifier = Modifier) {
    ComingSoonMessage(
        iconRes = R.drawable.ic_bookmark,
        description = stringResource(R.string.coming_soon_saved),
        modifier = modifier,
    )
}
