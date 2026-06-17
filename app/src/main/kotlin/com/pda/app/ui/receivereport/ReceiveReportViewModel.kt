package com.pda.app.ui.receivereport

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pda.app.data.NetworkResult
import com.pda.app.data.repository.ReceivingRepository
import com.pda.app.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ReceiveReportViewModel @Inject constructor(
    private val repo: ReceivingRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val TAG = "PDA/ReceiveReportViewModel"
        const val WINDOW_DAYS = 3L
    }

    private val warehouseId: Int? =
        savedStateHandle.get<String>("warehouseId")?.toIntOrNull()

    private val _uiState = MutableStateFlow<ReceiveReportUiState>(ReceiveReportUiState.Loading)
    val uiState: StateFlow<ReceiveReportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val wid = warehouseId
        val user = sessionManager.session.value?.user?.username
        if (wid == null || user.isNullOrBlank()) {
            _uiState.value = ReceiveReportUiState.Error("Select a warehouse first")
            return
        }
        val today = LocalDate.now()
        val since = today.minusDays(WINDOW_DAYS).toString() // yyyy-MM-dd, filters StartTime
        repo.getReceivedBatches(wid, user, since)
            .onEach { result ->
                when (result) {
                    is NetworkResult.Loading -> _uiState.value = ReceiveReportUiState.Loading
                    is NetworkResult.Success -> {
                        val days = buildReceiveReport(result.data, today)
                        _uiState.value = if (days.isEmpty()) ReceiveReportUiState.Empty
                        else ReceiveReportUiState.Success(days)
                    }
                    is NetworkResult.Error -> {
                        Log.w(TAG, "load failed: ${result.message}")
                        _uiState.value = ReceiveReportUiState.Error(result.message)
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}
