package com.pda.app.data.api.model

import kotlinx.serialization.Serializable

/** 镜像 RMA WarehouseDto，仅取 PDA 需要的字段（其余由 Json.ignoreUnknownKeys 忽略）。 */
@Serializable
data class WarehouseDto(
    val id: Int,
    val warehouseCode: String,
    val warehouseName: String,
    val isActive: Boolean = true
)
