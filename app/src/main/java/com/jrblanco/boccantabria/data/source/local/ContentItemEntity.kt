package com.jrblanco.boccantabria.data.source.local

/** Content item as the local source stores it. Never crosses into the presentation layer. */
data class ContentItemEntity(
    val id: String,
    val title: String,
)
