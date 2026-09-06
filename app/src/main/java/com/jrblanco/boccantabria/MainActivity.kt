package com.jrblanco.boccantabria

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.ui.navigation.BOCantabriaNavHost
import com.jrblanco.boccantabria.ui.navigation.PendingNavigationStore
import com.jrblanco.boccantabria.ui.navigation.toPendingNavigation
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val pendingNavigation: PendingNavigationStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // A tapped notification. Only on a fresh start: after a configuration change the same intent
        // comes back, and consuming it again would reopen the detail on every rotation. The cover is
        // never skipped — the graph consumes this after it (012 research.md D-424).
        if (savedInstanceState == null) intent?.toPendingNavigation()?.let(pendingNavigation::set)

        // Left to itself, enableEdgeToEdge picks the system bar icon colour from the phone's
        // light/dark setting. This application has a single, light appearance, so on a phone set to
        // dark the icons would come out light on a white background and be unreadable. Pinning the
        // light style makes them dark everywhere; the blue cover flips them while it is on screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        // The system splash is held only until the first frame is composed, not until the startup
        // work finishes. Holding it longer would leave the user staring at a static image with no
        // progress and no way out; the Compose cover can show both.
        var readyToDraw = false
        splashScreen.setKeepOnScreenCondition { !readyToDraw }

        setContent {
            BOCantabriaTheme {
                BOCantabriaNavHost(modifier = Modifier.fillMaxSize())
            }
            readyToDraw = true
        }
    }

    /** `singleTop`: a tap with the application already open lands here instead of in a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toPendingNavigation()?.let(pendingNavigation::set)
    }
}
