package com.jrblanco.boccantabria.domain.model

/**
 * A page of the document an answer leans on, and a short label saying what is there.
 *
 * [page] is **1-based**, the way a person reads it. The viewer's route counts from zero, and the
 * conversion happens where the navigation happens — the same arrangement the summary's page chips
 * already use.
 *
 * Every source that reaches the screen has been through `ChatAnswerValidator` and therefore exists in
 * the document. A citation to page 14 of a nine-page bulletin is not a harmless slip: it is a
 * reference that leads nowhere, and a reference nobody can check is not a reference (FR-014, SC-005).
 */
data class AiAnswerSource(
    val page: Int,
    val label: String,
) {
    init {
        require(page >= 1) { "pages are counted from one, was: $page" }
    }
}
