package com.pda.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pda.app.data.NetworkResult
import com.pda.app.data.api.model.WarehouseDto
import com.pda.app.data.prefs.UserPreferences
import com.pda.app.data.repository.WarehouseRepository
import com.pda.app.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val warehouseRepository: WarehouseRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private companion object {
        const val TAG = "PDA/HomeViewModel"
    }

    private val _uiState = MutableStateFlow(
        HomeUiState(userFullName = sessionManager.session.value?.user?.fullName ?: "")
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadWarehouses()
    }

    fun loadWarehouses() {
        warehouseRepository.getWarehouses()
            .onEach { result ->
                when (result) {
                    is NetworkResult.Loading ->
                        _uiState.value = _uiState.value.copy(warehouseState = WarehouseState.Loading)
                    is NetworkResult.Success -> {
                        val list = result.data
                        val selected = resolveSelected(list)
                        selected?.let { sessionManager.selectWarehouse(it) }
                        _uiState.value = _uiState.value.copy(
                            warehouseState = WarehouseState.Success(list),
                            selectedWarehouse = selected
                        )
                    }
                    is NetworkResult.Error ->
                        _uiState.value = _uiState.value.copy(
                            warehouseState = WarehouseState.Error(result.message)
                        )
                }
            }
            .launchIn(viewModelScope)
    }

    /** 优先用 DataStore 记住的仓库（仍在列表中），否则取首个启用仓库。 */
    private suspend fun resolveSelected(list: List<WarehouseDto>): WarehouseDto? {
        if (list.isEmpty()) return null
        val savedId = userPreferences.selectedWarehouseId.first()
        return list.firstOrNull { it.id == savedId }
            ?: list.firstOrNull { it.isActive }
            ?: list.first()
    }

    fun selectWarehouse(warehouse: WarehouseDto) {
        Log.i(TAG, "selectWarehouse: ${warehouse.warehouseCode}")
        sessionManager.selectWarehouse(warehouse)
        _uiState.value = _uiState.value.copy(selectedWarehouse = warehouse)
        viewModelScope.launch { userPreferences.setSelectedWarehouseId(warehouse.id) }
    }

    fun logout() {
        Log.i(TAG, "logout")
        sessionManager.clear()
    }
}
