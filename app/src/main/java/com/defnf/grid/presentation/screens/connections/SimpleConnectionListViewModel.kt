package com.defnf.grid.presentation.screens.connections

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SimpleConnectionListViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionListUiState())
    val uiState: StateFlow<ConnectionListUiState> = _uiState.asStateFlow()

    fun refresh() {
        // TODO: Implement when dependencies are fixed
    }

    fun deleteConnection() {
        // TODO: Implement when dependencies are fixed
    }
}