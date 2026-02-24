package com.alphaomegos.annasagenda

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alphaomegos.annasagenda.screens.AnthropometryScreen
import com.alphaomegos.annasagenda.screens.CalendarDayRoute
import com.alphaomegos.annasagenda.screens.CalendarMonthRoute
import com.alphaomegos.annasagenda.screens.LanguageScreen
import com.alphaomegos.annasagenda.screens.NewTaskScreen
import com.alphaomegos.annasagenda.screens.RecurringTasksScreen
import com.alphaomegos.annasagenda.screens.SomedayScreen
import com.alphaomegos.annasagenda.screens.CalorimeterRoute
import com.alphaomegos.annasagenda.screens.MainMenuScreen
import com.alphaomegos.annasagenda.screens.RunningPlanScreen
import java.time.LocalDate


private object Route {
    const val MENU = "menu"
    const val LANGUAGE = "language"
    const val CALENDAR = "calendar"              // month view
    const val CALENDAR_DAY = "calendar_day"      // day view
    const val NEW_TASK = "new_task"
    const val SOMEDAY = "someday"
    const val RECURRING = "recurring"
    const val ANTHROPOMETRY = "anthropometry"
    const val NEW_TASK_DATE = "new_task_date"
    const val CALORIMETER = "calorimeter"
    const val RUNNING = "running"
}

@Composable
fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Route.MENU) {

        composable(Route.MENU) {
            MainMenuScreen(
                vm = vm,
                onLanguage = { nav.navigate(Route.LANGUAGE) },
                onCalendar = { nav.navigate(Route.CALENDAR) },
                onNewTask = { nav.navigate(Route.NEW_TASK) },
                onSomeday = { nav.navigate(Route.SOMEDAY) },
                onRecurring = { nav.navigate(Route.RECURRING) },
                onAnthropometry = { nav.navigate(Route.ANTHROPOMETRY) },
                onCalorimeter = { nav.navigate(Route.CALORIMETER) },
                onRunning = { nav.navigate(Route.RUNNING) },
                )
        }

        composable(Route.RECURRING) {
            RecurringTasksScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }


        composable(Route.ANTHROPOMETRY) {
            AnthropometryScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }

        composable(Route.LANGUAGE) {
            LanguageScreen(onBack = { nav.popBackStack() })
        }

        composable(Route.CALENDAR) {
            CalendarMonthRoute(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenDay = { epochDay -> nav.navigate("${Route.CALENDAR_DAY}/$epochDay") },
                onOpenSomeday = { nav.navigate(Route.SOMEDAY) }
            )
        }

        composable("${Route.CALENDAR_DAY}/{epochDay}") { backStackEntry ->
            val epochDay = backStackEntry.arguments
                ?.getString("epochDay")
                ?.toLongOrNull()
                ?: LocalDate.now().toEpochDay()

            CalendarDayRoute(
                vm = vm,
                onBack = { nav.popBackStack() },
                initialEpochDay = epochDay,
                onAddTask = { d -> nav.navigate("${Route.NEW_TASK_DATE}/$d") }
            )
        }
        composable(Route.CALORIMETER) {
            CalorimeterRoute(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }
        composable(Route.RUNNING) {
            RunningPlanScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }



        composable(
            route = "${Route.NEW_TASK_DATE}/{epochDay}",
            arguments = listOf(navArgument("epochDay") { type = NavType.LongType })
        ) { backStackEntry ->
            val epochDay =
                backStackEntry.arguments?.getLong("epochDay") ?: LocalDate.now().toEpochDay()
            NewTaskScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                preselectedEpochDay = epochDay
            )
        }

        composable(Route.NEW_TASK) {
            NewTaskScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }

        composable(Route.SOMEDAY) {
            SomedayScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }
    }
}