package com.pda.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/** 持久化：上次用户名 / 记住开关 / 选中仓库 id。 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_LAST_USERNAME = stringPreferencesKey("last_username")
        val KEY_REMEMBER_USERNAME = booleanPreferencesKey("remember_username")
        val KEY_SELECTED_WAREHOUSE_ID = intPreferencesKey("selected_warehouse_id")
    }

    val lastUsername: Flow<String?> =
        context.dataStore.data.map { it[KEY_LAST_USERNAME] }

    val rememberUsername: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_REMEMBER_USERNAME] ?: true }

    val selectedWarehouseId: Flow<Int?> =
        context.dataStore.data.map { it[KEY_SELECTED_WAREHOUSE_ID] }

    /** 登录成功后调用：记住则存用户名，否则清除；同时保存复选框状态。 */
    suspend fun saveUsername(username: String, remember: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REMEMBER_USERNAME] = remember
            if (remember) {
                prefs[KEY_LAST_USERNAME] = username
            } else {
                prefs.remove(KEY_LAST_USERNAME)
            }
        }
    }

    suspend fun setSelectedWarehouseId(id: Int) {
        context.dataStore.edit { it[KEY_SELECTED_WAREHOUSE_ID] = id }
    }
}
