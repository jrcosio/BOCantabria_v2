package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.BuildConfig

/**
 * Where the credential comes from.
 *
 * An abstraction so the rest of the feature never knows. Today it is a build-time field; tomorrow it
 * could be a proxy of our own, or Firebase AI Logic — which would keep the key out of the package
 * altogether — and that change would be one implementation and one URL (009 research.md D-102).
 *
 * The limitation is written down rather than glossed over: a credential inside a distributed
 * application **can be recovered** by anyone who inspects the package. The owner knows, and it is
 * why the key must be revocable and tightly limited. It is recorded in the assumptions of `spec.md`.
 */
fun interface GeminiApiKeyProvider {

    /** `null` when there is no credential, which the screen reports as «not configured» (FR-029). */
    suspend fun apiKey(): String?
}

/**
 * Reads the field the build injects from `local.properties`.
 *
 * When the key is absent the field is an empty string and this returns `null`: the build stays
 * green, the tests run, and only the summary announces itself as unavailable. That is what lets
 * continuous integration work without secrets (FR-033, SC-011).
 */
class BuildConfigGeminiApiKeyProvider(
    private val configured: String = BuildConfig.GEMINI_API_KEY,
) : GeminiApiKeyProvider {

    override suspend fun apiKey(): String? = configured.takeIf { it.isNotBlank() }

    /**
     * Deliberately says nothing. A credential that reaches a log, a crash report or a `toString()`
     * has left the device, and this is the object most likely to end up in one of the three
     * (FR-032, SC-010).
     */
    override fun toString(): String = "BuildConfigGeminiApiKeyProvider(configured=<oculto>)"
}
