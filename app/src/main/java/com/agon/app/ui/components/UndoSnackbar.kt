package com.agon.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 * Material 3 撤销条：单行正文 + 右侧 Replay 图标（中间倒计时数字，点了即撤销）。
 * 不用默认 Snackbar 的 action 槽，也不用 48dp IconButton，避免条被撑高、文字叠在正文上。
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
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.visuals.message,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ReplayCountdownButton(
                secondsLeft = secondsLeft,
                color = actionColor,
                onClick = { data.performAction() },
            )
        }
    }
}

/** Material Replay 图标，中间叠 6 秒倒计时数字。 */
@Composable
private fun ReplayCountdownButton(
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
        Icon(
            Icons.Rounded.Replay,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = color,
        )
        Text(
            "$secondsLeft",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
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
