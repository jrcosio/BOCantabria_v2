package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import com.jrblanco.boccantabria.domain.model.Publication
import java.time.LocalDate

/** Shared builder so no test has to spell out fourteen fields to talk about one of them. */
@Suppress("LongParameterList")
fun publication(
    key: String = "boc:439765",
    title: String = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva de la Ordenanza Fiscal.",
    issuer: String? = "Ayuntamiento de Piélagos",
    sectionCode: String = "1",
    subsectionCode: String? = null,
    date: LocalDate = LocalDate.of(2026, 8, 27),
    editionType: EditionType = EditionType.ORDINARY,
    documentUrl: String = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=" +
        key.substringAfter(':'),
): Publication = Publication(
    externalKey = key,
    blobId = key.substringAfter(':'),
    idSource = IdSource.BLOB_ID,
    feedId = "6802081",
    sectionCode = sectionCode,
    subsectionCode = subsectionCode,
    title = title,
    issuer = issuer,
    organizationPath = listOfNotNull(issuer),
    editionType = editionType,
    publicationDate = date,
    documentUrl = documentUrl,
    rawCategories = "1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD",
)
