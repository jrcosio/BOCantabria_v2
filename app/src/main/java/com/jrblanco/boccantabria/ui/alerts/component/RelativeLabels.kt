package com.jrblanco.boccantabria.ui.alerts.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.util.RelativeTime
import com.jrblanco.boccantabria.domain.model.SectionSelection
import java.time.format.DateTimeFormatter
import java.util.Locale

/** «Ahora mismo», «Hace 20 min», «Hace 2 h», «Hoy», «Ayer», or the date. Interface copy, so it lives here. */
@Composable
fun relativeLabelText(label: RelativeTime.Label): String = when (label) {
    RelativeTime.Label.JustNow -> stringResource(R.string.alerts_time_just_now)
    is RelativeTime.Label.Minutes -> stringResource(R.string.alerts_time_minutes, label.count)
    is RelativeTime.Label.Hours -> stringResource(R.string.alerts_time_hours, label.count)
    RelativeTime.Label.Today -> stringResource(R.string.alerts_day_today)
    RelativeTime.Label.Yesterday -> stringResource(R.string.alerts_day_yesterday)
    is RelativeTime.Label.Day -> label.date.format(SPANISH_DAY)
}

/** «Todas las secciones», «Autoridades y personal (todas)», or the names joined by commas. */
@Composable
fun sectionSummaryText(parts: List<SectionSelection.Part>?): String {
    if (parts == null) return stringResource(R.string.alerts_rule_all_sections)
    // `map` is inline, so the composable call inside it is fine; `joinToString`'s transform is not.
    return parts.map { part -> sectionPartText(part) }.joinToString(separator = ", ")
}

@Composable
fun sectionPartText(part: SectionSelection.Part): String =
    if (part.allChildren) stringResource(R.string.alerts_rule_section_all_children, part.section.name) else part.section.name

private val SPANISH_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-ES"))
