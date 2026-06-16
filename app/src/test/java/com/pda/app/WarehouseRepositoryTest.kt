package com.pda.app

import com.pda.app.data.NetworkResult
import com.pda.app.data.api.WarehouseApiService
import com.pda.app.data.api.model.WarehouseDto
import com.pda.app.data.repository.WarehouseRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeWarehouseApiService(
    private val response: Response<List<WarehouseDto>>
) : WarehouseApiService {
    override suspend fun getWarehouses(): Response<List<WarehouseDto>> = response
}

class WarehouseRepositoryTest {

    @Test
    fun `getWarehouses emits Loading then Success`() = runTest {
        val wh = WarehouseDto(1, "WH01", "深圳总仓", true)
        val repo = WarehouseRepository(FakeWarehouseApiService(Response.success(listOf(wh))))

        val emissions = repo.getWarehouses().toList()

        assertTrue(emissions[0] is NetworkResult.Loading)
        assertTrue(emissions[1] is NetworkResult.Success)
        assertEquals(listOf(wh), (emissions[1] as NetworkResult.Success).data)
    }

    @Test
    fun `getWarehouses maps 401 to expired message`() = runTest {
        val body = "{}".toResponseBody("application/json".toMediaType())
        val repo = WarehouseRepository(FakeWarehouseApiService(Response.error(401, body)))

        val emissions = repo.getWarehouses().toList()
        val error = emissions[1] as NetworkResult.Error

        assertEquals("登录已过期，请重新登录", error.message)
        assertEquals(401, error.code)
    }

    @Test
    fun `getWarehouses maps 403 to permission message`() = runTest {
        val body = "{}".toResponseBody("application/json".toMediaType())
        val repo = WarehouseRepository(FakeWarehouseApiService(Response.error(403, body)))

        val error = repo.getWarehouses().toList()[1] as NetworkResult.Error
        assertEquals("无权限访问仓库列表", error.message)
    }
}
