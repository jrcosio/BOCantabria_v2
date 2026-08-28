package com.jrblanco.boccantabria.domain.model

/**
 * Which rung of the identifier fallback produced a publication's external key.
 *
 * Worth storing rather than deriving: a publication identified by content hash is *replaceable*
 * the day the source starts publishing a real identifier for it, and nothing else would tell us
 * which records those are.
 */
enum class IdSource {

    /** `idAnuBlob` was present in the link. The good case. */
    BLOB_ID,

    /** No identifier in the link, so the canonical URL is the key. */
    CANONICAL_URL,

    /** Neither was usable: the key is a digest of feed, date, title and categories. */
    CONTENT_HASH,
}
