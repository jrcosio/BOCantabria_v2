package com.jrblanco.boccantabria.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_BOTTOM_BAR: String = "bottom_bar"
const val TAG_BOTTOM_HOME: String = "bottom_home"
const val TAG_BOTTOM_SEARCH: String = "bottom_search"
const val TAG_BOTTOM_SAVED: String = "bottom_saved"

/**
 * Three destinations, not the four the design document drew.
 *
 * Alerts are postponed with the rest of the notification work, and a fourth destination leading
 * to a promise would be worse than three that all lead somewhere. The document has been updated.
 */
@Composable
fun BocBottomBar(
    current: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.testTag(TAG_BOTTOM_BAR),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        BottomDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(destination.iconRes),
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        // Weight as well as colour: a selected state that depends on colour alone
                        // is invisible to a good number of readers.
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = BocTheme.colors.textSecondary,
                    unselectedTextColor = BocTheme.colors.textSecondary,
                ),
                modifier = Modifier.testTag(destination.testTag),
            )
        }
    }
}

enum class BottomDestination(
    val iconRes: Int,
    val labelRes: Int,
    val testTag: String,
) {
    HOME(R.drawable.ic_home, R.string.nav_home, TAG_BOTTOM_HOME),
    SEARCH(R.drawable.ic_search, R.string.nav_search, TAG_BOTTOM_SEARCH),
    SAVED(R.drawable.ic_bookmark, R.string.nav_saved, TAG_BOTTOM_SAVED),
}
