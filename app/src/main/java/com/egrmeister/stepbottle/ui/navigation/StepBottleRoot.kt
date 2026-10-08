package com.egrmeister.stepbottle.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.egrmeister.stepbottle.AppContainer
import com.egrmeister.stepbottle.R
import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.ui.components.SbIcon
import com.egrmeister.stepbottle.ui.entry.EntryEditorScreen
import com.egrmeister.stepbottle.ui.entry.EntryEditorViewModel
import com.egrmeister.stepbottle.ui.entry.EntryKind
import com.egrmeister.stepbottle.ui.settings.PrivacyScreen
import com.egrmeister.stepbottle.ui.settings.SettingsScreen
import com.egrmeister.stepbottle.ui.settings.SettingsViewModel
import com.egrmeister.stepbottle.ui.theme.StepBottleTheme
import com.egrmeister.stepbottle.ui.today.DayScreen
import com.egrmeister.stepbottle.ui.today.DayViewModel
import com.egrmeister.stepbottle.ui.week.WeekScreen
import com.egrmeister.stepbottle.ui.week.WeekViewModel
import java.time.LocalDate

private object Routes {
    const val TODAY = "today"
    const val WEEK = "week"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val DAY = "day/{date}"
    const val ENTRY = "entry/{kind}?date={date}&id={id}&amount={amount}&category={category}"

    fun day(date: LocalDate) = "day/$date"

    fun entry(kind: EntryKind, date: LocalDate, id: Long? = null, amount: Int? = null, category: ActivityCategory? = null) =
        "entry/${kind.name}?date=$date&id=${id ?: -1L}&amount=${amount ?: -1}&category=${category?.name ?: ""}"
}

private data class Tab(val route: String, val label: String, @param:DrawableRes val icon: Int)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", R.drawable.ic_nav_today),
    Tab(Routes.WEEK, "Week", R.drawable.ic_nav_week),
    Tab(Routes.SETTINGS, "Settings", R.drawable.ic_nav_settings),
)

@Composable
fun StepBottleRoot(container: AppContainer, openTodayRequests: Int) {
    StepBottleTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            AppNavigation(container, openTodayRequests)
        }
    }
}

@Composable
private fun AppNavigation(container: AppContainer, openTodayRequests: Int) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showNav = route != null && route != Routes.ENTRY
    val selectedTab = when (route) {
        Routes.TODAY -> Routes.TODAY
        Routes.WEEK, Routes.DAY -> Routes.WEEK
        Routes.SETTINGS, Routes.PRIVACY -> Routes.SETTINGS
        else -> null
    }

    LaunchedEffect(openTodayRequests) {
        if (openTodayRequests > 0) nav.goToTab(Routes.TODAY)
    }

    val openWater: (LocalDate, Int?) -> Unit = { date, amount -> nav.navigate(Routes.entry(EntryKind.WATER, date, amount = amount)) }
    val openActivity: (LocalDate, Int?, ActivityCategory?) -> Unit = { date, minutes, category ->
        nav.navigate(Routes.entry(EntryKind.ACTIVITY, date, amount = minutes, category = category))
    }
    val editWater: (Long) -> Unit = { id -> nav.navigate(Routes.entry(EntryKind.WATER, container.todayClock.today.value, id = id)) }
    val editActivity: (Long) -> Unit = { id -> nav.navigate(Routes.entry(EntryKind.ACTIVITY, container.todayClock.today.value, id = id)) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showNav && !wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab.route,
                                onClick = { nav.goToTab(tab.route) },
                                icon = { SbIcon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (showNav && wide) {
                    NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab.route,
                                onClick = { nav.goToTab(tab.route) },
                                icon = { SbIcon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
                NavHost(navController = nav, startDestination = Routes.TODAY, modifier = Modifier.weight(1f)) {
                    composable(Routes.TODAY) {
                        val vm: DayViewModel = viewModel(
                            key = "today",
                            factory = viewModelFactory { initializer { DayViewModel(container, createSavedStateHandle(), null) } },
                        )
                        DayScreen(
                            vm = vm,
                            feedback = container.feedback.messages,
                            showBack = false,
                            onBack = {},
                            onOpenWater = openWater,
                            onOpenActivity = openActivity,
                            onEditWater = editWater,
                            onEditActivity = editActivity,
                        )
                    }
                    composable(Routes.WEEK) {
                        val vm: WeekViewModel = viewModel(
                            factory = viewModelFactory { initializer { WeekViewModel(container, createSavedStateHandle()) } },
                        )
                        WeekScreen(vm) { date -> nav.navigate(Routes.day(date)) }
                    }
                    composable(
                        Routes.DAY,
                        arguments = listOf(navArgument("date") { type = NavType.StringType }),
                    ) { entry ->
                        val date = LocalDate.parse(requireNotNull(entry.arguments?.getString("date")))
                        val vm: DayViewModel = viewModel(
                            key = "day-$date",
                            factory = viewModelFactory { initializer { DayViewModel(container, createSavedStateHandle(), date) } },
                        )
                        // Back from a historical daily log returns to Week.
                        DayScreen(
                            vm = vm,
                            feedback = container.feedback.messages,
                            showBack = true,
                            onBack = { nav.popBackStack() },
                            onOpenWater = openWater,
                            onOpenActivity = openActivity,
                            onEditWater = editWater,
                            onEditActivity = editActivity,
                        )
                    }
                    composable(
                        Routes.ENTRY,
                        arguments = listOf(
                            navArgument("kind") { type = NavType.StringType },
                            navArgument("date") { type = NavType.StringType },
                            navArgument("id") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument("amount") {
                                type = NavType.IntType
                                defaultValue = -1
                            },
                            navArgument("category") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { entry ->
                        val args = requireNotNull(entry.arguments)
                        val kind = EntryKind.valueOf(requireNotNull(args.getString("kind")))
                        val date = LocalDate.parse(requireNotNull(args.getString("date")))
                        val id = args.getLong("id").takeIf { it > 0 }
                        val amount = args.getInt("amount").takeIf { it > 0 }
                        val category = ActivityCategory.fromName(args.getString("category"))
                        val vm: EntryEditorViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer {
                                    EntryEditorViewModel(container, createSavedStateHandle(), kind, id, date, amount, category)
                                }
                            },
                        )
                        EntryEditorScreen(vm) { nav.popBackStack() }
                    }
                    composable(Routes.SETTINGS) {
                        val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
                        SettingsScreen(
                            vm = vm,
                            onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                            onCleared = { nav.goToTab(Routes.TODAY) },
                        )
                    }
                    composable(Routes.PRIVACY) {
                        PrivacyScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TODAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
