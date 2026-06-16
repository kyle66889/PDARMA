package com.pda.app.data.repository

import android.util.Log
import com.pda.app.data.NetworkResult
import com.pda.app.data.api.WarehouseApiService
import com.pda.app.data.api.model.WarehouseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val apiService: WarehouseApiService
) {
    private companion object {
        const val TAG = "PDA/WarehouseRepository"
    }

    fun getWarehouses(): Flow<NetworkResult<List<WarehouseDto>>> = flow {
        Log.i(TAG, "getWarehouses: start")
        emit(NetworkResult.Loading)
        try {
            val response = apiService.getWarehouses()
            Log.d(TAG, "getWarehouses: code=${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!
                Log.i(TAG, "getWarehouses: success — count=${list.size}")
                emit(NetworkResult.Success(list))
            } else {
                val message = when (response.code()) {
                    401 -> "登录已过期，请重新登录"
                    403 -> "无权限访问仓库列表"
                    else -> "加载仓库失败（${response.code()}）"
                }
                Log.w(TAG, "getWarehouses: failed — code=${response.code()}")
                emit(NetworkResult.Error(message, response.code()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getWarehouses: exception — ${e.javaClass.simpleName}: ${e.message}", e)
            emit(NetworkResult.Error(e.message ?: "网络连接失败，请检查网络设置"))
        }
    }.flowOn(Dispatchers.IO)
}
