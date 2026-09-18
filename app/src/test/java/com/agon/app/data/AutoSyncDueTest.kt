package com.agon.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 自动同步的**间隔边界**（路线图 #5b，2026-09-18）。
 *
 * 这段判定原先长在 `AppViewModel.maybeAutoSync()` 里（写作 `if (today - last < days) return`），
 * 而 VM 需要 Android 环境才构造得起来 ⇒ 它**从来没被测过**，偏偏它是最典型的「差一天」逻辑：
 * 边界写成 `<` 还是 `<=` 用户完全能感觉到（每 7 天同步一次，还是每 8 天）。
 * #5b 把它抽成纯函数 [isAutoSyncDue]，这里把边界钉住；行为逐位不变（原式取反即此式）。
 *
 * 注意「间隔 <= 0 = 关闭自动同步」那条短路**留在 VM 里**（理由见 [isAutoSyncDue] 的注释：
 * 它必须早于读凭据发生），所以本文件不测 days <= 0。
 */
class AutoSyncDueTest {

    /** 任取一个固定日子：判定只关心差值，与真实日期无关（这正是它能被测的原因）。 */
    private val today = LocalDate.of(2026, 3, 5).toEpochDay()

    @Test
    fun `恰好隔满间隔天数就同步，差一天就不同步`() {
        assertTrue("隔满 7 天应到期", isAutoSyncDue(today - 7, today, 7))
        assertFalse("只差 6 天不该同步（这条就是原式 today - last < days 的边界）", isAutoSyncDue(today - 6, today, 7))
    }

    @Test
    fun `从未同步过时必然到期`() {
        // 仓库在没存过时给的 fallback 是 0（见 lastAutoSyncEpochDayFlow）⇒ 差值极大 ⇒ 到期。
        assertTrue(isAutoSyncDue(0L, today, 1))
        assertTrue(isAutoSyncDue(0L, today, 365))
    }

    @Test
    fun `间隔 1 天时只有同一天不同步`() {
        assertFalse("同一天里不该反复同步", isAutoSyncDue(today, today, 1))
        assertTrue("隔天就该同步", isAutoSyncDue(today - 1, today, 1))
    }

    @Test
    fun `系统日期被往回调时不同步`() {
        // 用户把设备日期改回过去 ⇒ today < last ⇒ 差值为负 ⇒ 不到期。
        // 这不是新行为，是把原式的行为钉住：负数差永远小于正的天数间隔。
        assertFalse(isAutoSyncDue(today, today - 30, 7))
    }
}
