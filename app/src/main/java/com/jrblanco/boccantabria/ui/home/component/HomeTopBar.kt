package com.jrblanco.boccantabria.ui.home.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.ui.home.HomeSearchState

const val TAG_MENU: String = "home_menu"
const val TAG_SEARCH: String = "home_search"
const val TAG_INFO: String = "home_info"
const val TAG_HOME_SEARCH_FIELD: String = "home_search_field"
const val TAG_HOME_SEARCH_CLOSE: String = "home_search_close"
const val TAG_HOME_SEARCH_CLEAR: String = "home_search_clear"

/**
 * The main top bar: menu, shield and name, then search and information — **or** the in-place search
 * field, when the magnifier has been tapped.
 *
 * One bar with two faces rather than two bars, and on the same surface colour, so that opening the
 * search reads as the header changing shape and not as landing somewhere else.
 *
 * No bell here. Since feature 012 the alerts live in the bottom bar as a fourth destination, with
 * their badge; a second bell up here would say the same thing twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun HomeTopBar(
    onOpenSections: () -> Unit,
    onSearch: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    search: HomeSearchState = HomeSearchState(),
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchClosed: () -> Unit = {},
) {
    if (search.isOpen) {
        HomeSearchBar(
            query = search.query,
            onQueryChanged = onSearchQueryChanged,
            onClose = onSearchClosed,
            modifier = modifier,
        )
        return
    }

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

/**
 * The bar in search mode.
 *
 * The field takes the focus as soon as it appears, so the keyboard comes up without a second tap:
 * somebody who reached for the magnifier is already going to type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TopAppBar(
        modifier = modifier,
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(TAG_HOME_SEARCH_FIELD),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(
                        text = stringResource(R.string.home_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BocTheme.colors.textMuted,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChanged("") },
                            modifier = Modifier.testTag(TAG_HOME_SEARCH_CLEAR),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.home_search_clear),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.testTag(TAG_HOME_SEARCH_CLOSE)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.home_search_close),
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
