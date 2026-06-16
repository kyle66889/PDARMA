package com.pda.app.ui.home

import com.pda.app.data.api.model.WarehouseDto

data class HomeUiState(
    val userFullName: String = "",
    val warehouseState: WarehouseState = WarehouseState.Loading,
    val selectedWarehouse: WarehouseDto? = null
)

sealed interface WarehouseState {
    data object Loading : WarehouseState
    data class Success(val warehouses: List<WarehouseDto>) : WarehouseState
    data class Error(val message: String) : WarehouseState
}
