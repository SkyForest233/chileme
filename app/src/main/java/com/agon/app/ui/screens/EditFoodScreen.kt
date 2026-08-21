package com.agon.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.agon.app.data.FoodItem
import com.agon.app.data.byId
import com.agon.app.data.cn
import com.agon.app.data.copyImageToCovers
import com.agon.app.ui.components.CheckSwitch
import com.agon.app.ui.theme.filterPanelEnter
import com.agon.app.ui.theme.filterPanelExit
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private val unitOptions = listOf("件", "包", "袋", "盒", "瓶", "杯", "桶", "罐")
private val shelfLifePresets = listOf(7, 15, 30, 90, 180, 270, 365)

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
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        if (success && uri != null) {
            scope.launch {
                val saved = copyImageToCovers(context, uri)
                if (saved != null) photoPath = saved
                else snackbarHostState.showSnackbar("照片保存失败")
            }
        }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraTempUri = uri
        cameraLauncher.launch(uri)
    }

    val production = LocalDate.ofEpochDay(productionDay)
    val shelfLife = shelfLifeText.toIntOrNull() ?: 0
    val expiry = production.plusDays(shelfLife.toLong())

    Scaffold(
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
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                            return@Button
                        }
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
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isEdit) "保存修改" else "添加到零食柜",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
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

            // ---- Cover photo ----
            Column {
                Text("封面", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (photoPath.isNotBlank() && File(photoPath).exists()) {
                        Box {
                            AsyncImage(
                                model = File(photoPath),
                                contentDescription = "封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(MaterialTheme.shapes.large),
                            )
                            // IconButton 自带 48dp 最小触摸目标（MD3 无障碍要求）
                            IconButton(
                                onClick = { photoPath = "" },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "移除封面",
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(16.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(88.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    coverText.ifBlank { categories.byId(category).emoji },
                                    style = if (coverText.length > 2) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { launchCamera() },
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("拍照")
                        }
                        OutlinedButton(
                            onClick = {
                                galleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("相册")
                        }
                    }
                    Text(
                        "照片 > 自定义封面\n> 分类 emoji",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = coverText,
                    onValueChange = { coverText = it.take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义封面（可选）") },
                    placeholder = { Text("输入 emoji 或短文字，如 🍓 或 牛奶") },
                    supportingText = { Text("最多 4 个字符；未设置照片时显示它，都不设则显示分类 emoji") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            }

            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                        showSuggestions = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("食品名称") },
                    placeholder = { Text("例如：草莓夹心饼干") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("请输入食品名称") }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                AnimatedVisibility(
                    visible = !isEdit && suggestions.isNotEmpty() && (showSuggestions || name.isBlank()),
                    enter = filterPanelEnter(),
                    exit = filterPanelExit(),
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (name.isBlank()) "最近录入过，点击一键填入全部信息" else "匹配到历史记录，点击一键填入",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions) { s ->
                                AssistChip(
                                    onClick = {
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
                                    },
                                    label = {
                                        Text(
                                            "${s.coverText.ifBlank { categories.byId(s.category).emoji }} ${s.name}"
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                )
                            }
                        }
                    }
                }
            }

            Column {
                Text("分类", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { c ->
                        FilterChip(
                            selected = category == c.id,
                            onClick = { category = c.id },
                            label = { Text("${c.emoji} ${c.label}") },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { ch -> ch.isDigit() }.take(4) },
                    modifier = Modifier.weight(1f),
                    label = { Text("数量") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Column(Modifier.weight(2f)) {
                    Text("单位", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(unitOptions) { u ->
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u },
                                label = { Text(u) },
                                shape = RoundedCornerShape(50),
                            )
                        }
                    }
                }
            }

            // ---- Location ----
            Column {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("存放位置（可选）") },
                    placeholder = { Text("例如：零食柜") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(locationPresets) { loc ->
                        FilterChip(
                            selected = location == loc,
                            onClick = { location = if (location == loc) "" else loc },
                            label = { Text(loc) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        )
                    }
                }
            }

            Column {
                Text("生产日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(production.cn())
                }
            }

            Column {
                OutlinedTextField(
                    value = shelfLifeText,
                    onValueChange = { shelfLifeText = it.filter { ch -> ch.isDigit() }.take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("保质期（天）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shelfLifePresets) { d ->
                        FilterChip(
                            selected = shelfLifeText == d.toString(),
                            onClick = { shelfLifeText = d.toString() },
                            label = { Text(if (d % 30 == 0) "${d / 30}个月" else "${d}天") },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        )
                    }
                }
            }

            // ---- Per-item expiring threshold ----
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "单独设置临期提醒",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "不开启则使用分类默认阈值（可在设置中修改）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CheckSwitch(
                            checked = customThresholdEnabled,
                            onCheckedChange = { customThresholdEnabled = it },
                        )
                    }
                    AnimatedVisibility(
                        visible = customThresholdEnabled,
                        enter = filterPanelEnter(),
                        exit = filterPanelExit(),
                    ) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customThresholdText,
                                onValueChange = { customThresholdText = it.filter { ch -> ch.isDigit() }.take(3) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("提前多少天算临期") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "预计过期日期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (shelfLife > 0) expiry.cn() else "请输入保质期",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注（可选）") },
                placeholder = { Text("例如：放在客厅柜子第二层") },
                minLines = 2,
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = production.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        productionDay = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
