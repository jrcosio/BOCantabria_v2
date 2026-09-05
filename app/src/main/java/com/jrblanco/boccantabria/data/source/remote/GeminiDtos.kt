package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ---------- Files API ----------

/** The metadata that starts a resumable upload. Only the display name travels. */
@Serializable
internal data class GeminiFileUploadStart(val file: GeminiFileDisplayName)

@Serializable
internal data class GeminiFileDisplayName(@SerialName("display_name") val displayName: String)

/** What both the upload and the status poll answer with. */
@Serializable
internal data class GeminiFileEnvelope(val file: GeminiFile? = null)

@Serializable
internal data class GeminiFile(
    val name: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val state: String? = null,
    val error: GeminiError? = null,
)

// ---------- generateContent ----------

@Serializable
internal data class GeminiGenerateRequest(
    @SerialName("system_instruction") val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig,
)

@Serializable
internal data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
internal data class GeminiPart(
    val text: String? = null,
    @SerialName("file_data") val fileData: GeminiFileData? = null,
)

@Serializable
internal data class GeminiFileData(
    @SerialName("file_uri") val fileUri: String,
    @SerialName("mime_type") val mimeType: String,
)

@Serializable
internal data class GeminiGenerationConfig(
    @SerialName("thinkingConfig") val thinkingConfig: GeminiThinkingConfig,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
    @SerialName("responseMimeType") val responseMimeType: String,
    @SerialName("responseJsonSchema") val responseJsonSchema: JsonElement,
)

@Serializable
internal data class GeminiThinkingConfig(@SerialName("thinkingLevel") val thinkingLevel: String)

@Serializable
internal data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsageMetadata? = null,
    val modelVersion: String? = null,
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiResponseContent? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class GeminiResponseContent(val parts: List<GeminiResponsePart> = emptyList())

/**
 * `thought` marks a reasoning part, and those must be skipped.
 *
 * Feature 009 learned this the hard way against the live service: the reasoning step arrives
 * **always** before the answer, so taking the first part would have been wrong a hundred per cent of
 * the time. Skip by the flag, never by position.
 */
@Serializable
internal data class GeminiResponsePart(
    val text: String? = null,
    val thought: Boolean? = null,
)

@Serializable
internal data class GeminiUsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0,
    val thoughtsTokenCount: Int = 0,
)

// ---------- Errors ----------

@Serializable
internal data class GeminiErrorEnvelope(val error: GeminiError? = null)

@Serializable
internal data class GeminiError(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
    val details: List<JsonObject> = emptyList(),
)

/**
 * The wire's token counts, in ours.
 *
 * `internal` and here rather than private in the summary's file, because since feature 011 two data
 * sources build a `SummaryUsage` from the same response shape.
 */
internal fun GeminiGenerateResponse.toUsage(): SummaryUsage = SummaryUsage(
    totalInputTokens = usageMetadata?.promptTokenCount ?: 0,
    totalOutputTokens = usageMetadata?.candidatesTokenCount ?: 0,
    totalTokens = usageMetadata?.totalTokenCount ?: 0,
    // Should be low or zero. If it grows, the thinking level is not being applied.
    totalThoughtTokens = usageMetadata?.thoughtsTokenCount ?: 0,
)
