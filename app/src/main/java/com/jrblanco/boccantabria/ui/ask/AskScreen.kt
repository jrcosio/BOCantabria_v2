package com.jrblanco.boccantabria.ui.ask

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.ui.detail.component.ComingSoonTab

const val TAG_ASK_SCREEN: String = "ask_screen"
const val TAG_ASK_BACK: String = "ask_back"

/**
 * Asking about the document. A real destination with nothing behind it yet.
 *
 * It was a third tab until this feature was tried on a phone. A conversation about a forty-page
 * bulletin needs the whole screen and its own place in the back stack, which is not what a tab
 * beside a metadata card is for — so the action bar's button brings you here instead.
 *
 * Stateless and with no view model, like Buscar and Guardados: there is nothing to hold yet. It
 * keeps the AI identity of section 20.1 rather than the generic grey notice, so the reader can tell
 * what is coming and not merely that something is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_ASK_SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ask_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_ASK_BACK)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            ComingSoonTab(
                iconRes = R.drawable.ic_ask,
                label = stringResource(R.string.detail_ask_label),
                description = stringResource(R.string.detail_ask_coming),
            )
        }
    }
}
