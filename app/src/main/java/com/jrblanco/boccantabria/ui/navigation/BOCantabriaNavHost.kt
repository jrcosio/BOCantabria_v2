package com.jrblanco.boccantabria.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.splash.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            val scope = rememberCoroutineScope()
            SplashScreen(
                onStartupComplete = {
                    // Navigating moves the lifecycle of the back-stack entries, and that is only
                    // legal on the main thread. On a device it always is: the cover fires this
                    // from a `LaunchedEffect`, and composition effects run on the UI dispatcher.
                    // Under Compose's test environment the same continuation can be resumed from
                    // the thread pumping the frames, and then it throws. Pinning it here costs
                    // nothing in production —`immediate` runs inline when already on main— and
                    // turns an intermittent failure into no failure at all.
                    //
                    // The one place the project references a dispatcher statically instead of
                    // injecting it. It is not business logic: it is a platform requirement of the
                    // navigation call, and injecting it would only move the constant elsewhere.
                    scope.launch(Dispatchers.Main.immediate) {
                        navController.navigate(Route.Home()) {
                            popUpTo(Route.Splash) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<Route.Home> {
            MainShell(navController = mainNavController)
        }
    }
}
