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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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

                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
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

                        // 5. OCR processing, editing and formats export screen
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
