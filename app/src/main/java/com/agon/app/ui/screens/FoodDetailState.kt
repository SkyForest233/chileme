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
    val thresholds: Map<String, Int>,
    val showDeleteDialog: Boolean,
    val bounceScale: Animatable<Float, AnimationVector1D>,
    val floatOffset: Animatable<Float, AnimationVector1D>,
    val floatAlpha: Animatable<Float, AnimationVector1D>,
    val burstCount: Int,
    private val viewModel: AppViewModel,
    private val onShowDeleteDialogChanged: (Boolean) -> Unit,
    private val onPlayEatAnimation: () -> Unit,
) {
    fun setShowDeleteDialog(show: Boolean) = onShowDeleteDialogChanged(show)

    fun playEatAnimation() = onPlayEatAnimation()

    fun consumeOne(id: String) {
        viewModel.consumeOne(id)
    }

    fun changeQuantity(id: String, delta: Int) {
        viewModel.changeQuantity(id, delta)
    }

    fun deleteItem(id: String) {
        viewModel.archive(id, ArchiveReason.DELETED)
    }
}

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
        thresholds,
        showDeleteDialog,
        burstCount,
    ) {
        FoodDetailUiState(
            item = item,
            today = today,
            status = status,
            categoryDef = categoryDef,
            thresholds = thresholds,
            showDeleteDialog = showDeleteDialog,
            bounceScale = bounceScale,
            floatOffset = floatOffset,
            floatAlpha = floatAlpha,
            burstCount = burstCount,
            viewModel = viewModel,
            onShowDeleteDialogChanged = { showDeleteDialog = it },
            onPlayEatAnimation = { triggerEatAnimation() },
        )
    }
}
