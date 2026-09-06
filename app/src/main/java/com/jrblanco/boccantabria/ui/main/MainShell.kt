package com.jrblanco.boccantabria.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
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
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.ui.alerts.AlertsScreen
import com.jrblanco.boccantabria.ui.alerts.AlertsTab
import com.jrblanco.boccantabria.ui.home.HomeScreen
import com.jrblanco.boccantabria.ui.navigation.BocBottomBar
import com.jrblanco.boccantabria.ui.navigation.BottomDestination
import com.jrblanco.boccantabria.ui.navigation.PendingNavigation
import com.jrblanco.boccantabria.ui.navigation.PendingNavigationStore
import com.jrblanco.boccantabria.ui.navigation.Route
import com.jrblanco.boccantabria.ui.saved.SavedScreen
import com.jrblanco.boccantabria.ui.search.SearchScreen
import com.jrblanco.boccantabria.ui.sections.SectionsDrawerContent
import com.jrblanco.boccantabria.ui.sections.SectionsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

const val TAG_SHELL_SNACKBAR: String = "shell_snackbar"

/**
 * The frame the four destinations share: the sections panel and the bottom bar, wrapped around
 * their own navigation host.
 *
 * The splash screen stays **outside** this frame. Wrapping the whole application would mean
 * drawing a bar and a panel over the cover and then hiding them, and the cover has no business
 * knowing they exist.
 *
 * Since feature 012 the frame also owns the bell's badge and the in-app message with «VER»: this is
 * the one `Scaffold` that wraps every destination, so a Snackbar hosted here shows over the bottom
 * bar wherever the person is (012 research.md D-416).
 */
@Composable
@Suppress("LongParameterList")
fun MainShell(
    navController: NavHostController,
    onOpenPublication: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenInfo: () -> Unit = {},
    onOpenAlertForm: (Route.AlertForm) -> Unit = {},
    sectionsViewModel: SectionsViewModel = koinViewModel(),
    shellViewModel: MainShellViewModel = koinViewModel(),
    getSections: GetBocSectionsUseCase = koinInject(),
    pendingNavigation: PendingNavigationStore = koinInject(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sectionsState by sectionsViewModel.uiState.collectAsStateWithLifecycle()
    val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // The whole tree, from the use case, and not `sectionsState.rows`. The panel's rows are its
    // presentation — grouped into parents and children, and carrying whatever it has expanded — and
    // the chips and the cards need the flat catalogue. Until feature 013 there was a sharper reason:
    // those rows were filtered by whatever was typed in the panel, so using them emptied the chips
    // and stripped the section label off every card the moment somebody searched. The filter is
    // gone; the separation still belongs.
    val sections = remember(getSections) { getSections() }

    /**
     * El puente entre las dos búsquedas: lo que no aparecía en la edición se busca en todo lo
     * almacenado, con el término ya escrito.
     *
     * **Sin `restoreState`**, al contrario que la barra inferior. Con restauración, el estado
     * guardado de la pestaña Buscar pisaría el argumento y el término traspasado se perdería —justo
     * en el caso de quien ya había usado Buscar antes—, y lo haría sin error: se llegaría con el
     * campo vacío.
     */
    fun searchGlobally(query: String) {
        navController.navigate(Route.Search(query)) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
        }
    }

    /**
     * Novedades, reached from the in-app message or from a tapped group summary. **Without
     * `restoreState`**, for the same reason as the search hand-off: saved state would win over the
     * tab argument and the person would land on whatever tab they last had open.
     */
    fun openAlertNews() {
        navController.navigate(Route.Alerts(tab = AlertsTab.NEWS.name)) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
        }
    }

    // The in-app message (FR-050, FR-051). Shown wherever the person is except on the alerts
    // themselves, where the list and the badge already say it; consumed in both cases so the same
    // cycle never shows twice.
    val pendingAlert = shellState.pendingAlert
    val onAlerts = backStackEntry.toDestination() == BottomDestination.ALERTS
    val singleText = pendingAlert?.ruleName?.let { stringResource(R.string.alert_snackbar_single, it) }
    val manyText = pendingAlert?.let {
        pluralStringResource(R.plurals.alert_snackbar_many, it.publicationCount, it.publicationCount)
    }
    val actionLabel = stringResource(R.string.alert_snackbar_action)
    LaunchedEffect(pendingAlert) {
        if (pendingAlert == null) return@LaunchedEffect
        val message = singleText ?: manyText.orEmpty()
        // Consumed first, so the same cycle can never show twice. Consuming sets the key to null and
        // restarts this effect, which would cancel a Snackbar suspended inside it — so the Snackbar
        // runs in the composable's own scope, which only dies with the shell. The instrumented test
        // caught exactly that: a message that vanished the instant it appeared.
        shellViewModel.onInAppAlertHandled()
        if (onAlerts) return@LaunchedEffect
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) openAlertNews()
        }
    }

    // A tapped group summary, after the cover (012 research.md D-424).
    val pendingDestination by pendingNavigation.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDestination) {
        if (pendingDestination is PendingNavigation.AlertNews) {
            pendingNavigation.consume()
            openAlertNews()
        }
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
                    onToggleExpanded = sectionsViewModel::onToggleExpanded,
                    onSelect = ::openSection,
                    // The same `scope` and `drawerState` that `openSection` already uses. The panel
                    // asks to be put away; it does not know how, and must not navigate to do it.
                    onClose = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Scaffold(
            // Sin insets propios: cada destino los resuelve. El Scaffold interior de Inicio ya
            // aplica el de la barra de estado a su barra superior, y sumarlo aquí lo contaba dos
            // veces —un hueco en blanco del alto de la barra de estado sobre el escudo—.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.testTag(TAG_SHELL_SNACKBAR))
            },
            bottomBar = {
                BocBottomBar(
                    current = backStackEntry.toDestination(),
                    onSelect = { destination -> navController.navigateTo(destination) },
                    alertBadge = shellState.unreadAlerts,
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Route.Home(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Reservar el sitio no es decir que el margen esté servido, y son dos cosas
                    // distintas: `padding` no consume el inset. Sin esto, el Scaffold de cada
                    // destino —que no lleva barra inferior y toma los `systemBars` de por defecto—
                    // vuelve a aplicar el margen de abajo por su cuenta, y la lista termina una
                    // barra de navegación por encima de la barra que ya estaba despejada. Es la
                    // otra mitad de la política que explica el comentario de arriba.
                    .consumeWindowInsets(innerPadding),
            ) {
                composable<Route.Home> {
                    HomeScreen(
                        sections = sections,
                        onOpenSections = { scope.launch { drawerState.open() } },
                        onSelectSection = { code ->
                            openSection(sections.firstOrNull { it.code == code })
                        },
                        onSearchGlobally = ::searchGlobally,
                        onInfo = onOpenInfo,
                        onOpenPublication = { publication ->
                            onOpenPublication(publication.externalKey)
                        },
                    )
                }
                composable<Route.Search> {
                    SearchScreen(
                        sections = sections,
                        // La misma lambda que Inicio y Guardados: el detalle vive en el grafo
                        // exterior, así que abierto desde aquí tampoco dibuja la barra inferior.
                        onOpenPublication = { publication ->
                            onOpenPublication(publication.externalKey)
                        },
                    )
                }
                composable<Route.Saved> {
                    SavedScreen(
                        sections = sections,
                        // La misma lambda que Inicio: el detalle vive en el grafo exterior, así que
                        // abierto desde aquí tampoco dibuja la barra inferior.
                        onOpenPublication = { publication ->
                            onOpenPublication(publication.externalKey)
                        },
                        onExplore = { navController.navigateTo(BottomDestination.HOME) },
                    )
                }
                composable<Route.Alerts> {
                    AlertsScreen(
                        sections = sections,
                        onOpenPublication = onOpenPublication,
                        onCreateRule = { onOpenAlertForm(Route.AlertForm()) },
                        onEditRule = { id -> onOpenAlertForm(Route.AlertForm(ruleId = id)) },
                        onDuplicateRule = { id -> onOpenAlertForm(Route.AlertForm(duplicateOf = id)) },
                    )
                }
            }
        }
    }
}

private fun NavHostController.navigateTo(destination: BottomDestination) {
    val route: Route = when (destination) {
        BottomDestination.HOME -> Route.Home()
        BottomDestination.SEARCH -> Route.Search()
        BottomDestination.SAVED -> Route.Saved
        BottomDestination.ALERTS -> Route.Alerts()
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
    this?.destination?.hasRoute<Route.Alerts>() == true -> BottomDestination.ALERTS
    else -> BottomDestination.HOME
}
