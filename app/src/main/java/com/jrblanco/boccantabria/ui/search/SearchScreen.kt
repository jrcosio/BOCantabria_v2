package com.jrblanco.boccantabria.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.ComingSoonMessage

/**
 * A real destination with nothing behind it yet.
 *
 * It exists so the navigation structure is settled and so the bottom bar never leads nowhere: a
 * destination that silently did nothing would read as a broken application rather than as an
 * unfinished one.
 */
@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    ComingSoonMessage(
        iconRes = R.drawable.ic_search,
        description = stringResource(R.string.coming_soon_search),
        modifier = modifier,
    )
}
