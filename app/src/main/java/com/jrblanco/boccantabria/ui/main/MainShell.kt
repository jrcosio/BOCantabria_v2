package com.jrblanco.boccantabria.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.ui.home.HomeScreen
import com.jrblanco.boccantabria.ui.navigation.BocBottomBar
import com.jrblanco.boccantabria.ui.navigation.BottomDestination
import com.jrblanco.boccantabria.ui.navigation.Route
import com.jrblanco.boccantabria.ui.saved.SavedScreen
import com.jrblanco.boccantabria.ui.search.SearchScreen
import com.jrblanco.boccantabria.ui.sections.SectionsDrawerContent
import com.jrblanco.boccantabria.ui.sections.SectionsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The frame the three destinations share: the sections panel and the bottom bar, wrapped around
 * their own navigation host.
 *
 * The splash screen stays **outside** this frame. Wrapping the whole application would mean
 * drawing a bar and a panel over the cover and then hiding them, and the cover has no business
 * knowing they exist.
 */
@Composable
fun MainShell(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    sectionsViewModel: SectionsViewModel = koinViewModel(),
    getSections: GetBocSectionsUseCase = koinInject(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sectionsState by sectionsViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()

    // The whole tree, not `sectionsState.rows`: those are filtered by whatever is typed in the
    // panel, so using them would empty the chips and strip the section label off every card the
    // moment someone searched.
    val sections = remember(getSections) { getSections() }
    val comingSoon = stringResource(R.string.coming_soon)

    fun showComingSoon() {
        Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show()
    }

    fun openSection(section: BocSection?) {
        scope.launch { drawerState.close() }
        val route = when {
            section == null -> Route.Home()
            section.isTopLevel -> Route.Home(sectionCode = section.code)
            else -> Route.Home(sectionCode = section.parentCode, subsectionCode = section.code)
        }
        sectionsViewModel.onSelectionChanged(
            HomeSelection.of(route.sectionCode, route.subsectionCode),
        )
        navController.navigate(route) {
            // One home entry, always: strolling through the panel must not build a back stack,
            // and the back gesture has to keep closing the application exactly as it does today.
            popUpTo<Route.Home> { inclusive = true }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet {
                SectionsDrawerContent(
                    state = sectionsState,
                    onQueryChanged = sectionsViewModel::onQueryChanged,
                    onToggleExpanded = sectionsViewModel::onToggleExpanded,
                    onSelect = ::openSection,
                )
            }
        },
    ) {
        Scaffold(
            // Sin insets propios: cada destino los resuelve. El Scaffold interior de Inicio ya
            // aplica el de la barra de estado a su barra superior, y sumarlo aquí lo contaba dos
            // veces —un hueco en blanco del alto de la barra de estado sobre el escudo—.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                BocBottomBar(
                    current = backStackEntry.toDestination(),
                    onSelect = { destination -> navController.navigateTo(destination) },
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Route.Home(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable<Route.Home> {
                    HomeScreen(
                        sections = sections,
                        onOpenSections = { scope.launch { drawerState.open() } },
                        onSelectSection = { code ->
                            openSection(sections.firstOrNull { it.code == code })
                        },
                        onSearch = ::showComingSoon,
                        onInfo = {},
                        onShare = { publication -> context.sharePublication(publication) },
                        onSave = ::showComingSoon,
                    )
                }
                composable<Route.Search> { SearchScreen() }
                composable<Route.Saved> { SavedScreen() }
            }
        }
    }
}

private fun NavHostController.navigateTo(destination: BottomDestination) {
    val route: Route = when (destination) {
        BottomDestination.HOME -> Route.Home()
        BottomDestination.SEARCH -> Route.Search
        BottomDestination.SAVED -> Route.Saved
    }
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavBackStackEntry?.toDestination(): BottomDestination = when {
    this?.destination?.hasRoute<Route.Search>() == true -> BottomDestination.SEARCH
    this?.destination?.hasRoute<Route.Saved>() == true -> BottomDestination.SAVED
    else -> BottomDestination.HOME
}

/** Shares the link to the official document, which is all the feeds give us. */
private fun android.content.Context.sharePublication(publication: Publication) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, publication.title)
        putExtra(Intent.EXTRA_TEXT, "${publication.title}\n${publication.documentUrl}")
    }
    startActivity(Intent.createChooser(intent, getString(R.string.publication_share_chooser)))
}
