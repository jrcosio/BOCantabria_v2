package com.jrblanco.boccantabria.domain.model

/**
 * A section or subsection of the bulletin, as the official hierarchy defines it.
 *
 * Sections 2, 4, 7 and 8 have **no feed of their own**: their content is the union of their
 * subsections'. A query for a top-level section must therefore reach its descendants too.
 *
 * @param code `1`, `2`, `2.1`… Also the identity of the section.
 * @param shortName label for the filter chips, where the official name would not fit.
 * @param parentCode `null` for the nine top-level sections.
 */
data class BocSection(
    val code: String,
    val name: String,
    val shortName: String,
    val parentCode: String?,
    val order: Int,
    val colorGroup: SectionColorGroup,
) {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(shortName.isNotBlank()) { "shortName must not be blank" }
        require(parentCode == null || code.startsWith("$parentCode.")) {
            "subsection code '$code' must be prefixed by its parent '$parentCode'"
        }
    }

    val isTopLevel: Boolean get() = parentCode == null

    /** `1 · Disposiciones generales`, the form the drawer rows use. */
    val displayLabel: String get() = "$code · $name"
}
