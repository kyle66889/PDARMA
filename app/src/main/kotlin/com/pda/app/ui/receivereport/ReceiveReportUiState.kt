package com.pda.app.ui.receivereport

sealed interface ReceiveReportUiState {
    data object Loading : ReceiveReportUiState
    data object Empty : ReceiveReportUiState
    data class Success(val days: List<ReceiveReportDay>) : ReceiveReportUiState
    data class Error(val message: String) : ReceiveReportUiState
}
