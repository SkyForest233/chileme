package com.agon.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
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
 * Material 3 撤销条：单行正文 + 右侧 History 撤回环（左侧缺口 + 箭头 + 居中倒计时，点了即撤销）。
 * 不用 History/Replay 矢量：箭头会把圈的视觉中心挤偏，数字看起来不居中。
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
    val totalSec = (UndoSnackbarTimeoutMs / 1000L).toInt()
    var secondsLeft by remember { mutableIntStateOf(totalSec) }
    LaunchedEffect(data) {
        secondsLeft = totalSec
        for (s in totalSec downTo 1) {
            secondsLeft = s
            delay(1000)
        }
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
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.visuals.message,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = snackbarMessageStyle(),
            )
            HistoryCountdownButton(
                secondsLeft = secondsLeft,
                color = actionColor,
                onClick = { data.performAction() },
            )
        }
    }
}

@Composable
private fun snackbarMessageStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

/** 缺口圆环 + 撤回箭头，倒计时数字落在圆心。 */
@Composable
private fun HistoryCountdownButton(
    secondsLeft: Int,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = "撤销"
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            drawUndoRing(color)
        }
        Text(
            "$secondsLeft",
            color = color,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 12.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            maxLines = 1,
        )
    }
}

/**
 * History 那种逆时针撤回环：左侧缺口，9 点位置箭头沿圆周朝下。
 * 开口箭头与圆环同线宽，避免再画成顶上那颗朝外的三角尖。
 * 圆环几何中心就是画布中心，数字才能真正居中。
 */
private fun DrawScope.drawUndoRing(color: Color) {
    val stroke = 2.2.dp.toPx()
    val radius = size.minDimension / 2f - stroke * 1.6f - 1.dp.toPx()
    val cx = size.width / 2f
    val cy = size.height / 2f
    // 尾端约 7:30，逆时针绕到 9 点；缺口在左下，对齐 History。
    val startAngle = 138f
    val sweepAngle = -318f
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Butt),
    )
    val end = Math.toRadians((startAngle + sweepAngle).toDouble())
    val cosE = cos(end).toFloat()
    val sinE = sin(end).toFloat()
    val ex = cx + radius * cosE
    val ey = cy + radius * sinE
    // 9 点处逆时针切线朝下。
    val tx = sinE
    val ty = -cosE
    val tip = Offset(ex + tx * stroke * 0.4f, ey + ty * stroke * 0.4f)
    val wing = 6.2.dp.toPx()
    val ang = Math.toRadians(26.0)
    val ca = cos(ang).toFloat()
    val sa = sin(ang).toFloat()
    val bx = -tx
    val by = -ty
    fun wingPoint(sign: Float): Offset {
        val rx = bx * ca - by * sa * sign
        val ry = bx * sa * sign + by * ca
        return Offset(tip.x + rx * wing, tip.y + ry * wing)
    }
    drawLine(
        color = color,
        start = tip,
        end = wingPoint(1f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = tip,
        end = wingPoint(-1f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
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
