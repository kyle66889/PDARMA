# PDA 登录美化 + 主界面（仓库切换 / Dock Receive / Logout）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 PDA 安卓 App 上美化登录界面（记住用户名）、新增登录后主界面（仓库切换 + 姓名）、Dock Receive 按钮与退出登录。

**Architecture:** 方案 A — 新增内存级 `SessionManager` 持有登录会话（token + user + 选中仓库），OkHttp `AuthInterceptor` 自动附带 Bearer token，DataStore `UserPreferences` 持久化「上次用户名 / 记住开关 / 选中仓库 id」。仓库列表来自 RMA 后端 `GET /api/warehouses`。遵循现有 `Flow<NetworkResult<T>>` + Hilt + Compose 约定。

**Tech Stack:** Kotlin 2.0、Jetpack Compose（Material3）、Hilt、Retrofit + OkHttp、Kotlinx Serialization、DataStore Preferences、Coroutines/Flow。测试：JUnit4 + kotlinx-coroutines-test（仅纯逻辑）。

**关于 git：** 本项目当前**不是 git 仓库**。Task 0 可选地 `git init`。若不使用 git，请把每个 "Commit" 步骤当作检查点跳过 `git` 命令即可。

**验证命令（Windows PowerShell，工作目录 `PDA\PDA`）：**
- 编译：`.\gradlew.bat assembleDebug`
- 单测：`.\gradlew.bat testDebugUnitTest`

---

## 文件结构

**新增（main）：**
- `data/session/SessionManager.kt` — 内存会话状态（StateFlow）
- `data/prefs/UserPreferences.kt` — DataStore 封装（用户名 / 记住开关 / 选中仓库 id）
- `data/api/AuthInterceptor.kt` — OkHttp 拦截器，附带 Bearer token
- `data/api/model/WarehouseModels.kt` — `WarehouseDto`
- `data/api/WarehouseApiService.kt` — `GET /api/warehouses`
- `data/repository/WarehouseRepository.kt` — 仓库列表加载 + 错误映射
- `ui/home/HomeUiState.kt` — 主界面状态
- `ui/home/HomeViewModel.kt` — 主界面逻辑
- `ui/home/HomeScreen.kt` — 主界面 UI（布局 B）

**修改：**
- `gradle/libs.versions.toml` — 增加测试依赖
- `app/build.gradle.kts` — 增加 testImplementation
- `di/NetworkModule.kt` — 注入 SessionManager + AuthInterceptor + WarehouseApiService
- `ui/login/LoginUiState.kt` — 新增 prefill 数据类
- `ui/login/LoginViewModel.kt` — 注入 prefs + session，处理记住用户名
- `ui/login/LoginScreen.kt` — 改设计 B + 记住用户名复选框
- `MainActivity.kt` — 接入 HomeScreen + 登出导航

**新增（test）：**
- `app/src/test/java/com/pda/app/SessionManagerTest.kt`
- `app/src/test/java/com/pda/app/WarehouseRepositoryTest.kt`

---

## Task 0: （可选）初始化 git

- [ ] **Step 1: 初始化仓库并首次提交**

Run（工作目录 `PDA\PDA`）：
```bash
git init
git add -A
git commit -m "chore: initial commit before PDA home feature"
```

若选择不使用 git，跳过本任务及后续所有 Commit 步骤。

---

## Task 1: 增加单元测试依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 在 `libs.versions.toml` 的 `[versions]` 末尾增加版本**

```toml
junit = "4.13.2"
coroutinesTest = "1.9.0"
```

- [ ] **Step 2: 在 `[libraries]` 末尾增加库**

```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
```

- [ ] **Step 3: 在 `app/build.gradle.kts` 的 `dependencies { }` 块末尾（`debugImplementation` 行之后）增加**

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 4: 验证 Gradle 同步**

Run: `.\gradlew.bat help`
Expected: BUILD SUCCESSFUL（依赖解析无误）

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add junit and coroutines-test for unit tests"
```

---

## Task 2: 仓库 DTO 与 ApiService

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/data/api/model/WarehouseModels.kt`
- Create: `app/src/main/kotlin/com/pda/app/data/api/WarehouseApiService.kt`

- [ ] **Step 1: 创建 WarehouseDto**

`app/src/main/kotlin/com/pda/app/data/api/model/WarehouseModels.kt`:
```kotlin
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
```

- [ ] **Step 2: 创建 WarehouseApiService**

`app/src/main/kotlin/com/pda/app/data/api/WarehouseApiService.kt`:
```kotlin
package com.pda.app.data.api

import com.pda.app.data.api.model.WarehouseDto
import retrofit2.Response
import retrofit2.http.GET

interface WarehouseApiService {

    /** GET /api/warehouses — 需 JWT，返回按 code 升序的仓库列表。 */
    @GET("api/warehouses")
    suspend fun getWarehouses(): Response<List<WarehouseDto>>
}
```

- [ ] **Step 3: 验证编译**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/data/api/model/WarehouseModels.kt app/src/main/kotlin/com/pda/app/data/api/WarehouseApiService.kt
git commit -m "feat: add warehouse DTO and api service"
```

---

## Task 3: SessionManager（TDD）

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/data/session/SessionManager.kt`
- Test: `app/src/test/java/com/pda/app/SessionManagerTest.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/pda/app/SessionManagerTest.kt`:
```kotlin
package com.pda.app

import com.pda.app.data.api.model.UserInfoDto
import com.pda.app.data.api.model.WarehouseDto
import com.pda.app.data.session.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionManagerTest {

    private val user = UserInfoDto(
        userId = "u1", username = "admin", email = "a@b.com", fullName = "张伟"
    )
    private val warehouse = WarehouseDto(1, "WH01", "深圳总仓", true)

    @Test
    fun `start sets token and user`() {
        val sm = SessionManager()
        sm.start("tok123", user)
        val session = sm.session.value
        assertEquals("tok123", session?.token)
        assertEquals("张伟", session?.user?.fullName)
        assertEquals("tok123", sm.currentToken)
    }

    @Test
    fun `selectWarehouse updates selected warehouse`() {
        val sm = SessionManager()
        sm.start("tok123", user)
        sm.selectWarehouse(warehouse)
        assertEquals(warehouse, sm.session.value?.selectedWarehouse)
    }

    @Test
    fun `clear resets session to null`() {
        val sm = SessionManager()
        sm.start("tok123", user)
        sm.clear()
        assertNull(sm.session.value)
        assertNull(sm.currentToken)
    }

    @Test
    fun `selectWarehouse without session is ignored`() {
        val sm = SessionManager()
        sm.selectWarehouse(warehouse)
        assertNull(sm.session.value)
    }
}
```

> `WarehouseDto` 已在 Task 2 创建，本测试可直接引用。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.pda.app.SessionManagerTest"`
Expected: 编译失败（`SessionManager` 未定义）

- [ ] **Step 3: 实现 SessionManager**

`app/src/main/kotlin/com/pda/app/data/session/SessionManager.kt`:
```kotlin
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.pda.app.SessionManagerTest"`
Expected: PASS（4 tests）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/data/session/SessionManager.kt app/src/test/java/com/pda/app/SessionManagerTest.kt
git commit -m "feat: add in-memory SessionManager"
```

---

## Task 4: WarehouseRepository（TDD）

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/data/repository/WarehouseRepository.kt`
- Test: `app/src/test/java/com/pda/app/WarehouseRepositoryTest.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/pda/app/WarehouseRepositoryTest.kt`:
```kotlin
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
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.pda.app.WarehouseRepositoryTest"`
Expected: 编译失败（`WarehouseRepository` 未定义）

- [ ] **Step 3: 实现 WarehouseRepository**

`app/src/main/kotlin/com/pda/app/data/repository/WarehouseRepository.kt`:
```kotlin
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.pda.app.WarehouseRepositoryTest"`
Expected: PASS（3 tests）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/data/repository/WarehouseRepository.kt app/src/test/java/com/pda/app/WarehouseRepositoryTest.kt
git commit -m "feat: add WarehouseRepository with error mapping"
```

---

## Task 5: AuthInterceptor 与 NetworkModule 接线

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/data/api/AuthInterceptor.kt`
- Modify: `app/src/main/kotlin/com/pda/app/di/NetworkModule.kt`

- [ ] **Step 1: 创建 AuthInterceptor**

`app/src/main/kotlin/com/pda/app/data/api/AuthInterceptor.kt`:
```kotlin
package com.pda.app.data.api

import com.pda.app.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** 会话存在时为请求附带 Authorization: Bearer <token>；登录等公开请求原样放行。 */
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionManager.currentToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
```

- [ ] **Step 2: 修改 NetworkModule —— 注入拦截器与新 ApiService**

在 `di/NetworkModule.kt` 中：

(a) 顶部增加 import：
```kotlin
import com.pda.app.data.api.AuthInterceptor
import com.pda.app.data.api.WarehouseApiService
import com.pda.app.data.session.SessionManager
```

(b) 把 `provideOkHttpClient` 改为接收 `SessionManager` 并加入 `AuthInterceptor`（放在 logging 拦截器之前）：
```kotlin
    @Provides
    @Singleton
    fun provideOkHttpClient(sessionManager: SessionManager): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(sessionManager))
        .addInterceptor(
            HttpLoggingInterceptor { message ->
                Log.d("PDA/OkHttp", message)
            }.apply {
                level = if (BuildConfig.DEBUG)
                    HttpLoggingInterceptor.Level.BODY
                else
                    HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()
```

(c) 在 `provideAuthApiService` 之后增加：
```kotlin
    @Provides
    @Singleton
    fun provideWarehouseApiService(retrofit: Retrofit): WarehouseApiService =
        retrofit.create(WarehouseApiService::class.java)
```

- [ ] **Step 3: 验证编译**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL（Hilt 自动注入 SessionManager，无循环依赖）

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/data/api/AuthInterceptor.kt app/src/main/kotlin/com/pda/app/di/NetworkModule.kt
git commit -m "feat: attach bearer token via AuthInterceptor and provide WarehouseApiService"
```

---

## Task 6: UserPreferences（DataStore）

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/data/prefs/UserPreferences.kt`

> DataStore 读写需 Android 运行时，单测需 instrumentation，本任务不做 TDD，靠编译 + Task 11 手动验证。

- [ ] **Step 1: 实现 UserPreferences**

`app/src/main/kotlin/com/pda/app/data/prefs/UserPreferences.kt`:
```kotlin
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
```

- [ ] **Step 2: 验证编译**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/data/prefs/UserPreferences.kt
git commit -m "feat: add UserPreferences datastore"
```

---

## Task 7: 登录逻辑接入 prefs 与 session

**Files:**
- Modify: `app/src/main/kotlin/com/pda/app/ui/login/LoginUiState.kt`
- Modify: `app/src/main/kotlin/com/pda/app/ui/login/LoginViewModel.kt`

- [ ] **Step 1: 在 LoginUiState.kt 增加 prefill 数据类**

在 `LoginUiState.kt` 末尾追加：
```kotlin
/** 登录界面初始预填值（来自 DataStore）。 */
data class LoginPrefill(
    val username: String,
    val rememberUsername: Boolean
)
```

- [ ] **Step 2: 重写 LoginViewModel**

`app/src/main/kotlin/com/pda/app/ui/login/LoginViewModel.kt` 全量替换为：
```kotlin
package com.pda.app.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pda.app.data.NetworkResult
import com.pda.app.data.prefs.UserPreferences
import com.pda.app.data.repository.AuthRepository
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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private companion object {
        const val TAG = "PDA/LoginViewModel"
    }

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** 一次性预填值；null 表示尚未加载。 */
    private val _prefill = MutableStateFlow<LoginPrefill?>(null)
    val prefill: StateFlow<LoginPrefill?> = _prefill.asStateFlow()

    init {
        viewModelScope.launch {
            val username = userPreferences.lastUsername.first() ?: ""
            val remember = userPreferences.rememberUsername.first()
            _prefill.value = LoginPrefill(username, remember)
        }
    }

    fun login(username: String, password: String, rememberUsername: Boolean) {
        Log.i(TAG, "login: triggered — username=$username, remember=$rememberUsername")
        authRepository.login(username, password)
            .onEach { result ->
                when (result) {
                    is NetworkResult.Loading -> _uiState.value = LoginUiState.Loading
                    is NetworkResult.Success -> {
                        Log.i(TAG, "login success — user=${result.data.user.username}")
                        sessionManager.start(result.data.token, result.data.user)
                        userPreferences.saveUsername(username.trim(), rememberUsername)
                        _uiState.value = LoginUiState.Success(result.data.token, result.data.user)
                    }
                    is NetworkResult.Error -> {
                        Log.w(TAG, "login error — ${result.message}")
                        _uiState.value = LoginUiState.Error(result.message)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `.\gradlew.bat assembleDebug`
Expected: 编译失败 —— `LoginScreen` 仍以旧的 2 参 `login(username, password)` 调用新签名。这是预期的：登录界面在 Task 8 改、整体在 Task 11 才全绿。

> 本任务与 Task 8、9、10 之间存在签名/依赖交叉，**直到 Task 11 才会整体编译通过**。subagent 严格逐任务模式可跳过本 Step 的 assembleDebug，待 Task 11 统一编译。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/login/LoginUiState.kt app/src/main/kotlin/com/pda/app/ui/login/LoginViewModel.kt
git commit -m "feat: login persists username and starts session"
```

---

## Task 8: 登录界面美化（设计 B）+ 记住用户名复选框

**Files:**
- Modify: `app/src/main/kotlin/com/pda/app/ui/login/LoginScreen.kt`

- [ ] **Step 1: 全量替换 LoginScreen.kt**

```kotlin
package com.pda.app.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefill by viewModel.prefill.collectAsStateWithLifecycle()

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberUsername by rememberSaveable { mutableStateOf(true) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val isLoading = uiState is LoginUiState.Loading

    // 一次性应用 DataStore 预填
    LaunchedEffect(prefill) {
        val p = prefill
        if (p != null && !prefilled) {
            username = p.username
            rememberUsername = p.rememberUsername
            prefilled = true
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) onLoginSuccess()
    }

    fun submit() {
        focusManager.clearFocus()
        if (username.isNotBlank() && password.isNotBlank()) {
            viewModel.login(username, password, rememberUsername)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 居中 Logo（设计 B）
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "FBD RMA",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "仓库管理系统",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                viewModel.clearError()
            },
            label = { Text("用户名") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            enabled = !isLoading,
            isError = uiState is LoginUiState.Error,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearError()
            },
            label = { Text("密码") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            enabled = !isLoading,
            isError = uiState is LoginUiState.Error,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )

        // 记住用户名复选框
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberUsername,
                onCheckedChange = { rememberUsername = it },
                enabled = !isLoading
            )
            Text(
                text = "记住用户名",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        AnimatedVisibility(
            visible = uiState is LoginUiState.Error,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = (uiState as? LoginUiState.Error)?.message ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { submit() },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = username.isNotBlank() && password.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = "登 录", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `.\gradlew.bat assembleDebug`
Expected: 编译失败 —— `MainActivity` 仍以 `onLoginSuccess = { _ -> ... }` 单参调用。**Task 11 整体修复**。

> 同 Task 7：subagent 严格模式可推迟到 Task 10 后统一编译。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/login/LoginScreen.kt
git commit -m "feat: redesign login screen (style B) with remember-username checkbox"
```

---

## Task 9: 主界面状态与 ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/ui/home/HomeUiState.kt`
- Create: `app/src/main/kotlin/com/pda/app/ui/home/HomeViewModel.kt`

- [ ] **Step 1: 创建 HomeUiState**

`app/src/main/kotlin/com/pda/app/ui/home/HomeUiState.kt`:
```kotlin
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
```

- [ ] **Step 2: 创建 HomeViewModel**

`app/src/main/kotlin/com/pda/app/ui/home/HomeViewModel.kt`:
```kotlin
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
```

- [ ] **Step 3: 验证编译**

Run: `.\gradlew.bat assembleDebug`
Expected: 编译失败（`MainActivity`/`LoginScreen` 签名交叉仍在）—— 预期，**Task 11 整体修复后全绿**。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/home/HomeUiState.kt app/src/main/kotlin/com/pda/app/ui/home/HomeViewModel.kt
git commit -m "feat: add HomeViewModel and state"
```

---

## Task 10: 主界面 UI（布局 B）

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/ui/home/HomeScreen.kt`

- [ ] **Step 1: 创建 HomeScreen.kt**

```kotlin
package com.pda.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pda.app.data.api.model.WarehouseDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menuExpanded by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定退出登录？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onLogout()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    // 顶栏内的仓库切换器
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(40.dp)
                        ) {
                            TextButton(onClick = { menuExpanded = true }) {
                                Text(
                                    text = uiState.selectedWarehouse?.let { "${it.warehouseCode} · ${it.warehouseName}" }
                                        ?: "选择仓库",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "切换仓库",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        val state = uiState.warehouseState
                        if (state is WarehouseState.Success) {
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                state.warehouses.forEach { wh ->
                                    DropdownMenuItem(
                                        text = { Text("${wh.warehouseCode} · ${wh.warehouseName}") },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.selectWarehouse(wh)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "退出登录"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "你好，${uiState.userFullName}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState.warehouseState) {
                is WarehouseState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is WarehouseState.Error -> {
                    Column {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { viewModel.loadWarehouses() }) { Text("重试") }
                    }
                }
                is WarehouseState.Success -> {
                    if (state.warehouses.isEmpty()) {
                        Text("暂无可用仓库", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 功能磁贴行：Dock Receive + 预留
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    label = "Dock Receive",
                    icon = Icons.Default.MoveToInbox,
                    enabled = true,
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch { snackbarHostState.showSnackbar("功能开发中") }
                }
                Card(modifier = Modifier.weight(1f).height(100.dp)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("预留", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `.\gradlew.bat assembleDebug`
Expected: 编译失败 —— `MainActivity` 仍以单参 `onLoginSuccess` 调用且未引用 `HomeScreen`。预期，Task 11 统一修复。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/home/HomeScreen.kt
git commit -m "feat: add HomeScreen (layout B) with warehouse switcher, dock receive, logout"
```

---

## Task 11: 接入导航并全量编译

**Files:**
- Modify: `app/src/main/kotlin/com/pda/app/MainActivity.kt`

- [ ] **Step 1: 修改 MainActivity 的 NavHost**

把 `MainActivity.kt` 中 `NavHost { ... }` 内两个 composable 替换为：
```kotlin
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
```

并在顶部 import 区增加：
```kotlin
import com.pda.app.ui.home.HomeScreen
```
同时可删除不再使用的 `Box` / `Text` / `Alignment` import（若编译警告，按提示清理；保留无害）。

- [ ] **Step 2: 全量编译**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 跑全部单测**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS（SessionManagerTest 4 + WarehouseRepositoryTest 3）

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/MainActivity.kt
git commit -m "feat: wire HomeScreen into navigation"
```

---

## Task 12: 手动验证（模拟器/真机）

> 需要后端可达：debug 默认 `http://10.0.2.2/`（模拟器→宿主 localhost）。真机请改 `app/build.gradle.kts` 中 `RMA_BASE_URL` 为可达 IP。确保 RMA 后端已运行（见 RMA 项目 `dotnet run --project RmaSystem.Api`）。

- [ ] **Step 1: 安装并启动**

Run: `.\gradlew.bat installDebug`
然后在模拟器打开 App。

- [ ] **Step 2: 验证登录界面**
  - [ ] 居中 Logo + 「FBD RMA / 仓库管理系统」，圆角输入框（设计 B）。
  - [ ] 「记住用户名」复选框默认勾选。
  - [ ] 密码可见切换、加载转圈、错误高亮正常。

- [ ] **Step 3: 验证记住用户名**
  - [ ] 勾选并用 `admin/admin123` 登录成功。
  - [ ] 杀掉 App 重开 → 用户名预填为 `admin`，复选框仍勾选。
  - [ ] 取消勾选再登录 → 重开后用户名为空。

- [ ] **Step 4: 验证主界面**
  - [ ] 顶栏显示当前仓库「编码 · 名称」与登出图标（门+箭头）。
  - [ ] 「你好，{姓名}」显示登录人 fullName。
  - [ ] 点顶栏仓库 → 下拉列表出现，选择后顶栏更新。
  - [ ] 杀掉 App 重新登录 → 选中的仓库被记住。
  - [ ] 点 Dock Receive → 底部弹「功能开发中」snackbar，无跳转。

- [ ] **Step 5: 验证登出**
  - [ ] 点登出图标 → 弹「确定退出登录？」。
  - [ ] 确定 → 回到登录页，且无法返回主界面（返回键不回退到 home）。
  - [ ] 用户名仍预填。

- [ ] **Step 6:（可选）最终提交**

```bash
git add -A
git commit -m "docs: verified PDA home feature end-to-end"
```

---

## 自检对照（Spec → Task 覆盖）

| Spec 要求 | 对应 Task |
|---|---|
| 美化登录（设计 B） | Task 8 |
| 记住用户名复选框 + 持久化 | Task 6, 7, 8 |
| SessionManager（内存会话） | Task 3 |
| AuthInterceptor 附带 Bearer | Task 5 |
| 仓库 DTO/Api/Repository | Task 2, 4 |
| 主界面姓名 + 仓库切换（布局 B） | Task 9, 10 |
| 选中仓库跨重启记住 | Task 6, 9 |
| Dock Receive 仅按钮 + snackbar | Task 10 |
| Logout（确认框 + 本地清除 + 回登录） | Task 9, 10, 11 |
| 错误处理（中文 + 重试 + 空列表） | Task 4, 10 |
| 轻量单测（SessionManager / Repository） | Task 3, 4 |
| 导航接线 | Task 11 |
