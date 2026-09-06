package com.jrblanco.boccantabria.ui.alerts.form

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AlertRuleValidationError
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.ui.alerts.component.sectionSummaryText
import com.jrblanco.boccantabria.ui.alerts.form.component.KeywordChipsInput
import com.jrblanco.boccantabria.ui.alerts.form.component.MatchModeSelector
import com.jrblanco.boccantabria.ui.alerts.form.component.NotificationPermissionDialog
import com.jrblanco.boccantabria.ui.alerts.form.component.OrganizationField
import com.jrblanco.boccantabria.ui.alerts.form.component.PreviewSheet
import com.jrblanco.boccantabria.ui.alerts.form.component.RuleSummaryCard
import com.jrblanco.boccantabria.ui.alerts.form.component.SectionPickerSheet
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

const val TAG_ALERT_FORM_SCREEN: String = "alert_form_screen"
const val TAG_ALERT_FORM_BACK: String = "alert_form_back"
const val TAG_ALERT_FORM_NAME: String = "alert_form_name"
const val TAG_ALERT_FORM_SECTIONS_OPEN: String = "alert_form_sections_open"
const val TAG_ALERT_FORM_SECTIONS_SUMMARY: String = "alert_form_sections_summary"
const val TAG_ALERT_FORM_ENABLED: String = "alert_form_enabled"
const val TAG_ALERT_FORM_PREVIEW: String = "alert_form_preview"
const val TAG_ALERT_FORM_PREVIEW_OPEN: String = "alert_form_preview_open"
const val TAG_ALERT_FORM_SAVE: String = "alert_form_save"
const val TAG_ALERT_FORM_CANCEL: String = "alert_form_cancel"

/**
 * Crear / Editar aviso with its state attached.
 *
 * Owns the two things a composable has to own here: the permission launcher, which needs an activity
 * result registry, and the exit. Saving without a permission to ask for leaves at once; saving the
 * first rule with the permission pending shows the explanation first, and leaves whatever the person
 * answers (spec §16).
 */
@Composable
fun AlertFormScreen(
    onBack: () -> Unit,
    onOpenPublication: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertFormViewModel = koinViewModel(),
    getSections: GetBocSectionsUseCase = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = remember(getSections) { getSections() }
    val context = LocalContext.current
    var permissionDialogOpen by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
        onBack()
    }

    LaunchedEffect(state) {
        val saved = state as? AlertFormUiState.Saved ?: return@LaunchedEffect
        if (saved.requestPermission) permissionDialogOpen = true else onBack()
    }

    val failureText = stringResource(R.string.alert_form_save_failed)
    val ready = state as? AlertFormUiState.Ready
    LaunchedEffect(ready?.saveFailed) {
        if (ready?.saveFailed == true) {
            Toast.makeText(context, failureText, Toast.LENGTH_SHORT).show()
            viewModel.onSaveFailureConsumed()
        }
    }

    AlertFormContent(
        state = state,
        sections = sections,
        onBack = onBack,
        onNameChanged = viewModel::onNameChanged,
        onKeywordAdded = viewModel::onKeywordAdded,
        onKeywordRemoved = viewModel::onKeywordRemoved,
        onMatchModeChanged = viewModel::onMatchModeChanged,
        onSectionsOpened = viewModel::onSectionsOpened,
        onSectionsClosed = viewModel::onSectionsClosed,
        onSectionToggled = viewModel::onSectionToggled,
        onAllSectionsSelected = viewModel::onAllSectionsSelected,
        onOrganizationChanged = viewModel::onOrganizationChanged,
        onOrganizationChosen = viewModel::onOrganizationChosen,
        onEnabledChanged = viewModel::onEnabledChanged,
        onPreviewOpened = viewModel::onPreviewOpened,
        onPreviewClosed = viewModel::onPreviewClosed,
        onOpenPublication = onOpenPublication,
        onSave = viewModel::onSave,
        modifier = modifier,
    )

    if (permissionDialogOpen) {
        NotificationPermissionDialog(
            onLater = {
                permissionDialogOpen = false
                onBack()
            },
            onContinue = {
                permissionDialogOpen = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onBack()
                }
            },
        )
    }
}

/** The screen with nothing behind it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
fun AlertFormContent(
    state: AlertFormUiState,
    sections: List<BocSection>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNameChanged: (String) -> Unit = {},
    onKeywordAdded: (String) -> Unit = {},
    onKeywordRemoved: (String) -> Unit = {},
    onMatchModeChanged: (KeywordMatchMode) -> Unit = {},
    onSectionsOpened: () -> Unit = {},
    onSectionsClosed: () -> Unit = {},
    onSectionToggled: (String) -> Unit = {},
    onAllSectionsSelected: () -> Unit = {},
    onOrganizationChanged: (String) -> Unit = {},
    onOrganizationChosen: (String) -> Unit = {},
    onEnabledChanged: (Boolean) -> Unit = {},
    onPreviewOpened: () -> Unit = {},
    onPreviewClosed: () -> Unit = {},
    onOpenPublication: (String) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val ready = state as? AlertFormUiState.Ready
    val isEdit = ready?.isEdit == true

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_ALERT_FORM_SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (isEdit) R.string.alert_form_title_edit else R.string.alert_form_title_create),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_ALERT_FORM_BACK)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.alert_form_back),
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
            if (ready != null) {
                FormActions(
                    canSave = ready.canSave,
                    isSaving = ready.isSaving,
                    isEdit = ready.isEdit,
                    onCancel = onBack,
                    onSave = onSave,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when (ready) {
            null -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> FormBody(
                state = ready,
                innerPadding = innerPadding,
                onNameChanged = onNameChanged,
                onKeywordAdded = onKeywordAdded,
                onKeywordRemoved = onKeywordRemoved,
                onMatchModeChanged = onMatchModeChanged,
                onSectionsOpened = onSectionsOpened,
                onOrganizationChanged = onOrganizationChanged,
                onOrganizationChosen = onOrganizationChosen,
                onEnabledChanged = onEnabledChanged,
                onPreviewOpened = onPreviewOpened,
            )
        }
    }

    if (ready?.sectionsOpen == true) {
        SectionPickerSheet(
            rows = ready.sectionRows,
            selected = ready.draft.sectionCodes,
            selectedLeafCount = ready.selectedLeafCount,
            onToggle = onSectionToggled,
            onSelectAll = onAllSectionsSelected,
            onApply = onSectionsClosed,
            onDismiss = onSectionsClosed,
        )
    }
    if (ready?.previewOpen == true) {
        PreviewSheet(
            publications = ready.preview,
            sections = sections,
            onOpenPublication = onOpenPublication,
            onDismiss = onPreviewClosed,
        )
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun FormBody(
    state: AlertFormUiState.Ready,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onNameChanged: (String) -> Unit,
    onKeywordAdded: (String) -> Unit,
    onKeywordRemoved: (String) -> Unit,
    onMatchModeChanged: (KeywordMatchMode) -> Unit,
    onSectionsOpened: () -> Unit,
    onOrganizationChanged: (String) -> Unit,
    onOrganizationChosen: (String) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onPreviewOpened: () -> Unit,
) {
    val draft = state.draft
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = BocTheme.spacing.screenMargin, vertical = BocTheme.spacing.space3),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space5),
    ) {
        IntroNotice()

        Section(label = stringResource(R.string.alert_form_name_label)) {
            val nameError = state.errors.firstOrNull { it != AlertRuleValidationError.NO_CRITERIA }
            OutlinedTextField(
                value = draft.name,
                onValueChange = onNameChanged,
                singleLine = true,
                placeholder = { Text(text = stringResource(R.string.alert_form_name_hint)) },
                // Blank is only an error once something else has been typed: an empty form must not
                // open shouting.
                isError = nameError == AlertRuleValidationError.NAME_TOO_LONG,
                supportingText = when (nameError) {
                    AlertRuleValidationError.NAME_TOO_LONG ->
                        { { Text(text = stringResource(R.string.alert_form_name_error_long, AlertRuleDraft.NAME_MAX_LENGTH)) } }
                    else -> null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_ALERT_FORM_NAME),
            )
        }

        Section(label = stringResource(R.string.alert_form_keywords_label), help = stringResource(R.string.alert_form_keywords_help)) {
            KeywordChipsInput(
                keywords = draft.keywords,
                rejection = state.keywordRejection,
                onAdd = onKeywordAdded,
                onRemove = onKeywordRemoved,
            )
        }

        Section(label = stringResource(R.string.alert_form_match_label)) {
            MatchModeSelector(selected = draft.matchMode, onSelect = onMatchModeChanged)
        }

        Section(label = stringResource(R.string.alert_form_sections_label), help = stringResource(R.string.alert_form_sections_help)) {
            OutlinedButton(
                onClick = onSectionsOpened,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_ALERT_FORM_SECTIONS_OPEN),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(painter = painterResource(R.drawable.ic_document), contentDescription = null, modifier = Modifier.size(ICON_SIZE))
                Spacer(modifier = Modifier.width(BocTheme.spacing.space2))
                Text(
                    text = sectionSummaryText(state.sectionParts),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TAG_ALERT_FORM_SECTIONS_SUMMARY),
                    maxLines = 2,
                )
                Icon(painter = painterResource(R.drawable.ic_expand_more), contentDescription = stringResource(R.string.alert_form_sections_open))
            }
        }

        Section(label = stringResource(R.string.alert_form_organization_label)) {
            OrganizationField(
                value = draft.organizationQuery,
                suggestions = state.organizationSuggestions,
                onChange = onOrganizationChanged,
                onChosen = onOrganizationChosen,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.alert_form_enabled_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.alert_form_enabled_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BocTheme.colors.textSecondary,
                )
            }
            Switch(checked = draft.isEnabled, onCheckedChange = onEnabledChanged, modifier = Modifier.testTag(TAG_ALERT_FORM_ENABLED))
        }

        RuleSummaryCard(draft = draft, sectionParts = state.sectionParts)

        PreviewLine(count = state.previewCount, onOpen = onPreviewOpened)
    }
}

@Composable
private fun IntroNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(BocTheme.spacing.space4), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(INTRO_ICON_SIZE),
            )
            Spacer(modifier = Modifier.width(BocTheme.spacing.space3))
            Text(
                text = stringResource(R.string.alert_form_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Section(label: String, help: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        help?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = BocTheme.colors.textSecondary)
        }
        content()
    }
}

/** «N publicaciones actuales coinciden…» with «Ver resultados», or that none does (spec §15). */
@Composable
private fun PreviewLine(count: Int?, onOpen: () -> Unit) {
    if (count == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_ALERT_FORM_PREVIEW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count > 0) {
                pluralStringResource(R.plurals.alert_form_preview_count, count, count)
            } else {
                stringResource(R.string.alert_form_preview_none)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            TextButton(onClick = onOpen, modifier = Modifier.testTag(TAG_ALERT_FORM_PREVIEW_OPEN)) {
                Text(text = stringResource(R.string.alert_form_preview_open))
            }
        }
    }
}

/**
 * Cancelar / Guardar as a bottom bar. A `Scaffold` with a `bottomBar` discards its own bottom inset,
 * so the bar applies the system margin itself, exactly as the detail's action bar does.
 */
@Composable
private fun FormActions(canSave: Boolean, isSaving: Boolean, isEdit: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = BocTheme.colors.divider)
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = BocTheme.spacing.screenMargin, vertical = BocTheme.spacing.space3),
                horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TAG_ALERT_FORM_CANCEL),
                ) {
                    Text(text = stringResource(R.string.alert_form_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TAG_ALERT_FORM_SAVE),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE), strokeWidth = PROGRESS_STROKE)
                    } else {
                        Text(text = stringResource(if (isEdit) R.string.alert_form_save_edit else R.string.alert_form_save_create))
                    }
                }
            }
        }
    }
}

private val ICON_SIZE = 20.dp
private val INTRO_ICON_SIZE = 32.dp
private val PROGRESS_SIZE = 20.dp
private val PROGRESS_STROKE = 2.dp
