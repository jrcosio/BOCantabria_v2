package com.jrblanco.boccantabria.ui.home.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_MENU: String = "home_menu"
const val TAG_SEARCH: String = "home_search"
const val TAG_INFO: String = "home_info"

/**
 * The main top bar: menu, shield and name, then search and information.
 *
 * No bell. Alerts are a later feature, and an icon that exists but does nothing is worse than an
 * icon that is not there yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    onOpenSections: () -> Unit,
    onSearch: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_escudo_cantabria),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(SHIELD_HEIGHT),
                )
                Text(
                    text = stringResource(R.string.app_bar_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = BocTheme.spacing.space3),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenSections, modifier = Modifier.testTag(TAG_MENU)) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = stringResource(R.string.app_bar_open_sections),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            IconButton(onClick = onSearch, modifier = Modifier.testTag(TAG_SEARCH)) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.app_bar_search),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onInfo, modifier = Modifier.testTag(TAG_INFO)) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.app_bar_info),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

private val SHIELD_HEIGHT = 34.dp
