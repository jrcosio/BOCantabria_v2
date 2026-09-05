package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AiAnswerSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun questionBubbleTag(id: String): String = "ask_question_$id"

fun answerBubbleTag(id: String): String = "ask_answer_$id"

/** What the person asked. Institutional blue, aligned right, like every chat anyone has ever used. */
@Composable
fun QuestionBubble(
    id: String,
    text: String,
    atEpochMillis: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = QuestionShape,
            modifier = Modifier
                .widthIn(max = MAX_BUBBLE_WIDTH)
                .testTag(questionBubbleTag(id)),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(
                    horizontal = BocTheme.spacing.space4,
                    vertical = BocTheme.spacing.space3,
                ),
            )
        }
        Timestamp(atEpochMillis, Modifier.padding(top = BocTheme.spacing.space1))
    }
}

/**
 * What the assistant answered, with the robot beside it.
 *
 * The avatar is what tells the two apart at a glance for someone who does not read alignment, which
 * is most people scrolling.
 */
@Composable
fun AnswerBubble(
    id: String,
    text: String,
    atEpochMillis: Long,
    sources: List<AiAnswerSource>,
    onSourceClick: (AiAnswerSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color = BocTheme.colors.aiContainer,
            contentColor = BocTheme.colors.aiAccent,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.size(AVATAR_SIZE),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_robot),
                contentDescription = stringResource(R.string.ask_assistant),
                modifier = Modifier.padding(BocTheme.spacing.space2),
            )
        }

        Column(modifier = Modifier.widthIn(max = MAX_BUBBLE_WIDTH)) {
            Surface(
                color = BocTheme.colors.surfaceSoft,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = AnswerShape,
                modifier = Modifier.testTag(answerBubbleTag(id)),
            ) {
                Column {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(
                            horizontal = BocTheme.spacing.space4,
                            vertical = BocTheme.spacing.space3,
                        ),
                    )
                    // Only when something survived validation. An answer whose every citation was
                    // impossible is shown whole and without the block, rather than hidden (FR-015).
                    if (sources.isNotEmpty()) {
                        AnswerSources(sources = sources, onSourceClick = onSourceClick)
                    }
                }
            }
            Timestamp(atEpochMillis, Modifier.padding(top = BocTheme.spacing.space1))
        }
    }
}

@Composable
private fun Timestamp(atEpochMillis: Long, modifier: Modifier = Modifier) {
    Text(
        text = TIME.format(Instant.ofEpochMilli(atEpochMillis).atZone(ZoneId.systemDefault())),
        style = MaterialTheme.typography.labelSmall,
        color = BocTheme.colors.textMuted,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val MAX_BUBBLE_WIDTH = 300.dp
private val AVATAR_SIZE = 36.dp
private val QuestionShape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
private val AnswerShape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
