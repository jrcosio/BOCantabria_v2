package com.jrblanco.boccantabria.ui.search.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.SearchQuery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

const val TAG_SEARCH_FILTERS_SHEET: String = "search_filters_sheet"
const val TAG_SEARCH_FILTERS_APPLY: String = "search_filters_apply"
const val TAG_SEARCH_FILTERS_CLEAR: String = "search_filters_clear"
const val TAG_SEARCH_FILTER_FROM: String = "search_filter_from"
const val TAG_SEARCH_FILTER_TO: String = "search_filter_to"
const val TAG_SEARCH_FILTER_SECTION: String = "search_filter_section"
const val TAG_SEARCH_FILTER_SUBSECTION: String = "search_filter_subsection"
const val TAG_SEARCH_FILTER_ISSUER: String = "search_filter_issuer"

/**
 * `Filtrar resultados`: the bottom sheet of section 17.3.
 *
 * A sheet rather than a panel always on screen, because six selectors above the results would push
 * the results —the thing somebody came to see— off the bottom. What stays visible on the screen is
 * the chips of what is actually applied, which is the half of the reference image worth keeping.
 *
 * The edits are held in a draft and only reach the screen on `Aplicar filtros`, so backing out of
 * the sheet changes nothing. **There is no municipality filter**: the bulletin does not publish it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SearchFiltersSheet(
    query: SearchQuery,
    sections: List<BocSection>,
    issuers: List<String>,
    onApply: (SearchQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(query) { mutableStateOf(query) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(TAG_SEARCH_FILTERS_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = BocTheme.spacing.space6,
                    end = BocTheme.spacing.space6,
                    bottom = BocTheme.spacing.space10,
                ),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
        ) {
            Text(
                text = stringResource(R.string.search_filters_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            DateField(
                label = stringResource(R.string.search_filter_date_from),
                value = draft.from,
                tag = TAG_SEARCH_FILTER_FROM,
                onPicked = { draft = draft.copy(from = it) },
            )
            DateField(
                label = stringResource(R.string.search_filter_date_to),
                value = draft.to,
                tag = TAG_SEARCH_FILTER_TO,
                onPicked = { draft = draft.copy(to = it) },
                minDate = draft.from,
            )

            val topLevel = remember(sections) { sections.filter { it.isTopLevel } }
            PickerField(
                label = stringResource(R.string.search_filter_section),
                selected = topLevel.firstOrNull { it.code == draft.sectionCode }?.name,
                emptyLabel = stringResource(R.string.search_filter_all_feminine),
                options = topLevel.map { it.code to it.name },
                tag = TAG_SEARCH_FILTER_SECTION,
                // Choosing a section drops a subsection that does not belong to it. The rule lives
                // in the model, so it has a test and no caller can forget it.
                onSelected = { code -> draft = draft.withSection(code) },
            )

            // Only the subsections of the chosen section: offering the rest would let somebody
            // build a combination that can never match anything.
            val subsections = remember(sections, draft.sectionCode) {
                sections.filter { it.parentCode != null && it.parentCode == draft.sectionCode }
            }
            if (subsections.isNotEmpty()) {
                PickerField(
                    label = stringResource(R.string.search_filter_subsection),
                    selected = subsections.firstOrNull { it.code == draft.subsectionCode }?.name,
                    emptyLabel = stringResource(R.string.search_filter_all_feminine),
                    options = subsections.map { it.code to it.name },
                    tag = TAG_SEARCH_FILTER_SUBSECTION,
                    onSelected = { code -> draft = draft.copy(subsectionCode = code) },
                )
            }

            PickerField(
                label = stringResource(R.string.search_filter_issuer),
                selected = draft.issuer,
                emptyLabel = stringResource(R.string.search_filter_all_masculine),
                options = issuers.map { it to it },
                tag = TAG_SEARCH_FILTER_ISSUER,
                // There are hundreds of them — every town hall, every department, every court — so
                // a flat list would be unusable.
                searchable = true,
                onSelected = { issuer -> draft = draft.copy(issuer = issuer) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { draft = draft.clearedFilters() },
                    modifier = Modifier.testTag(TAG_SEARCH_FILTERS_CLEAR),
                ) {
                    Text(text = stringResource(R.string.search_filters_clear))
                }
                Button(
                    onClick = { onApply(draft) },
                    // An impossible range cannot be applied. Refused in the interface rather than
                    // thrown from the model: a handling mistake must not close the application.
                    enabled = !draft.hasInvalidDateRange,
                    modifier = Modifier.testTag(TAG_SEARCH_FILTERS_APPLY),
                ) {
                    Text(text = stringResource(R.string.search_filters_apply))
                }
            }
        }
    }
}

/**
 * One end of the range.
 *
 * The field itself is read-only and a transparent surface over it takes the tap: typing a date by
 * hand is a way to enter an impossible one, and the picker cannot produce a date that does not
 * exist. The cross clears the end rather than the whole range — an open end is a legitimate filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    tag: String,
    onPicked: (LocalDate?) -> Unit,
    minDate: LocalDate? = null,
) {
    var picking by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value?.format(SHORT_DATE).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.ic_calendar), contentDescription = null)
            },
            trailingIcon = {
                if (value != null) {
                    IconButton(onClick = { onPicked(null) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.search_filter_remove, label),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only text field swallows taps, so the surface that opens the picker sits over it
        // — everywhere except the cross, which keeps its own touch target.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = CLEAR_TOUCH_TARGET)
                .clickable { picking = true }
                .testTag(tag),
        )
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            // The "to" picker offers nothing before "from": the impossible combination is kept out
            // of reach rather than reported after the fact.
            selectableDates = minDate?.let { floor -> NotBefore(floor) } ?: AnyDate,
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onPicked(state.selectedDateMillis?.toLocalDate())
                }) {
                    Text(text = stringResource(R.string.search_filters_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(text = stringResource(R.string.search_filters_clear))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private object AnyDate : SelectableDates

@OptIn(ExperimentalMaterial3Api::class)
private class NotBefore(private val floor: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !utcTimeMillis.toLocalDate().isBefore(floor)

    override fun isSelectableYear(year: Int): Boolean = year >= floor.year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun PickerField(
    label: String,
    selected: String?,
    emptyLabel: String,
    options: List<Pair<String, String>>,
    tag: String,
    onSelected: (String?) -> Unit,
    searchable: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: emptyLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(tag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (searchable) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    placeholder = { Text(text = stringResource(R.string.search_filter_issuer_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BocTheme.spacing.space4),
                )
            }
            DropdownMenuItem(
                text = { Text(text = emptyLabel) },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
            )
            options
                .filter { (_, name) -> !searchable || name.contains(filter, ignoreCase = true) }
                .take(MAX_OPTIONS)
                .forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(text = name) },
                        onClick = {
                            expanded = false
                            onSelected(code)
                        },
                    )
                }
        }
    }
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** A menu is not a list. Beyond this, the field's own filter is the way through. */
private const val MAX_OPTIONS = 60

/** Room for the cross, so the surface that opens the picker does not steal its taps. */
private val CLEAR_TOUCH_TARGET = 48.dp

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
