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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
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
 * Material 3 撤销条：单行正文 + 右侧 History 圆环（去指针，数字居中，点了即撤销）。
 * 不用 Icons.Rounded.History：指针还在，且左边箭头算进 bounds，圈会挤偏。
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

/** Material Rounded History 圆环（无指针）。圆心 (13,12)，24 视口。 */
private const val HistoryRingPath =
    "M13.26,3C8.17,2.86,4,6.95,4,12H1l4,4,4-4H6c0-3.87,3.13-7,7-7s7,3.13,7,7-3.13,7-7,7c-1.93,0-3.68-.79-4.94-2.06l-1.42,1.42C8.27,19.99,10.51,21,13,21c4.97,0,9-4.03,9-9,0-5.11-4.21-9.17-8.74-9z"

private const val HistoryRingCx = 13f
private const val HistoryRingCy = 12f

/** History 圆环去指针，倒计时数字落在圆心。 */
@Composable
private fun HistoryCountdownButton(
    secondsLeft: Int,
    color: Color,
    onClick: () -> Unit,
) {
    val ringPath = remember {
        PathParser().parsePathString(HistoryRingPath).toPath()
    }
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
            val s = size.minDimension / 24f * 0.92f
            translate(size.width / 2f, size.height / 2f) {
                scale(s, pivot = Offset.Zero) {
                    translate(-HistoryRingCx, -HistoryRingCy) {
                        drawPath(ringPath, color)
                    }
                }
            }
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

/**
 * 主壳覆盖层的跨主题撤销条：按 [isMiuix] 挑宿主弹条，只把「用户点没点撤销」这一个 Boolean 交回调用方。
 *
 * **为什么不让主壳直接用二级页那个 `AppSnackbarHostState` 容器**（`ui/components/app/AppChrome.kt`）：
 * 那容器是 `remember(isMiuix)` 建的，切主题会换一个**新**容器与两个**新**宿主；而主壳的收集协程是
 * `LaunchedEffect(Unit)` —— key 一变协程就被取消、`showSnackbar` 被中断（这正是当年 MD3 撤销条不出现的
 * 根因），所以主壳这两个宿主的身份必须跨主题稳定（`remember {}` 无 key）。若换成带 key 的容器，
 * `Unit` 协程捕获的还是**旧**容器 ⇒ 提示会弹到没有渲染的宿主上，而 `showSnackbar` 挂起到关闭为止
 * ⇒ 那条协程永久堵住，之后所有撤销条都不再出现。于是分流只能收在这个自由函数里：
 * 宿主身份不变、effect key 不变，三处重复的 if/else 收成一处。
 *
 * 两主题的 `SnackbarResult` 枚举到此为止，不再往 `MainApp` 泄漏（`docs/ARCHITECTURE.md:157` 的既有约束：
 * 跨主题宿主对象不得漏回屏幕层）。`internal` = 只给本模块用，不进公开 API 面。
 */
internal suspend fun showUndoSnackbarAcrossThemes(
    isMiuix: Boolean,
    md3Host: SnackbarHostState,
    miuixHost: MiuixSnackbarHostState,
    message: String,
): Boolean =
    if (isMiuix) {
        miuixHost.showUndoSnackbar(message) == MiuixSnackbarResult.ActionPerformed
    } else {
        md3Host.showUndoSnackbar(message) == SnackbarResult.ActionPerformed
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
