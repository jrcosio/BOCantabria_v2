package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.local.PageCountResult
import com.jrblanco.boccantabria.data.source.local.PdfPageCounter

/** A page counter the test drives, so no unit test needs a real PDF or an emulator. */
class FakePdfPageCounter(
    var result: PageCountResult = PageCountResult.Success(totalPages = 1),
) : PdfPageCounter {

    var calls: Int = 0
        private set

    override suspend fun pageCount(localPath: String): PageCountResult {
        calls++
        return result
    }
}
