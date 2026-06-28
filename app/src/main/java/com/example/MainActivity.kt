package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ScannerViewModel

class MainActivity : ComponentActivity() {
    
    private val scannerViewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = scannerViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: ScannerViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // 1. Home Dashboard
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToSetup = {
                    navController.navigate("scan_setup")
                },
                onNavigateToCamera = { batchId, preset ->
                    navController.navigate("camera_scan/$batchId/$preset")
                },
                onNavigateToReview = { batchId ->
                    navController.navigate("review_grid/$batchId")
                }
            )
        }

        // 2. Scan Setup Setup Screen
        composable("scan_setup") {
            ScanSetupScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCamera = { batchId, preset ->
                    navController.navigate("camera_scan/$batchId/$preset") {
                        // Pop setup screen so back navigation from camera takes user home
                        popUpTo("home")
                    }
                }
            )
        }

        // 3. Live Camera View / Simulator view
        composable(
            route = "camera_scan/{batchId}/{preset}",
            arguments = listOf(
                navArgument("batchId") { type = NavType.StringType },
                navArgument("preset") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val batchId = backStackEntry.arguments?.getString("batchId") ?: ""
            val preset = backStackEntry.arguments?.getString("preset") ?: "Document"
            
            CameraScanScreen(
                viewModel = viewModel,
                batchId = batchId,
                initialPreset = preset,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToReview = { bId ->
                    navController.navigate("review_grid/$bId") {
                        popUpTo("home")
                    }
                }
            )
        }

        // 4. Review Grid Screen
        composable(
            route = "review_grid/{batchId}",
            arguments = listOf(
                navArgument("batchId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val batchId = backStackEntry.arguments?.getString("batchId") ?: ""
            
            // Ensure the correct batch is loaded into ViewModel state
            LaunchedEffect(batchId) {
                viewModel.selectBatch(batchId)
            }

            ReviewGridScreen(
                viewModel = viewModel,
                batchId = batchId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCamera = { bId, preset ->
                    navController.navigate("camera_scan/$bId/$preset")
                },
                onNavigateToEditor = { pageId ->
                    navController.navigate("page_editor/$pageId")
                }
            )
        }

        // 5. Page Editor Screen
        composable(
            route = "page_editor/{pageId}",
            arguments = listOf(
                navArgument("pageId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getLong("pageId") ?: 0L

            PageEditorScreen(
                viewModel = viewModel,
                pageId = pageId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
