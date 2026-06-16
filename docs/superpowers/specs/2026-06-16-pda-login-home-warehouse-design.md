# PDA — 登录美化 + 主界面（仓库切换 / Dock Receive / Logout）设计

**日期:** 2026-06-16
**状态:** 已确认，待实施计划
**范围:** PDA 安卓 App（`com.pda.app`），单 `app` 模块

## 1. 目标

在现有「仅登录」的 PDA App 上增加四项功能：

1. **美化登录界面**，并按用户勾选记住上次登录的用户名。
2. **登录后的主界面**，显示登录人姓名 + 可切换的仓库列表。
3. 主界面增加一个 **Dock Receive** 按钮（本期仅按钮，无跳转）。
4. 主界面增加 **退出登录（Logout）** 按钮。

## 2. 关键决策（已与用户确认）

| 项 | 决策 |
|---|---|
| 仓库数据源 | 复用 RMA 后端 `GET /api/warehouses`（需 JWT） |
| 登录界面风格 | 方案 B：极简扁平居中（无渐变，高对比，适合仓库强光） |
| 记住用户名 | 通过「记住用户名」**复选框**控制，DataStore 持久化 |
| 主界面布局 | 方案 B：顶栏放仓库下拉 + Logout 图标；下方问候 + 功能磁贴 |
| 选中仓库 | **跨重启记住**（DataStore 持久化 warehouseId） |
| Logout 图标 | Material `Logout`（门+箭头），非关机图标 |
| Logout 行为 | 弹确认框 → 仅本地清除会话 → 回登录页（不调后端） |
| Dock Receive | 仅按钮，点击弹「功能开发中」snackbar，无跳转 |
| 会话架构 | 方案 A：`SessionManager`（内存）+ `AuthInterceptor` + DataStore |
| 自动登录 | **不在本期范围**（token 不持久化，重启需重新登录） |

## 3. 后端契约（来自 RMA 项目）

- **端点:** `GET /api/warehouses`，位于 `secured` 组，需 `Authorization: Bearer <token>`。
  页面权限要求含 `dock-receiving` 等（登录用户具备其一即可读取）。
- **响应:** `WarehouseDto[]`，按 `warehouseCode` 升序。PDA 只取 4 个字段，
  其余靠 `Json { ignoreUnknownKeys = true }`（现有配置）忽略：

  ```jsonc
  { "id": 1, "warehouseCode": "WH01", "warehouseName": "深圳总仓", "isActive": true }
  ```
- **登录端点（已存在）:** `POST /api/auth/login` → `LoginResponse(token, user: UserInfoDto)`。
  `UserInfoDto.fullName` 用作主界面显示的登录人姓名。

## 4. 架构与组件

遵循现有约定：包结构 feature-by-package；源码在 `app/src/main/kotlin`，包根 `com.pda.app`；
Repository 返回 `Flow<NetworkResult<T>>` 并 `.flowOn(Dispatchers.IO)`；DTO 用 `@Serializable`；
Hilt DI；日志 TAG 形如 `"PDA/<ClassName>"`；面向用户文案用中文。

### 4.1 会话与鉴权基础设施（新增，跨切面）

**`com.pda.app.data.session.SessionManager`** — `@Singleton`，`@Inject constructor()`
- 内存持有 `StateFlow<Session?>`，`Session = (token: String, user: UserInfoDto, selectedWarehouse: WarehouseDto?)`。
- 方法：`start(token, user)`、`selectWarehouse(w: WarehouseDto)`、`clear()`。
- 不持久化；进程结束即丢失（符合「无自动登录」决策）。
- 提供 `currentToken: String?` 供拦截器读取。

**`com.pda.app.data.api.AuthInterceptor`** — `okhttp3.Interceptor`
- 从 `SessionManager` 读取 token；非空时为请求加 `Authorization: Bearer <token>` 头。
- 会话为空（如登录请求）时原样放行。
- 在 `NetworkModule.provideOkHttpClient` 中注册（与现有 `HttpLoggingInterceptor` 并存）。

**`com.pda.app.data.prefs.UserPreferences`** — DataStore Preferences 封装，`@Singleton`
- 键：`last_username: String?`、`remember_username: Boolean`（默认 true）、`selected_warehouse_id: Int?`。
- 暴露读取（`Flow` 或 suspend getter）与写入 suspend 方法。
- DataStore 依赖已在 `app/build.gradle.kts` 中。

> NetworkModule 调整：`provideOkHttpClient` 增加 `SessionManager` 参数并加入 `AuthInterceptor`。
> 因 `SessionManager` 无构造依赖，Hilt 可直接注入，无循环依赖。

### 4.2 仓库数据层（新增）

- **`data/api/model/WarehouseModels.kt`** — `@Serializable data class WarehouseDto(val id: Int, val warehouseCode: String, val warehouseName: String, val isActive: Boolean)`。
- **`data/api/WarehouseApiService.kt`** — `@GET("api/warehouses") suspend fun getWarehouses(): Response<List<WarehouseDto>>`。
- **`data/repository/WarehouseRepository.kt`** — `@Singleton`，注入 `WarehouseApiService`；
  `fun getWarehouses(): Flow<NetworkResult<List<WarehouseDto>>>`，结构参照现有 `AuthRepository`
  （emit `Loading` → 调 API → 判 `isSuccessful` → 错误体解析 / HTTP 码映射中文 → `try/catch` emit `Error`）→ `.flowOn(Dispatchers.IO)`。
- **NetworkModule** 增加 `provideWarehouseApiService(retrofit)`。

### 4.3 登录功能（修改）

- **`LoginScreen`**：改为设计 B（居中 Logo + 表单，无渐变）；密码字段下方增加「记住用户名」复选框；
  从 `UserPreferences` 预填用户名与复选框状态。保留：密码可见切换、加载转圈、错误高亮。
- **`LoginViewModel`**：注入 `UserPreferences` + `SessionManager`。
  - 初始化时读取 `lastUsername` / `rememberUsername` 暴露给界面预填。
  - 登录成功：`SessionManager.start(token, user)`；按复选框 `rememberUsername` 保存或清除 `lastUsername`，并保存复选框状态。
  - `LoginUiState.Success` 仍可携带数据，但导航回调简化为无参 `onLoginSuccess: () -> Unit`（token 改由 session 持有）。

### 4.4 主界面功能（新增）`ui/home/`

- **`HomeUiState`**：`data class HomeUiState(val userFullName: String, val warehouseState: WarehouseState, val selectedWarehouse: WarehouseDto?)`；
  `sealed interface WarehouseState { Loading; Success(list); Error(message) }`。
- **`HomeViewModel`**：`@HiltViewModel`，注入 `SessionManager` + `WarehouseRepository` + `UserPreferences`。
  - 从 session 读取 `user.fullName`。
  - init 加载仓库列表（`onEach{}.launchIn(viewModelScope)`）。
  - 默认选中：`UserPreferences.selectedWarehouseId` 命中列表则用之，否则取首个 `isActive` 仓库；并同步进 `SessionManager.selectWarehouse(...)`。
  - `selectWarehouse(w)`：更新 session + state，写入 `UserPreferences.selectedWarehouseId`。
  - `retry()`：重新加载仓库。
  - `logout()`：`SessionManager.clear()`（保留 `lastUsername`）。
- **`HomeScreen`**（布局 B）：
  - 顶栏：`ExposedDropdownMenuBox` 仓库切换器（显示「编码 · 名称」）+ Logout 图标按钮（`Icons.AutoMirrored.Filled.Logout`）。
  - Logout 图标 → 弹 `AlertDialog`「确定退出登录？」→ 确认触发 `onLogout`。
  - 问候行：「你好，{fullName}」。
  - 功能磁贴（2 列网格）：**Dock Receive** 磁贴（onClick → `SnackbarHost` 显示「功能开发中」）+ 1 个预留磁贴（禁用样式）。
  - 仓库加载中显示进度；`Error` 显示中文消息 + 重试按钮；空列表显示「暂无可用仓库」。

### 4.5 导航（修改 `MainActivity`）

- `login` 目的地：`onLoginSuccess = { navController.navigate("home"){ popUpTo("login"){ inclusive = true } } }`。
- `home` 目的地：用 `HomeScreen(onLogout = { navController.navigate("login"){ popUpTo("home"){ inclusive = true } } })` 替换占位 `Box`。

## 5. 数据流

1. 登录成功 → `LoginViewModel` 写入 `SessionManager`，按复选框存/清用户名 → 界面导航至 home。
2. Home 初始化 → 从 `SessionManager` 取姓名 → 调 `WarehouseRepository.getWarehouses()`（`AuthInterceptor` 自动加 Bearer）→ 渲染列表 → 按 DataStore 恢复或默认选中。
3. 切换仓库 → 更新 `SessionManager` + state + DataStore。
4. 退出登录 → 确认 → `SessionManager.clear()` → 导航回登录页（用户名仍预填）。

## 6. 错误处理

- 仓库加载失败（网络 / 401 / 403）→ `WarehouseState.Error(中文消息)` + 重试按钮，复用 `NetworkResult` 的 HTTP 码→中文映射。
- 空列表 → 「暂无可用仓库」。
- 沿用现有日志规范 `"PDA/SessionManager"`、`"PDA/WarehouseRepository"`、`"PDA/HomeViewModel"`。

## 7. 测试（轻量，遵循现仓约定）

- `SessionManager`：`start` / `clear` / `selectWarehouse` 状态流转单测。
- `WarehouseRepository`：成功映射 + 错误码→中文消息映射单测。
- UI 测试不在本期强制范围。

## 8. 非目标（YAGNI）

- 自动登录 / token 持久化。
- Dock Receive 实际收货业务（扫码、收货单等）。
- 仓库的多级层级（Zone/Aisle/Bin）选择。
- 后端登出接口。

## 9. 受影响 / 新增文件清单

**修改：**
- `app/src/main/kotlin/com/pda/app/MainActivity.kt`
- `app/src/main/kotlin/com/pda/app/di/NetworkModule.kt`
- `app/src/main/kotlin/com/pda/app/ui/login/LoginScreen.kt`
- `app/src/main/kotlin/com/pda/app/ui/login/LoginViewModel.kt`
- `app/src/main/kotlin/com/pda/app/ui/login/LoginUiState.kt`（如需）

**新增：**
- `data/session/SessionManager.kt`
- `data/api/AuthInterceptor.kt`
- `data/prefs/UserPreferences.kt`
- `data/api/model/WarehouseModels.kt`
- `data/api/WarehouseApiService.kt`
- `data/repository/WarehouseRepository.kt`
- `ui/home/HomeUiState.kt`
- `ui/home/HomeViewModel.kt`
- `ui/home/HomeScreen.kt`
