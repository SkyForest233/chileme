package com.agon.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchiveReason
import com.agon.app.data.CategoryDef
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import com.agon.app.data.byId
import com.agon.app.data.statusForAt
import com.agon.app.ui.theme.LocalToday
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 食品详情页跨主题共享状态容器。
 */
class FoodDetailUiState(
    val item: FoodItem?,
    val today: LocalDate,
    val status: FoodStatus,
    val categoryDef: CategoryDef,
    val showDeleteDialog: Boolean,
    val onShowDeleteDialogChange: (Boolean) -> Unit,
    val bounceScale: Animatable<Float, AnimationVector1D>,
    val floatOffset: Animatable<Float, AnimationVector1D>,
    val floatAlpha: Animatable<Float, AnimationVector1D>,
    val burstCount: Int,
    val playEatAnimation: () -> Unit,
    val onConsumeOne: (onAutoArchived: () -> Unit) -> Unit,
    val onChangeQuantity: (delta: Int, onAutoArchived: () -> Unit) -> Unit,
    val onDeleteItem: () -> Unit,
)

@Composable
fun rememberFoodDetailUiState(
    viewModel: AppViewModel,
    itemId: String,
): FoodDetailUiState {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val item = items.find { it.id == itemId }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val bounceScale = remember { Animatable(1f) }
    val floatOffset = remember { Animatable(0f) }
    val floatAlpha = remember { Animatable(0f) }
    var burstCount by remember { mutableIntStateOf(0) }

    val today = LocalToday.current
    val status = item?.statusForAt(today, thresholds) ?: FoodStatus.SAFE
    val categoryDef = item?.let { categories.byId(it.category) } ?: categories.firstOrNull() ?: CategoryDef("OTHER", "其他", "🧺")

    fun triggerEatAnimation() {
        burstCount++
        scope.launch {
            bounceScale.snapTo(1f)
            bounceScale.animateTo(1.25f, tween(120, easing = MotionEasing.EmphasizedDecelerate))
            bounceScale.animateTo(1f, tween(220, easing = MotionEasing.Emphasized))
        }
        scope.launch {
            floatOffset.snapTo(0f)
            floatAlpha.snapTo(1f)
            launch { floatOffset.animateTo(-72f, tween(700, easing = MotionEasing.EmphasizedDecelerate)) }
            floatAlpha.animateTo(0f, tween(700, easing = MotionEasing.Standard))
        }
    }

    return remember(
        item,
        today,
        status,
        categoryDef,
        showDeleteDialog,
        burstCount,
    ) {
        FoodDetailUiState(
            item = item,
            today = today,
            status = status,
            categoryDef = categoryDef,
            showDeleteDialog = showDeleteDialog,
            onShowDeleteDialogChange = { showDeleteDialog = it },
            bounceScale = bounceScale,
            floatOffset = floatOffset,
            floatAlpha = floatAlpha,
            burstCount = burstCount,
            playEatAnimation = { triggerEatAnimation() },
            onConsumeOne = { onAutoArchived ->
                item?.let { viewModel.consumeOne(it.id, onAutoArchived) }
            },
            onChangeQuantity = { delta, onAutoArchived ->
                item?.let { viewModel.changeQuantity(it.id, delta, onAutoArchived) }
            },
            onDeleteItem = {
                item?.let { viewModel.archive(it.id, ArchiveReason.DELETED) }
            },
        )
    }
}
