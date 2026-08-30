package com.jrblanco.boccantabria.core.ui.component

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R

/**
 * Says that the mark could not be written, and clears the signal.
 *
 * One piece for the three places that offer the action (FR-009). The other half of that requirement
 * needs no code at all: the bookmark is derived from what is stored, so a write that failed leaves
 * the icon exactly as it was — nothing is ever shown as saved when it is not.
 *
 * @param onConsumed called once said, so a configuration change does not repeat it.
 */
@Composable
fun SaveFailureToast(failed: Boolean, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val message = stringResource(R.string.save_failed)

    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onConsumed()
    }
}
