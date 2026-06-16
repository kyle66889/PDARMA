package com.pda.app.data.session

import android.util.Log
import com.pda.app.data.api.model.UserInfoDto
import com.pda.app.data.api.model.WarehouseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 登录会话（仅内存，进程结束即丢失——无自动登录）。 */
data class Session(
    val token: String,
    val user: UserInfoDto,
    val selectedWarehouse: WarehouseDto? = null
)

@Singleton
class SessionManager @Inject constructor() {

    private companion object {
        const val TAG = "PDA/SessionManager"
    }

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** 供 AuthInterceptor 读取的当前 token。 */
    val currentToken: String?
        get() = _session.value?.token

    fun start(token: String, user: UserInfoDto) {
        Log.i(TAG, "start: user=${user.username}")
        _session.value = Session(token = token, user = user)
    }

    fun selectWarehouse(warehouse: WarehouseDto) {
        val current = _session.value ?: run {
            Log.w(TAG, "selectWarehouse ignored: no active session")
            return
        }
        Log.d(TAG, "selectWarehouse: ${warehouse.warehouseCode}")
        _session.value = current.copy(selectedWarehouse = warehouse)
    }

    fun clear() {
        Log.i(TAG, "clear: session cleared")
        _session.value = null
    }
}
