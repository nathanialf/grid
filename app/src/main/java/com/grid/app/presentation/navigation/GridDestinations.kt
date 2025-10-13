package com.grid.app.presentation.navigation

sealed class GridDestinations(val route: String) {
    object ConnectionList : GridDestinations("connections")
    object AddConnection : GridDestinations("add_connection")
    object EditConnection : GridDestinations("edit_connection/{connectionId}") {
        fun createRoute(connectionId: String) = "edit_connection/$connectionId"
    }
    object FileBrowser : GridDestinations("file_browser/{connectionId}") {
        fun createRoute(connectionId: String) = "file_browser/$connectionId"
    }
    object Settings : GridDestinations("settings")
}