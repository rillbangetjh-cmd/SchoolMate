package id.schoolmate.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import id.schoolmate.app.ui.navigation.ExamDetailRoute
import id.schoolmate.app.ui.navigation.ExamEditRoute
import id.schoolmate.app.ui.navigation.ExamsRoute
import id.schoolmate.app.ui.navigation.HomeRoute
import id.schoolmate.app.ui.navigation.ScheduleEditRoute
import id.schoolmate.app.ui.navigation.ScheduleRoute
import id.schoolmate.app.ui.navigation.SettingsRoute
import id.schoolmate.app.ui.navigation.StudyLogRoute
import id.schoolmate.app.ui.navigation.SubjectsRoute
import id.schoolmate.app.ui.navigation.TaskDetailRoute
import id.schoolmate.app.ui.navigation.TaskEditRoute
import id.schoolmate.app.ui.navigation.TasksRoute
import id.schoolmate.app.ui.navigation.TopLevelDestination
import id.schoolmate.app.ui.screens.exams.ExamDetailScreen
import id.schoolmate.app.ui.screens.exams.ExamEditorScreen
import id.schoolmate.app.ui.screens.exams.ExamsScreen
import id.schoolmate.app.ui.screens.home.HomeActions
import id.schoolmate.app.ui.screens.home.HomeScreen
import id.schoolmate.app.ui.screens.schedule.ScheduleEditorScreen
import id.schoolmate.app.ui.screens.schedule.ScheduleScreen
import id.schoolmate.app.ui.screens.settings.SettingsScreen
import id.schoolmate.app.ui.screens.studylog.StudyLogScreen
import id.schoolmate.app.ui.screens.subjects.SubjectsScreen
import id.schoolmate.app.ui.screens.tasks.TaskDetailScreen
import id.schoolmate.app.ui.screens.tasks.TaskEditorScreen
import id.schoolmate.app.ui.screens.tasks.TasksScreen

private fun NavDestination?.isTopLevel(): Boolean =
    this?.hierarchy?.any { d -> TopLevelDestination.entries.any { d.hasRoute(it.route::class) } } == true

@Composable
fun SchoolMateApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination == null || currentDestination.isTopLevel()

    Scaffold(
        // Each screen handles its own top insets (TopAppBar / header); this padding only covers the bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, contentDescription = null)
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                            modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
            // Tabs cross-fade; detail/editor screens slide in from the side.
            enterTransition = { slideInHorizontally(tween(260)) { it / 8 } + fadeIn(tween(260)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { slideOutHorizontally(tween(220)) { it / 8 } + fadeOut(tween(220)) },
        ) {
            composable<HomeRoute>(enterTransition = { fadeIn(tween(220)) }) {
                HomeScreen(
                    actions = HomeActions(
                        openLesson = { navController.navigate(ScheduleEditRoute(entryId = it)) },
                        openTask = { navController.navigate(TaskDetailRoute(it)) },
                        openExam = { navController.navigate(ExamDetailRoute(it)) },
                        openSchedule = { navController.navigateToTopLevel(TopLevelDestination.SCHEDULE) },
                        openTasks = { navController.navigateToTopLevel(TopLevelDestination.TASKS) },
                        openExams = { navController.navigateToTopLevel(TopLevelDestination.EXAMS) },
                        openStudyLog = { navController.navigate(StudyLogRoute) },
                        openSubjects = { navController.navigate(SubjectsRoute) },
                        addTask = { navController.navigate(TaskEditRoute()) },
                        addExam = { navController.navigate(ExamEditRoute()) },
                        addLesson = { navController.navigate(ScheduleEditRoute()) },
                    ),
                )
            }
            composable<ScheduleRoute>(enterTransition = { fadeIn(tween(220)) }) {
                ScheduleScreen(
                    onAddLesson = { day -> navController.navigate(ScheduleEditRoute(day = day.value)) },
                    onOpenLesson = { navController.navigate(ScheduleEditRoute(entryId = it)) },
                    onOpenSubjects = { navController.navigate(SubjectsRoute) },
                )
            }
            composable<TasksRoute>(enterTransition = { fadeIn(tween(220)) }) {
                TasksScreen(
                    onOpenTask = { navController.navigate(TaskDetailRoute(it)) },
                    onAddTask = { navController.navigate(TaskEditRoute()) },
                )
            }
            composable<ExamsRoute>(enterTransition = { fadeIn(tween(220)) }) {
                ExamsScreen(
                    onOpenExam = { navController.navigate(ExamDetailRoute(it)) },
                    onAddExam = { navController.navigate(ExamEditRoute()) },
                )
            }
            composable<SettingsRoute>(enterTransition = { fadeIn(tween(220)) }) {
                SettingsScreen(
                    onOpenSubjects = { navController.navigate(SubjectsRoute) },
                    onOpenStudyLog = { navController.navigate(StudyLogRoute) },
                )
            }
            composable<SubjectsRoute> { SubjectsScreen(onBack = navController::popBackStack) }
            composable<StudyLogRoute> { StudyLogScreen(onBack = navController::popBackStack) }

            composable<ScheduleEditRoute> { entry ->
                val route = entry.toRoute<ScheduleEditRoute>()
                ScheduleEditorScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.scheduleEditor(route.entryId, route.day)),
                    onClose = navController::popBackStack,
                )
            }
            composable<TaskDetailRoute> { entry ->
                val route = entry.toRoute<TaskDetailRoute>()
                TaskDetailScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.taskDetail(route.taskId)),
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(TaskEditRoute(route.taskId)) },
                )
            }
            composable<TaskEditRoute> { entry ->
                val route = entry.toRoute<TaskEditRoute>()
                TaskEditorScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.taskEditor(route.taskId)),
                    onClose = navController::popBackStack,
                    // Deleting from the editor also closes the (now empty) detail screen.
                    onDeleted = {
                        if (!navController.popBackStack<TaskDetailRoute>(inclusive = true)) navController.popBackStack()
                    },
                )
            }
            composable<ExamDetailRoute> { entry ->
                val route = entry.toRoute<ExamDetailRoute>()
                ExamDetailScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.examDetail(route.examId)),
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(ExamEditRoute(route.examId)) },
                )
            }
            composable<ExamEditRoute> { entry ->
                val route = entry.toRoute<ExamEditRoute>()
                ExamEditorScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.examEditor(route.examId)),
                    onClose = navController::popBackStack,
                    onDeleted = {
                        if (!navController.popBackStack<ExamDetailRoute>(inclusive = true)) navController.popBackStack()
                    },
                )
            }
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
