# Miuix 升级指南（AI Agent 操作手册）

> 本文件用于在「升级 Miuix 版本」时，由 AI Agent 阅读并执行。
> 目标项目：「吃了么」家庭零食柜（`SkyForest233/chileme`），含 Material 3 与 MIUIX 双主题。

---

## 0. 升级前必读的前提

1. **Miuix 是 KMP 库，版本与工具链强绑定**。升级时需核对产物 metadata / Android AAR 要求与上游构建基线；上游构建版本不全等于消费者最低要求，不应盲目复制所有工具。
2. **本项目当前锁定在 `0.9.5-rc01`（候选版，非稳定版）**，工具链为 Kotlin 2.4.21 / AGP 9.5.2 / **Gradle 9.9.2** / compileSdk API 37 extension level 2 / **minSdk 26** / JDK 21。
3. **未来升级优先选择稳定版**；本次按用户明确选择使用 `0.9.5-rc01`，需将候选版风险保留在变更说明中。
4. **升级前确保工作区干净、PR 已合并**，避免在未合并改动上叠加升级。
5. **严禁凭记忆臆造 Miuix API**。本项目安装 `.claude/skills/miuix-components/` 与 `.claude/skills/miuix-guidelines/`；组件 skill 固定到 Miuix `v0.9.5-rc01` source commit `542463a0863b393a3d6052237daae6c3da217e70`，skill 内容同步自 `limczhh/miuix-skill` commit `2694ee277fc64156331a992efc9e21e7d212b1ce`。升级后仍须用目标版本 pinned source 逐一核对，不得只凭 skill 或旧版记忆。

---

## 1. 升级的完整步骤

### 第 1 步：查目标版本的「上游基线」

读目标 tag 的 `gradle/libs.versions.toml`，确认它要求的：

| 项 | 查什么 |
|---|---|
| Kotlin | `kotlin` 版本 |
| AGP | `agp` 版本 |
| Compose Multiplatform | `jetbrains-compose-multiplatform` 版本 |
| kotlinx-serialization | `kotlinx-serialization` 版本 |
| minSdk / compileSdk | 源码或 release notes |

获取方式（GitHub 可达时）：
```
https://raw.githubusercontent.com/compose-miuix-ui/miuix/<tag>/gradle/libs.versions.toml
```

**判断规则**：
- 优先对齐上游 Kotlin 构建版本，并确认 Compose / serialization 插件与实际编译器兼容；不能把所有补丁版本差异一概视为 metadata 不兼容。
- AGP/Gradle 跟着 Miuix 的 Android 基线走。
- `compileSdk` 要 ≥ Miuix 依赖要求的版本（否则 `checkDebugAarMetadata` 报错，本项目历史上踩过这个坑）。

### 第 2 步：改依赖版本

**v2.8.1 起项目已引入 Version Catalog，Miuix 版本号只有一个位点**：`gradle/libs.versions.toml` 的

```toml
[versions]
miuix = "0.9.5-rc01"     # 当前按用户选择使用的候选版
```

Miuix 0.9.5-rc01 的 Android-only app 使用 `-android` artifacts；这些坐标共用同一 `version.ref`（blur 的 API 33+ gate 见下）：

```toml
miuix-nav-android = { module = "top.yukonga.miuix.kmp:miuix-nav-android", version.ref = "miuix" }
miuix-ui-android = { module = "top.yukonga.miuix.kmp:miuix-ui-android", version.ref = "miuix" }
miuix-preference-android = { module = "top.yukonga.miuix.kmp:miuix-preference-android", version.ref = "miuix" }
miuix-icons-android = { module = "top.yukonga.miuix.kmp:miuix-icons-android", version.ref = "miuix" }
miuix-material-android = { module = "top.yukonga.miuix.kmp:miuix-material-android", version.ref = "miuix" }
miuix-blur-android = { module = "top.yukonga.miuix.kmp:miuix-blur-android", version.ref = "miuix" }
miuix-glass-android = { module = "top.yukonga.miuix.kmp:miuix-glass-android", version.ref = "miuix" }
miuix-glass-icons-android = { module = "top.yukonga.miuix.kmp:miuix-glass-icons-android", version.ref = "miuix" }
```

`app/build.gradle.kts` 里只写 `implementation(libs.miuix.*.android)` 这类别名，**不要再写死坐标字符串**。

**minSdk 例外必须双重门控**：`miuix-blur-android` 的 Android 运行时能力为 API 33+，但项目 `minSdk` 保持 26。manifest 用 `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"` 仅覆盖 AAR SDK 元数据；所有 blur / lens / edge-stroke 入口必须由 `miuix-material.isRuntimeShaderSupported()` 或已门控的 backdrop CompositionLocal 保证 API 26–32 回退实色。不要把 manifest override 当成 API 兼容证明。RC01 中 `RuntimeShader` / `isRuntimeShaderSupported`、`EdgeStroke` 与 tilt sensor API 在 `miuix-material`；`textureBlur` 不再有 `highlight` 参数。

### 第 3 步：连带升级工具链（若基线变化）

- `gradle/libs.versions.toml` 的 `[versions]`：`agp`、`kotlin`、`composeBom`（`[plugins]` 里 `kotlin-compose` / `kotlin-serialization` 的版本都 `version.ref = "kotlin"`，改 `kotlin` 一处即可；根 `build.gradle.kts` 与 `app/build.gradle.kts` 均用 `alias(libs.plugins.*)` 引用）。**注意：本项目是 AGP 9 内置 Kotlin，勿重新加 `org.jetbrains.kotlin.android`**。
- `gradle/wrapper/gradle-wrapper.properties`：Gradle distributionUrl。
- `app/build.gradle.kts`：`compileSdk`（如需更高）。
- ⚠️ Miuix 0.9.5-rc01 上游使用 Compose Multiplatform 1.13.0-rc01；本项目 AndroidX Compose BOM 仍为 2026.08.00。两者是不同发布轴，不能只凭版本号推断兼容性。本次保留 BOM，须用 Gradle dependency insight / CI 编译核实实际解析版本；若出现 API/二进制不兼容，再单独调整 BOM。

### 第 4 步：扫描并核对受影响的 API

1. 读 `.claude/skills/miuix-components/references/rc01-migration.md` 与目标版本的官方 release notes；页面结构任务另读 `.claude/skills/miuix-guidelines/` 对应规则。
2. 扫描本项目所有 Miuix 调用点，重点核对易变组件：
   - 弹窗与下拉：`GlassDialog` / `GlassDropdownPreference`（页面级 Scaffold 不安装第二个 popup host；由根级 Miuix Scaffold 托管，核对 `renderInRootScaffold` / `defaultWindowInsetsPadding` 与 root backdrop 边界）
   - Preference：`SwitchPreference` / `ArrowPreference` / `RadioButtonPreference` / `GlassDropdownPreference`（需要标准非玻璃样式时再用 `OverlayDropdownPreference`）
   - 主题：`MiuixTheme` / `ThemeController` / `ColorSchemeMode` / `Colors` 字段
   - 基础：`Button` / `TextButton` / `TextField` / `InputField` / `Card` / `Snackbar`
   - squircle：`squircleBorder` / `squircleSurface`
   - blur/glass：`textureBlur` / `drawBackdrop` / runtime shader / tilt / custom `Backdrop`；重点复核 backdrop 捕获边界，不能采样浮栏自身
   - Glass 组件：`GlassTopAppBar` / `GlassNavigationBar` / `GlassFloatingActionButton` / `GlassIconButton` / `GlassTextField` / `GlassDialogButton`，以及 `MiuixIcons.Glass.*` 图标是否仍存在
   - 图标：`MiuixIcons.*`（含 `Glass` 命名空间）的图标名是否仍存在
3. 用新版本的 pinned source 逐一核对签名，不要凭旧版本记忆。

**0.9.4 → 0.9.5-rc01 的本仓实际迁移点**：`Highlight` / `BloomStroke` 迁为 `miuix-material` 的 `EdgeStroke`；原 `BloomStroke(dualPeak = true)` 对应 `EdgePeak.Dual`，继续旋转 primary light 并保留 secondary light；`Highlight.alpha` 改为 `drawEdgeStroke(alpha = ...)`。`textureBlur` 不再接受 `highlight`，边缘描光改为独立 `Modifier.edgeStroke` 或 `drawBackdrop(onDrawFront = ...)`；`RuntimeShader` / `asBrush` / `isRuntimeShaderSupported` 与 `rememberDeviceTilt` 移到 `miuix-material`（sensor 子包）；`Backdrop.downscaleFactor` 从 `Int` 改为 `Float`。自定义 lens 与 `CombinedBackdrop` 均需同步签名/缩放处理。未使用的 `GlassBottomSheet`、RC01 重命名图标、dropdown popup `onDismissFinished` 调用点已扫描确认无本仓调用。

扫描命令参考：
```bash
grep -rn "top.yukonga.miuix.kmp" app/src/main/java | sed 's/.*import //' | sort | uniq
```

### 第 5 步：编译验证

- 本地：`./gradlew assembleDebug`
- CI：本仓 `build.yml` 由 PR、master push 或手动 dispatch 触发；只推工作分支不会自动运行。须检查 debug / 单测 / lint 与 release R8 三个 job。
- 若报 `checkDebugAarMetadata` 要求更高 compileSdk → 升 `compileSdk`。
- 若报依赖解析失败 → 检查 Maven Central 是否可达（本项目历史上遇过间歇 403，已加 gradle.properties 重试）。

### 第 6 步：真机/设备验证

重点回归（MIUIX 模式）：
- 设置页（含坚果云弹窗）、食品列表/详情、归档、统计、管理页
- 弹窗显示与返回、深浅色切换、动态取色（Android 12+）
- squircle 圆角（需 API 33+ 设备）
- 图标显示、底部导航分流
- Miuix 导航偏好分流：关闭 `floating_nav` 时仍显示标准 Glass 胶囊；开启时才允许 `liquid_glass_nav_enabled` 切换到 iOS-like 液态玻璃。检查胶囊/拖拽栏点击、跨页指示器与 Pager 回弹、显隐阴影无裁切；API 26–32 实色回退、API 33+ backdrop/vibrancy/lens 正常
- 顶栏玻璃的滚动交叠：设置页条目与有结果的食品列表条目可从 Miuix 顶栏后方经过；食品列表搜索/筛选保持固定，首屏位置不变；MD3 布局不变，底栏仍自动隐藏/显示
- 编辑页：在 Material3 与 Miuix 下分别新增/编辑、滚动表单、保存防重复点击、相册/相机封面、键盘遮挡与 DatePicker 确认/取消；Miuix 仍使用 Material3 DatePicker + 根主题桥接，其余控件走 Miuix/Glass。MD3 表单外观与保存逻辑不得回退

---

## 2. 本项目的固定约定（升级时不可破坏）

这些是历史决策，升级时必须保持：

1. **双主题**：`ThemeStyle`（MATERIAL3 / MIUIX）+ `LocalThemeStyle`，MD3 与 MIUIX 两套实现分流。MD3 侧**只动 Miuix 不碰 MD3**。
2. **根级主题**：MIUIX 用 `MiuixRootTheme`（`MiuixTheme` + 桥接 `MaterialTheme`），页面内不再重复包 `MiuixTheme`。
3. **Glass 弹窗与下拉必须由根 Miuix Scaffold 的 popup host 托管**：`MainApp` 在 Miuix 模式安装唯一 root host，页面级 `AppScaffold` 将 `popupHost` 留空；调用点保持组合、用 `show` 控制显隐以保留退出动画，并传正确的 root backdrop。
4. **图标分流**：Miuix 使用 `MiuixIcons.*`（含 `Regular` / `Extended` / `Glass` 命名空间），MD3 使用 Material 图标；没有对应 Miuix glyph 时才保留 Material 图标。
5. **桥接层**：`MiuixRootTheme.kt` 的 `miuixColorsToMd3ColorScheme` 是「MD3 页面取色」的过渡层，升级时若 Miuix `Colors` 字段变化，需同步修正映射。
6. **状态色**：安全/临期/过期是硬编码语义色（`Color.kt`），不随主题/版本变。
7. **minSdk 26 不变**（2026-08-21 由 24 提升：全项目 28 处 `java.time` 未开脱糖，API 24/25 会 `NoClassDefFoundError`）。`miuix-blur-android` 的 API 33+ 限制只能在确认所有入口都有 runtime gate + 实色 fallback 后，通过 manifest `tools:overrideLibrary` 接入；不能因此提高 app minSdk。
8. **Miuix 弹窗的 `content` 必须是单一根节点**（2026-09-17 真机复测踩坑）：库 `DialogContent` 把 `title` / `summary` / `content()` 依次放进一个**不带 `verticalArrangement` 的 Column**（间距只由 title、summary 各自的 `padding(bottom = 12.dp)` 提供），所以 content 里两个平级节点之间是 **0dp**。标准写法：单一 `Column(verticalArrangement = Arrangement.spacedBy(12.dp))`，按钮区再额外留 4~8.dp（上游示例 `example/shared/.../component/DialogSection.kt:351`；本仓 `app/AppBatchMoveDialog.kt` / `AppFormDialog.kt` / `SettingsCloudDialogs.kt` 坚果云弹窗 —— #10a-1 前在 `SettingsScreen.kt`）。静态守卫：`MiuixDialogContentTest`。
9. **Miuix Glass 弹窗动作统一使用 `GlassDialogButton` 并显式指定角色**：`Normal` 用于取消/返回，`Primary` 用于主要确认，`Destructive` 用于危险操作；不要在 GlassDialog 中混用普通 `Button` / `TextButton`。适配封装 `MiuixGlassDialogButton` 设置 Miuix 按钮字型，官方组件负责对比度、disabled 状态与按压反馈。静态守卫：`MiuixDialogContentTest.dialogActionsUseGlassButtons`。

---

## 3. 常见坑（本项目历史踩过的）

| 症状 | 原因 | 处理 |
|---|---|---|
| `checkDebugAarMetadata` 报 compileSdk 不足 | Miuix 依赖要求更高 compileSdk | 升 `compileSdk` |
| `Unresolved reference 'kotlin.android'` 或内置 Kotlin 冲突 | AGP 9 内置 Kotlin | 移除 `org.jetbrains.kotlin.android` 插件 |
| Maven Central 403 | CI 共享 IP 被限流 | gradle.properties 已加重试，若仍失败重跑 |
| GlassDialog / dropdown 不显示或 backdrop 自采样 | 根级 popup host 缺失，或页面级 Scaffold 又安装了第二个 host / capture 含浮层自身 | Miuix 模式由 MainApp 外层 Scaffold 托管；页面级 Scaffold 设 `popupHost = {}`，玻璃浮层传 root backdrop 并用 `renderInRootScaffold = true` |
| 弹窗里输入框与「取消/确定」上下边重合（零间距） | `content` 写了两个平级节点（字段 Column + 按钮 Row），而库的弹窗根 Column 不带 `verticalArrangement` | 合成单一 `Column(spacedBy(12.dp))`，按钮 Row 再加 `padding(top = 8.dp)`（见 §2 第 8 条） |
| Glass 弹窗按钮层级或 disabled 样式不一致 | 使用通用 Miuix `Button` / `TextButton`，绕过 Glass 动作语义 | 所有动作经 `MiuixGlassDialogButton`，显式传 `Normal` / `Primary` / `Destructive`，见 §2 第 9 条 |
| `Key was already used` 闪退 | LazyColumn key 冲突 | 用 `itemsIndexed` + index 兜底 |
| 注释里 `*/` 导致编译错误 | 块注释被 `*/` 提前闭合 | 避免在注释里写 `inverse*/` 这类 |
| 同包同名枚举 Redeclaration | MD3/MIUIX 文件重名 | 用 `MiuixXxx` 前缀区分 |

---

## 4. 交付检查清单

升级完成后，逐项确认：

- [ ] 依赖版本已改，工具链（Kotlin/AGP/Gradle/compileSdk）已对齐目标版本基线
- [ ] `./gradlew assembleDebug`（或 CI）通过
- [ ] `bash tools/ci-gates.sh` 通过（ktlint 为拦截模式，见 `docs/WORKFLOW.md` §3）
- [ ] `./gradlew testDebugUnitTest` 通过 —— 尤其 `ScreenParityTest`（原名 `MiuixParityTest`，拦「屏幕文件重写业务计算」）与 `ImeHandlingTest`（拦「弹窗/输入屏丢失 IME 处理」）、`MiuixDialogContentTest`（拦「Miuix 弹窗 content 写了多个平级节点 → 零间距」）
- [ ] 所有 Miuix API 调用已对照新版本 source 核对，无臆造签名
- [ ] MD3 主题未受影响（未改 MD3 页面代码）
- [ ] 双主题切换、弹窗、图标、squircle 等关键路径回归正常
- [ ] devlog 记录本次升级（改了哪些版本、遇到哪些迁移项）
- [ ] 若上游仍是候选版，在文档中标注「非稳定版」风险

---

## 5. 附：本项目 Miuix 相关文件索引

| 文件 | 作用 |
|---|---|
| `gradle/libs.versions.toml` | **Miuix 版本的唯一位点**（`miuix = "..."`）+ 插件/工具链版本 |
| `app/build.gradle.kts` | 依赖别名引用（`libs.miuix.*`）+ compileSdk / minSdk / targetSdk |
| `app/src/main/AndroidManifest.xml` | API 33+ `miuix-blur` 的受控 `overrideLibrary`；需与 `isRuntimeShaderSupported()` fallback 一起审计 |
| `app/src/main/java/com/agon/app/LiquidGlassLens.kt` | 基于 Miuix 公共 `runtimeShaderEffect` 的圆角折射 lens，支持色散 |
| `app/src/main/java/com/agon/app/LiquidGlassNavigationBar.kt` | Miuix v0.9.5-rc01 demo 风格的可拖拽玻璃底栏：Tab 选择、弹性指示器、按压缩放与 EdgeStroke 传感器高光 |
| `app/src/main/java/com/agon/app/MainTabsPagerState.kt` | 对齐 Miuix demo 的跨页协调器：目标页与 Pager 中间页分离；MIUIX 用 `springAnimateToPage()` 连续跨页，MD3 保留原动画 |
| `app/src/main/java/com/agon/app/DampedDragAnimation.kt` / `InteractiveHighlight.kt` | 阻尼拖拽回弹与交互高光 |
| `app/src/main/java/com/agon/app/CombinedBackdrop.kt` / `InnerShadow.kt` / `LiquidGlassVibrancy.kt` | 多层 backdrop 采样、指示器内阴影及 vibrancy 辅助效果 |
| `app/src/main/java/com/agon/app/ui/components/app/MiuixBlurLocals.kt` | 页面 backdrop 与 Miuix blur / glass 控制的 CompositionLocal |
| `build.gradle.kts` | 插件声明（`alias(libs.plugins.*)`，均 `apply false`） |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 版本 |
| `app/src/main/java/com/agon/app/ui/theme/MiuixRootTheme.kt` | 根主题 + 桥接 |
| `app/src/main/java/com/agon/app/ui/theme/ThemeStyle.kt` | 主题风格枚举 |
| `app/src/main/java/com/agon/app/ui/components/app/` | **App 级双主题外壳 —— Miuix API 调用最集中的一层**（`AppScaffold` / `AppTopBar` / 确认·表单·选项三类弹窗 / `AppText` / `AppButtons` / `AppIme` …）。组件清单与每个组件的关键约定见 [`docs/DESIGN_SPEC.md`](DESIGN_SPEC.md) **§4.1（由源码生成，本表不复述）** |
| `app/src/main/java/com/agon/app/ui/components/*.kt` | 复用组件（12 个文件 —— 09-19 #11d① 加 `StatsCharts.kt`、#11e 加 `CalendarMonthLayout.kt`；现值 = `ls app/src/main/java/com/agon/app/ui/components/*.kt | wc -l`，**单文件双主题** —— 分流在组件内部走 `LocalThemeStyle`，不是两份实现）；2026-09-16 由**已删除**的 `Common.kt` 拆出 8 个，另有原本就独立的 `UndoSnackbar.kt` / `ExpiryCalendar.kt` |
| `app/src/main/java/com/agon/app/ui/screens/*.kt` | 屏幕：9 个渲染文件 + 3 个设置页弹窗文件（`Settings*Dialogs.kt`，#10a-1 起，不是新增屏幕）+ 8 个 `*State.kt`。⚠️ **`Miuix*Screen.kt` 双胞胎已于 2026-09-16 全部删除**，别照旧清单去找「各页 Miuix 实现」—— Miuix 分支现在就写在同一个屏幕文件里 |
| 包根 `app/src/main/java/com/agon/app/*.kt` | `MainActivity` / `MainApp` / `AppNavGraph` / `NavChrome` / `BatchBars` —— 底栏四套形态、`NavDisplay` 转场与系统圆角、Snackbar / FAB / 批量栏都在这层调 Miuix API |
| `.claude/skills/miuix-components/` + `.claude/skills/miuix-guidelines/` | 上游双 skill；组件证据锁定 Miuix `v0.9.5-rc01`，与 App 依赖一致（候选版；具体依赖以 `libs.versions.toml` 为准） |
| `docs/audits/2026-08-20-miuix-review.md` | 设计审查报告 |

> ⚠️ **本表刻意不写「各层有多少处 Miuix 调用」** —— 这类数字会随重构悄悄过期（本表此前就指着
> `ui/screens/Miuix*.kt`「各页 Miuix 实现」，而那批文件 2026-09-16 已全部删除；当时 Miuix 调用最集中的
> `ui/components/app/` 反而整层没被列进来）。要知道当下的确切分布，跑第 4 步那条命令并按目录聚合：
>
> ```bash
> grep -rn "top.yukonga.miuix.kmp" app/src/main/java \
>   | sed 's|app/src/main/java/com/agon/app/||; s|/[^/]*\.kt:.*|/|' | sort | uniq -c | sort -rn
> ```
>
> 升级前**务必跑一次**：它列出的是「今天真正在调 Miuix API 的目录」，比任何文档里的清单都可信。
