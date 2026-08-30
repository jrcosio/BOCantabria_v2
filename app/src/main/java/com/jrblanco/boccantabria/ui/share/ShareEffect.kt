package com.jrblanco.boccantabria.ui.share

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.domain.model.ShareTarget

/**
 * Hands a prepared share to the system, saying out loud what is going on.
 *
 * Both the wait and the fall back to the link are announced: sharing may have to fetch the document
 * first, and a share sheet that takes three seconds to appear —or that offers a link when the
 * document was asked for— would otherwise look like the application ignoring the tap.
 *
 * Extracted because the bulletin and the saved list share it **exactly** (FR-014), and a third copy
 * of these twenty lines would guarantee that the next correction reached two of the three. The detail
 * screen deliberately keeps its own: it draws a progress line of its own instead of a toast, so
 * folding it in here would need a parameter whose only job is to tell them apart.
 *
 * @param onConsumed called once the target has been handed over, so a configuration change does not
 *   open the share sheet again.
 */
@Composable
fun ShareEffect(share: ShareState, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val preparing = stringResource(R.string.share_preparing)
    val linkFallback = stringResource(R.string.share_link_fallback)

    LaunchedEffect(share) {
        when (share) {
            ShareState.Preparing ->
                Toast.makeText(context, preparing, Toast.LENGTH_SHORT).show()

            is ShareState.Ready -> {
                if (share.target is ShareTarget.Link) {
                    Toast.makeText(context, linkFallback, Toast.LENGTH_LONG).show()
                }
                context.share(share.target, share.subject)
                onConsumed()
            }

            ShareState.Idle -> Unit
        }
    }
}
