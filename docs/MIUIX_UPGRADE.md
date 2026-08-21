# Miuix 升级指南（AI Agent 操作手册）

> 本文件用于在「升级 Miuix 版本」时，由 AI Agent 阅读并执行。
> 目标项目：「吃了么」家庭零食柜（`SkyForest233/chileme`），含 Material 3 与 MIUIX 双主题。

---

## 0. 升级前必读的前提

1. **Miuix 是 KMP 库，版本与工具链强绑定**。每个 Miuix 版本都要求特定的 Kotlin / AGP / Compose 版本。升级 Miuix 大概率要**连带升级整套工具链**，不是只改依赖版本号。
2. **本项目当前锁定在 `0.9.4-rc01`（候选版）**，工具链为 Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.6.1 / compileSdk 37 / minSdk 24。
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

`app/build.gradle.kts` 中三处（注意保持 common 坐标，勿加 `-android` 后缀）：

```kotlin
implementation("top.yukonga.miuix.kmp:miuix-ui:<新版本>")
implementation("top.yukonga.miuix.kmp:miuix-preference:<新版本>")
implementation("top.yukonga.miuix.kmp:miuix-icons:<新版本>")
```

### 第 3 步：连带升级工具链（若基线变化）

- 根 `build.gradle.kts`：Kotlin 插件版本（`org.jetbrains.kotlin.plugin.compose` / `plugin.serialization`）+ AGP（`com.android.application`）。**注意：本项目是 AGP 9 内置 Kotlin，勿重新加 `org.jetbrains.kotlin.android`**。
- `gradle/wrapper/gradle-wrapper.properties`：Gradle distributionUrl。
- `app/build.gradle.kts`：`compileSdk`（如需更高）。

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
7. **minSdk 24 不变**（除非新 Miuix 强制要求更高，需评估）。

---

## 3. 常见坑（本项目历史踩过的）

| 症状 | 原因 | 处理 |
|---|---|---|
| `checkDebugAarMetadata` 报 compileSdk 不足 | Miuix 依赖要求更高 compileSdk | 升 `compileSdk` |
| `Unresolved reference 'kotlin.android'` 或内置 Kotlin 冲突 | AGP 9 内置 Kotlin | 移除 `org.jetbrains.kotlin.android` 插件 |
| Maven Central 403 | CI 共享 IP 被限流 | gradle.properties 已加重试，若仍失败重跑 |
| OverlayDialog 不显示 | 弹窗在 Scaffold content 外 | 移入 content lambda |
| `Key was already used` 闪退 | LazyColumn key 冲突 | 用 `itemsIndexed` + index 兜底 |
| 注释里 `*/` 导致编译错误 | 块注释被 `*/` 提前闭合 | 避免在注释里写 `inverse*/` 这类 |
| 同包同名枚举 Redeclaration | MD3/MIUIX 文件重名 | 用 `MiuixXxx` 前缀区分 |

---

## 4. 交付检查清单

升级完成后，逐项确认：

- [ ] 依赖版本已改，工具链（Kotlin/AGP/Gradle/compileSdk）已对齐目标版本基线
- [ ] `./gradlew assembleDebug`（或 CI）通过
- [ ] 所有 Miuix API 调用已对照新版本 source 核对，无臆造签名
- [ ] MD3 主题未受影响（未改 MD3 页面代码）
- [ ] 双主题切换、弹窗、图标、squircle 等关键路径回归正常
- [ ] devlog 记录本次升级（改了哪些版本、遇到哪些迁移项）
- [ ] 若上游仍是候选版，在文档中标注「非稳定版」风险

---

## 5. 附：本项目 Miuix 相关文件索引

| 文件 | 作用 |
|---|---|
| `app/build.gradle.kts` | Miuix 依赖 + compileSdk |
| `build.gradle.kts` | Kotlin/AGP 插件版本 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 版本 |
| `app/src/main/java/com/agon/app/ui/theme/MiuixRootTheme.kt` | 根主题 + 桥接 |
| `app/src/main/java/com/agon/app/ui/theme/ThemeStyle.kt` | 主题风格枚举 |
| `app/src/main/java/com/agon/app/ui/screens/Miuix*.kt` | 各页 Miuix 实现 |
| `app/src/main/java/com/agon/app/ui/components/Common.kt` | 复用组件（双实现） |
| `.claude/skills/miuix/` | skill（组件 API 证据基线） |
| `docs/audits/2026-08-20-miuix-review.md` | 设计审查报告 |
