# Dock Receiving (PDA) — 设计文档

- 日期：2026-06-17
- 范围：PDA Android app 新增 **Dock Receiving** 功能，原生复刻 RMA web 端 dock-receiving 的核心收货流程，调用同一套 RMA .NET 后端接口。
- 状态：已与用户确认，待评审。

## 1. 目标与范围

登录后从 Home 点击 "Dock Receive" 磁贴进入收货流程，三步：

1. **开 Batch**：生成一个批次号（后端自动生成）。
2. **逐个 shipping label**：CameraX 内嵌拍照 → 上传存储 + AI 解析运单号/承运商 → 确认页核对/修改 → 保存为一条 receiving item。
3. **Receive Batch**：录完所有条目后关闭该批次。

### 范围内（第一版）
- 仅核心三步。
- CameraX 内嵌预览拍照，连续扫描。
- 拍照后自动 AI 解析，进确认页核对再保存。
- 批次进行中显示批次号 + 已录入计数 + 条目列表；关批弹框显示总数与需复检数。

### 明确不做（YAGNI，web 端有但第一版不移植）
- 批次历史列表 / 查询 / 恢复未关闭 batch。
- dispatch 派发。
- 手动录入 tab、Excel 导入 / 模板。
- receiving alerts、unit details。

## 2. 复用的后端接口（无需改动后端）

base URL：debug `http://10.0.2.2/`（见 `BuildConfig.RMA_BASE_URL`）。全部需 JWT（沿用 `AuthInterceptor`）。

| 用途 | 方法与路径 | 请求 | 响应 |
|---|---|---|---|
| 开 batch | `POST /api/receiving-batches` | `{ warehouseId }` | `{ receivingBatchId, batchNumber }` |
| 传照片 | `POST /api/dock-receiving-photos` | multipart，字段名 `files` | `{ urls: [string] }` |
| AI 解析 | `POST /api/analyze` | `{ mode: "shipping", photos: [base64] }` | `{ mode, trackingNumber?, carrier?, service?, raw? }` |
| 建条目 | `POST /api/receiving-items` | 见下 | `{ receivingItemId }` |
| 查条目 | `GET /api/receiving-items?batchId={id}` | — | `[ReceivingItem]` |
| 关 batch | `POST /api/receiving-batches/{id}/close` | — | `{ receivingBatchId, status }` |

建条目请求体（对齐 web `ReceivingItemCreateRequest`，第一版用到的字段）：

```
{
  receivingBatchId: Int,
  trackingNumber: String?,   // AI 或手填，trim 后空则不传
  carrier: String?,          // 选自 CARRIERS
  condition: String?,        // 选自 CONDITIONS
  photoPath: String,         // 上传照片返回的 url（必需）
  source: "AI",
  rawJson: String?,          // analyze 返回的 raw
  needsReview: Boolean?      // 未识别到运单号时为 true
}
```

`ReceivingItem` 响应中第一版列表用到的字段：`receivingItemId, trackingNo, carrier, needsReview`（其余由 `ignoreUnknownKeys` 忽略）。

## 3. 架构与分层

沿用项目现有 MVVM + Repository + `NetworkResult` + Hilt + Flow 模式（见 CLAUDE.md 与 `AuthRepository`/`WarehouseRepository`）。

### 数据层 `com.pda.app.data`
- `api/model/ReceivingModels.kt` — `@Serializable` DTO：
  - 请求：`CreateBatchRequest(warehouseId)`、`AnalyzeRequest(mode, photos)`、`CreateItemRequest(...)`
  - 响应：`CreateBatchResponse(receivingBatchId, batchNumber)`、`UploadPhotosResponse(urls)`、`ShippingAnalyzeResponse(mode, trackingNumber, carrier, service, raw)`、`CreateItemResponse(receivingItemId)`、`CloseBatchResponse(receivingBatchId, status)`、`ReceivingItemDto(receivingItemId, trackingNo, carrier, needsReview)`
- `api/ReceivingApiService.kt` — Retrofit 接口，含 6 个端点；上传用 `@Multipart` + `@Part`。
- `repository/ReceivingRepository.kt` — 每个操作返回 `Flow<NetworkResult<T>>`，`flow { emit(Loading); ... }.flowOn(Dispatchers.IO)`，按 **`AuthRepository`** 的方式解析错误体的 `error` 字段、映射 HTTP 码、`try/catch` emit `Error`。DTO → 干净领域模型（如 `ReceivingItemUi`）。**注**：`WarehouseRepository` 未解析 error body，实现时以 `AuthRepository` 为模板，勿抄 `WarehouseRepository`。
- `di/NetworkModule.kt` — 增加 `@Provides @Singleton fun provideReceivingApiService(retrofit)`。

### UI 层 `com.pda.app.ui.dockreceiving`
- `DockReceivingUiState.kt` — UI 状态（见 §5）。
- `DockReceivingViewModel.kt` — `@HiltViewModel @Inject constructor(repo)`，私有 `MutableStateFlow` + `asStateFlow()`；用 `.onEach{}.launchIn(viewModelScope)` 消费 repo flow。**不持有 Context / View / Bitmap**。
- `DockReceivingScreen.kt` — 单屏，状态驱动；内嵌 CameraX 预览；确认页为子状态/弹层。
- `components/` — 小而无状态的 composable：`CameraCapture`（CameraX 预览 + 拍照按钮）、`ItemConfirmSheet`（确认页）、`RecordedItemRow`、`CloseBatchDialog`。

### 工具 `com.pda.app.ui.dockreceiving.util`（或 `com.pda.app.util`）
- `ImageEncoder.kt` — 纯函数：拍照文件 → 降采样 → JPEG → base64（无 data URL 前缀）。参数对齐 web：最长边 `1800`，JPEG 质量 `0.8`，`Base64.NO_WRAP`。**注**：后端 `GeminiAnalyzeService` 对 base64 解码后单张限制 4 MB；当前参数下正常手机照片远低于此值，如日后调大分辨率或质量需重新验证不超限。
- CameraX 封装：优先用 `camera-view` 的 `LifecycleCameraController` 直接在 composable 里绑定，相机选择器固定 `CameraSelector.DEFAULT_BACK_CAMERA`（后置摄像头，对齐 web `facingMode: "environment"`），拍照写入 cache 目录临时文件；可测逻辑（编码、状态流转）放在 ViewModel/ImageEncoder，camera 绑定保持薄。

### 导航 `MainActivity.kt`
- 新增路由 `composable("dock-receiving/{warehouseId}") { ... }`，从 path arg 取 `warehouseId: Int`。
- `HomeScreen` 的 "Dock Receive" 磁贴 `onClick`：若已选仓库则 `navController.navigate("dock-receiving/${wh.id}")`；未选仓库则提示"请先选择仓库"并禁用/拦截。需把 `onNavigateToDockReceiving: (Int) -> Unit` 通过 HomeScreen 参数传入，或在 MainActivity 注入 navController 回调；选中仓库 id 取自 `HomeUiState.selectedWarehouse.id`。
- **当前代码落差**：`HomeScreen` 现在磁贴 `enabled = true` 且 onClick 弹"功能开发中"，实现时按本文档改为未选仓库时禁用、已选仓库时导航，不算文档问题。

> 注：`WarehouseDto` 的主键字段名是 `id`，建 batch 时作为 `warehouseId` 传。

## 4. 数据流（逐步）

**① 开 Batch**：Idle 状态点「开始 Batch」→ `repo.createBatch(warehouseId)` → 成功后保存 `receivingBatchId` + `batchNumber`，切到 Recording 状态，条目列表清空、计数 0。

> **离开行为（第一版）**：用户离开收货页（返回 Home 或退出 app）时不自动关批，服务器上该 batch 保持 Open 状态。再次进入收货页时始终回到 Idle，需重新点「开始 Batch」开一个新批次（**不做未关批次检测/恢复**）。

**② 逐个 label**：
1. Recording 状态内嵌 CameraX 预览，点拍照按钮 → `ImageCapture` 写入 cache 临时文件，得到 `File`/`Uri`。
2. 进入 Confirm 子状态：先由 `ImageEncoder.compress(file)` 生成压缩后的 JPEG 字节/文件（最长边 1800、JPEG 质量 0.8），再**自动**并发触发两件事：

   > **与 web 的差异（有意为之）**：web 是拍照后先上传，由用户手动点「AI 识别」再解析。PDA 第一版进入确认页后自动同时触发上传和 AI 解析，省去一次点击，加快连续扫描节奏。
   - 上传：将**压缩后的 JPEG** `repo.uploadPhoto(compressedFile)` → `photoPath`（取 `urls[0]`）。
   - 解析：将**同一压缩产物**的 base64 → `repo.analyze(base64)` → 运单号/承运商。
   - 同一份压缩结果复用，避免重复压缩，也保证上传内容与 AI 所见一致，并规避后端 10 MB 单文件限制。
3. 确认页展示：照片缩略图、运单号输入框（AI 命中则预填 + 高亮）、承运商下拉（AI 命中则预填 + 高亮）、condition 下拉。`needsReview = trackingNumber.isNullOrBlank()`（确认录入时运单号为空则标记，无论是否跑过 AI）。

   > **与 web 的差异（有意为之）**：web 仅在跑过 OCR 且未识别时才传 `needsReview: true`，未跑 AI 时传 `undefined`（后端默认 false）。PDA 第一版采用更严格的规则：只要确认录入时运单号仍为空，无论原因一律标记 `needsReview = true`，避免无运单号的条目漏检。这是有意差异，不是 bug。
   - **承运商归一化**：AI 返回的承运商字符串（如 `"fedex"`、`"FEDEX"`）先做大小写不敏感匹配 CARRIERS 列表，命中则用列表中的标准写法（如 `"FedEx"`）填入；未命中则原样填入，留给用户手动选正确项。
4. 点「确认录入」→ `repo.createItem(...)`（`photoPath` 必需）→ 成功后立即调用 `repo.getItems(batchId)` 重新拉取服务器列表（**不做本地 append**），以保证 `needsReview` 等字段与服务器一致；拉取完成后回 Recording 状态、更新列表与计数、清空确认页字段。可继续拍下一张。

**③ Receive Batch**：Recording 状态点「Receive Batch」→ 弹 `CloseBatchDialog`（显示总条数 + needsReview 计数）→ 确认 → `repo.closeBatch(batchId)` → 成功后回 Idle 状态（清空批次与列表），Snackbar 提示 `"{batchNumber} 已关闭"`。

## 5. 屏幕状态

`DockReceivingUiState` 建议结构：
- `phase: Idle | Recording | Confirming`（密封类/枚举 + 数据）
- `batchNumber: String?`、`batchId: Int?`
- `items: List<ReceivingItemUi>`、派生 `itemCount`、`needsReviewCount`
- `confirm: ConfirmState?`（photoPath、解析结果、可编辑的 trackingNumber/carrier/condition、各子操作 loading/error）
- 顶层 `loading` / `errorMessage`（一次性事件用 channel/SharedFlow 或 consumable flag → Snackbar）

UI：
- **Idle**：居中「开始 Batch」按钮。
- **Recording**：顶部 batch 号 + 已录入计数；CameraX 预览 + 拍照按钮；下方滚动列表（运单号 / 承运商 / needsReview 角标）；底部「Receive Batch」。
- **Confirming**：照片 + 可编辑字段 + 「确认录入」/「取消」。
- 进行中操作显示 loading 并禁用按钮，结果用 Snackbar（中文）。

## 6. 错误处理

- 统一 `NetworkResult`，失败 → 中文 Snackbar。
- **AI 解析失败**：不阻断，确认页保留空字段供手填运单号（同 web）。
- **照片上传失败**：阻断该条录入（`photoPath` 必需），提示重拍。
- **建 batch / 关 batch 失败**：保持当前状态 + 提示重试。
- **相机权限**：进入录入流程时申请 `CAMERA` 运行时权限；拒绝则显示中文提示，引导去系统设置开启权限（**不提供相册兜底**，保持流程单一）。
- **权限（运维）**：所有收货接口均带 `.RequirePage("dock-receiving")`，账号缺权限返回 403。测试和生产账号须在 RMA 后台权限管理中勾选 **Dock Receiving** 页面，否则所有接口全部 403。建议在测试前确认账号权限，`NetworkResult.Error` 遇到 403 显示中文提示"无权限，请联系管理员"。
- 网络/JWT 过期等沿用现有 `AuthInterceptor` 与 `NetworkResult.Error` 映射。

## 7. 依赖与清单

- `gradle/libs.versions.toml` + `app/build.gradle.kts`：新增 CameraX（`androidx.camera:camera-core`、`camera-camera2`、`camera-lifecycle`、`camera-view`），按版本目录声明、`libs.*` 引用。Retrofit 已有；multipart 用 OkHttp `MultipartBody.Part`。
- `AndroidManifest.xml`：`<uses-permission android:name="android.permission.CAMERA"/>`。
- 复用常量（抄自 web，保持一致）：`CARRIERS = ["UPS","FedEx","USPS","DHL","Amazon","OnTrac","Other"]`、`CONDITIONS = ["Good","Fair","Damaged","Unknown"]`。
- 用户可见字符串一律中文（沿用项目约定）。

## 8. 测试

- `DockReceivingViewModelTest`（fake repository，沿用 `SessionManagerTest` 风格）：开批→状态切到 Recording；录入条目→列表/计数更新；运单号空→`needsReview=true`；关批→重置回 Idle；各失败分支保持状态 + 暴露错误。
- `ReceivingRepositoryTest`（沿用 `WarehouseRepositoryTest` 风格）：DTO 映射、HTTP 错误体 `error` 解析、异常 → `NetworkResult.Error`。
- `ImageEncoderTest`：降采样边界（长边 > / ≤ 1800）、输出为合法 base64、无 data URL 前缀。

## 9. 验收标准

1. 登录后 Home 点 "Dock Receive"（已选仓库）进入收货页。
2. 点「开始 Batch」生成批次号并显示。
3. CameraX 拍 shipping label → 自动解析 → 确认页预填运单号/承运商 → 确认录入，列表与计数更新；连续多张可顺畅录入。
4. AI 未识别运单号的条目标记 needsReview，可手填后保存。
5. 点「Receive Batch」弹框确认（显示总数/需复检数）后批次关闭，回到初始状态。
6. 上述失败路径均有中文提示且不致崩溃。
