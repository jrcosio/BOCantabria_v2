package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.local.PdfExtractionResult
import com.jrblanco.boccantabria.data.source.local.PdfTextExtractor

/** An extractor the test drives, so no unit test needs a real PDF or an emulator. */
class FakePdfTextExtractor(
    var result: PdfExtractionResult = PdfExtractionResult.Success(pdfCorpus()),
) : PdfTextExtractor {

    var calls: Int = 0
        private set

    override suspend fun extract(
        localPath: String,
        externalKey: String,
        pdfSha256: String,
    ): PdfExtractionResult {
        calls++
        return result
    }
}
