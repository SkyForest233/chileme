package com.agon.app.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 应用路由（miuix-nav 扁平栈）。底栏四个 Tab 仍是 [Main] 里的 HorizontalPager，
 * 只有详情/编辑/归档等二级页入栈，才能用 MiuixDefault 卡片滑预测返回。
 */
@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Main : AppRoute

    @Serializable
    data class Detail(val id: String) : AppRoute

    @Serializable
    data class Edit(val id: String? = null) : AppRoute

    @Serializable
    data object Archive : AppRoute

    @Serializable
    data object Consumption : AppRoute

    @Serializable
    data object ManageThresholds : AppRoute

    @Serializable
    data object ManageCategories : AppRoute

    @Serializable
    data object ManageLocations : AppRoute
}
