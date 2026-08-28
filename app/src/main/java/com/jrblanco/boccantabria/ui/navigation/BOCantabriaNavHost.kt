package com.jrblanco.boccantabria.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jrblanco.boccantabria.ui.home.HomeScreen
import com.jrblanco.boccantabria.ui.splash.SplashScreen

@Composable
fun BOCantabriaNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.Splash,
        modifier = modifier,
    ) {
        composable<Route.Splash> {
            SplashScreen(
                onStartupComplete = {
                    navController.navigate(Route.Home) {
                        // The cover is dropped from the back stack, so pressing back from Home
                        // closes the application instead of returning to a screen whose work is
                        // already done.
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
            )
        }
        composable<Route.Home> {
            HomeScreen()
        }
    }
}
