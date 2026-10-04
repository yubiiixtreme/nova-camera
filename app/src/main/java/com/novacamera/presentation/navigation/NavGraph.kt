package com.novacamera.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novacamera.presentation.camera.CameraScreen
import com.novacamera.presentation.gallery.GalleryScreen
import com.novacamera.presentation.gallery.ViewerScreen
import com.novacamera.presentation.scan.ScanScreen
import com.novacamera.presentation.settings.SettingsScreen
import com.novacamera.presentation.vault.VaultScreen

object Routes {
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val VIEWER = "viewer"
    const val SETTINGS = "settings"
    const val VAULT = "vault"
    const val SCAN = "scan"
}

@Composable
fun NovaNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            CameraScreen(
                onOpenGallery = { nav.navigate(Routes.GALLERY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenVault = { nav.navigate(Routes.VAULT) },
                onOpenScan = { nav.navigate(Routes.SCAN) },
            )
        }
        composable(Routes.GALLERY) {
            GalleryScreen(
                onBack = { nav.popBackStack() },
                onOpenViewer = { nav.navigate("${Routes.VIEWER}/$it") },
                onOpenVault = { nav.navigate(Routes.VAULT) },
            )
        }
        composable(
            "${Routes.VIEWER}/{index}",
            arguments = listOf(navArgument("index") { type = NavType.IntType }),
        ) { entry ->
            ViewerScreen(startIndex = entry.arguments?.getInt("index") ?: 0, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.VAULT) { VaultScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SCAN) { ScanScreen(onBack = { nav.popBackStack() }) }
    }
}
