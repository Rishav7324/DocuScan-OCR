package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.ui.screens.*
import com.example.ui.components.AppLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ScannerViewModel
import com.example.data.api.OAuthManager
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: ScannerViewModel by lazy {
        ViewModelProvider(this)[ScannerViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OAuthManager.init(this)
        enableEdgeToEdge()
        setContent {
            val viewModel: ScannerViewModel = viewModel()

            MyApplicationTheme {
                val isAppLocked by viewModel.isAppLocked.collectAsState()

                // Evaluate + keep app-lock state in sync with the lifecycle.
                LaunchedEffect(Unit) {
                    viewModel.evaluateAppLockOnResume()
                    lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            viewModel.evaluateAppLockOnResume()
                        }
                    }
                }

                if (isAppLocked) {
                    AppLockScreen(viewModel = viewModel)
                } else {
                    val navController = rememberNavController()
                    var currentRoute by remember { mutableStateOf(navController.currentDestination?.route ?: "dashboard") }

                    // Bottom-nav tabs. Routes not in this set are shown full-screen (no bar).
                    val tabRoutes = listOf("dashboard", "ocr_export/none", "cloud_sync", "help_legal")
                    val showBottomBar = tabRoutes.any { currentRoute.startsWith(it.takeWhile { c -> c != '/' })) }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar {
                                    val items = listOf(
                                        BottomTab("dashboard", "Home", Icons.Filled.Home),
                                        BottomTab("camera_scan/0", "Scan", Icons.Filled.DocumentScanner),
                                        BottomTab("ocr_export/none", "Library", Icons.Filled.AutoStories),
                                        BottomTab("cloud_sync", "Sync", Icons.Filled.CloudSync),
                                        BottomTab("help_legal", "More", Icons.Filled.MoreHoriz)
                                    )
                                    val selectedRoute = currentRoute.takeWhile { it != '/' }
                                    items.forEach { tab ->
                                        val tabRoot = tab.route.takeWhile { it != '/' }
                                        NavigationBarItem(
                                            selected = selectedRoute == tabRoot,
                                            onClick = {
                                                if (selectedRoute != tabRoot) {
                                                    navController.navigate(tab.route) {
                                                        popUpTo("dashboard") { inclusive = false }
                                                        launchSingleTop = true
                                                    }
                                                }
                                            },
                                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                                            label = { Text(tab.label) }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "dashboard",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            // Keep the bottom-bar highlight in sync with the current route.
                            navController.addOnDestinationChangedListener { _, dest, _ ->
                                currentRoute = dest.route ?: "dashboard"
                            }

                            // 1. Dashboard screen
                            composable("dashboard") {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToFolder = { folderId ->
                                        navController.navigate("folder_detail/$folderId")
                                    },
                                    onNavigateToCapture = { folderId ->
                                        navController.navigate("camera_scan/$folderId")
                                    },
                                    onNavigateToSync = {
                                        navController.navigate("cloud_sync")
                                    },
                                    onNavigateToCompliance = {
                                        navController.navigate("compliance")
                                    },
                                    onNavigateToOcr = { pin ->
                                        val arg = pin ?: "none"
                                        navController.navigate("ocr_export/$arg")
                                    },
                                    onNavigateToHelpAndLegal = {
                                        navController.navigate("help_legal")
                                    }
                                )
                            }

                            // 2. Folder details screen
                            composable(
                                route = "folder_detail/{folderId}",
                                arguments = listOf(navArgument("folderId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val folderId = backStackEntry.arguments?.getLong("folderId") ?: 0L
                                FolderDetailScreen(
                                    viewModel = viewModel,
                                    folderId = folderId,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToCapture = {
                                        navController.navigate("camera_scan/$folderId")
                                    },
                                    onNavigateToOcr = { pin ->
                                        val arg = pin ?: "none"
                                        navController.navigate("ocr_export/$arg")
                                    }
                                )
                            }

                            // 3. Simulated/Live batch scanning camera screen
                            composable(
                                "camera_scan/{folderId}",
                                arguments = listOf(navArgument("folderId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val folderId = backStackEntry.arguments?.getLong("folderId") ?: 0L
                                CameraScanScreen(
                                    viewModel = viewModel,
                                    folderId = folderId,
                                    onNavigateBack = { navController.popBackStack() },
                                    onScanComplete = { navController.navigate("crop_correction") }
                                )
                            }

                            // 4. Interactive crop perspective correction screen
                            composable("crop_correction") {
                                CropCorrectionScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToOcr = {
                                        // Finalize: auto-create a folder when scanning into root, then save everything.
                                        viewModel.finalizeBatch("", 0L)
                                        navController.navigate("dashboard") {
                                            popUpTo("dashboard") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // 5. OCR processing, editing and formats export screen (also the Library tab)
                            composable(
                                route = "ocr_export/{folderPin}",
                                arguments = listOf(navArgument("folderPin") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val pinArg = backStackEntry.arguments?.getString("folderPin")
                                val folderPin = if (pinArg == "none" || pinArg == null) null else pinArg

                                OcrExportScreen(
                                    viewModel = viewModel,
                                    folderPin = folderPin,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToHome = {
                                        navController.navigate("dashboard") {
                                            popUpTo("dashboard") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // 6. Cloud sync (Cloudflare R2, Drive, Dropbox config)
                            composable("cloud_sync") {
                                CloudSyncScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            // 7. HIPAA and GDPR Compliance Auditing Terminal
                            composable("compliance") {
                                ComplianceScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            // 8. Integration Help and Legal Center
                            composable("help_legal") {
                                HelpAndLegalScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToLegalPage = { pageName ->
                                        navController.navigate("legal_page/$pageName")
                                    }
                                )
                            }

                            // 9. Legal page viewer
                            composable(
                                route = "legal_page/{pageName}",
                                arguments = listOf(navArgument("pageName") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val pageName = backStackEntry.arguments?.getString("pageName") ?: "index"
                                LegalPageViewer(
                                    pageName = pageName,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

    override fun onResume() {
        super.onResume()
        // ponytail: lock screen uses FLAG_SECURE so the PIN entry can't be screenshotted
        if (vm.isAppLocked.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Zeroize sensitive in-memory state when the app leaves the foreground.
        vm.clearSensitiveState()
    }
}
