package com.jrblanco.boccantabria.core.util

/**
 * The version code of the installed application.
 *
 * An interface rather than a direct read so the "installed version is below the minimum supported"
 * branch can be exercised from a plain unit test. Reading it statically at the point of use would
 * push that check onto an emulator (research.md, D-009).
 */
interface AppVersionProvider {
    val versionCode: Int
}
