package com.jrblanco.boccantabria.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.EmptyMessage
import com.jrblanco.boccantabria.core.ui.component.ErrorMessage
import com.jrblanco.boccantabria.core.ui.component.LoadingIndicator
import com.jrblanco.boccantabria.domain.model.DomainError
import org.koin.androidx.compose.koinViewModel

const val TAG_CONTENT: String = "home_content"

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

/**
 * Stateless on purpose: it knows nothing about the view model, so the UI tests can walk the
 * four states without starting the dependency graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.home_title)) }) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            when (state) {
                HomeUiState.Loading -> LoadingIndicator()

                is HomeUiState.Content -> LazyColumn(modifier = Modifier.testTag(TAG_CONTENT)) {
                    items(items = state.items, key = { it.id }) { item ->
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                }

                HomeUiState.Empty -> EmptyMessage(message = stringResource(R.string.home_empty))

                is HomeUiState.Error -> ErrorMessage(
                    message = stringResource(state.error.messageRes()),
                    onRetry = onRetry,
                )
            }
        }
    }
}

/**
 * Maps the domain error to a message here, in the UI layer, so the domain never has to know
 * about string resources. The `when` is exhaustive: adding a [DomainError] breaks compilation
 * until someone decides what the user should read.
 */
private fun DomainError.messageRes(): Int = when (this) {
    DomainError.Network -> R.string.home_error_network
    DomainError.Unknown -> R.string.home_error_unknown
}
