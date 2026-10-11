package id.schoolmate.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.schoolmate.app.ui.navigation.FocusRoute
import id.schoolmate.app.ui.navigation.HomeRoute
import id.schoolmate.app.ui.navigation.SettingsRoute
import id.schoolmate.app.ui.navigation.SubjectsRoute
import id.schoolmate.app.ui.navigation.TasksRoute
import id.schoolmate.app.ui.navigation.TopLevelDestination
import id.schoolmate.app.ui.screens.focus.FocusScreen
import id.schoolmate.app.ui.screens.home.HomeScreen
import id.schoolmate.app.ui.screens.settings.SettingsScreen
import id.schoolmate.app.ui.screens.subjects.SubjectsScreen
import id.schoolmate.app.ui.screens.tasks.TasksScreen

@Composable
fun SchoolMateApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeRoute> {
                HomeScreen(onOpenSubjects = { navController.navigateToTopLevel(TopLevelDestination.SUBJECTS) })
            }
            composable<SubjectsRoute> { SubjectsScreen() }
            composable<TasksRoute> { TasksScreen() }
            composable<FocusRoute> { FocusScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
