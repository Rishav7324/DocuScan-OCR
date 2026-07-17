package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ScannerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val viewModel: ScannerViewModel = viewModel()

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
                            route = "camera_scan/{folderId}",
                            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val folderId = backStackEntry.arguments?.getLong("folderId") ?: 0L
                            CameraScanScreen(
                                viewModel = viewModel,
                                folderId = folderId,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToCrop = {
                                    navController.navigate("crop_correction")
                                }
                            )
                        }

                        // 4. Interactive crop perspective correction screen
                        composable("crop_correction") {
                            CropCorrectionScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToOcr = {
                                    // By default when finalizing crops, we navigate directly to our active document's OCR terminal
                                    navController.navigate("ocr_export/none") {
                                        popUpTo("dashboard") { saveState = true }
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
                    }
                }
            }
        }
    }
}
