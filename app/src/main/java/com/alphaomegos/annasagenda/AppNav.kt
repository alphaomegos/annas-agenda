package com.alphaomegos.annasagenda

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alphaomegos.annasagenda.screens.AnthropometryScreen
import com.alphaomegos.annasagenda.screens.media.BookDetailsScreen
import com.alphaomegos.annasagenda.screens.CalendarDayRoute
import com.alphaomegos.annasagenda.screens.CalendarMonthRoute
import com.alphaomegos.annasagenda.screens.CalorimeterRoute
import com.alphaomegos.annasagenda.screens.CountersScreen
import com.alphaomegos.annasagenda.screens.LanguageScreen
import com.alphaomegos.annasagenda.screens.MainMenuScreen
import com.alphaomegos.annasagenda.screens.media.MediaLibraryScreen
import com.alphaomegos.annasagenda.screens.media.MovieDetailsScreen
import com.alphaomegos.annasagenda.screens.NewTaskScreen
import com.alphaomegos.annasagenda.screens.media.ReadingSessionScreen
import com.alphaomegos.annasagenda.screens.RecurringTasksScreen
import com.alphaomegos.annasagenda.screens.RunningPlanScreen
import com.alphaomegos.annasagenda.screens.media.SeriesDetailsScreen
import com.alphaomegos.annasagenda.screens.SomedayScreen
import java.time.LocalDate

private object Route {
    const val MENU = "menu"
    const val LANGUAGE = "language"
    const val CALENDAR = "calendar"
    const val CALENDAR_DAY = "calendar_day"
    const val NEW_TASK = "new_task"
    const val NEW_TASK_SOMEDAY = "new_task_someday"
    const val SOMEDAY = "someday"
    const val RECURRING = "recurring"
    const val ANTHROPOMETRY = "anthropometry"
    const val NEW_TASK_DATE = "new_task_date"
    const val CALORIMETER = "calorimeter"
    const val RUNNING = "running"
    const val COUNTERS = "counters"

    const val READING = "reading"
    const val READING_BOOK = "reading_book"
    const val READING_MOVIE = "reading_movie"
    const val READING_SERIES = "reading_series"
    const val READING_SESSION = "reading_session"

    fun readingBook(bookId: Long) = "$READING_BOOK/$bookId"
    fun readingMovie(movieId: Long) = "$READING_MOVIE/$movieId"
    fun readingSeries(seriesId: Long) = "$READING_SERIES/$seriesId"
    fun readingSession(bookId: Long) = "$READING_SESSION/$bookId"
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
                onCounters = { nav.navigate(Route.COUNTERS) },
                onMediaLibrary = { nav.navigate(Route.READING) },
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

        composable(Route.COUNTERS) {
            CountersScreen(
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }

        composable(Route.READING) {
            MediaLibraryScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenBook = { bookId -> nav.navigate(Route.readingBook(bookId)) },
                onOpenMovie = { movieId -> nav.navigate(Route.readingMovie(movieId)) },
                onOpenSeries = { seriesId -> nav.navigate(Route.readingSeries(seriesId)) },
                onStartReading = { bookId -> nav.navigate(Route.readingSession(bookId)) }
            )
        }

        composable(
            route = "${Route.READING_BOOK}/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: -1L
            BookDetailsScreen(
                vm = vm,
                bookId = bookId,
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            route = "${Route.READING_MOVIE}/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.LongType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getLong("movieId") ?: -1L
            MovieDetailsScreen(
                vm = vm,
                movieId = movieId,
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            route = "${Route.READING_SERIES}/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.LongType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getLong("seriesId") ?: -1L
            SeriesDetailsScreen(
                vm = vm,
                seriesId = seriesId,
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            route = "${Route.READING_SESSION}/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: -1L
            ReadingSessionScreen(
                vm = vm,
                bookId = bookId,
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

        // NEW: open NewTask with "Someday" default (no date)
        composable(Route.NEW_TASK_SOMEDAY) {
            NewTaskScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                preselectedEpochDay = -1L
            )
        }

        composable(Route.SOMEDAY) {
            SomedayScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onCreateTask = { nav.navigate(Route.NEW_TASK_SOMEDAY) }
            )
        }
    }
}