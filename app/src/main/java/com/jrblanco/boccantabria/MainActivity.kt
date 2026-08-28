package com.jrblanco.boccantabria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jrblanco.boccantabria.core.ui.theme.BOCantabriaTheme
import com.jrblanco.boccantabria.ui.navigation.BOCantabriaNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
