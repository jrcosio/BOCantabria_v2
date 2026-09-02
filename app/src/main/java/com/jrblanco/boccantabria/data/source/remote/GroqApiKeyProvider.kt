package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.BuildConfig

/**
 * Where the credential comes from.
 *
 * An abstraction so the rest of the feature never knows. Today it is a build-time field; tomorrow it
 * could be a proxy of our own, and that change would be one implementation and one URL
 * (research.md D-017).
 *
 * The limitation is written down rather than glossed over: a credential inside a distributed
 * application **can be recovered** by anyone who inspects the package. That is acceptable while the
 * application is not published, and it is why the key must be revocable and tightly limited. It is
 * recorded in the assumptions of `spec.md`.
 */
fun interface GroqApiKeyProvider {

    /** `null` when there is no credential, which the screen reports as «not configured» (FR-042). */
    suspend fun apiKey(): String?
}

/**
 * Reads the field the build injects from `local.properties`.
 *
 * When the key is absent the field is an empty string and this returns `null`: the build stays
 * green, the tests run, and only the summary announces itself as unavailable. That is what lets
 * continuous integration work without secrets.
 */
class BuildConfigGroqApiKeyProvider(
    private val configured: String = BuildConfig.GROQ_API_KEY,
) : GroqApiKeyProvider {

    override suspend fun apiKey(): String? = configured.takeIf { it.isNotBlank() }

    /**
     * Deliberately says nothing. A credential that reaches a log, a crash report or a `toString()`
     * has left the device, and this is the object most likely to end up in one of the three
     * (FR-047, SC-009).
     */
    override fun toString(): String = "BuildConfigGroqApiKeyProvider(configured=<oculto>)"
}
