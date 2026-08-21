package com.agon.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration as MiuixSnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult

/** 撤销 Snackbar 停留时长：点倒计时按钮或滑掉可提前结束。 */
const val UndoSnackbarTimeoutMs = 6_000L

private const val UndoActionLabel = "撤销"

/**
 * Material 3 撤销条：单行正文 + 右侧环形倒计时按钮（点了即撤销）。
 * 不用默认 Snackbar 的 action 槽——在 SwipeToDismissBox 的 Row 里会把「撤销」叠到正文上。
 */
@Composable
fun SwipeDismissSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier.fillMaxWidth()) { data ->
        key(data) {
            val dismissState = rememberSwipeToDismissBoxState()
            LaunchedEffect(dismissState.currentValue) {
                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                    data.dismiss()
                }
            }
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {},
                modifier = Modifier.fillMaxWidth(),
                content = {
                    UndoCountdownSnackbar(
                        data = data,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun UndoCountdownSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }
    val totalSec = (UndoSnackbarTimeoutMs / 1000L).toInt()
    var secondsLeft by remember { mutableIntStateOf(totalSec) }
    LaunchedEffect(data) {
        progress.snapTo(1f)
        secondsLeft = totalSec
        val ring = launch {
            // 倒计时必须匀速；MotionEasing 的强调曲线会让后半段看起来卡住。
            progress.animateTo(
                0f,
                animationSpec = tween(UndoSnackbarTimeoutMs.toInt(), easing = LinearEasing),
            )
        }
        for (s in totalSec downTo 1) {
            secondsLeft = s
            delay(1000)
        }
        ring.join()
    }
    val actionColor = SnackbarDefaults.actionContentColor
    Surface(
        modifier = modifier,
        shape = SnackbarDefaults.shape,
        color = SnackbarDefaults.color,
        contentColor = SnackbarDefaults.contentColor,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.visuals.message,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = { data.performAction() },
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "撤销" },
            ) {
                SnackbarCountdown(
                    progress = { progress.value },
                    secondsLeft = secondsLeft,
                    color = actionColor,
                )
            }
        }
    }
}

/** 环形倒计时按钮：弧在 Canvas 里读 Animatable，数字每秒跳一次。点了即撤销。 */
@Composable
private fun SnackbarCountdown(
    progress: () -> Float,
    secondsLeft: Int,
    color: Color,
) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = color.copy(alpha = 0.24f), style = stroke)
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = -360f * progress(),
                useCenter = false,
                style = stroke,
            )
        }
        Text(
            "$secondsLeft",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * 弹出带「撤销」的 Snackbar，6 秒后自行关掉。
 *
 * 不用 Short/Long：MD3 有 action 时默认 Indefinite；Miuix 虽默认 Short，
 * 但 toMillis 会走无障碍 interactive timeout（HyperOS 上常被拉成永不超时）。
 * 自己 dismiss 才能保证两主题都是 6 秒。
 */
suspend fun SnackbarHostState.showUndoSnackbar(message: String): SnackbarResult =
    coroutineScope {
        val timeout = launch {
            val data = awaitShown { currentSnackbarData }
            if (data == null) return@launch
            delay(UndoSnackbarTimeoutMs)
            data.dismiss()
        }
        try {
            showSnackbar(
                message = message,
                actionLabel = UndoActionLabel,
                duration = SnackbarDuration.Indefinite,
            )
        } finally {
            timeout.cancel()
        }
    }

suspend fun MiuixSnackbarHostState.showUndoSnackbar(message: String): MiuixSnackbarResult =
    coroutineScope {
        val timeout = launch {
            val data = awaitShown { newestSnackbarData() }
            if (data == null) return@launch
            delay(UndoSnackbarTimeoutMs)
            data.dismiss()
        }
        try {
            showSnackbar(
                message = message,
                actionLabel = UndoActionLabel,
                duration = MiuixSnackbarDuration.Indefinite,
            )
        } finally {
            timeout.cancel()
        }
    }

private suspend inline fun <T : Any> awaitShown(crossinline current: suspend () -> T?): T? {
    var data = current()
    var tries = 0
    while (data == null && tries++ < 50) {
        delay(16)
        data = current()
    }
    return data
}
