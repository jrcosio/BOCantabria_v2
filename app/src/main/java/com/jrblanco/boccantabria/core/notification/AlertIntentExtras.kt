package com.jrblanco.boccantabria.core.notification

/**
 * What a notification's `Intent` carries so the application knows where to land.
 *
 * Lives in `core` because two layers read it: `data` writes the extras when it posts a notification,
 * and `MainActivity` reads them when the notification is tapped. Neither may import the other
 * (012 research.md D-425).
 */
object AlertIntentExtras {

    const val EXTRA_TARGET: String = "boc.alert.target"
    const val EXTRA_EXTERNAL_KEY: String = "boc.alert.external_key"

    const val TARGET_PUBLICATION: String = "publication"
    const val TARGET_NEWS: String = "news"
}
