package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.BocSection

/**
 * The official section tree of the bulletin.
 *
 * Deliberately not suspending and not a flow: the nine sections and their subsections are
 * compiled knowledge, not a remote resource. If that ever changes, this is the one place that
 * has to change with it.
 */
interface BocSectionRepository {

    /** Every section and subsection, in official order. */
    fun sections(): List<BocSection>
}
