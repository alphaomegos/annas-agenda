package com.alphaomegos.annasagenda

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate


private object Route {
    const val Menu = "menu"
    const val Language = "language"
    const val Calendar = "calendar"              // month view
    const val CalendarDay = "calendar_day"       // day view
    const val NewTask = "new_task"
    const val Someday = "someday"
    const val Recurring = "recurring"
    const val NewTaskDate = "new_task_date"
}

@Composable
fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Route.Menu) {

        composable(Route.Menu) {
            MainMenuScreen(
                vm = vm,
                onLanguage = { nav.navigate(Route.Language) },
                onCalendar = { nav.navigate(Route.Calendar) },
                onNewTask = { nav.navigate(Route.NewTask) },
                onSomeday = { nav.navigate(Route.Someday) },
                onRecurring = { nav.navigate(Route.Recurring) },
            )
        }

        composable(Route.Recurring) {
            RecurringTasksScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }

        composable(Route.Language) {
            LanguageScreen(onBack = { nav.popBackStack() })
        }

        composable(Route.Calendar) {
            CalendarMonthScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenDay = { epochDay -> nav.navigate("${Route.CalendarDay}/$epochDay") },
                onOpenSomeday = { nav.navigate(Route.Someday) }
            )
        }

        composable("${Route.CalendarDay}/{epochDay}") { backStackEntry ->
            val epochDay = backStackEntry.arguments
                ?.getString("epochDay")
                ?.toLongOrNull()
                ?: LocalDate.now().toEpochDay()

            CalendarDayScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                initialEpochDay = epochDay,
                onAddTask = { epochDay -> nav.navigate("${Route.NewTaskDate}/$epochDay") }
            )
        }

        composable(
            route = "${Route.NewTaskDate}/{epochDay}",
            arguments = listOf(navArgument("epochDay") { type = NavType.LongType })
        ) { backStackEntry ->
            val epochDay = backStackEntry.arguments?.getLong("epochDay") ?: LocalDate.now().toEpochDay()
            NewTaskScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                preselectedEpochDay = epochDay
            )
        }

        composable(Route.NewTask) {
            NewTaskScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }

        composable(Route.Someday) {
            SomedayScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }
    }
}