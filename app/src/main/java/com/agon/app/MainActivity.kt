package com.agon.app

// App 入口：Activity 本体 —— 深浅色 / 主题风格分流、启动放行超时（splash）、
// 用 CompositionLocalProvider 下发 LocalThemeStyle 与 LocalToday，然后把渲染交给 MainApp。
//
// 原 MainActivity.kt 有 1,123 行，2026-09-16 起按职责拆到同包（com.agon.app）的兄弟文件：
//   MainApp.kt（App 外壳：状态 + Scaffold + Snackbar 覆盖层）· AppNavGraph.kt（路由入口）
//   AppDialogs.kt（弹窗）· BatchBars.kt（多选批量操作栏）· NavChrome.kt（底栏与 Tab Pager）
// 跨文件复用的顶层声明由 private 放宽为 internal —— Kotlin 顶层 private 是**文件级**作用域，不放宽
// 就看不见；internal 只是模块内可见（app 模块没有第二个消费方，R8 照常裁剪），不是公开 API。
// 代价：detekt 的 UnusedPrivateMember 从此不再覆盖它们。取舍见 devlog/2026-09-16.md「🧭 拆分路线图」。
//
// 硬约定（拆分不得破坏）：rememberNavBackStack + NavDisplay 只在 MainApp 一处；二级页一律走回调
// （navigate / onTabs），**不得**把 backStack 往下传给屏幕；外层 Scaffold 的 contentPadding 刻意不消费。

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.ui.theme.AppPalette
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.LocalToday
import com.agon.app.ui.theme.MiuixRootTheme
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * 启动放行超时：`ready`（DataStore 首发）在此时间内未达成也强制渲染首帧。
 * 见 `onCreate` 中 `contentReady` 的注释——宁可闪一帧，也不能变砖。
 */
private const val READY_TIMEOUT_MS = 3_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 持住 SplashScreen 直到 DataStore 首次发出数据，
        // 避免启动时先用默认绿主题/空内容渲染一帧再闪成真实内容。
        //
        // 用 MutableState 而非普通 var：composition 里读它，超时兜底翻转后要触发重组，
        // 否则 `if (!contentReady) return@setContent` 会一直停在空白帧。
        var contentReady by mutableStateOf(false)
        splash.setKeepOnScreenCondition { !contentReady }
        setContent {
            val viewModel: AppViewModel = viewModel()
            val ready by viewModel.ready.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
            val paletteName by viewModel.palette.collectAsStateWithLifecycle()
            val themeStyleName by viewModel.themeStyle.collectAsStateWithLifecycle()
            // 跨零点刷新：每次回到前台用最新日期提供 LocalToday。
            // 日期未变（同日多次 resume）时值相等，不会触发重组；跨过午夜则值变化，
            // 所有读取 LocalToday 的屏幕（剩余天数/状态/新鲜度）随之刷新。
            var today by remember { mutableStateOf(LocalDate.now()) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
            // 周期性检查：每 30 秒比对一次当前日期，变了就更新 today。
            // 覆盖所有「日期变化」场景（自然跨午夜、手动拨时钟前进/后退、时区变化），
            // 比「一次性睡到下一个午夜」更稳健——后者在时钟被改动后会失效。
            // 与 ON_RESUME 互补（后台跨午夜由后者即时兜底，这里兜前台）。
            LaunchedEffect(Unit) {
                while (true) {
                    val now = LocalDate.now()
                    if (now != today) today = now
                    delay(30_000)
                }
            }
            // 放行条件 = ready（正常路径）或超时兜底。
            // 兜底必不可少：异常/读阻塞会让 ready 永不发射，没有超时就是
            // 「启动画面永久停留、只能杀进程」——比多显示一帧默认主题糟糕得多。
            LaunchedEffect(ready) { if (ready) contentReady = true }
            LaunchedEffect(Unit) {
                delay(READY_TIMEOUT_MS)
                contentReady = true
            }
            if (!contentReady) return@setContent
            val darkTheme = when (darkMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val themeStyle = ThemeStyle.fromName(themeStyleName)
            CompositionLocalProvider(
                LocalThemeStyle provides themeStyle,
                LocalToday provides today,
            ) {
                if (themeStyle == ThemeStyle.MIUIX) {
                    MiuixRootTheme(darkMode = darkMode, dynamicColor = dynamicColor) {
                        MainApp(viewModel)
                    }
                } else {
                    AgonAppTheme(
                        darkTheme = darkTheme,
                        dynamicColor = dynamicColor,
                        palette = AppPalette.fromName(paletteName),
                    ) {
                        MainApp(viewModel)
                    }
                }
            }
        }
    }
}
