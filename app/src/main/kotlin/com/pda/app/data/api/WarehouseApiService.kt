package com.pda.app.data.api

import com.pda.app.data.api.model.WarehouseDto
import retrofit2.Response
import retrofit2.http.GET

interface WarehouseApiService {

    /** GET /api/warehouses — 需 JWT，返回按 code 升序的仓库列表。 */
    @GET("api/warehouses")
    suspend fun getWarehouses(): Response<List<WarehouseDto>>
}
