package com.jrblanco.boccantabria.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.component.AiNoticeSheet
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AiAnswerSource
import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiChatStatus
import com.jrblanco.boccantabria.ui.ask.component.AnswerBubble
import com.jrblanco.boccantabria.ui.ask.component.AskComposer
import com.jrblanco.boccantabria.ui.ask.component.AskDocumentHeader
import com.jrblanco.boccantabria.ui.ask.component.AskFooter
import com.jrblanco.boccantabria.ui.ask.component.AskScopeNotice
import com.jrblanco.boccantabria.ui.ask.component.ChatErrorRow
import com.jrblanco.boccantabria.ui.ask.component.QuestionBubble
import com.jrblanco.boccantabria.ui.ask.component.SuggestedQuestions
import com.jrblanco.boccantabria.ui.ask.component.ThinkingIndicator

const val TAG_ASK_SCREEN: String = "ask_screen"
const val TAG_ASK_BACK: String = "ask_back"

/**
 * The conversation about the official document.
 *
 * Stateless: it renders [AskUiState] and emits events. Notably, **it does not decide what an
 * out-of-scope answer says** — that substitution happens in the data layer so no screen, present or
 * future, can skip it by accident (FR-021, 011 contracts §3.3).
 *
 * The composer is the `bottomBar`, and the window insets are its business rather than the scaffold's:
 * see the note on `AskComposer`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskContent(
    state: AskUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestionTapped: (String) -> Unit,
    onRetry: () -> Unit,
    onToggleSaved: () -> Unit,
    onOpenDocument: () -> Unit,
    onSourceClick: (AiAnswerSource) -> Unit,
    onNoticeAccepted: () -> Unit,
    onNoticeDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // A new message means the newest one should be the one you can see.
    LaunchedEffect(state.messages.size, state.status) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
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
        bottomBar = {
            AskComposer(
                draft = state.draft,
                onDraftChange = onDraftChange,
                onSend = onSend,
                canSend = state.canSend,
                enabled = state.isServiceConfigured && !state.isBusy,
                showCounter = state.showCounter,
                isOverLimit = state.isOverLimit,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Whoever applies the space is who must declare it served. Without this the list and
                // the composer are separated by a dead strip the height of the navigation bar.
                .consumeWindowInsets(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(BocTheme.spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
            ) {
                state.publication?.let { publication ->
                    item(key = "header") {
                        AskDocumentHeader(
                            title = publication.titleWithoutIssuer,
                            date = publication.publicationDate,
                            isSaved = state.isSaved,
                            onToggleSaved = onToggleSaved,
                        )
                    }
                }

                item(key = "scope") { AskScopeNotice() }

                if (state.showSuggestions) {
                    item(key = "suggestions") {
                        SuggestedQuestions(
                            onQuestionTapped = onSuggestionTapped,
                            enabled = !state.isBusy && state.publication != null,
                        )
                    }
                }

                items(state.messages, key = { it.id }) { message ->
                    when (message) {
                        is AiChatMessage.Question -> QuestionBubble(
                            id = message.id,
                            text = message.text,
                            atEpochMillis = message.atEpochMillis,
                        )
                        is AiChatMessage.Answer -> AnswerBubble(
                            id = message.id,
                            text = message.text,
                            atEpochMillis = message.atEpochMillis,
                            sources = message.sources,
                            onSourceClick = onSourceClick,
                        )
                    }
                }

                when (val status = state.status) {
                    is AiChatStatus.Preparing -> item(key = "preparing") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            ThinkingIndicator(label = stringResource(status.phase.labelRes()))
                        }
                    }
                    AiChatStatus.Thinking -> item(key = "thinking") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            ThinkingIndicator(label = stringResource(R.string.ask_thinking))
                        }
                    }
                    is AiChatStatus.Failed -> item(key = "failed") {
                        ChatErrorRow(
                            message = stringResource(status.error.messageRes()),
                            onRetry = onRetry.takeIf { status.retryableQuestionId != null },
                        )
                    }
                    AiChatStatus.Idle -> Unit
                }

                item(key = "footer") { AskFooter(onOpenDocument = onOpenDocument) }
            }
        }
    }

    if (state.noticePending) {
        AiNoticeSheet(onContinue = onNoticeAccepted, onDismiss = onNoticeDismissed)
    }
}

private fun AiChatStatus.Preparing.Phase.labelRes(): Int = when (this) {
    AiChatStatus.Preparing.Phase.FETCHING_DOCUMENT -> R.string.ask_phase_fetching
    AiChatStatus.Preparing.Phase.UPLOADING_DOCUMENT -> R.string.ask_phase_uploading
}
