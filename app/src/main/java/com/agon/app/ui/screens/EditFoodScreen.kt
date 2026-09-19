package com.agon.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.FoodItem
import com.agon.app.data.HistoryEntry
import com.agon.app.data.copyImageToCovers
import com.agon.app.viewmodel.AppViewModel
import com.agon.app.viewmodel.upsert
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 相机临时原图保留时长：超过就当作「拍完被取消/没走完流程」的残留清掉。 */
private const val CAMERA_TEMP_TTL_MS = 24L * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodScreen(
    viewModel: AppViewModel,
    editId: String?,
    onBack: () -> Unit,
) {
    val existing = remember(editId) { viewModel.items.value.find { it.id == editId } }
    val isEdit = existing != null
    val historyEntries by viewModel.suggestionSource.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val locationPresets by viewModel.locations.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "SNACK") }
    var quantityText by rememberSaveable { mutableStateOf((existing?.quantity ?: 1).toString()) }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: "件") }
    var productionDay by rememberSaveable {
        mutableStateOf(existing?.productionEpochDay ?: LocalDate.now().toEpochDay())
    }
    var shelfLifeText by rememberSaveable { mutableStateOf((existing?.shelfLifeDays ?: 180).toString()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var photoPath by rememberSaveable { mutableStateOf(existing?.photoPath ?: "") }
    var coverText by rememberSaveable { mutableStateOf(existing?.coverText ?: "") }
    var customThresholdEnabled by rememberSaveable {
        mutableStateOf(existing?.expiringThresholdDays != null)
    }
    var customThresholdText by rememberSaveable {
        mutableStateOf((existing?.expiringThresholdDays ?: 7).toString())
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 名称联想：数据源含录入历史 + 库存 + 归档（全部食品）；
    // 空白时展示最近若干条，输入时匹配全部、不截断（LazyRow 可横向滚动）
    val suggestions = remember(name, historyEntries, isEdit) {
        when {
            isEdit -> emptyList()
            name.isBlank() -> historyEntries.take(8) // 最近录入优先
            else -> historyEntries.filter {
                it.name.contains(name.trim(), ignoreCase = true) && it.name != name.trim()
            }
        }
    }

    // ---- Cover photo pickers ----
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val saved = copyImageToCovers(context, uri)
                if (saved != null) photoPath = saved
                else snackbarHostState.showSnackbar("图片保存失败")
            }
        }
    }

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        if (success && uri != null) {
            scope.launch {
                val saved = copyImageToCovers(context, uri)
                if (saved != null) photoPath = saved
                else snackbarHostState.showSnackbar("照片保存失败")
                // 原图已经拷进 covers/（或已失败），临时文件没用了，立刻删掉。
                cameraTempFile?.let { temp -> withContext(Dispatchers.IO) { runCatching { temp.delete() } } }
                cameraTempFile = null
            }
        }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        // 清理残留（2026-09-15）：拍照被取消 / 进程被杀时原图会留在 cacheDir，
        // 此前从不清理（每拍一张留一份）。这里随手清掉超过一天的残留。
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { old ->
            if (old.isFile && now - old.lastModified() > CAMERA_TEMP_TTL_MS) runCatching { old.delete() }
        }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraTempUri = uri
        cameraTempFile = file
        cameraLauncher.launch(uri)
    }

    val production = LocalDate.ofEpochDay(productionDay)
    val shelfLife = shelfLifeText.toIntOrNull() ?: 0
    val expiry = production.plusDays(shelfLife.toLong())

    // 保存：名称为空只标红不提交，否则按当前表单组装 FoodItem 落库并返回。
    // 逻辑留在这里（不跟着按钮搬走）：它要读全部 12 个表单状态与 existing。
    // 原来的 `return@Button` 提前返回改成 if/else，行为等价。
    val onSave: () -> Unit = {
        if (name.isBlank()) {
            nameError = true
        } else {
            val item = FoodItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                category = category,
                quantity = (quantityText.toIntOrNull() ?: 1).coerceAtLeast(0),
                unit = unit,
                productionEpochDay = productionDay,
                shelfLifeDays = shelfLife.coerceAtLeast(1),
                note = note.trim(),
                location = location.trim(),
                photoPath = photoPath,
                coverText = coverText.trim(),
                expiringThresholdDays = if (customThresholdEnabled)
                    (customThresholdText.toIntOrNull() ?: 7).coerceIn(1, 365)
                else null,
            )
            viewModel.upsert(item)
            onBack()
        }
    }

    // 联想条一键回填：点一条历史记录就把它的字段灌进表单（空字段保留现值）。
    // 同样留在这里 —— 它写 9 个状态、横跨七个区块，抽出去参数会爆。
    val onPickSuggestion: (HistoryEntry) -> Unit = { s ->
        name = s.name
        category = s.category
        unit = s.unit
        shelfLifeText = s.shelfLifeDays.toString()
        if (s.location.isNotBlank()) location = s.location
        if (s.coverText.isNotBlank()) coverText = s.coverText
        if (s.note.isNotBlank()) note = s.note
        if (s.expiringThresholdDays != null) {
            customThresholdEnabled = true
            customThresholdText = s.expiringThresholdDays.toString()
        }
        showSuggestions = false
    }

    Scaffold(
        // 键盘避让（2026-09-15）：Android 15+ 强制 edge-to-edge 后 adjustResize 已不再缩窗口，
        // 必须自己消费 IME inset。整屏缩到键盘之上后，底部「保存/添加到零食柜」贴着键盘顶边，
        // 滚动区同步变矮，任意输入框都能滚到可见位置。
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑食品" else "添加食品", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = { EditFoodSaveBar(isEdit = isEdit, onSave = onSave) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            EditFoodCoverSection(
                photoPath = photoPath,
                onPhotoPathChange = { photoPath = it },
                coverText = coverText,
                onCoverTextChange = { coverText = it },
                category = category,
                categories = categories,
                onTakePhoto = { launchCamera() },
                onPickFromGallery = {
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )

            EditFoodNameSection(
                name = name,
                onNameChange = {
                    name = it
                    nameError = false
                    showSuggestions = true
                },
                nameError = nameError,
                isEdit = isEdit,
                suggestions = suggestions,
                categories = categories,
                showSuggestions = showSuggestions,
                onPickSuggestion = onPickSuggestion,
            )

            EditFoodCategorySection(
                category = category,
                onCategoryChange = { category = it },
                categories = categories,
            )

            EditFoodQuantityUnitSection(
                quantityText = quantityText,
                onQuantityTextChange = { quantityText = it },
                unit = unit,
                onUnitChange = { unit = it },
            )

            EditFoodLocationSection(
                location = location,
                onLocationChange = { location = it },
                locationPresets = locationPresets,
            )

            EditFoodProductionDateSection(
                production = production,
                onPickDate = { showDatePicker = true },
            )

            EditFoodShelfLifeSection(
                shelfLifeText = shelfLifeText,
                onShelfLifeTextChange = { shelfLifeText = it },
            )

            EditFoodThresholdSection(
                customThresholdEnabled = customThresholdEnabled,
                onCustomThresholdEnabledChange = { customThresholdEnabled = it },
                customThresholdText = customThresholdText,
                onCustomThresholdTextChange = { customThresholdText = it },
            )

            EditFoodExpirySection(shelfLife = shelfLife, expiry = expiry)

            EditFoodNoteSection(note = note, onNoteChange = { note = it })

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        EditFoodDatePickerDialog(
            production = production,
            onDismiss = { showDatePicker = false },
            onConfirmDay = { day -> productionDay = day },
        )
    }
}
