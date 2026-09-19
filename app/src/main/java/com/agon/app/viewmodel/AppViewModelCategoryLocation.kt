package com.agon.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.agon.app.data.CategoryDef
import com.agon.app.data.setCategories
import com.agon.app.data.setCategoryThreshold
import com.agon.app.data.setLocations
import com.agon.app.data.updateLocationBatch
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * ViewModel 的**分类与位置领域：分类的增删改与阈值、位置的增删与批量改位置**（路线图 #10b-5，2026-09-19）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选「门面转发」和「领域对象」，
 * 完整取舍写在 `data/RepositoryCore.kt` 的文件 KDoc 里（#5c 一次说清，这里只指路 —— 本文件与
 * `AppViewModelBackup.kt` / `AppViewModelCloud.kt` / `AppViewModelArchiveUndo.kt` / `AppViewModelFood.kt` 同形）。
 *
 * 三条落地约束（#10b 规划里写死的，本领域逐条对过）：
 *
 * 1. Flow 属性**留在类里**：本领域读的 [categories] 与 [locations] 都是 `stateIn` 出来的 `StateFlow`
 *    （搬成扩展属性 = `get() =` 每次新建 Flow，#5c 已否决）⇒ 两对属性一个没动，5 个函数照旧写
 *    `categories.value` / `locations.value`（同包扩展函数够得着 public 属性）。
 *    ⚠️ 本领域有**两条段标题是「删」不是「搬」**（#10b-2 那条 `坚果云同步` 之后第二次）：
 *    - `// ---- 分类管理 ----` 是**真孤儿** —— 它底下 3 个函数（增 / 改 / 删）全搬来了本文件，
 *      留在类里就是个没有内容的帽子；
 *    - `// ---- 位置管理 ----` 严格说**不是**孤儿 —— 它底下还留着 5 个主题开关
 *      （`setDynamicColor` / `setDarkMode` / `setPalette` / `setThemeStyle` / `setFloatingNav`），
 *      但那 5 个属 #10b-6「设置」领域、跟"位置"毫无关系（它们本来就只是**排在那儿**），
 *      位置函数一走，这条标题就开始给不属于自己的内容当帽子 ⇒ 一并删掉；#10b-6 落地时那 5 个也会走，
 *      届时它无论如何都保不住。两处都由本 KDoc 接替作用。
 * 2. 类里**不留同名转发**：成员会遮蔽扩展，`fun x() = x()` 是无限递归而编译期不报（#5c 已否决）。
 * 3. 这几个函数要用到的类成员得从 `private` 放宽成 `internal` —— **本轮还是一个都不用放**（连着第四轮）：
 *    `repo` 在 #10b-1 已放宽，`categories` / `locations` 本来就是 public（放宽总数仍停在 7 个）。
 *
 * ⚠️ 同名相撞 2 处（判据 7 的形状，比 #10b-4 的 7 处少）：[setCategoryThreshold] 与 [updateLocationBatch]
 * 与 data 层的同名扩展一字不差（那边是 `internal suspend fun FoodRepository.同名`，分别在
 * `data/FoodSettings.kt` 与 `data/FoodItems.kt`）⇒ 本文件**既声明** `internal fun AppViewModel.同名`、
 * **又必须** `import com.agon.app.data.同名`：体内写的是 `repo.同名(...)`，接收者是 `FoodRepository`
 * ⇒ 要解析的正是 data 那份。另外两条 `setCategories` / `setLocations` 只有 import、没有同名声明
 * （本领域的函数名是 `addCategory` / `updateCategory` / `deleteCategory` / `addLocation` / `deleteLocation`，
 * 它们在 data 层**没有**同名扩展 —— 那边只暴露"整表覆盖"的两个 setter）。
 *
 * 本轮跟着搬走的两样小东西：
 * - **一个字面量**：`addCategory` 里那个默认餐具 emoji（`emoji.trim().ifBlank { … }`）—— 这是 #10b-2 的
 *   `NO_CREDENTIALS_MESSAGE` 之后第一个随迁的字面量。它**不被任何测试追踪**（测试树里 0 处命中；
 *   `SnackbarCopyTest` 钉的是中文文案片段，逐条仿真过分布在搬家前后完全一致），所以没有守卫要同批改。
 * - **一条 import**：`java.util.UUID` —— 它在 `AppViewModel.kt` 里的唯一使用者就是 `addCategory`
 *   （搬完那边整词 0 出现 ⇒ 删掉），本文件接手。`CategoryDef` 则是**两边都要**：类里 `categories`
 *   属性的类型仍是它，本文件的参数类型也是它（import 是按文件算的，不冲突）。
 *
 * 对外调用写法一个字没变：跨包调用方只有 **2 个文件 / 7 处** ⇒ 共加 **7** 行 import（本仓禁通配导入）：
 * `ui/screens/ManageState.kt` 6 行、`MainApp.kt` 1 行（批量改位置那处）。
 * `ManageScreens.kt` 走的是 `state.<名>(`（状态类自己的成员）⇒ 不需要 import。
 * ⚠️ `ManageState.kt` 里有 **5 个同名的转发函数**（`addCategory` / `updateCategory` / `deleteCategory` /
 * `addLocation` / `deleteLocation`）⇒ 声明照样不压制 import，那几行必须加（与判据 7 同一形状，
 * 先例见 `SettingsState.kt`（#10b-1/2）、`FoodListState.kt`（#10b-3）、`ArchiveState.kt` 等（#10b-4），
 * 都已编译通过）。
 *
 * 守卫本轮**一处都不用改**（实测，不是省事）：全仓测试树里这 7 个函数名 **0** 处命中；
 * 读 `AppViewModel.kt` 的那 4 个测试（`CorruptGuardTest` / `SnackbarCopyTest` / `AutoSyncDueTest` /
 * `UiEventTest`）断言的都不是本领域的东西（前两个在 #10b-4 / #10b-2 已各自安顿好）。
 *
 * 领域边界（哪些「看着像」却不在本文件）：5 个主题开关与 `setAutoSyncDays` 属 #10b-6「设置」；
 * `setFabSuppressed`、选择集三件套与 `emit` 属 #10b-7「UI 状态与事件」；`maybeAutoSync` /
 * `maybeAutoSnapshot` 是自动同步策略，暂留类里；**批量归档/批量恢复**在 `AppViewModelFood.kt`（#10b-4）
 * —— 本文件只有「批量改位置」这一个批量操作。
 *
 * 搬运口径：函数体**逐字未动**，只做了两件事 —— 整体左移 4 空格（脱离类体）、声明行改写成
 * `internal …fun AppViewModel.原名(原参数表)`（接收者加上，**名字与参数一个没改**；这 7 个原本都不是
 * `private` ⇒ 连可见性都没变）。本领域**没有** KDoc 随迁（7 块里一条注释都没有，实测）。
 */

internal fun AppViewModel.setCategoryThreshold(categoryId: String, days: Int) =
    viewModelScope.launch { repo.setCategoryThreshold(categoryId, days) }

internal fun AppViewModel.addCategory(label: String, emoji: String) = viewModelScope.launch {
    val def = CategoryDef(UUID.randomUUID().toString(), label.trim(), emoji.trim().ifBlank { "🍽️" })
    repo.setCategories(categories.value + def)
}

internal fun AppViewModel.updateCategory(def: CategoryDef) = viewModelScope.launch {
    repo.setCategories(categories.value.map { if (it.id == def.id) def else it })
}

internal fun AppViewModel.deleteCategory(id: String) = viewModelScope.launch {
    val remaining = categories.value.filterNot { it.id == id }
    if (remaining.isNotEmpty()) repo.setCategories(remaining)
}

internal fun AppViewModel.addLocation(name: String) = viewModelScope.launch {
    val trimmed = name.trim()
    if (trimmed.isNotBlank() && trimmed !in locations.value) {
        repo.setLocations(locations.value + trimmed)
    }
}

internal fun AppViewModel.deleteLocation(name: String) = viewModelScope.launch {
    repo.setLocations(locations.value.filterNot { it == name })
}

internal fun AppViewModel.updateLocationBatch(ids: Set<String>, newLocation: String) = viewModelScope.launch {
    repo.updateLocationBatch(ids, newLocation)
}
