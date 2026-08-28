package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.Serializable

/**
 * Content item as the remote source delivers it.
 *
 * The field is `label`, not `title`, on purpose: it keeps the mapping to the domain a real
 * translation instead of a field-for-field copy, which is what makes the boundary visible.
 */
@Serializable
data class ContentItemDto(
    val id: String,
    val label: String,
)
