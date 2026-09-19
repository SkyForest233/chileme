/**
 * 统计页自用的两个图表件（09-19 #11d ① 从 `ui/screens/StatsScreen.kt` 下沉到组件层）。
 *
 * 为什么不在屏幕文件里：`DonutChart` 是 Canvas 自绘 + `animateFloatAsState`，`LegendRow` 是它的图例行，
 * 两者都不含统计口径（口径在 `StatsState.kt`）、也不含主题分支 —— 它们是**呈现层组件**，
 * 与同目录的 `ExpiryCalendar.kt`（同样自绘、同样与主题无关）是同一类东西，住哪儿照它对齐。
 *
 * 留在屏幕文件里的代价很具体：`StatsScreen` 本体 409 行里有 66 行是这两个件；
 * 下一个想复用同一个环图的视图（比如按位置统计）最省事的做法就是再抄一遍 Canvas。
 *
 * ⚠️ 不放 `ui/components/app/`：那层是**双主题骨架**，这两个件本来就没有主题分支；
 * 放进去还会被 `ComponentAppHomeTest` 那张「App 级组件登记表」要求登记，而它不是 App 级件。
 */
package com.agon.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.data.CategoryDef
import com.agon.app.ui.components.app.AppMutedText
import com.agon.app.ui.components.app.AppText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.theme.MotionEasing

/**
 * 库存分类占比的环图：`Canvas` 自绘，800ms 扫出动画，中心叠总件数。
 *
 * 合并前两版**除了中心两行文字的样式以外逐字相同**（连 `Stroke(width = 30f)`、`topLeft = Offset(15f, 15f)`、
 * 每段之间留 3° 缝隙、最小 1° 的兜底都一样），所以两份并成一份：中心大字走 [AppTextScale.Hero]
 * （MD3 `headlineSmall` / Miuix `title2`）、小字走 [AppTextScale.Tag] + 弱化色
 * （MD3 `labelSmall` + `onSurfaceVariant` / Miuix `footnote2` + `onSurfaceVariantSummary`）。
 * 弧色由调用方传（`appChartColors` 的两份清单不同，见其 KDoc）。
 */
@Composable
internal fun DonutChart(
    data: List<Float>,
    colors: List<Color>,
    centerLabel: String,
    centerSub: String,
) {
    val total = data.sum().coerceAtLeast(0.001f)
    val sweep = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = MotionEasing.EmphasizedDecelerate),
        label = "donut",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val stroke = Stroke(width = 30f)
            var startAngle = -90f
            val sweepValue = sweep.value
            data.forEachIndexed { i, value ->
                val angle = value / total * 360f * sweepValue
                drawArc(
                    color = colors[i],
                    startAngle = startAngle,
                    sweepAngle = (angle - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    style = stroke,
                    topLeft = Offset(15f, 15f),
                    size = Size(size.width - 30f, size.height - 30f),
                )
                startAngle += angle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppText(centerLabel, AppTextScale.Hero, fontWeight = FontWeight.ExtraBold)
            AppMutedText(centerSub, AppTextScale.Tag)
        }
    }
}

/**
 * 环图的图例一行：色点 + 「emoji 分类名」+「N 件 · P%」。
 * 两版只有文字样式不同（都是 MD3 `bodyMedium` / Miuix `body2` = [AppTextScale.Meta] 档），故并成一份。
 */
@Composable
internal fun LegendRow(color: Color, category: CategoryDef, qty: Int, percent: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        AppText(
            "${category.emoji} ${category.label}",
            AppTextScale.Meta,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        AppMutedText("$qty 件 · $percent%", AppTextScale.Meta, maxLines = 1)
    }
}
