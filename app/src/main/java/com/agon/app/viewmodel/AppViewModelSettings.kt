package com.agon.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.agon.app.data.setAutoSyncDays
import com.agon.app.data.setDarkMode
import com.agon.app.data.setDynamicColor
import com.agon.app.data.setFloatingNav
import com.agon.app.data.setPalette
import com.agon.app.data.setThemeStyle
import kotlinx.coroutines.launch

/**
 * ViewModel 的**设置领域：自动同步天数 + 5 个外观/主题开关**（路线图 #10b-6，2026-09-19）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选「门面转发」和「领域对象」，
 * 完整取舍写在 `data/RepositoryCore.kt` 的文件 KDoc 里（#5c 一次说清，这里只指路 —— 本文件与
 * `AppViewModelBackup.kt` / `AppViewModelCloud.kt` / `AppViewModelArchiveUndo.kt` / `AppViewModelFood.kt` /
 * `AppViewModelCategoryLocation.kt` 同形）。
 *
 * 6 个函数全是**一行体**：`fun 原名(原参数表) = viewModelScope.launch { repo.同名(原实参) }` ——
 * 搬完一个字没改，变的只有接收者（从「类成员」变成「同包扩展」）。
 *
 * 三条落地约束（#10b 规划里写死的，本领域逐条对过）：
 *
 * 1. Flow 属性**留在类里**：这 6 个开关各有一条读出来的 `StateFlow`（`dynamicColor` / `darkMode` /
 *    `palette` / `themeStyle` / `floatingNav` / `autoSyncDays`，六条都是 `repo.<名>Flow` 以
 *    `SharingStarted.Eagerly` 起、在 `viewModelScope` 上转出来的）
 *    ⇒ 一律没动（搬成扩展属性 = `get() =` 每次新建 Flow，#5c 已否决）。本领域自己**一个属性都没有**，
 *    也没有段标题要搬或删：这 6 个原本夹在 `maybeAutoSnapshot` 与「本地快照管理」之间、**自己没有段标题**
 *    （#10b-5 删掉的那条「位置管理」一度给它们当过帽子，那时就已经名不副实了 —— 本轮它们才真正搬到自己的
 *    领域文件里）。
 * 2. 类里**不留同名转发**：成员会遮蔽扩展，`fun x() = x()` 是无限递归而编译期不报（#5c 已否决）。
 * 3. 这几个函数要用到的类成员得从 `private` 放宽成 `internal` —— **本轮还是一个都不用放**（连着第五轮）：
 *    6 块只用到 `repo`（#10b-1 已放宽）⇒ 放宽总数仍停在 **7**。
 *    ⚠️ 但**下一轮**（#10b-7「UI 状态与事件」）必须放宽 6 个（`_fabSuppressed` / `_selectedIds` 与 4 条
 *    `Channel`）⇒ 届时累计 **13**，正是 #10 规划向用户交底时给的那个数，不是新账。
 *
 * ⚠️ 同名相撞 **6 处、一个不漏**：6 个函数名与 data 层的同名扩展**全部**一字不差（都在 `data/FoodSettings.kt`，
 * 那边是 `internal suspend fun FoodRepository.同名`）⇒ 本文件**既声明** `internal fun AppViewModel.同名`、
 * **又必须** `import com.agon.app.data.同名`：体内写的是 `repo.同名(...)`，接收者是 `FoodRepository`
 * ⇒ 要解析的正是 data 那份，本文件这份接收者类型不对、根本不参与解析。
 * 顺带结掉一笔旧账（实测，不是推测）：上一轮（#10b-5）`move-importcheck` 在 **VM 侧**报的那 6 条
 * 「类成员遮蔽」假警，名字正是这 6 个 —— VM 自己的同名成员把 data 层扩展遮住了，工具判不出 `repo.<名>(`
 * 才是真用法。本轮这 6 个成员搬走 ⇒ VM 侧那 6 条 import 一起删掉 ⇒ VM 那栏从「疑似多余 **6**」变成 **0**。
 * ⚠️ 但**同一个限制换到了调用方那栏**：`SettingsState.kt` 自己声明了 6 个同名 override，工具的
 * `local_decls` 减法照样把它们判成「多余」（Kotlin 里成员声明**不**压制另一接收者要用的 import）
 * ⇒ 那 6 行是必须的，看见假警别删。判据 7 的注释里记的就是这一类。
 * （另：复跑 #10b-5 那条命令时还会多出一条 `ChiliMeApp` 假警，成因不同 —— `--new` 跨了 3 个目录时
 * `same_package_decls` 取的是**并集**，`MainApp.kt` 所在的 `com/agon/app/` 里有 `ChiliMeApp.kt`；
 * 已补注在 ROADMAP #10b-5 那条上。）
 *
 * 对外调用写法一个字没变：跨包调用方只有 **1 个文件 / 6 处** —— `ui/screens/SettingsState.kt` 里那个
 * `ViewModelSettingsActions` 适配器 ⇒ 加 **6** 行 import（本仓禁通配导入）。
 * ⚠️ 它自己**声明了 6 个同名的 override**（对应 `internal interface SettingsActions` 里的 6 个抽象成员）
 * ⇒ 声明照样不压制 import，那 6 行必须加（与判据 7 同一形状；先例见 `SettingsState.kt` 自己（#10b-1/2/4）、
 * `FoodListState.kt`（#10b-3/4）、`ManageState.kt`（#10b-5），都已编译通过）。
 * 它的 viewmodel import 组里夹着一条 #10b 分组注释 ⇒ 搬运器按 ASCII 位置逐条插入、绝不重排已有行
 * （#10b-4 起的做法）；插完 6 条落在 `saveNutstoreCredentials` 与 `syncDownload` 之间。
 * 那个接口的注释还留着一条实测教训（「全部声明为 Unit：`AppViewModel` 的这些方法返回 `Job`，
 * 直接写表达式体 override 会因返回类型不匹配编译失败」）⇒ 本轮**没有**动任何 override 的写法，
 * 扩展函数一样返回 `Job`、适配器一样用块体丢弃它。
 *
 * 守卫本轮**一处都不用改**（实测，不是省事）：`SettingsStateTest` 用的是 `RecordingActions : SettingsActions`
 * 假实现 —— 既不构造真 `AppViewModel`、也不读源码文本，断言的是「状态类把调用转给了 actions」
 * ⇒ 与 VM 侧函数长在哪个文件无关；`SnackbarCopyTest` 那边 6 块里 **0** 个字面量 ⇒ 分布不变；
 * 另外两个提到 `AppViewModel` 的测试（`AutoSyncDueTest` / `UiEventTest`）只在注释里提一句、不读源码文本；
 * 真读源码文本的第三个（`CorruptGuardTest`）读的是 `AppViewModelFood/Backup/Cloud.kt` 三个兄弟文件，
 * 5 条断言本轮逐条重跑仍全为真。
 *
 * 领域边界（哪些「看着像设置」却不在本文件）：`clearAll` 与 `discardCorruptData` 在 `AppViewModelFood.kt`
 * （#10b-4，属数据操作、不属外观设置）；凭据 / 上传 / 列表 / 下载在 `AppViewModelCloud.kt`（#10b-2）；
 * 文件导入导出与本地快照在 `AppViewModelBackup.kt`（#10b-1）；`maybeAutoSync` / `maybeAutoSnapshot` 是
 * 自动同步**策略**（何时触发），不是设置项 ⇒ 暂留类里；`setFabSuppressed` 与选择集三件套属 #10b-7。
 *
 * 搬运口径：函数体**逐字未动**，只做了两件事 —— 整体左移 4 空格（脱离类体）、声明行改写成
 * `internal fun AppViewModel.原名(原参数表)`（接收者加上，**名字与参数一个没改**；这 6 个原本都不是
 * `private` ⇒ 连可见性都没变）。本领域 6 块里一条注释都没有 ⇒ 没有 KDoc 随迁。
 */

internal fun AppViewModel.setAutoSyncDays(days: Int) = viewModelScope.launch { repo.setAutoSyncDays(days) }

internal fun AppViewModel.setDynamicColor(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }

internal fun AppViewModel.setDarkMode(mode: Int) = viewModelScope.launch { repo.setDarkMode(mode) }

internal fun AppViewModel.setPalette(name: String) = viewModelScope.launch { repo.setPalette(name) }

internal fun AppViewModel.setThemeStyle(name: String) = viewModelScope.launch { repo.setThemeStyle(name) }

internal fun AppViewModel.setFloatingNav(enabled: Boolean) = viewModelScope.launch { repo.setFloatingNav(enabled) }
