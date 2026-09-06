package com.jrblanco.boccantabria.domain.model

/**
 * The hierarchy of sections, applied to a selection.
 *
 * Sections 2, 4, 7 and 8 have no feed of their own: choosing one of them means choosing every
 * subsection. This object is the single place where that is decided, so the form, the rule and the
 * summary can never disagree about what "section 2" selects (research.md D-407).
 *
 * A selection is always a set of **leaf** classification codes. A parent code fed into any of these
 * functions is understood as "all of its children".
 */
object SectionSelection {

    enum class ToggleState { CHECKED, INDETERMINATE, UNCHECKED }

    /** One line of the summary: a section, and whether it stands for all of its children. */
    data class Part(val section: BocSection, val allChildren: Boolean)

    /** Replaces every parent in [selected] with its children. Leaves and unknown codes pass through. */
    fun expandToLeaves(selected: Set<String>, sections: List<BocSection>): Set<String> {
        val childrenOf = childrenByParent(sections)
        return selected.flatMap { code -> childrenOf[code]?.map { it.code } ?: listOf(code) }.toSet()
    }

    /**
     * Toggles [code]: a parent puts all of its children in or —when every one of them already is—
     * takes them all out; a leaf simply flips.
     */
    fun toggled(selected: Set<String>, code: String, sections: List<BocSection>): Set<String> {
        val children = childrenByParent(sections)[code]
        if (children.isNullOrEmpty()) {
            return if (code in selected) selected - code else selected + code
        }
        val childCodes = children.map { it.code }.toSet()
        return if (childCodes.all { it in selected }) selected - childCodes else selected + childCodes
    }

    /** What the checkbox of [section] shows: for a parent, derived from its children. */
    fun stateOf(section: BocSection, sections: List<BocSection>, selected: Set<String>): ToggleState {
        val children = childrenByParent(sections)[section.code]
        if (children.isNullOrEmpty()) {
            return if (section.code in selected) ToggleState.CHECKED else ToggleState.UNCHECKED
        }
        val chosen = children.count { it.code in selected }
        return when (chosen) {
            0 -> ToggleState.UNCHECKED
            children.size -> ToggleState.CHECKED
            else -> ToggleState.INDETERMINATE
        }
    }

    /**
     * The selection in reading order, one [Part] per top-level section touched.
     *
     * `null` means nothing is selected, which the interface reads as «Todas las secciones». A parent
     * whose every child is in comes out as a single part with [Part.allChildren] set; otherwise each
     * chosen child is its own part.
     */
    fun summaryParts(selected: Set<String>, sections: List<BocSection>): List<Part>? {
        if (selected.isEmpty()) return null
        val childrenOf = childrenByParent(sections)
        val parts = sections.filter { it.isTopLevel }.sortedBy { it.order }.flatMap { parent ->
            val children = childrenOf[parent.code].orEmpty()
            when {
                children.isEmpty() -> listOfNotNull(parent.takeIf { it.code in selected }?.let { Part(it, false) })
                children.all { it.code in selected } -> listOf(Part(parent, true))
                else -> children.filter { it.code in selected }.sortedBy { it.order }.map { Part(it, false) }
            }
        }
        return parts.ifEmpty { null }
    }

    /** How many leaves are selected: what the picker counts. */
    fun leafCount(selected: Set<String>, sections: List<BocSection>): Int =
        expandToLeaves(selected, sections).size

    private fun childrenByParent(sections: List<BocSection>): Map<String, List<BocSection>> =
        sections.filter { !it.isTopLevel }.groupBy { requireNotNull(it.parentCode) }
}
