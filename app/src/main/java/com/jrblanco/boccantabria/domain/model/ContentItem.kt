package com.jrblanco.boccantabria.domain.model

/**
 * Smallest unit of information shown on the home screen.
 *
 * Deliberate placeholder: it will be replaced by the real entity when the first business
 * feature is specified. Its purpose here is to prove and protect the trip across the layers.
 *
 * @param id stable across loads: it identifies the item, not its position.
 * @param title text shown to the user.
 */
data class ContentItem(
    val id: String,
    val title: String,
)
