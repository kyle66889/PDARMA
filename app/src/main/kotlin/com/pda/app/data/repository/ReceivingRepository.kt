package com.pda.app.data.repository

import android.util.Log
import com.pda.app.data.NetworkResult
import com.pda.app.data.api.ReceivingApiService
import com.pda.app.data.api.model.AnalyzeRequest
import com.pda.app.data.api.model.BatchInfo
import com.pda.app.data.api.model.CreateBatchRequest
import com.pda.app.data.api.model.CreateItemRequest
import com.pda.app.data.api.model.ReceivingItemUi
import com.pda.app.data.api.model.ShippingAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ReceivingRepository @Inject constructor(
    private val api: ReceivingApiService
) {
    companion object {
        private const val TAG = "PDA/ReceivingRepository"
        private const val NETWORK_FAIL = "网络连接失败，请检查网络设置"
    }

    open fun createBatch(warehouseId: Int): Flow<NetworkResult<BatchInfo>> = flow {
        emit(NetworkResult.Loading)
        try {
            val resp = api.createBatch(CreateBatchRequest(warehouseId))
            if (resp.isSuccessful && resp.body() != null) {
                val b = resp.body()!!
                emit(NetworkResult.Success(BatchInfo(b.receivingBatchId, b.batchNumber)))
            } else {
                emit(errorFrom(resp, "创建批次失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createBatch: ${e.message}", e)
            emit(NetworkResult.Error(e.message ?: NETWORK_FAIL))
        }
    }.flowOn(Dispatchers.IO)

    open fun uploadPhoto(bytes: ByteArray, filename: String): Flow<NetworkResult<String>> = flow {
        emit(NetworkResult.Loading)
        try {
            val body = bytes.toRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("files", filename, body)
            val resp = api.uploadPhotos(part)
            if (resp.isSuccessful && resp.body() != null) {
                val url = resp.body()!!.urls.firstOrNull()
                if (url.isNullOrBlank()) emit(NetworkResult.Error("图片上传失败：未返回有效 URL"))
                else emit(NetworkResult.Success(url))
            } else {
                emit(errorFrom(resp, "图片上传失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadPhoto: ${e.message}", e)
            emit(NetworkResult.Error(e.message ?: NETWORK_FAIL))
        }
    }.flowOn(Dispatchers.IO)

    open fun analyzeShipping(base64: String): Flow<NetworkResult<ShippingAnalysis>> = flow {
        emit(NetworkResult.Loading)
        try {
            val resp = api.analyze(AnalyzeRequest(mode = "shipping", photos = listOf(base64)))
            if (resp.isSuccessful && resp.body() != null) {
                val a = resp.body()!!
                emit(NetworkResult.Success(ShippingAnalysis(a.trackingNumber, a.carrier, a.service, a.raw)))
            } else {
                emit(errorFrom(resp, "AI 识别失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "analyzeShipping: ${e.message}", e)
            emit(NetworkResult.Error(e.message ?: NETWORK_FAIL))
        }
    }.flowOn(Dispatchers.IO)

    open fun createItem(req: CreateItemRequest): Flow<NetworkResult<Int>> = flow {
        emit(NetworkResult.Loading)
        try {
            val resp = api.createItem(req)
            if (resp.isSuccessful && resp.body() != null) {
                emit(NetworkResult.Success(resp.body()!!.receivingItemId))
            } else {
                emit(errorFrom(resp, "录入失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createItem: ${e.message}", e)
            emit(NetworkResult.Error(e.message ?: NETWORK_FAIL))
        }
    }.flowOn(Dispatchers.IO)

    open fun getItems(batchId: Int): Flow<NetworkResult<List<ReceivingItemUi>>> = flow {
        emit(NetworkResult.Loading)
        try {
            val resp = api.getItems(batchId)
            if (resp.isSuccessful && resp.body() != null) {
                val items = resp.body()!!.map {
                    ReceivingItemUi(
                        receivingItemId = it.receivingItemId,
                        trackingNo = it.trackingNo.orEmpty(),
                        carrier = it.carrier.orEmpty(),
                        needsReview = it.needsReview ?: false
                    )
                }
                emit(NetworkResult.Success(items))
            } else {
                emit(errorFrom(resp, "加载条目失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getItems: ${e.message}", e)
            emit(NetworkResult.Error(e.message ?: NETWORK_FAIL))
        }
    }.flowOn(Dispatchers.IO)

    open fun closeBatch(batchId: Int): Flow<NetworkResult<Unit>> = flow {
        emit(NetworkResult.Loading)
        try {
            val resp = api.closeBatch(batchId)
            if (resp.isSuccessful) emit(NetworkResult.Success(Unit))
            else emit(errorFrom(resp, "关闭失败"))
        } catch (e: Exception) {
            Log.e(TAG, "closeBatch: ${e.message}", e)
            emit(NetworkResult.Error(e.message ?: NETWORK_FAIL))
        }
    }.flowOn(Dispatchers.IO)

    private fun errorFrom(resp: Response<*>, fallback: String): NetworkResult.Error {
        val serverError = runCatching {
            resp.errorBody()?.string()?.let { body ->
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
            }
        }.getOrNull()
        val message = serverError ?: when (resp.code()) {
            401 -> "登录已过期，请重新登录"
            403 -> "无权限，请联系管理员"
            else -> "$fallback（${resp.code()}）"
        }
        return NetworkResult.Error(message, resp.code())
    }
}
