# Dock Receiving (PDA) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native Compose "Dock Receiving" flow to the PDA app — start a batch, photograph each shipping label with an in-app CameraX preview, auto-parse it via the backend AI endpoint, confirm/save each as a receiving item, then close (receive) the batch.

**Architecture:** MVVM + Repository + `NetworkResult` + Hilt + Flow, exactly mirroring the existing `auth`/`home` features. A single state-driven screen (`Idle → Recording → Confirming`) backed by `DockReceivingViewModel`. All six backend endpoints are reused unchanged via a new `ReceivingApiService` + `ReceivingRepository`. Image compression and Base64 live behind an injected `ImageEncoder` interface so the ViewModel stays pure JVM-testable; only the thin Android Bitmap glue is untested by unit tests.

**Tech Stack:** Kotlin 2.0, Jetpack Compose, Hilt (KSP), Retrofit + OkHttp (multipart), Kotlinx Serialization, CameraX (`camera-view` `LifecycleCameraController`), Coroutines/Flow. Spec: [docs/superpowers/specs/2026-06-17-dock-receiving-design.md](../specs/2026-06-17-dock-receiving-design.md).

---

## Conventions for every task

- **Build/test JDK:** the Bash/PowerShell shell has no `JAVA_HOME`. Before any Gradle command run:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  ```
- **Working directory:** `C:\Users\KyleHu\source\repos\PDA\PDA` (the nested Gradle root).
- **Run unit tests:** `.\gradlew.bat testDebugUnitTest`
- **Build:** `.\gradlew.bat assembleDebug`
- **Source set:** new code under `app/src/main/kotlin/com/pda/app/...`; tests under `app/src/test/java/com/pda/app/...` (the existing test source set — package is `com.pda.app`, the folder is `java` but holds Kotlin).
- **User-facing strings:** Chinese.
- **Commits:** the project convention is no auto-commit unless requested. This plan includes commit steps per task; if the operator prefers, batch them — but do commit at task boundaries so work is checkpointed.

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/pda/app/data/api/model/ReceivingModels.kt` — all `@Serializable` request/response DTOs + clean domain models (`BatchInfo`, `ShippingAnalysis`, `ReceivingItemUi`).
- `app/src/main/kotlin/com/pda/app/data/api/ReceivingApiService.kt` — Retrofit interface, 6 endpoints.
- `app/src/main/kotlin/com/pda/app/data/repository/ReceivingRepository.kt` — `Flow<NetworkResult<T>>` per operation, error-body parsing modelled on `AuthRepository`.
- `app/src/main/kotlin/com/pda/app/ui/dockreceiving/ImageScaling.kt` — pure sample-size / scaled-dimension math (JVM-testable).
- `app/src/main/kotlin/com/pda/app/ui/dockreceiving/ImageEncoder.kt` — `ImageEncoder` interface + `CompressedImage` data class.
- `app/src/main/kotlin/com/pda/app/ui/dockreceiving/AndroidImageEncoder.kt` — Android impl (Bitmap glue) + Hilt `@Binds` module.
- `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingConstants.kt` — `CARRIERS`, `CONDITIONS`, `normalizeCarrier()`.
- `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingUiState.kt` — UI state + `Phase` + `ConfirmState`.
- `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingViewModel.kt` — the orchestration.
- `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingScreen.kt` — screen + child composables (`CameraCapture`, `ItemConfirmContent`, `RecordedItemRow`, `CloseBatchDialog`).
- `app/src/test/java/com/pda/app/ReceivingRepositoryTest.kt`
- `app/src/test/java/com/pda/app/ImageScalingTest.kt`
- `app/src/test/java/com/pda/app/DockReceivingConstantsTest.kt`
- `app/src/test/java/com/pda/app/DockReceivingViewModelTest.kt`

**Modify:**
- `gradle/libs.versions.toml` — add CameraX version + library aliases.
- `app/build.gradle.kts` — add CameraX dependencies.
- `app/src/main/AndroidManifest.xml` — add `CAMERA` permission.
- `app/src/main/kotlin/com/pda/app/di/NetworkModule.kt` — provide `ReceivingApiService`.
- `app/src/main/kotlin/com/pda/app/MainActivity.kt` — add `dock-receiving/{warehouseId}` route + pass nav callback to `HomeScreen`.
- `app/src/main/kotlin/com/pda/app/ui/home/HomeScreen.kt` — wire the tile to navigate (enabled only when a warehouse is selected).

### Locked-in type contracts (used across tasks)

Domain models (Task 2):
```kotlin
data class BatchInfo(val batchId: Int, val batchNumber: String)
data class ShippingAnalysis(
    val trackingNumber: String?, val carrier: String?, val service: String?, val raw: String?
)
data class ReceivingItemUi(
    val receivingItemId: Int, val trackingNo: String, val carrier: String, val needsReview: Boolean
)
```

Repository surface (Task 4):
```kotlin
fun createBatch(warehouseId: Int): Flow<NetworkResult<BatchInfo>>
fun uploadPhoto(bytes: ByteArray, filename: String): Flow<NetworkResult<String>>   // returns photoPath
fun analyzeShipping(base64: String): Flow<NetworkResult<ShippingAnalysis>>
fun createItem(req: CreateItemRequest): Flow<NetworkResult<Int>>                    // returns receivingItemId
fun getItems(batchId: Int): Flow<NetworkResult<List<ReceivingItemUi>>>
fun closeBatch(batchId: Int): Flow<NetworkResult<Unit>>
```

Encoder (Task 7):
```kotlin
data class CompressedImage(val bytes: ByteArray, val base64: String)
interface ImageEncoder { suspend fun compress(file: java.io.File): CompressedImage }
```

---

## Task 1: CameraX dependencies + CAMERA permission

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:60-92`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add CameraX version + aliases to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add (after line `coroutinesTest = "1.9.0"`):
```toml
camerax = "1.4.1"
```
Under `[libraries]` add (after the `kotlinx-coroutines-test` line):
```toml
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
```

- [ ] **Step 2: Reference CameraX in the app build file**

In `app/build.gradle.kts`, in the `dependencies { }` block after the DataStore line (`implementation(libs.androidx.datastore.preferences)`), add:
```kotlin
    // CameraX (in-app preview + capture)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
```

- [ ] **Step 3: Add CAMERA permission to the manifest**

In `app/src/main/AndroidManifest.xml`, add immediately before the `<application` tag:
```xml
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

- [ ] **Step 4: Verify the build resolves the new dependencies**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`. (Downloads CameraX artifacts on first run.)

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "build: add CameraX dependencies and CAMERA permission for dock receiving"
```

---

## Task 2: Receiving DTOs, domain models, and constants

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/data/api/model/ReceivingModels.kt`
- Create: `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingConstants.kt`
- Test: `app/src/test/java/com/pda/app/DockReceivingConstantsTest.kt`

- [ ] **Step 1: Write the DTO + domain model file**

Create `app/src/main/kotlin/com/pda/app/data/api/model/ReceivingModels.kt`:
```kotlin
package com.pda.app.data.api.model

import kotlinx.serialization.Serializable

// ── Requests ──────────────────────────────────────────────────────────────────

@Serializable
data class CreateBatchRequest(val warehouseId: Int)

@Serializable
data class AnalyzeRequest(val mode: String, val photos: List<String>)

/**
 * 对齐 web ReceivingItemCreateRequest。可空字段默认 null：在共享 Json（encodeDefaults
 * 默认 false）下，null 字段不会被序列化，等价于 web 的 "trim 后空则不传"。
 */
@Serializable
data class CreateItemRequest(
    val receivingBatchId: Int,
    val trackingNumber: String? = null,
    val carrier: String? = null,
    val condition: String? = null,
    val photoPath: String,
    val source: String = "AI",
    val rawJson: String? = null,
    val needsReview: Boolean? = null
)

// ── Responses ─────────────────────────────────────────────────────────────────

@Serializable
data class CreateBatchResponse(val receivingBatchId: Int, val batchNumber: String)

@Serializable
data class UploadPhotosResponse(val urls: List<String> = emptyList())

@Serializable
data class ShippingAnalyzeResponse(
    val mode: String? = null,
    val trackingNumber: String? = null,
    val carrier: String? = null,
    val service: String? = null,
    val raw: String? = null
)

@Serializable
data class CreateItemResponse(val receivingItemId: Int)

@Serializable
data class CloseBatchResponse(val receivingBatchId: Int, val status: String)

@Serializable
data class ReceivingItemDto(
    val receivingItemId: Int,
    val trackingNo: String? = null,
    val carrier: String? = null,
    val needsReview: Boolean? = null
)

// ── Clean domain models (UI never sees raw DTOs) ────────────────────────────────

data class BatchInfo(val batchId: Int, val batchNumber: String)

data class ShippingAnalysis(
    val trackingNumber: String?,
    val carrier: String?,
    val service: String?,
    val raw: String?
)

data class ReceivingItemUi(
    val receivingItemId: Int,
    val trackingNo: String,
    val carrier: String,
    val needsReview: Boolean
)
```

- [ ] **Step 2: Write the failing test for constants + carrier normalization**

Create `app/src/test/java/com/pda/app/DockReceivingConstantsTest.kt`:
```kotlin
package com.pda.app

import com.pda.app.ui.dockreceiving.CARRIERS
import com.pda.app.ui.dockreceiving.CONDITIONS
import com.pda.app.ui.dockreceiving.normalizeCarrier
import org.junit.Assert.assertEquals
import org.junit.Test

class DockReceivingConstantsTest {

    @Test
    fun `carriers and conditions match web constants`() {
        assertEquals(listOf("UPS", "FedEx", "USPS", "DHL", "Amazon", "OnTrac", "Other"), CARRIERS)
        assertEquals(listOf("Good", "Fair", "Damaged", "Unknown"), CONDITIONS)
    }

    @Test
    fun `normalizeCarrier maps case-insensitively to canonical spelling`() {
        assertEquals("FedEx", normalizeCarrier("fedex"))
        assertEquals("FedEx", normalizeCarrier("FEDEX"))
        assertEquals("UPS", normalizeCarrier("ups"))
    }

    @Test
    fun `normalizeCarrier returns raw value when no match`() {
        assertEquals("LaserShip", normalizeCarrier("LaserShip"))
    }

    @Test
    fun `normalizeCarrier returns empty string for null or blank`() {
        assertEquals("", normalizeCarrier(null))
        assertEquals("", normalizeCarrier("  "))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --tests "com.pda.app.DockReceivingConstantsTest"
```
Expected: FAIL — `CARRIERS`/`CONDITIONS`/`normalizeCarrier` unresolved.

- [ ] **Step 4: Write the constants file**

Create `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingConstants.kt`:
```kotlin
package com.pda.app.ui.dockreceiving

/** 抄自 web constants.ts，保持与 RMA web 端一致。 */
val CARRIERS = listOf("UPS", "FedEx", "USPS", "DHL", "Amazon", "OnTrac", "Other")
val CONDITIONS = listOf("Good", "Fair", "Damaged", "Unknown")

/**
 * 大小写不敏感匹配 CARRIERS，命中返回标准写法；未命中返回原值（trim 后）；
 * null/空白返回 ""。对齐 web PhotoTab 的归一化逻辑。
 */
fun normalizeCarrier(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    return CARRIERS.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
}
```

- [ ] **Step 5: Run the test to verify it passes**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --tests "com.pda.app.DockReceivingConstantsTest"
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/data/api/model/ReceivingModels.kt app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingConstants.kt app/src/test/java/com/pda/app/DockReceivingConstantsTest.kt
git commit -m "feat: add receiving DTOs, domain models, and carrier constants"
```

---

## Task 3: ReceivingApiService (Retrofit interface)

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/data/api/ReceivingApiService.kt`

- [ ] **Step 1: Write the Retrofit interface**

Create `app/src/main/kotlin/com/pda/app/data/api/ReceivingApiService.kt`:
```kotlin
package com.pda.app.data.api

import com.pda.app.data.api.model.AnalyzeRequest
import com.pda.app.data.api.model.CloseBatchResponse
import com.pda.app.data.api.model.CreateBatchRequest
import com.pda.app.data.api.model.CreateBatchResponse
import com.pda.app.data.api.model.CreateItemRequest
import com.pda.app.data.api.model.CreateItemResponse
import com.pda.app.data.api.model.ReceivingItemDto
import com.pda.app.data.api.model.ShippingAnalyzeResponse
import com.pda.app.data.api.model.UploadPhotosResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ReceivingApiService {

    @POST("api/receiving-batches")
    suspend fun createBatch(@Body req: CreateBatchRequest): Response<CreateBatchResponse>

    @Multipart
    @POST("api/dock-receiving-photos")
    suspend fun uploadPhotos(@Part file: MultipartBody.Part): Response<UploadPhotosResponse>

    @POST("api/analyze")
    suspend fun analyze(@Body req: AnalyzeRequest): Response<ShippingAnalyzeResponse>

    @POST("api/receiving-items")
    suspend fun createItem(@Body req: CreateItemRequest): Response<CreateItemResponse>

    @GET("api/receiving-items")
    suspend fun getItems(@Query("batchId") batchId: Int): Response<List<ReceivingItemDto>>

    @POST("api/receiving-batches/{id}/close")
    suspend fun closeBatch(@Path("id") id: Int): Response<CloseBatchResponse>
}
```

> Note: the multipart `@Part` form-field name MUST be `files` (backend reads `form.Files.Where(name == "files")`). That name is set when building the `MultipartBody.Part` in the repository (Task 4), not here.

- [ ] **Step 2: Verify it compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/data/api/ReceivingApiService.kt
git commit -m "feat: add ReceivingApiService Retrofit interface"
```

---

## Task 4: ReceivingRepository + tests (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/data/repository/ReceivingRepository.kt`
- Test: `app/src/test/java/com/pda/app/ReceivingRepositoryTest.kt`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/pda/app/ReceivingRepositoryTest.kt`:
```kotlin
package com.pda.app

import com.pda.app.data.NetworkResult
import com.pda.app.data.api.ReceivingApiService
import com.pda.app.data.api.model.AnalyzeRequest
import com.pda.app.data.api.model.CloseBatchResponse
import com.pda.app.data.api.model.CreateBatchRequest
import com.pda.app.data.api.model.CreateBatchResponse
import com.pda.app.data.api.model.CreateItemRequest
import com.pda.app.data.api.model.CreateItemResponse
import com.pda.app.data.api.model.ReceivingItemDto
import com.pda.app.data.api.model.ShippingAnalyzeResponse
import com.pda.app.data.api.model.UploadPhotosResponse
import com.pda.app.data.repository.ReceivingRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/** 可配置的 fake：每个方法返回预设 Response，未用到的方法抛错。 */
private class FakeReceivingApiService(
    var createBatchResp: Response<CreateBatchResponse>? = null,
    var uploadResp: Response<UploadPhotosResponse>? = null,
    var analyzeResp: Response<ShippingAnalyzeResponse>? = null,
    var createItemResp: Response<CreateItemResponse>? = null,
    var getItemsResp: Response<List<ReceivingItemDto>>? = null,
    var closeResp: Response<CloseBatchResponse>? = null
) : ReceivingApiService {
    override suspend fun createBatch(req: CreateBatchRequest) = createBatchResp!!
    override suspend fun uploadPhotos(file: MultipartBody.Part) = uploadResp!!
    override suspend fun analyze(req: AnalyzeRequest) = analyzeResp!!
    override suspend fun createItem(req: CreateItemRequest) = createItemResp!!
    override suspend fun getItems(batchId: Int) = getItemsResp!!
    override suspend fun closeBatch(id: Int) = closeResp!!
}

private fun jsonBody(s: String) = s.toResponseBody("application/json".toMediaType())

class ReceivingRepositoryTest {

    @Test
    fun `createBatch emits Loading then Success with mapped BatchInfo`() = runTest {
        val api = FakeReceivingApiService(
            createBatchResp = Response.success(CreateBatchResponse(42, "B-2026-001"))
        )
        val repo = ReceivingRepository(api)

        val emissions = repo.createBatch(7).toList()

        assertTrue(emissions[0] is NetworkResult.Loading)
        val success = emissions[1] as NetworkResult.Success
        assertEquals(42, success.data.batchId)
        assertEquals("B-2026-001", success.data.batchNumber)
    }

    @Test
    fun `createBatch parses server error field`() = runTest {
        val api = FakeReceivingApiService(
            createBatchResp = Response.error(400, jsonBody("""{"error":"仓库无效"}"""))
        )
        val repo = ReceivingRepository(api)

        val error = repo.createBatch(7).toList()[1] as NetworkResult.Error
        assertEquals("仓库无效", error.message)
        assertEquals(400, error.code)
    }

    @Test
    fun `createBatch maps 403 to permission message when no error field`() = runTest {
        val api = FakeReceivingApiService(
            createBatchResp = Response.error(403, jsonBody("{}"))
        )
        val repo = ReceivingRepository(api)

        val error = repo.createBatch(7).toList()[1] as NetworkResult.Error
        assertEquals("无权限，请联系管理员", error.message)
    }

    @Test
    fun `uploadPhoto returns first url`() = runTest {
        val api = FakeReceivingApiService(
            uploadResp = Response.success(UploadPhotosResponse(listOf("/api/dock-receiving-photos/abc.jpg")))
        )
        val repo = ReceivingRepository(api)

        val success = repo.uploadPhoto(byteArrayOf(1, 2, 3), "capture.jpg").toList()[1] as NetworkResult.Success
        assertEquals("/api/dock-receiving-photos/abc.jpg", success.data)
    }

    @Test
    fun `uploadPhoto with empty urls is an error`() = runTest {
        val api = FakeReceivingApiService(uploadResp = Response.success(UploadPhotosResponse(emptyList())))
        val repo = ReceivingRepository(api)

        val error = repo.uploadPhoto(byteArrayOf(1), "x.jpg").toList()[1] as NetworkResult.Error
        assertEquals("图片上传失败：未返回有效 URL", error.message)
    }

    @Test
    fun `analyzeShipping maps fields`() = runTest {
        val api = FakeReceivingApiService(
            analyzeResp = Response.success(
                ShippingAnalyzeResponse(mode = "shipping", trackingNumber = "1Z999", carrier = "ups", raw = "{}")
            )
        )
        val repo = ReceivingRepository(api)

        val success = repo.analyzeShipping("base64").toList()[1] as NetworkResult.Success
        assertEquals("1Z999", success.data.trackingNumber)
        assertEquals("ups", success.data.carrier)
        assertEquals("{}", success.data.raw)
    }

    @Test
    fun `getItems maps dtos with null-safe defaults`() = runTest {
        val api = FakeReceivingApiService(
            getItemsResp = Response.success(
                listOf(
                    ReceivingItemDto(1, "1Z999", "FedEx", false),
                    ReceivingItemDto(2, null, null, true)
                )
            )
        )
        val repo = ReceivingRepository(api)

        val success = repo.getItems(42).toList()[1] as NetworkResult.Success
        assertEquals(2, success.data.size)
        assertEquals("1Z999", success.data[0].trackingNo)
        assertEquals("", success.data[1].trackingNo)
        assertEquals("", success.data[1].carrier)
        assertTrue(success.data[1].needsReview)
    }

    @Test
    fun `createItem returns new id`() = runTest {
        val api = FakeReceivingApiService(createItemResp = Response.success(CreateItemResponse(99)))
        val repo = ReceivingRepository(api)

        val req = CreateItemRequest(receivingBatchId = 42, photoPath = "/p.jpg")
        val success = repo.createItem(req).toList()[1] as NetworkResult.Success
        assertEquals(99, success.data)
    }

    @Test
    fun `closeBatch emits Success Unit`() = runTest {
        val api = FakeReceivingApiService(closeResp = Response.success(CloseBatchResponse(42, "Closed")))
        val repo = ReceivingRepository(api)

        val emissions = repo.closeBatch(42).toList()
        assertTrue(emissions[1] is NetworkResult.Success)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --tests "com.pda.app.ReceivingRepositoryTest"
```
Expected: FAIL — `ReceivingRepository` unresolved.

- [ ] **Step 3: Implement the repository**

Create `app/src/main/kotlin/com/pda/app/data/repository/ReceivingRepository.kt`:
```kotlin
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
class ReceivingRepository @Inject constructor(
    private val api: ReceivingApiService
) {
    companion object {
        private const val TAG = "PDA/ReceivingRepository"
    }

    fun createBatch(warehouseId: Int): Flow<NetworkResult<BatchInfo>> = flow {
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

    fun uploadPhoto(bytes: ByteArray, filename: String): Flow<NetworkResult<String>> = flow {
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

    fun analyzeShipping(base64: String): Flow<NetworkResult<ShippingAnalysis>> = flow {
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

    fun createItem(req: CreateItemRequest): Flow<NetworkResult<Int>> = flow {
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

    fun getItems(batchId: Int): Flow<NetworkResult<List<ReceivingItemUi>>> = flow {
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

    fun closeBatch(batchId: Int): Flow<NetworkResult<Unit>> = flow {
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

    /** 解析后端 error 字段（同 AuthRepository），否则按 HTTP 码给中文消息。 */
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

    private companion object Messages {
        const val NETWORK_FAIL = "网络连接失败，请检查网络设置"
    }
}
```

> The `companion object` for `TAG` and the private `Messages` companion cannot both exist — Kotlin allows only one companion. Fix: move `NETWORK_FAIL` into the single `companion object` with `TAG`. Apply this when implementing: keep one `companion object { private const val TAG = ...; private const val NETWORK_FAIL = "网络连接失败，请检查网络设置" }` and delete the `private companion object Messages` block.

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --tests "com.pda.app.ReceivingRepositoryTest"
```
Expected: PASS (all 9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/data/repository/ReceivingRepository.kt app/src/test/java/com/pda/app/ReceivingRepositoryTest.kt
git commit -m "feat: add ReceivingRepository with NetworkResult flows and tests"
```

---

## Task 5: Provide ReceivingApiService in NetworkModule

**Files:**
- Modify: `app/src/main/kotlin/com/pda/app/di/NetworkModule.kt:66-69`

- [ ] **Step 1: Add the provider**

In `app/src/main/kotlin/com/pda/app/di/NetworkModule.kt`, add the import near the other api imports:
```kotlin
import com.pda.app.data.api.ReceivingApiService
```
Then add after `provideWarehouseApiService` (before the closing `}` of the object):
```kotlin
    @Provides
    @Singleton
    fun provideReceivingApiService(retrofit: Retrofit): ReceivingApiService =
        retrofit.create(ReceivingApiService::class.java)
```

- [ ] **Step 2: Verify it compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/di/NetworkModule.kt
git commit -m "feat: provide ReceivingApiService via Hilt NetworkModule"
```

---

## Task 6: ImageScaling pure math + tests (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/ui/dockreceiving/ImageScaling.kt`
- Test: `app/src/test/java/com/pda/app/ImageScalingTest.kt`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/pda/app/ImageScalingTest.kt`:
```kotlin
package com.pda.app

import com.pda.app.ui.dockreceiving.calculateInSampleSize
import com.pda.app.ui.dockreceiving.scaledSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageScalingTest {

    @Test
    fun `inSampleSize is 1 when image already within maxEdge`() {
        assertEquals(1, calculateInSampleSize(1600, 1200, 1800))
    }

    @Test
    fun `inSampleSize grows as powers of two for oversized images`() {
        // 4000px longest, target 1800 -> halve once (2000) still > 1800 -> halve again? 
        // power-of-two largest that keeps dimension >= maxEdge: 4000/2=2000>=1800 ok, 4000/4=1000<1800 stop -> 2
        assertEquals(2, calculateInSampleSize(4000, 3000, 1800))
        assertEquals(4, calculateInSampleSize(8000, 6000, 1800))
    }

    @Test
    fun `scaledSize keeps longest edge at maxEdge and preserves aspect ratio`() {
        val (w, h) = scaledSize(3600, 1800, 1800)
        assertEquals(1800, w)
        assertEquals(900, h)
    }

    @Test
    fun `scaledSize is unchanged when within maxEdge`() {
        val (w, h) = scaledSize(1000, 800, 1800)
        assertEquals(1000, w)
        assertEquals(800, h)
    }

    @Test
    fun `scaledSize handles portrait orientation`() {
        val (w, h) = scaledSize(1200, 3600, 1800)
        assertEquals(600, w)
        assertEquals(1800, h)
    }

    @Test
    fun `scaledSize never returns zero for tiny inputs`() {
        val (w, h) = scaledSize(1, 1, 1800)
        assertTrue(w >= 1 && h >= 1)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --tests "com.pda.app.ImageScalingTest"
```
Expected: FAIL — functions unresolved.

- [ ] **Step 3: Implement the pure math**

Create `app/src/main/kotlin/com/pda/app/ui/dockreceiving/ImageScaling.kt`:
```kotlin
package com.pda.app.ui.dockreceiving

import kotlin.math.max
import kotlin.math.roundToInt

/** 对齐 web imageCompress.ts。 */
const val MAX_EDGE = 1800
const val JPEG_QUALITY = 80  // web 用 0.8；Android Bitmap.compress 用 0..100

/**
 * BitmapFactory.Options.inSampleSize：最大的 2 的幂，使降采样后最长边仍 >= maxEdge，
 * 以便后续精确缩放到 maxEdge 时不放大。纯函数，可单元测试。
 */
fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, maxEdge: Int): Int {
    val longest = max(srcWidth, srcHeight)
    var sample = 1
    while (longest / (sample * 2) >= maxEdge) {
        sample *= 2
    }
    return sample
}

/** 保持宽高比，把最长边缩到 maxEdge（不放大）。返回至少 1×1。 */
fun scaledSize(srcWidth: Int, srcHeight: Int, maxEdge: Int): Pair<Int, Int> {
    val longest = max(srcWidth, srcHeight)
    if (longest <= maxEdge) return srcWidth to srcHeight
    val scale = maxEdge.toDouble() / longest
    val w = max(1, (srcWidth * scale).roundToInt())
    val h = max(1, (srcHeight * scale).roundToInt())
    return w to h
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --tests "com.pda.app.ImageScalingTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/dockreceiving/ImageScaling.kt app/src/test/java/com/pda/app/ImageScalingTest.kt
git commit -m "feat: add pure image scaling math with tests"
```

---

## Task 7: ImageEncoder interface + Android impl + Hilt binding

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/ui/dockreceiving/ImageEncoder.kt`
- Create: `app/src/main/kotlin/com/pda/app/ui/dockreceiving/AndroidImageEncoder.kt`

> No unit test here: the impl is pure Android Bitmap glue (`BitmapFactory`, `Bitmap.compress`) which returns default/null values under plain JUnit. The testable math is already covered in Task 6. Base64 uses `java.util.Base64` (available API 26+, our minSdk) so it works on-device without `android.util.Base64`.

- [ ] **Step 1: Write the interface + data class**

Create `app/src/main/kotlin/com/pda/app/ui/dockreceiving/ImageEncoder.kt`:
```kotlin
package com.pda.app.ui.dockreceiving

import java.io.File

/** 压缩产物：上传用 bytes，AI 解析用 base64（无 data URL 前缀）。 */
data class CompressedImage(val bytes: ByteArray, val base64: String) {
    // ByteArray needs custom equals/hashCode for value semantics (used in tests/state).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompressedImage) return false
        return bytes.contentEquals(other.bytes) && base64 == other.base64
    }
    override fun hashCode(): Int = 31 * bytes.contentHashCode() + base64.hashCode()
}

interface ImageEncoder {
    /** 读取拍照文件 → 降采样 → 缩放至最长边 MAX_EDGE → JPEG(质量 JPEG_QUALITY) → bytes + base64。 */
    suspend fun compress(file: File): CompressedImage
}
```

- [ ] **Step 2: Write the Android implementation + Hilt module**

Create `app/src/main/kotlin/com/pda/app/ui/dockreceiving/AndroidImageEncoder.kt`:
```kotlin
package com.pda.app.ui.dockreceiving

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidImageEncoder @Inject constructor() : ImageEncoder {

    override suspend fun compress(file: File): CompressedImage = withContext(Dispatchers.IO) {
        // 1) bounds-only decode to read source dimensions
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth.coerceAtLeast(1)
        val srcH = bounds.outHeight.coerceAtLeast(1)

        // 2) downsample on decode
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(srcW, srcH, MAX_EDGE)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: throw IllegalStateException("无法读取照片文件")

        // 3) precise scale to MAX_EDGE longest edge
        val (targetW, targetH) = scaledSize(decoded.width, decoded.height, MAX_EDGE)
        val scaled = if (targetW != decoded.width || targetH != decoded.height) {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        } else decoded

        // 4) JPEG encode
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        val bytes = out.toByteArray()
        CompressedImage(bytes = bytes, base64 = Base64.getEncoder().encodeToString(bytes))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageEncoderModule {
    @Binds
    @Singleton
    abstract fun bindImageEncoder(impl: AndroidImageEncoder): ImageEncoder
}
```

- [ ] **Step 3: Verify it compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/dockreceiving/ImageEncoder.kt app/src/main/kotlin/com/pda/app/ui/dockreceiving/AndroidImageEncoder.kt
git commit -m "feat: add ImageEncoder interface and Android impl with Hilt binding"
```

---

## Task 8: DockReceivingUiState

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingUiState.kt`

- [ ] **Step 1: Write the state file**

Create `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingUiState.kt`:
```kotlin
package com.pda.app.ui.dockreceiving

import com.pda.app.data.api.model.ReceivingItemUi
import java.io.File

enum class Phase { Idle, Recording, Confirming }

/** 单条 label 确认页的状态。 */
data class ConfirmState(
    val photoFile: File,
    val uploading: Boolean = true,
    val analyzing: Boolean = true,
    val photoPath: String? = null,        // 上传成功后填入；为 null 时不可保存
    val uploadFailed: Boolean = false,
    val trackingNumber: String = "",
    val carrier: String = "",
    val condition: String = "",
    val rawJson: String? = null,
    val trackingAutoFilled: Boolean = false,
    val carrierAutoFilled: Boolean = false,
    val saving: Boolean = false
) {
    val canSave: Boolean get() = photoPath != null && !uploading && !saving
}

data class DockReceivingUiState(
    val phase: Phase = Phase.Idle,
    val batchId: Int? = null,
    val batchNumber: String? = null,
    val items: List<ReceivingItemUi> = emptyList(),
    val confirm: ConfirmState? = null,
    val isBusy: Boolean = false,          // batch-level op (start/close/refresh) in flight
    val showCloseDialog: Boolean = false,
    val message: String? = null           // one-shot snackbar text; cleared via messageShown()
) {
    val itemCount: Int get() = items.size
    val needsReviewCount: Int get() = items.count { it.needsReview }
}
```

- [ ] **Step 2: Verify it compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingUiState.kt
git commit -m "feat: add DockReceiving UI state model"
```

---

## Task 9: DockReceivingViewModel + tests (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingViewModel.kt`
- Test: `app/src/test/java/com/pda/app/DockReceivingViewModelTest.kt`

This is the orchestration core. Write the tests first.

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/pda/app/DockReceivingViewModelTest.kt`:
```kotlin
package com.pda.app

import androidx.lifecycle.SavedStateHandle
import com.pda.app.data.NetworkResult
import com.pda.app.data.api.model.BatchInfo
import com.pda.app.data.api.model.CreateItemRequest
import com.pda.app.data.api.model.ReceivingItemUi
import com.pda.app.data.api.model.ShippingAnalysis
import com.pda.app.data.repository.ReceivingRepository
import com.pda.app.ui.dockreceiving.CompressedImage
import com.pda.app.ui.dockreceiving.DockReceivingViewModel
import com.pda.app.ui.dockreceiving.ImageEncoder
import com.pda.app.ui.dockreceiving.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ReceivingRepository 是 final class（非 interface）。VM 依赖具体类型，这里用一个可覆写行为的
 * 子类做 fake —— 但 final class 不能继承。改为：VM 依赖通过构造注入，测试用 mock 工厂函数。
 * 为简化，下面用一个持有 lambda 的 fake 子类需要 repository open。
 *
 * 实施要点：把 ReceivingRepository 的方法标记为 open，或抽出接口。本计划选择给类与方法加 open
 * （见 Step 3 实现说明）。
 */
private class FakeReceivingRepository(
    private val api: com.pda.app.data.api.ReceivingApiService = ThrowingApi
) : ReceivingRepository(api) {
    var createBatchFlow: () -> Flow<NetworkResult<BatchInfo>> = { flowOf(NetworkResult.Loading) }
    var uploadFlow: () -> Flow<NetworkResult<String>> = { flowOf(NetworkResult.Loading) }
    var analyzeFlow: () -> Flow<NetworkResult<ShippingAnalysis>> = { flowOf(NetworkResult.Loading) }
    var createItemFlow: () -> Flow<NetworkResult<Int>> = { flowOf(NetworkResult.Success(1)) }
    var getItemsFlow: () -> Flow<NetworkResult<List<ReceivingItemUi>>> = { flowOf(NetworkResult.Success(emptyList())) }
    var closeFlow: () -> Flow<NetworkResult<Unit>> = { flowOf(NetworkResult.Success(Unit)) }
    var lastCreateItemReq: CreateItemRequest? = null

    override fun createBatch(warehouseId: Int) = createBatchFlow()
    override fun uploadPhoto(bytes: ByteArray, filename: String) = uploadFlow()
    override fun analyzeShipping(base64: String) = analyzeFlow()
    override fun createItem(req: CreateItemRequest): Flow<NetworkResult<Int>> {
        lastCreateItemReq = req
        return createItemFlow()
    }
    override fun getItems(batchId: Int) = getItemsFlow()
    override fun closeBatch(batchId: Int) = closeFlow()

    private companion object {
        val ThrowingApi = object : com.pda.app.data.api.ReceivingApiService {
            override suspend fun createBatch(req: com.pda.app.data.api.model.CreateBatchRequest) = error("unused")
            override suspend fun uploadPhotos(file: okhttp3.MultipartBody.Part) = error("unused")
            override suspend fun analyze(req: com.pda.app.data.api.model.AnalyzeRequest) = error("unused")
            override suspend fun createItem(req: CreateItemRequest) = error("unused")
            override suspend fun getItems(batchId: Int) = error("unused")
            override suspend fun closeBatch(id: Int) = error("unused")
        }
    }
}

private class FakeImageEncoder : ImageEncoder {
    override suspend fun compress(file: File) = CompressedImage(byteArrayOf(1, 2, 3), "BASE64")
}

private fun vm(repo: ReceivingRepository, warehouseId: String? = "7"): DockReceivingViewModel =
    DockReceivingViewModel(repo, FakeImageEncoder(), SavedStateHandle(mapOf("warehouseId" to warehouseId)))

class DockReceivingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `startBatch success moves to Recording with batch info`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Loading, NetworkResult.Success(BatchInfo(42, "B-001"))) }
        }
        val vm = vm(repo)

        vm.startBatch()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(Phase.Recording, s.phase)
        assertEquals(42, s.batchId)
        assertEquals("B-001", s.batchNumber)
        assertTrue(s.items.isEmpty())
    }

    @Test
    fun `startBatch failure stays Idle and surfaces message`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Loading, NetworkResult.Error("创建批次失败（500）", 500)) }
        }
        val vm = vm(repo)

        vm.startBatch()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(Phase.Idle, s.phase)
        assertEquals("创建批次失败（500）", s.message)
    }

    @Test
    fun `onPhotoCaptured runs upload and analyze, autofills fields`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Success(BatchInfo(42, "B-001"))) }
            uploadFlow = { flowOf(NetworkResult.Loading, NetworkResult.Success("/p/abc.jpg")) }
            analyzeFlow = {
                flowOf(
                    NetworkResult.Loading,
                    NetworkResult.Success(ShippingAnalysis("1Z999", "fedex", null, "{}"))
                )
            }
        }
        val vm = vm(repo)
        vm.startBatch(); advanceUntilIdle()

        vm.onPhotoCaptured(File("capture.jpg"))
        advanceUntilIdle()

        val c = vm.uiState.value.confirm!!
        assertEquals(Phase.Confirming, vm.uiState.value.phase)
        assertEquals("/p/abc.jpg", c.photoPath)
        assertFalse(c.uploading)
        assertFalse(c.analyzing)
        assertEquals("1Z999", c.trackingNumber)
        assertEquals("FedEx", c.carrier)            // normalized
        assertTrue(c.trackingAutoFilled)
        assertTrue(c.carrierAutoFilled)
        assertEquals("{}", c.rawJson)
    }

    @Test
    fun `analyze failure leaves fields empty but keeps confirm open`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Success(BatchInfo(42, "B-001"))) }
            uploadFlow = { flowOf(NetworkResult.Success("/p/abc.jpg")) }
            analyzeFlow = { flowOf(NetworkResult.Loading, NetworkResult.Error("AI 识别失败", null)) }
        }
        val vm = vm(repo)
        vm.startBatch(); advanceUntilIdle()

        vm.onPhotoCaptured(File("capture.jpg")); advanceUntilIdle()

        val c = vm.uiState.value.confirm!!
        assertEquals("/p/abc.jpg", c.photoPath)
        assertEquals("", c.trackingNumber)
        assertFalse(c.analyzing)
        assertEquals(Phase.Confirming, vm.uiState.value.phase)
    }

    @Test
    fun `upload failure marks uploadFailed and blocks save`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Success(BatchInfo(42, "B-001"))) }
            uploadFlow = { flowOf(NetworkResult.Loading, NetworkResult.Error("图片上传失败", null)) }
            analyzeFlow = { flowOf(NetworkResult.Success(ShippingAnalysis("1Z999", null, null, null))) }
        }
        val vm = vm(repo)
        vm.startBatch(); advanceUntilIdle()

        vm.onPhotoCaptured(File("capture.jpg")); advanceUntilIdle()

        val c = vm.uiState.value.confirm!!
        assertTrue(c.uploadFailed)
        assertNull(c.photoPath)
        assertFalse(c.canSave)
    }

    @Test
    fun `saveItem sends needsReview true when tracking blank, refreshes list`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Success(BatchInfo(42, "B-001"))) }
            uploadFlow = { flowOf(NetworkResult.Success("/p/abc.jpg")) }
            analyzeFlow = { flowOf(NetworkResult.Success(ShippingAnalysis(null, null, null, null))) }
            createItemFlow = { flowOf(NetworkResult.Success(7)) }
            getItemsFlow = { flowOf(NetworkResult.Success(listOf(
                ReceivingItemUi(7, "", "", true)
            ))) }
        }
        val vm = vm(repo)
        vm.startBatch(); advanceUntilIdle()
        vm.onPhotoCaptured(File("capture.jpg")); advanceUntilIdle()

        vm.saveItem(); advanceUntilIdle()

        assertEquals(true, repo.lastCreateItemReq!!.needsReview)
        assertEquals(42, repo.lastCreateItemReq!!.receivingBatchId)
        assertEquals("/p/abc.jpg", repo.lastCreateItemReq!!.photoPath)
        val s = vm.uiState.value
        assertEquals(Phase.Recording, s.phase)        // back to recording after save
        assertNull(s.confirm)
        assertEquals(1, s.itemCount)
        assertEquals(1, s.needsReviewCount)
    }

    @Test
    fun `saveItem sends needsReview false when tracking present`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Success(BatchInfo(42, "B-001"))) }
            uploadFlow = { flowOf(NetworkResult.Success("/p/abc.jpg")) }
            analyzeFlow = { flowOf(NetworkResult.Success(ShippingAnalysis("1Z999", "UPS", null, "{}"))) }
            getItemsFlow = { flowOf(NetworkResult.Success(listOf(ReceivingItemUi(7, "1Z999", "UPS", false)))) }
        }
        val vm = vm(repo)
        vm.startBatch(); advanceUntilIdle()
        vm.onPhotoCaptured(File("capture.jpg")); advanceUntilIdle()

        vm.saveItem(); advanceUntilIdle()

        assertEquals(false, repo.lastCreateItemReq!!.needsReview)
        assertEquals("1Z999", repo.lastCreateItemReq!!.trackingNumber)
    }

    @Test
    fun `cancelConfirm returns to Recording without saving`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Success(BatchInfo(42, "B-001"))) }
            uploadFlow = { flowOf(NetworkResult.Success("/p/abc.jpg")) }
            analyzeFlow = { flowOf(NetworkResult.Success(ShippingAnalysis("1Z999", null, null, null))) }
        }
        val vm = vm(repo)
        vm.startBatch(); advanceUntilIdle()
        vm.onPhotoCaptured(File("capture.jpg")); advanceUntilIdle()

        vm.cancelConfirm()

        assertEquals(Phase.Recording, vm.uiState.value.phase)
        assertNull(vm.uiState.value.confirm)
    }

    @Test
    fun `confirmCloseBatch resets to Idle on success`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Success(BatchInfo(42, "B-001"))) }
            closeFlow = { flowOf(NetworkResult.Loading, NetworkResult.Success(Unit)) }
        }
        val vm = vm(repo)
        vm.startBatch(); advanceUntilIdle()

        vm.requestCloseBatch()
        assertTrue(vm.uiState.value.showCloseDialog)

        vm.confirmCloseBatch(); advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(Phase.Idle, s.phase)
        assertNull(s.batchId)
        assertTrue(s.items.isEmpty())
        assertFalse(s.showCloseDialog)
        assertEquals("B-001 已关闭", s.message)
    }

    @Test
    fun `messageShown clears message`() = runTest {
        val repo = FakeReceivingRepository().apply {
            createBatchFlow = { flowOf(NetworkResult.Error("x", null)) }
        }
        val vm = vm(repo)
        vm.startBatch(); advanceUntilIdle()
        assertEquals("x", vm.uiState.value.message)

        vm.messageShown()
        assertNull(vm.uiState.value.message)
    }
}
```

> **Implementation note for Step 3:** the fake subclasses `ReceivingRepository`, so the class and its six public methods must be `open`. When implementing Task 9, go back and change `class ReceivingRepository` → `open class ReceivingRepository` and prefix each public `fun` with `open` in `ReceivingRepository.kt` (Task 4). This is a deliberate, minimal seam for testability (the codebase has no Mockito). Re-run Task 4's tests after the change to confirm they still pass.

- [ ] **Step 2: Run the tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --tests "com.pda.app.DockReceivingViewModelTest"
```
Expected: FAIL — `DockReceivingViewModel` unresolved (and `ReceivingRepository`/methods not yet `open`).

- [ ] **Step 3: Make ReceivingRepository open, then implement the ViewModel**

First edit `ReceivingRepository.kt` (from Task 4): change `class ReceivingRepository` to `open class ReceivingRepository`, and add `open` to each of the six public functions (`createBatch`, `uploadPhoto`, `analyzeShipping`, `createItem`, `getItems`, `closeBatch`).

Then create `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingViewModel.kt`:
```kotlin
package com.pda.app.ui.dockreceiving

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pda.app.data.NetworkResult
import com.pda.app.data.api.model.CreateItemRequest
import com.pda.app.data.repository.ReceivingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DockReceivingViewModel @Inject constructor(
    private val repo: ReceivingRepository,
    private val encoder: ImageEncoder,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PDA/DockReceivingViewModel"
    }

    private val warehouseId: Int? =
        savedStateHandle.get<String>("warehouseId")?.toIntOrNull()

    private val _uiState = MutableStateFlow(DockReceivingUiState())
    val uiState: StateFlow<DockReceivingUiState> = _uiState.asStateFlow()

    // ── ① Start batch ──────────────────────────────────────────────────────────
    fun startBatch() {
        val wid = warehouseId
        if (wid == null) {
            _uiState.update { it.copy(message = "请先选择仓库") }
            return
        }
        viewModelScope.launch {
            repo.createBatch(wid).collect { result ->
                when (result) {
                    is NetworkResult.Loading -> _uiState.update { it.copy(isBusy = true) }
                    is NetworkResult.Success -> _uiState.update {
                        it.copy(
                            isBusy = false,
                            phase = Phase.Recording,
                            batchId = result.data.batchId,
                            batchNumber = result.data.batchNumber,
                            items = emptyList()
                        )
                    }
                    is NetworkResult.Error -> _uiState.update {
                        it.copy(isBusy = false, message = result.message)
                    }
                }
            }
        }
    }

    // ── ② Per-label: photo captured → compress → upload + analyze ────────────────
    fun onPhotoCaptured(file: File) {
        _uiState.update {
            it.copy(phase = Phase.Confirming, confirm = ConfirmState(photoFile = file))
        }
        viewModelScope.launch {
            val img = try {
                encoder.compress(file)
            } catch (e: Exception) {
                Log.e(TAG, "compress: ${e.message}", e)
                _uiState.update {
                    it.copy(confirm = it.confirm?.copy(uploading = false, analyzing = false, uploadFailed = true),
                        message = "照片处理失败，请重拍")
                }
                return@launch
            }
            launch { runUpload(img.bytes, file.name) }
            launch { runAnalyze(img.base64) }
        }
    }

    private suspend fun runUpload(bytes: ByteArray, filename: String) {
        repo.uploadPhoto(bytes, filename).collect { result ->
            when (result) {
                is NetworkResult.Loading -> {}
                is NetworkResult.Success -> _uiState.update {
                    it.copy(confirm = it.confirm?.copy(uploading = false, photoPath = result.data, uploadFailed = false))
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(confirm = it.confirm?.copy(uploading = false, uploadFailed = true), message = result.message)
                }
            }
        }
    }

    private suspend fun runAnalyze(base64: String) {
        repo.analyzeShipping(base64).collect { result ->
            when (result) {
                is NetworkResult.Loading -> {}
                is NetworkResult.Success -> _uiState.update { state ->
                    val c = state.confirm ?: return@update state
                    val tracking = result.data.trackingNumber.orEmpty()
                    val carrier = normalizeCarrier(result.data.carrier)
                    state.copy(
                        confirm = c.copy(
                            analyzing = false,
                            trackingNumber = if (tracking.isNotBlank()) tracking else c.trackingNumber,
                            carrier = if (carrier.isNotBlank()) carrier else c.carrier,
                            trackingAutoFilled = tracking.isNotBlank(),
                            carrierAutoFilled = carrier.isNotBlank(),
                            rawJson = result.data.raw
                        )
                    )
                }
                is NetworkResult.Error -> _uiState.update {
                    // AI failure does not block; just stop the spinner and let user type.
                    it.copy(confirm = it.confirm?.copy(analyzing = false), message = result.message)
                }
            }
        }
    }

    // ── Confirm-page field edits ─────────────────────────────────────────────────
    fun onTrackingChanged(v: String) =
        _uiState.update { it.copy(confirm = it.confirm?.copy(trackingNumber = v, trackingAutoFilled = false)) }

    fun onCarrierChanged(v: String) =
        _uiState.update { it.copy(confirm = it.confirm?.copy(carrier = v, carrierAutoFilled = false)) }

    fun onConditionChanged(v: String) =
        _uiState.update { it.copy(confirm = it.confirm?.copy(condition = v)) }

    fun cancelConfirm() =
        _uiState.update { it.copy(phase = Phase.Recording, confirm = null) }

    // ── ②c Save item → refresh list from server ─────────────────────────────────
    fun saveItem() {
        val state = _uiState.value
        val c = state.confirm ?: return
        val bid = state.batchId ?: return
        val photoPath = c.photoPath ?: return
        val tracking = c.trackingNumber.trim()
        val req = CreateItemRequest(
            receivingBatchId = bid,
            trackingNumber = tracking.ifBlank { null },
            carrier = c.carrier.ifBlank { null },
            condition = c.condition.ifBlank { null },
            photoPath = photoPath,
            source = "AI",
            rawJson = c.rawJson,
            needsReview = tracking.isBlank()
        )
        _uiState.update { it.copy(confirm = it.confirm?.copy(saving = true)) }
        viewModelScope.launch {
            repo.createItem(req).collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {}
                    is NetworkResult.Success -> {
                        _uiState.update { it.copy(phase = Phase.Recording, confirm = null, message = "已录入") }
                        refreshItems(bid)
                    }
                    is NetworkResult.Error -> _uiState.update {
                        it.copy(confirm = it.confirm?.copy(saving = false), message = result.message)
                    }
                }
            }
        }
    }

    private suspend fun refreshItems(batchId: Int) {
        repo.getItems(batchId).collect { result ->
            if (result is NetworkResult.Success) _uiState.update { it.copy(items = result.data) }
            else if (result is NetworkResult.Error) _uiState.update { it.copy(message = result.message) }
        }
    }

    // ── ③ Close batch ────────────────────────────────────────────────────────────
    fun requestCloseBatch() = _uiState.update { it.copy(showCloseDialog = true) }
    fun dismissCloseDialog() = _uiState.update { it.copy(showCloseDialog = false) }

    fun confirmCloseBatch() {
        val bid = _uiState.value.batchId ?: return
        val number = _uiState.value.batchNumber
        viewModelScope.launch {
            repo.closeBatch(bid).collect { result ->
                when (result) {
                    is NetworkResult.Loading -> _uiState.update { it.copy(isBusy = true) }
                    is NetworkResult.Success -> _uiState.update {
                        DockReceivingUiState(message = "${number ?: "批次"} 已关闭")
                    }
                    is NetworkResult.Error -> _uiState.update {
                        it.copy(isBusy = false, showCloseDialog = false, message = result.message)
                    }
                }
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }
}
```

- [ ] **Step 4: Run the ViewModel tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --tests "com.pda.app.DockReceivingViewModelTest"
```
Expected: PASS (all tests).

- [ ] **Step 5: Re-run the full unit-test suite to confirm Task 4 tests still pass after `open`**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
```
Expected: PASS — all suites green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingViewModel.kt app/src/main/kotlin/com/pda/app/data/repository/ReceivingRepository.kt app/src/test/java/com/pda/app/DockReceivingViewModelTest.kt
git commit -m "feat: add DockReceivingViewModel orchestration with tests"
```

---

## Task 10: DockReceivingScreen + child composables

**Files:**
- Create: `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingScreen.kt`

No unit tests (Compose UI). Verification is the build + the manual checklist in Task 12.

- [ ] **Step 1: Write the screen**

Create `app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingScreen.kt`:
```kotlin
package com.pda.app.ui.dockreceiving

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pda.app.data.api.model.ReceivingItemUi
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockReceivingScreen(
    onBack: () -> Unit,
    viewModel: DockReceivingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (uiState.phase) {
                            Phase.Idle -> "Dock Receive"
                            else -> uiState.batchNumber?.let { "批次 $it · ${uiState.itemCount} 件" } ?: "Dock Receive"
                        }
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState.phase) {
                Phase.Idle -> IdleContent(
                    busy = uiState.isBusy,
                    onStart = viewModel::startBatch
                )
                Phase.Recording -> RecordingContent(
                    state = uiState,
                    onPhotoCaptured = viewModel::onPhotoCaptured,
                    onCloseBatch = viewModel::requestCloseBatch
                )
                Phase.Confirming -> uiState.confirm?.let { confirm ->
                    ItemConfirmContent(
                        confirm = confirm,
                        onTrackingChange = viewModel::onTrackingChanged,
                        onCarrierChange = viewModel::onCarrierChanged,
                        onConditionChange = viewModel::onConditionChanged,
                        onSave = viewModel::saveItem,
                        onCancel = viewModel::cancelConfirm
                    )
                }
            }

            if (uiState.showCloseDialog) {
                CloseBatchDialog(
                    itemCount = uiState.itemCount,
                    needsReviewCount = uiState.needsReviewCount,
                    busy = uiState.isBusy,
                    onConfirm = viewModel::confirmCloseBatch,
                    onDismiss = viewModel::dismissCloseDialog
                )
            }
        }
    }
}

@Composable
private fun IdleContent(busy: Boolean, onStart: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onStart, enabled = !busy, modifier = Modifier.height(56.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("开始 Batch")
        }
    }
}

@Composable
private fun RecordingContent(
    state: DockReceivingUiState,
    onPhotoCaptured: (File) -> Unit,
    onCloseBatch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        CameraCapture(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            onPhotoCaptured = onPhotoCaptured
        )
        Spacer(Modifier.height(12.dp))
        Text("已录入 ${state.itemCount} 件", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.items, key = { it.receivingItemId }) { item -> RecordedItemRow(item) }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onCloseBatch,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Receive Batch") }
    }
}

@Composable
private fun RecordedItemRow(item: ReceivingItemUi) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.trackingNo.ifBlank { "（无运单号）" }, fontWeight = FontWeight.Medium)
            Text(item.carrier.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
        }
        if (item.needsReview) {
            Icon(Icons.Default.Warning, contentDescription = "需复检", tint = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider()
}

@Composable
private fun CameraCapture(
    modifier: Modifier = Modifier,
    onPhotoCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("需要相机权限，请在系统设置中开启后返回", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Column(modifier = modifier) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { capturePhoto(context, controller, onPhotoCaptured) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("拍照")
        }
    }
}

private fun capturePhoto(
    context: Context,
    controller: LifecycleCameraController,
    onPhotoCaptured: (File) -> Unit
) {
    val file = File.createTempFile("capture", ".jpg", context.cacheDir)
    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    controller.takePicture(
        output,
        Executors.newSingleThreadExecutor(),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                onPhotoCaptured(file)
            }
            override fun onError(exception: ImageCaptureException) {
                // Swallow; user can retry. (Logged by CameraX internally.)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemConfirmContent(
    confirm: ConfirmState,
    onTrackingChange: (String) -> Unit,
    onCarrierChange: (String) -> Unit,
    onConditionChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (confirm.uploading || confirm.analyzing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (confirm.uploading) "上传中…" else "AI 识别中…")
            }
            Spacer(Modifier.height(12.dp))
        }
        if (confirm.uploadFailed) {
            Text("照片上传失败，请取消后重拍", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = confirm.trackingNumber,
            onValueChange = onTrackingChange,
            label = { Text("运单号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        DropdownField("承运商", confirm.carrier, CARRIERS, onCarrierChange)
        Spacer(Modifier.height(12.dp))
        DropdownField("Condition", confirm.condition, CONDITIONS, onConditionChange)

        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = onSave,
                enabled = confirm.canSave,
                modifier = Modifier.weight(1f)
            ) {
                if (confirm.saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("确认录入")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onValueChange(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun CloseBatchDialog(
    itemCount: Int,
    needsReviewCount: Int,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("关闭批次") },
        text = {
            Text(
                buildString {
                    append("共 $itemCount 件")
                    if (needsReviewCount > 0) append("，其中 $needsReviewCount 件需复检")
                    append("。确认关闭该批次？")
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !busy) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("确认关闭")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }
    )
}
```

- [ ] **Step 2: Verify it compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`. If `menuAnchor()` is flagged deprecated, that's a warning, not an error — leave it.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/ui/dockreceiving/DockReceivingScreen.kt
git commit -m "feat: add DockReceiving Compose screen with CameraX capture"
```

---

## Task 11: Navigation wiring (MainActivity + Home tile)

**Files:**
- Modify: `app/src/main/kotlin/com/pda/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/pda/app/ui/home/HomeScreen.kt`

- [ ] **Step 1: Add the route in MainActivity**

In `app/src/main/kotlin/com/pda/app/MainActivity.kt`, add imports:
```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.pda.app.ui.dockreceiving.DockReceivingScreen
```
Change the `home` composable to pass a navigation callback, and add the new route. Replace the existing `composable("home") { ... }` block with:
```kotlin
                        composable("home") {
                            HomeScreen(
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                onNavigateToDockReceiving = { warehouseId ->
                                    navController.navigate("dock-receiving/$warehouseId")
                                }
                            )
                        }
                        composable(
                            route = "dock-receiving/{warehouseId}",
                            arguments = listOf(navArgument("warehouseId") { type = NavType.StringType })
                        ) {
                            DockReceivingScreen(onBack = { navController.popBackStack() })
                        }
```

- [ ] **Step 2: Wire the Home tile**

In `app/src/main/kotlin/com/pda/app/ui/home/HomeScreen.kt`:

(a) Change the function signature (line 51-54) to add the callback:
```kotlin
fun HomeScreen(
    onLogout: () -> Unit,
    onNavigateToDockReceiving: (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
```

(b) Replace the `ActionTile(...)` call (lines 164-171) for Dock Receive with selection-aware behavior:
```kotlin
                ActionTile(
                    label = "Dock Receive",
                    icon = Icons.Default.MoveToInbox,
                    enabled = uiState.selectedWarehouse != null,
                    modifier = Modifier.weight(1f)
                ) {
                    val wh = uiState.selectedWarehouse
                    if (wh == null) {
                        scope.launch { snackbarHostState.showSnackbar("请先选择仓库") }
                    } else {
                        onNavigateToDockReceiving(wh.id)
                    }
                }
```

- [ ] **Step 3: Build**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run the full unit-test suite (regression)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
```
Expected: PASS — all suites.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pda/app/MainActivity.kt app/src/main/kotlin/com/pda/app/ui/home/HomeScreen.kt
git commit -m "feat: wire Dock Receive navigation from Home tile"
```

---

## Task 12: Manual end-to-end verification

No code. Confirm the acceptance criteria (spec §9) on a device/emulator with a backend that has the `dock-receiving` endpoints, using an account that has the **Dock Receiving** page permission (otherwise every call returns 403).

- [ ] **Step 1: Install on device/emulator**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
```
> If on a physical PDA (not emulator), the debug `RMA_BASE_URL` of `http://10.0.2.2/` only works for an emulator. For a physical device, temporarily point it at the host's reachable IP in `app/build.gradle.kts` debug `buildConfigField`, or test on an emulator.

- [ ] **Step 2: Walk the flow and tick each acceptance criterion**

- [ ] Login → Home → "Dock Receive" tile enabled (warehouse selected) → tap opens the receiving screen.
- [ ] Tap "开始 Batch" → batch number shown in the top bar, count = 0.
- [ ] Grant camera permission → live preview visible → tap "拍照".
- [ ] Confirm page auto-shows spinner, then pre-fills tracking number / carrier (carrier normalized to canonical spelling), condition selectable.
- [ ] Tap "确认录入" → returns to recording, list shows the item, count = 1. Repeat for a second label → count = 2, smooth.
- [ ] Photograph a label the AI can't read (or cover the tracking) → leave tracking blank → save → item shows the "需复检" warning icon.
- [ ] Tap "Receive Batch" → dialog shows total count + needs-review count → confirm → returns to Idle, snackbar "批次 ... 已关闭".
- [ ] Force an error (e.g. airplane mode during "开始 Batch") → Chinese snackbar, no crash; 403 (account without permission) → "无权限，请联系管理员".

- [ ] **Step 3: Note results**

Record any failures and loop back to the relevant task. If all pass, the feature is complete.

---

## Self-Review Notes (author)

- **Spec coverage:** ① start batch → Task 9 `startBatch` + Task 10 Idle. ② photo → Task 10 `CameraCapture`; compress → Tasks 6/7; upload+analyze auto/concurrent → Task 9 `onPhotoCaptured`; confirm/normalize/needsReview → Task 9 + Task 10; save→refresh from server → Task 9 `saveItem`/`refreshItems`. ③ close → Task 9 `confirmCloseBatch` + Task 10 dialog. Endpoints → Tasks 2/3/4. DI → Tasks 5/7. Nav + tile + leave-behavior (reset to Idle) → Task 11 / state default. Permissions (CAMERA runtime, no gallery fallback; 403 message) → Tasks 1/10/4. Deps/constants/strings → Tasks 1/2. Tests → Tasks 2/4/6/9. All spec sections mapped.
- **Carrier normalization, needsReview-stricter rule, auto-analyze, upload+analyze share one compressed JPEG, getItems refresh** — all explicitly implemented and unit-tested in Task 9 / Task 4.
- **Known seam:** Task 9 makes `ReceivingRepository` `open` for a no-Mockito fake. Called out in-line with a re-run of Task 4 tests.
- **Type consistency:** `ReceivingItemUi`, `BatchInfo`, `ShippingAnalysis`, `CompressedImage`, `ConfirmState`, `Phase`, repository method names, and `CreateItemRequest` fields are identical across Tasks 2/4/8/9/10.
```
