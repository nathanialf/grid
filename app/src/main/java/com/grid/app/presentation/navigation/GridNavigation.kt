package com.grid.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.grid.app.presentation.screens.connections.ConnectionListScreen
import com.grid.app.presentation.screens.connections.AddConnectionScreen
import com.grid.app.presentation.screens.connections.EditConnectionScreen
import com.grid.app.presentation.screens.filebrowser.FileBrowserScreen
import com.grid.app.presentation.screens.settings.SettingsScreen

@Composable
fun GridNavigation(
    navController: NavHostController,
    startDestination: String = GridDestinations.ConnectionList.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(GridDestinations.ConnectionList.route) {
            ConnectionListScreen(
                onNavigateToAddConnection = {
                    navController.navigate(GridDestinations.AddConnection.route)
                },
                onNavigateToEditConnection = { connectionId ->
                    navController.navigate(GridDestinations.EditConnection.createRoute(connectionId))
                },
                onNavigateToFileBrowser = { connectionId ->
                    navController.navigate(GridDestinations.FileBrowser.createRoute(connectionId))
                },
                onNavigateToSettings = {
                    navController.navigate(GridDestinations.Settings.route)
                }
            )
        }
        
        composable(GridDestinations.AddConnection.route) {
            AddConnectionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onConnectionSaved = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = GridDestinations.EditConnection.route,
            arguments = listOf(navArgument("connectionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getString("connectionId") ?: ""
            EditConnectionScreen(
                connectionId = connectionId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onConnectionSaved = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = GridDestinations.FileBrowser.route,
            arguments = listOf(navArgument("connectionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getString("connectionId") ?: ""
            FileBrowserScreen(
                connectionId = connectionId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(GridDestinations.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}