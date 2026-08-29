package com.jrblanco.boccantabria.domain.model

/**
 * Which of the five colour groups of the design document a section belongs to.
 *
 * Nine sections share five groups on purpose: the palette defines five, and inventing four more
 * would turn colour from a grouping cue into noise. No information is lost, because the colour
 * indicator always travels with text.
 *
 * A domain type, not a colour: turning it into a colour is the theme's job, and the domain
 * cannot see Compose.
 */
enum class SectionColorGroup {
    GENERAL,
    PERSONNEL,
    CONTRACTING,
    ECONOMY,
    ANNOUNCEMENTS,
}
