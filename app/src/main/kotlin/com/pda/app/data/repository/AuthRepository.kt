package com.pda.app.data.repository

import android.util.Log
import com.pda.app.data.NetworkResult
import com.pda.app.data.api.AuthApiService
import com.pda.app.data.api.model.LoginResponse
import com.pda.app.data.api.model.LoginRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: AuthApiService
) {
    companion object {
        private const val TAG = "PDA/AuthRepository"
    }

    fun login(username: String, password: String): Flow<NetworkResult<LoginResponse>> = flow {
        Log.i(TAG, "login: start — username=$username")
        emit(NetworkResult.Loading)
        try {
            val response = apiService.login(LoginRequest(username.trim(), password))
            Log.d(TAG, "login: response code=${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!.user
                Log.i(TAG, "login: success — userId=${user.userId}, roles=${user.roles}")
                emit(NetworkResult.Success(response.body()!!))
            } else {
                val serverError = runCatching {
                    response.errorBody()?.string()?.let { body ->
                        Log.d(TAG, "login: error body=$body")
                        Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
                    }
                }.getOrNull()

                val message = serverError ?: when (response.code()) {
                    400 -> "请填写用户名和密码"
                    401 -> "用户名或密码错误"
                    403 -> "用户被禁用"
                    else -> "登录失败（${response.code()}）"
                }
                Log.w(TAG, "login: failed — code=${response.code()}, message=$message")
                emit(NetworkResult.Error(message, response.code()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "login: exception — ${e.javaClass.simpleName}: ${e.message}", e)
            emit(NetworkResult.Error(e.message ?: "网络连接失败，请检查网络设置"))
        }
    }.flowOn(Dispatchers.IO)
}
