package com.jrblanco.boccantabria.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.jrblanco.boccantabria.ui.alerts.form.AlertFormScreen
import com.jrblanco.boccantabria.ui.ask.AskRoute
import com.jrblanco.boccantabria.ui.detail.PublicationDetailScreen
import com.jrblanco.boccantabria.ui.info.InfoScreen
import com.jrblanco.boccantabria.ui.main.MainShell
import com.jrblanco.boccantabria.ui.pdf.PdfViewerScreen
import com.jrblanco.boccantabria.ui.splash.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
    pendingNavigation: PendingNavigationStore = koinInject(),
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
            // A tapped notification lands here **after** the cover has done its checks, and only if
            // it did: a blocked cover never composes this destination, so nothing is consumed and
            // nothing navigates (FR-049; 012 research.md D-424). The news target is consumed by the
            // shell, which owns the inner controller.
            val pending by pendingNavigation.pending.collectAsStateWithLifecycle()
            LaunchedEffect(pending) {
                if (pending is PendingNavigation.Publication) {
                    val target = pendingNavigation.consume() as? PendingNavigation.Publication ?: return@LaunchedEffect
                    navController.navigate(Route.Detail(target.externalKey))
                }
            }
            MainShell(
                navController = mainNavController,
                onOpenPublication = { externalKey ->
                    navController.navigate(Route.Detail(externalKey))
                },
                onOpenInfo = { navController.navigate(Route.Info) },
                onOpenAlertForm = { route -> navController.navigate(route) },
                pendingNavigation = pendingNavigation,
            )
        }
        composable<Route.Info> {
            InfoScreen(onBack = navController::popBackStack)
        }
        composable<Route.AlertForm> {
            AlertFormScreen(
                onBack = navController::popBackStack,
                // The preview opens a publication on top of the form, so coming back lands on the
                // form with the draft intact.
                onOpenPublication = { externalKey -> navController.navigate(Route.Detail(externalKey)) },
            )
        }
        composable<Route.Detail> {
            PublicationDetailScreen(
                onBack = navController::popBackStack,
                onOpenDocument = { externalKey ->
                    navController.navigate(Route.PdfViewer(externalKey))
                },
                // A page reference in the AI summary, followed. Navigated **without**
                // `restoreState`: saved state wins over a route argument, and in feature 006 that
                // swallowed a handed-over search term without any error at all.
                onOpenDocumentAtPage = { externalKey, page ->
                    navController.navigate(Route.PdfViewer(externalKey, page))
                },
                onAsk = { externalKey ->
                    navController.navigate(Route.Ask(externalKey))
                },
            )
        }
        composable<Route.PdfViewer> {
            PdfViewerScreen(onBack = navController::popBackStack)
        }
        composable<Route.Ask> { entry ->
            val route = entry.toRoute<Route.Ask>()
            AskRoute(
                onBack = navController::popBackStack,
                // Following a source lands on the page it cites. The viewer stacks on top of the
                // conversation, which stacks on top of the detail, so the detail's entry — the one
                // that releases the document and discards the conversation — stays alive throughout.
                onOpenDocument = { page ->
                    navController.navigate(Route.PdfViewer(route.externalKey, page))
                },
            )
        }
    }
}
