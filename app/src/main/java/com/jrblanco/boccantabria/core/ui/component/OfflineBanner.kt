package com.jrblanco.boccantabria.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocBannerShape
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_OFFLINE_BANNER: String = "home_offline_banner"

/**
 * Tells the reader the content is the last one downloaded.
 *
 * Sits above the list rather than over it: the design document is explicit that the notice must
 * not hide content, and a person consulting an official bulletin without coverage needs the
 * bulletin more than they need the notice.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BocTheme.spacing.screenMargin)
            .clip(BocBannerShape)
            .background(BocTheme.colors.surfaceStrong)
            .padding(horizontal = BocTheme.spacing.space3, vertical = BocTheme.spacing.space2)
            .testTag(TAG_OFFLINE_BANNER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_cloud_off),
            contentDescription = null,
            tint = BocTheme.colors.textSecondary,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = stringResource(R.string.home_offline),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = BocTheme.colors.textSecondary,
        )
    }
}

private val ICON_SIZE = 20.dp
