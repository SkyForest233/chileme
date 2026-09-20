# Miuix 升级指南（AI Agent 操作手册）

> 本文件用于在「升级 Miuix 版本」时，由 AI Agent 阅读并执行。
> 目标项目：「吃了么」家庭零食柜（`SkyForest233/chileme`），含 Material 3 与 MIUIX 双主题。

---

## 0. 升级前必读的前提

1. **Miuix 是 KMP 库，版本与工具链强绑定**。每个 Miuix 版本都要求特定的 Kotlin / AGP / Compose 版本。升级 Miuix 大概率要**连带升级整套工具链**，不是只改依赖版本号。
2. **本项目当前锁定在 `0.9.4-rc01`（候选版）**，工具链为 Kotlin 2.4.10 / AGP 9.3.1 / **Gradle 9.7.1** / compileSdk 37 / **minSdk 26** / JDK 21。（2026-09-16 校正：此前本文写 Gradle 9.6.1、minSdk 24，均已过期）
3. **优先升级到稳定版**（如 `0.9.4` 正式 tag），候选版/快照版风险高。
4. **升级前确保工作区干净、PR 已合并**，避免在未合并改动上叠加升级。
5. **严禁凭记忆臆造 Miuix API**。本项目已安装 skill（`.claude/skills/miuix/`），所有组件签名一律以 skill 的 pinned source 为准；升级后需用**新版本的 source** 重新核对。

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
- 用 Miuix 要求的 Kotlin 版本（Kotlin 编译器无法读取更高版本产物的 metadata，不能低于也不能明显高于）。
- AGP/Gradle 跟着 Miuix 的 Android 基线走。
- `compileSdk` 要 ≥ Miuix 依赖要求的版本（否则 `checkDebugAarMetadata` 报错，本项目历史上踩过这个坑）。

### 第 2 步：改依赖版本

**v2.8.1 起项目已引入 Version Catalog，Miuix 版本号只有一个位点**：`gradle/libs.versions.toml` 的

```toml
[versions]
miuix = "0.9.4-rc01"     # ← 只改这一行
```

四个坐标都 `version.ref = "miuix"`，改一处即可全部对齐（注意 `miuix-ui` / `miuix-preference` / `miuix-icons` 保持 **common 坐标**，勿加 `-android` 后缀；只有导航用 `miuix-nav-android`）：

```toml
miuix-nav-android = { module = "top.yukonga.miuix.kmp:miuix-nav-android", version.ref = "miuix" }
miuix-ui          = { module = "top.yukonga.miuix.kmp:miuix-ui",          version.ref = "miuix" }
miuix-preference  = { module = "top.yukonga.miuix.kmp:miuix-preference",  version.ref = "miuix" }
miuix-icons       = { module = "top.yukonga.miuix.kmp:miuix-icons",       version.ref = "miuix" }
```

`app/build.gradle.kts` 里只写 `implementation(libs.miuix.ui)` 这类别名，**不要再写死坐标字符串**。

### 第 3 步：连带升级工具链（若基线变化）

- `gradle/libs.versions.toml` 的 `[versions]`：`agp`、`kotlin`、`composeBom`（`[plugins]` 里 `kotlin-compose` / `kotlin-serialization` 的版本都 `version.ref = "kotlin"`，改 `kotlin` 一处即可；根 `build.gradle.kts` 与 `app/build.gradle.kts` 均用 `alias(libs.plugins.*)` 引用）。**注意：本项目是 AGP 9 内置 Kotlin，勿重新加 `org.jetbrains.kotlin.android`**。
- `gradle/wrapper/gradle-wrapper.properties`：Gradle distributionUrl。
- `app/build.gradle.kts`：`compileSdk`（如需更高）。
- ⚠️ Miuix 依赖 Compose 1.12.0-rc01，升 `composeBom` 与升 `miuix` 有连带关系，**不要和功能改动混在同一个 PR**，保证能独立回滚。

### 第 4 步：扫描并核对受影响的 API

1. 读 skill 的迁移笔记（如 `.claude/skills/miuix/references/release-v0.9.4-rc01.md`、`release-v0.9.3.md`），以及**目标版本**的官方 release notes。
2. 扫描本项目所有 Miuix 调用点，重点核对易变组件：
   - 弹窗：`OverlayDialog` / `WindowDialog`（`maxWidth` / `largeScreen` / `cornerRadius` 等参数）
   - Preference：`SwitchPreference` / `ArrowPreference` / `RadioButtonPreference` / `OverlayDropdownPreference`
   - 主题：`MiuixTheme` / `ThemeController` / `ColorSchemeMode` / `Colors` 字段
   - 基础：`Button` / `TextButton` / `TextField` / `InputField` / `Card` / `Snackbar`
   - squircle：`squircleBorder` / `squircleSurface`
   - 图标：`MiuixIcons.Regular.*` 的图标名是否仍存在
3. 用新版本的 pinned source 逐一核对签名，不要凭旧版本记忆。

扫描命令参考：
```bash
grep -rn "top.yukonga.miuix.kmp" app/src/main/java | sed 's/.*import //' | sort | uniq
```

### 第 5 步：编译验证

- 本地：`./gradlew assembleDebug`
- CI：推送到分支触发 `build.yml`（本项目的 PR/push 会自动跑 `assembleDebug`）。
- 若报 `checkDebugAarMetadata` 要求更高 compileSdk → 升 `compileSdk`。
- 若报依赖解析失败 → 检查 Maven Central 是否可达（本项目历史上遇过间歇 403，已加 gradle.properties 重试）。

### 第 6 步：真机/设备验证

重点回归（MIUIX 模式）：
- 设置页（含坚果云弹窗）、食品列表/详情、归档、统计、管理页
- 弹窗显示与返回、深浅色切换、动态取色（Android 12+）
- squircle 圆角（需 API 33+ 设备）
- 图标显示、底部导航分流

---

## 2. 本项目的固定约定（升级时不可破坏）

这些是历史决策，升级时必须保持：

1. **双主题**：`ThemeStyle`（MATERIAL3 / MIUIX）+ `LocalThemeStyle`，MD3 与 MIUIX 两套实现分流。MD3 侧**只动 Miuix 不碰 MD3**。
2. **根级主题**：MIUIX 用 `MiuixRootTheme`（`MiuixTheme` + 桥接 `MaterialTheme`），页面内不再重复包 `MiuixTheme`。
3. **弹窗必须在 Miuix Scaffold 的 content lambda 内**（无条件调用 + `show` 参数），否则不显示（历史踩坑）。
4. **图标分流**：MIUIX 用 `MiuixIcons.Regular.*`，MD3 用 material 图标；`CleaningServices`/`Inventory2` 无 Miuix 对应，保留 material。
5. **桥接层**：`MiuixRootTheme.kt` 的 `miuixColorsToMd3ColorScheme` 是「MD3 页面取色」的过渡层，升级时若 Miuix `Colors` 字段变化，需同步修正映射。
6. **状态色**：安全/临期/过期是硬编码语义色（`Color.kt`），不随主题/版本变。
7. **minSdk 26 不变**（2026-08-21 由 24 提升：全项目 28 处 `java.time` 未开脱糖，API 24/25 会 `NoClassDefFoundError`。除非新 Miuix 强制要求更高，需评估）。
8. **Miuix 弹窗的 `content` 必须是单一根节点**（2026-09-17 真机复测踩坑）：库 `DialogContent` 把 `title` / `summary` / `content()` 依次放进一个**不带 `verticalArrangement` 的 Column**（间距只由 title、summary 各自的 `padding(bottom = 12.dp)` 提供），所以 content 里两个平级节点之间是 **0dp**。标准写法：单一 `Column(verticalArrangement = Arrangement.spacedBy(12.dp))`，按钮区再额外留 4~8.dp（上游示例 `example/shared/.../component/DialogSection.kt:351`；本仓 `app/AppBatchMoveDialog.kt` / `AppFormDialog.kt` / `SettingsCloudDialogs.kt` 坚果云弹窗 —— #10a-1 前在 `SettingsScreen.kt`）。静态守卫：`MiuixDialogContentTest`。
9. **Miuix 弹窗的动作按钮一律用 `TextButton`，主要动作传 `ButtonDefaults.textButtonColorsPrimary()`**（2026-09-17 真机复测踩坑）：库的 `TextButton` **不是**无底文字按钮 —— 它内部就是 `Button`，用 `.squircleSurface(color = containerColor)` 实心填充（`basic/Button.kt:76`）；默认 `textButtonColors()` 的容器色是 `secondaryVariant`（浅灰），所以不传 `colors` 时「确定 / 保存 / 添加」和「取消」完全同色。`textButtonColorsPrimary()` = 容器 `primary` 蓝 + 文字 `onPrimary` 白 + 对应 disabled 角色 ⇒ 蓝底白字胶囊（上游 `DialogSection.kt` 的 7 个弹窗一律如此）。弹窗里**不要**用 `Button` + `buttonColorsPrimary()`：颜色虽同，但要自己补文字色与字重、拿不到 `textStyles.button` 与 disabled 角色。静态守卫：`MiuixDialogContentTest.dialogActionsFollowMiuixButtonConvention`。

---

## 3. 常见坑（本项目历史踩过的）

| 症状 | 原因 | 处理 |
|---|---|---|
| `checkDebugAarMetadata` 报 compileSdk 不足 | Miuix 依赖要求更高 compileSdk | 升 `compileSdk` |
| `Unresolved reference 'kotlin.android'` 或内置 Kotlin 冲突 | AGP 9 内置 Kotlin | 移除 `org.jetbrains.kotlin.android` 插件 |
| Maven Central 403 | CI 共享 IP 被限流 | gradle.properties 已加重试，若仍失败重跑 |
| OverlayDialog 不显示 | 弹窗在 Scaffold content 外 | 移入 content lambda |
| 弹窗里输入框与「取消/确定」上下边重合（零间距） | `content` 写了两个平级节点（字段 Column + 按钮 Row），而库的弹窗根 Column 不带 `verticalArrangement` | 合成单一 `Column(spacedBy(12.dp))`，按钮 Row 再加 `padding(top = 8.dp)`（见 §2 第 8 条） |
| 弹窗里「确定 / 保存 / 添加」和「取消」同色，看不出主次 | `TextButton` 不传 `colors` 时用库默认 `textButtonColors()`，容器色是 `secondaryVariant` 浅灰（Miuix 的 TextButton 是填充胶囊，不是无底文字按钮） | 主要动作传 `ButtonDefaults.textButtonColorsPrimary()`（蓝底白字）；见 §2 第 9 条 |
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
| `build.gradle.kts` | 插件声明（`alias(libs.plugins.*)`，均 `apply false`） |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 版本 |
| `app/src/main/java/com/agon/app/ui/theme/MiuixRootTheme.kt` | 根主题 + 桥接 |
| `app/src/main/java/com/agon/app/ui/theme/ThemeStyle.kt` | 主题风格枚举 |
| `app/src/main/java/com/agon/app/ui/components/app/` | **App 级双主题外壳 —— Miuix API 调用最集中的一层**（`AppScaffold` / `AppTopBar` / 确认·表单·选项三类弹窗 / `AppText` / `AppButtons` / `AppIme` …）。组件清单与每个组件的关键约定见 [`docs/DESIGN_SPEC.md`](DESIGN_SPEC.md) **§4.1（由源码生成，本表不复述）** |
| `app/src/main/java/com/agon/app/ui/components/*.kt` | 复用组件（12 个文件 —— 09-19 #11d① 加 `StatsCharts.kt`、#11e 加 `CalendarMonthLayout.kt`；现值 = `ls app/src/main/java/com/agon/app/ui/components/*.kt | wc -l`，**单文件双主题** —— 分流在组件内部走 `LocalThemeStyle`，不是两份实现）；2026-09-16 由**已删除**的 `Common.kt` 拆出 8 个，另有原本就独立的 `UndoSnackbar.kt` / `ExpiryCalendar.kt` |
| `app/src/main/java/com/agon/app/ui/screens/*.kt` | 屏幕：9 个渲染文件 + 3 个设置页弹窗文件（`Settings*Dialogs.kt`，#10a-1 起，不是新增屏幕）+ 8 个 `*State.kt`。⚠️ **`Miuix*Screen.kt` 双胞胎已于 2026-09-16 全部删除**，别照旧清单去找「各页 Miuix 实现」—— Miuix 分支现在就写在同一个屏幕文件里 |
| 包根 `app/src/main/java/com/agon/app/*.kt` | `MainActivity` / `MainApp` / `AppNavGraph` / `NavChrome` / `BatchBars` —— 底栏四套形态、`NavDisplay` 转场与系统圆角、Snackbar / FAB / 批量栏都在这层调 Miuix API |
| `.claude/skills/miuix/` | skill（组件 API 证据基线） |
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
