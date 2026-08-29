package com.jrblanco.boccantabria.data.source.remote

/**
 * A source's response, exactly as it arrived.
 *
 * Everything is nullable and `declaredSize` is an `Int?` rather than an `Int` because this type
 * describes what the service **sent**, not what it should have sent. Validation happens
 * afterwards, which is what allows one bad announcement to be dropped without losing the other
 * ninety-nine.
 *
 * @param declaredSize the non-standard `<size>` node. Diagnostic only: the real count is the
 *   number of `<item>` nodes, and a mismatch is a warning, never a reason to reject the response.
 */
data class RssChannelDto(
    val title: String?,
    val link: String?,
    val description: String?,
    val declaredSize: Int?,
    val items: List<RssItemDto>,
)

/**
 * One announcement as the source publishes it.
 *
 * Four fields, and no `guid`: the service does not provide one, so the identifier has to be
 * recovered from [link]. `pubDateRaw` is text and not a date because the source uses
 * `yyyy-MM-dd` instead of the RFC 822 form RSS normally carries, and an unparseable value has to
 * reach the normaliser to be rejected there with a reason.
 */
data class RssItemDto(
    val title: String?,
    val link: String?,
    val pubDateRaw: String?,
    val categoriesRaw: String?,
)
