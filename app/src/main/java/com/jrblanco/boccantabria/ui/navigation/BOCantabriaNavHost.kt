package com.jrblanco.boccantabria.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.splash.SplashScreen

/**
 * The outer graph: the cover, and then everything else.
 *
 * Two hosts rather than one because the cover shares nothing with the rest — no bottom bar, no
 * sections panel — and because keeping it here leaves its back-stack behaviour exactly as the
 * previous feature left it.
 */
@Composable
fun BOCantabriaNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    mainNavController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.Splash,
        modifier = modifier,
    ) {
        composable<Route.Splash> {
            SplashScreen(
                onStartupComplete = {
                    navController.navigate(Route.Home()) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
            )
        }
        composable<Route.Home> {
            MainShell(navController = mainNavController)
        }
    }
}
