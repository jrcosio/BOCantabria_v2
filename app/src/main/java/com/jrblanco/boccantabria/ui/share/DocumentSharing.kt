package com.jrblanco.boccantabria.ui.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.domain.model.ShareTarget
import java.io.File

/**
 * Hands the document to the system share sheet.
 *
 * The file travels as a `content://` URI granted read permission for this one send. A `file://`
 * URI would throw since Android 7, and opening the cache directory wholesale to every application
 * on the phone to avoid that would be a far worse trade.
 */
fun Context.share(target: ShareTarget, subject: String) {
    val intent = when (target) {
        is ShareTarget.Document -> Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME
            putExtra(Intent.EXTRA_STREAM, documentUri(target.document.localPath))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        is ShareTarget.Link -> Intent(Intent.ACTION_SEND).apply {
            type = TEXT_MIME
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "$subject\n${target.url}")
        }
    }
    startActivity(Intent.createChooser(intent, getString(R.string.publication_share_chooser)))
}

private fun Context.documentUri(localPath: String) =
    FileProvider.getUriForFile(this, "$packageName.documents", File(localPath))

private const val PDF_MIME = "application/pdf"
private const val TEXT_MIME = "text/plain"
