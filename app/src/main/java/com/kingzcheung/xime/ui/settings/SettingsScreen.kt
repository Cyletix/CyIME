package com.kingzcheung.xime.ui.settings

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

data class SettingsNavigationRequest(val route: String, val id: Int)

@Composable
fun SettingsScreen(
    initialRoute: String? = null,
    initialPluginId: String? = null,
    onThemeChanged: () -> Unit = {},
    onWizardBack: () -> Unit = {},
    navigationRequest: SettingsNavigationRequest? = null,
) {
    val navController = rememberNavController()
    val startDestination = if (initialRoute == "manage_dict") SettingsRoutes.Dictionary
    else if (initialRoute == "schema") SettingsRoutes.Schema
    else if (initialRoute == "plugins") SettingsRoutes.Plugins
    else SettingsRoutes.Main

    // Shortcuts enter the existing settings graph with Main underneath, so Back
    // works exactly as it does when the page is opened from the settings home.
    LaunchedEffect(initialRoute) {
        // 键盘提示「缺手写模型」发的是 model_management，冷启动时也要落到模型页（以前只在设置已打开时才认）。
        val route = if (initialRoute == "model_management") SettingsRoutes.ModelLocal else initialRoute
        // 起始页已处理的几个之外，图里有的页面都可直接进（键盘菜单长按跳到对应设置）；不认识的留在首页。
        if (route != null && route !in setOf(SettingsRoutes.Main, "manage_dict", SettingsRoutes.Schema, SettingsRoutes.Plugins)) {
            runCatching { navController.navigate(route) { launchSingleTop = true } }
        }
    }

    LaunchedEffect(initialPluginId) {
        if (initialPluginId != null) {
            navController.navigate("plugin_detail/$initialPluginId")
        }
    }

    LaunchedEffect(navigationRequest) {
        val request = navigationRequest ?: return@LaunchedEffect
        val destination = when (request.route) {
            "manage_dict" -> SettingsRoutes.Dictionary
            "model_management" -> SettingsRoutes.ModelLocal
            else -> request.route
        }
        if (destination == SettingsRoutes.Main) {
            navController.popBackStack(navController.graph.startDestinationId, inclusive = false)
        } else if (navController.currentDestination?.route != destination) {
            runCatching {
                navController.navigate(destination) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }
    
    val motion = rememberSettingsMotionEnabled()
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { if (motion) SettingsPageMotion.enter else EnterTransition.None },
        exitTransition = { if (motion) SettingsPageMotion.exit else ExitTransition.None },
        popEnterTransition = { if (motion) SettingsPageMotion.popEnter else EnterTransition.None },
        popExitTransition = { if (motion) SettingsPageMotion.popExit else ExitTransition.None },
    ) {
        composable(SettingsRoutes.Main) {
            androidx.compose.runtime.CompositionLocalProvider(LocalSettingsSearchAnchors provides false) {
                SettingsMainContent(
                    onNavigateToHardwareKeyboard = { navController.navigate(SettingsRoutes.HardwareKeyboard) },
                    onNavigateToLanguages = { navController.navigate("languages") },
                    onNavigateToSchema = { navController.navigate(SettingsRoutes.Schema) },
                    onNavigateToMarket = { navController.navigate(SettingsRoutes.Market) },
                    onNavigateToTheme = { navController.navigate(SettingsRoutes.Theme) },
                    onNavigateToKeyEffect = { navController.navigate(SettingsRoutes.KeyEffect) },
                    onNavigateToLayoutDisplay = { navController.navigate(SettingsRoutes.LayoutDisplay) },
                    onNavigateToDictionary = { navController.navigate(SettingsRoutes.Dictionary) },
                    onNavigateToPlugins = { navController.navigate(SettingsRoutes.Plugins) },
                    onNavigateToModelLocal = { navController.navigate(SettingsRoutes.ModelLocal) },
                    onNavigateToHandwritingTraining = { navController.navigate(SettingsRoutes.HandwritingTraining) },
                    onNavigateToSmartPrediction = { navController.navigate(SettingsRoutes.SmartPrediction) },
                    onNavigateToSpeechToText = { navController.navigate(SettingsRoutes.SpeechToText) },
                    onNavigateToAbout = { navController.navigate(SettingsRoutes.About) },
                    onNavigateToClipboardSync = { navController.navigate(SettingsRoutes.ClipboardSync) },
                    onNavigateToBackup = { navController.navigate(SettingsRoutes.Backup) },
                    onNavigateToRoute = { navController.navigate(it) },
                )
            }
        }
        composable(SettingsRoutes.HardwareKeyboard) { HardwareKeyboardSettingsContent { navController.popBackStack() } }
        composable("languages") {
            LanguageSettingsContent(onBack = { navController.popBackStack() },
                onOpenSchemaResources = { navController.navigate(SettingsRoutes.Schema) },
                onOpenDoublePinyin = { navController.navigate(SettingsRoutes.DoublePinyin) })
        }
        composable(SettingsRoutes.DoublePinyin) { DoublePinyinSettingsContent { navController.popBackStack() } }
        composable(SettingsRoutes.Schema) {
            SchemaSettingsContent(
                onBack = {
                    if (initialRoute == "schema") {
                        navController.navigate(SettingsRoutes.Main) {
                            popUpTo(SettingsRoutes.Main) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                    onWizardBack()
                },
                onNavigateToRimeFileBrowser = { navController.navigate(SettingsRoutes.RimeFileBrowser) },
            )
        }
        composable(SettingsRoutes.Market) {
            MarketHubContent(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { schemeId ->
                    navController.navigate("schema_market_detail/$schemeId")
                },
                onNavigateToModelDetail = { modelId ->
                    navController.navigate("model_market_detail/$modelId")
                },
                onNavigateToPluginDetail = { pluginId ->
                    navController.navigate("plugin_detail/$pluginId")
                },
                // 方案的已安装管理与「语言与方案 › 已安装的方案」是同一页，不再另开一份本地方案页。
                onNavigateToLocal = { navController.navigate(SettingsRoutes.Schema) },
                onNavigateToModelLocal = { navController.navigate(SettingsRoutes.ModelLocal) },
                onNavigateToPluginSettings = { pluginId ->
                    navController.navigate("${SettingsRoutes.PluginSettings}/$pluginId")
                },
                onNavigateToSpeechToText = { navController.navigate(SettingsRoutes.SpeechToText) },
                onNavigateToClipboardSync = { navController.navigate(SettingsRoutes.ClipboardSync) },
            )
        }
        composable(SettingsRoutes.ModelLocal) {
            ModelLocalContent(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SettingsRoutes.SchemaMarketDetail,
            arguments = listOf(navArgument("schemeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val schemeId = backStackEntry.arguments?.getString("schemeId") ?: return@composable
            MarketSchemeDetailContent(
                schemeId = schemeId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SettingsRoutes.ModelMarketDetail,
            arguments = listOf(navArgument("modelId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val modelId = backStackEntry.arguments?.getString("modelId") ?: return@composable
            MarketModelDetailContent(
                modelId = modelId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = SettingsRoutes.PluginDetail,
            arguments = listOf(navArgument("pluginId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId") ?: return@composable
            PluginDetailContent(
                pluginId = pluginId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SettingsRoutes.Theme) {
            ThemeSettingsContent(
                onBack = { navController.popBackStack() },
                onThemeChanged = onThemeChanged
            )
        }
        composable(SettingsRoutes.Plugins) {
            PluginsSettingsContent(
                onBack = { navController.popBackStack() },
                onNavigateToPluginSettings = { pluginId ->
                    navController.navigate("${SettingsRoutes.PluginSettings}/$pluginId")
                },
                onNavigateToPluginDetail = { pluginId ->
                    navController.navigate("plugin_detail/$pluginId")
                },
                onNavigateToSpeechToText = { navController.navigate(SettingsRoutes.SpeechToText) },
                onNavigateToClipboardSync = { navController.navigate(SettingsRoutes.ClipboardSync) }
            )
        }
        composable(
            route = "${SettingsRoutes.PluginSettings}/{pluginId}",
            arguments = listOf(navArgument("pluginId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId")
            PluginSettingsContent(
                pluginId = pluginId ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.Performance) {
            PerformanceSettingsContent(onBack = { navController.popBackStack() },
                onOpenSetupGuide = { navController.navigate(SettingsRoutes.SetupGuide) })
        }
        composable(SettingsRoutes.KeyEffect) {
            KeyEffectSettingsContent(
                onBack = { navController.popBackStack() }
            )
        }
        composable("${SettingsRoutes.LayoutDisplay}?page={page}",
            arguments = listOf(navArgument("page") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            LayoutDisplaySettingsContent(
                onBack = { navController.popBackStack() },
                onOpenLayout = { navController.navigate(it) },
                initialPage = entry.arguments?.getString("page"),
            )
        }
        LayoutInputCategory.entries.forEach { category ->
            composable(category.route) {
                LayoutInputSettingsContent(category, onBack = { navController.popBackStack() })
            }
        }
        composable(SettingsRoutes.SmartPrediction) {
            SmartPredictionSettingsContent(
                onBack = { navController.popBackStack() },
                onNavigateToModelManagement = { navController.navigate(SettingsRoutes.ModelLocal) },
                onNavigateToModelDetail = { modelId ->
                    navController.navigate("model_market_detail/$modelId")
                }
            )
        }
        composable(SettingsRoutes.SpeechToText) {
            SpeechToTextSettingsContent(
                onBack = { navController.popBackStack() },
                onNavigateToSetupGuide = { navController.navigate(SettingsRoutes.SetupGuide) },
                onNavigateToPluginSettings = { pluginId ->
                    navController.navigate("${SettingsRoutes.PluginSettings}/$pluginId")
                },
            )
        }
        composable(SettingsRoutes.Dictionary) {
            DictionarySettingsContent(
                onBack = { navController.popBackStack() },
                onCellDictionaries = { navController.navigate(SettingsRoutes.CellDictionaries) }
            )
        }
        composable(SettingsRoutes.CellDictionaries) {
            CellDictionarySettingsContent(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.RimeFileBrowser) {
            RimeFileBrowserContent(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.ClipboardSync) {
            ClipboardSyncSettingsContent(
                onBack = { navController.popBackStack() },
                onNavigateToPlugins = { navController.navigate(SettingsRoutes.Plugins) },
                onNavigateToGroups = { navController.navigate(SettingsRoutes.ClipboardGroups) },
            )
        }
        composable(SettingsRoutes.ClipboardGroups) {
            ClipboardGroupSettingsContent(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.KeyboardResize) {
            KeyboardPanelSettingsContent(com.kingzcheung.xime.keyboard.KeyboardPanelLaunch.Panel.RESIZE) { navController.popBackStack() }
        }
        composable(SettingsRoutes.ToolbarArrange) {
            KeyboardPanelSettingsContent(com.kingzcheung.xime.keyboard.KeyboardPanelLaunch.Panel.ARRANGE) { navController.popBackStack() }
        }
        composable(SettingsRoutes.Backup) {
            BackupSettingsContent(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.About) {
            AboutContent(
                onBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(SettingsRoutes.Privacy) },
                onNavigateToLicenses = { navController.navigate(SettingsRoutes.Licenses) },
                onNavigateToCredits = { navController.navigate(SettingsRoutes.Credits) },
                onNavigateToLogViewer = { navController.navigate(SettingsRoutes.LogViewer) },
                onNavigateToHandwritingCapture = { navController.navigate(SettingsRoutes.HandwritingCapture) },
                onNavigateToStorageSpace = { navController.navigate(SettingsRoutes.StorageSpace) }
            )
        }
        composable(SettingsRoutes.SetupGuide) {
            SetupGuideScreen(firstRun = false, onDone = { navController.popBackStack() },
                onOpenPerformance = { navController.navigate(SettingsRoutes.Performance) })
        }
        composable(SettingsRoutes.StorageSpace) {
            StorageSpaceScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPlugins = { navController.navigate(SettingsRoutes.Plugins) }
            )
        }
        composable(SettingsRoutes.LogViewer) {
            LogViewerScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.Handwriting) {
            HandwritingSettingsContent(onBack = { navController.popBackStack() },
                onOpenModels = { navController.navigate(SettingsRoutes.ModelLocal) },
                onOpenTraining = { navController.navigate(SettingsRoutes.HandwritingTraining) },
                onOpenCapture = { navController.navigate(SettingsRoutes.HandwritingCapture) })
        }
        composable(SettingsRoutes.HandwritingTraining) {
            HandwritingTrainingScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.HandwritingCapture) {
            HandwritingCaptureScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.Privacy) {
            PrivacyPolicyContent(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.Licenses) {
            LicensesContent(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.Credits) {
            CreditsContent(onBack = { navController.popBackStack() })
        }
    }
}
