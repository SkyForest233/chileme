# 开发日志索引（devlog/INDEX.md）

> 维护规则见 `CLAUDE.md` 第 3 节：每轮开发完成（构建成功）后写当日日志并更新本索引。

## 日志列表（新 → 旧）

| 日期 | 主题 | 构建状态 |
|---|---|---|
| [2026-09-16](2026-09-16.md) | **文档对账轮（不改运行时行为）+ `Common.kt` 拆分轮**：先 `git fetch` + 快进到最新 master（上一轮核查因落后 6 个提交而误报）→ 修 `ARCHITECTURE.md` 11 处（分层树缺 5 个文件、统计页 Miuix 化、备份排除规则精确化、37 个颜色角色、**FileProvider authority 硬编码是错的**、legacy 图标已删、release 2.4 MB）→ `DESIGN_SPEC.md` 2 处（**7 套配色实为 15 套**、§7 迁移进度补统计页/消耗记录页 + 双实现铁律 + 已知缺口）→ `CLAUDE.md` 3 处（删「统计页保留 MD3」错误约束、守卫升级为 key 粒度语义、文档索引补 MIUIX_UPGRADE）→ **`REQUIREMENTS.md` 补登记 F22–F43 共 22 项已上线未登记功能 + 修 §4「❌ 云同步」与 F20 自相矛盾** → `MIUIX_UPGRADE.md` 5 处（Gradle 9.7.1 / minSdk 26 / 依赖改走 Version Catalog 单一位点）→ `README.md` 4 处（「智能临期提醒」改口径并说明无系统推送、补齐已上线特性、隐私段落如实说明系统备份通道、JDK/门禁说明）→ 三份历史审查报告加**状态批注**（不改写原文：`chileme-review.md` 逐条现状 + **P0-6 主体论点不成立** + P3-1 Widget 撞需求红线 + 4 处计数订正；`project-review.md` §1.1–1.4 与 B5/B6/B7/B9 状态订正；`backlog.md` 校订对照表；`2026-09-15-code-review.md` §5 路线图逐条状态）→ `devlog/2026-09-15.md` 门禁分档更新（**ktlint 已恢复 block**、detekt 仍 report）+ 本 INDEX 两处自相矛盾修正 → 代码改动：`MiuixSettingsScreen.kt` KDoc 去掉不成立的「功能对等（含配色）」声明 → **同日第二轮：`ui/components/Common.kt`（983 行）拆成 8 个按职责命名的文件**（`StatusUi` / `Badges` / `FoodAvatar` / `QuantityStepper` / `FoodCard` / `Controls` / `DataCorrupt` / `MiuixDialog`；**同包纯搬运**，18 个声明逐字节校验通过，27 个调用方 import 零改动，顺手清掉 7 处死 import，并记录「机械拆分必漏 `getValue` 委托操作符 import」这一陷阱）→ **新发现并已修**：批量「移动存放位置」弹窗 MD3 分支缺 `DialogProperties(decorFitsSystemWindows = false)` + `imePadding()`（`MainActivity.kt:650`，`ImeHandlingTest` 第 3 条清单未覆盖故漏网；已补并把该文件纳入清单，**真机复测待做**）；连带把 `ImeHandlingTest` 第 2 条「imePadding 数 < navigationBarsPadding 数」这个会误判的代理指标换成直接检查导航栏段→ **写入路线图**：第二批 `MainActivity.kt` 拆分与第三批结构性 9 项（含双主题 7,205 行去重的分步与两个静态守卫的迁移）→ **同日第四轮：第二批拆分执行完毕（详见 §13）** —— `MainActivity.kt`（1,123 行）→ 6 个文件（`MainActivity` 118 / `MainApp` 335 / `AppNavGraph` 170 / `AppDialogs` 211 / `BatchBars` 213 / `NavChrome` 324，合计 1,371 行；同包 `com.agon.app`、消费方 import 零改动、`private→internal` 实测只需 8 处而非计划的 11 处；四步各一个提交，`ImeHandlingTest` 的文件清单与期望计数在**同一提交**内跟着搬，中间态不留红）；**CI 曾红一次**（run 35062328443），两处根因都不是手误而是「本地无 JDK + 用脚本搬代码」这个方式的必然产物：① `internal val MainTabs = listOf(TabSpec(...))` 的**推断类型**暴露了 `private data class TabSpec`（Kotlin 拒绝；且只有编 main 源集的 release job 撞得到，debug 的报错被 ② 盖住）② 拆分脚本用 Python 写出的 `Regex("/\*.*?\*/")` 在 Kotlin 里是**非法转义**（反斜杠没加倍）。ktlint/detekt 这类静态门**不编译**，两类都拦不住 → 已由 `2a71bc6` 修掉，并补了一条「`private` 顶层类型是否被 `internal`/`public` 声明暴露」的全量审计（本轮结果：0 处） | ✅ **CI 通过**（run [35060113017](https://github.com/SkyForest233/chileme/actions/runs/35060113017)：静态门禁 / debug APK+单测+lintDebug / release+R8+lintRelease 三个 job 全绿）—— 拆分后的 8 个文件编译通过、ktlint 零违规、单测全绿。✅ 弹窗键盘避让已由**用户真机复测通过**（2026-09-16）。✅ 第二批拆分后 run [35063573632](https://github.com/SkyForest233/chileme/actions/runs/35063573632)（①②③+修复）与 [35064714882](https://github.com/SkyForest233/chileme/actions/runs/35064714882)（④）三 job 全绿；中间的 [35062328443](https://github.com/SkyForest233/chileme/actions/runs/35062328443) 曾红（见左，已修）。⚠️ 沙箱无 JDK/SDK，逐条 CI 日志下载不到，只以 job 结论为准 |
| [2026-09-15](2026-09-15.md) | **两轮审查交叉验证后的 5 项修复**：读流兜底（异常不再杀进程）/ 启动放行超时 / 损坏态不再删封面图（P0）/ 过期浪费改按件数 / `MiuixStatsScreen` 接回状态层（消除静默分叉）+ 导入备份二次确认与导入前自动快照 + `MiuixParityTest` 静态守卫；另入库两份审查报告（自查 + 第三方复核）；**IME 键盘避让修复（A 方案 + 弹窗；真机实测通过）**；**写守卫按 key 粒度降级 + 放弃损坏数据入口 + 三条恢复路径前置快照 + 凭据加密失败不再静默 + 本地快照 IO 下沉与按件口径（含 `CorruptGuardTest` 守卫）**；**建议批：消耗记录「月度聚合」不可单删 + 归档单条删除二次确认 + CSV 公式注入防护 + `corrupt/` 留档上限 + 相机临时文件清理 + 导出文件名时间戳 + 文档漂移 10 处（含删除 `docs/audits/ci/*`）**；**性能批（用户选定）：同一份 JSON 只解一次（`DecodeCache`）+ `SettingsUiState` 重组粒度（19×`State` 精确订阅 + 本屏 UI 状态内置 + `SettingsActions` 窄接口）+ 仓储写守卫集成测试（注入临时 DataStore 的 8 例，纯 JVM、未用 Robolectric）**；**CI 门禁批：`tools/ci-gates.sh`（ktlint 6 条规则 + detekt 四个缺陷规则集，sha256 校验、无 Android SDK 也能跑）+ `.editorconfig`/`detekt.yml` 规则边界 + `build.yml` 新增 `static-gates` 与 `release-r8`（R8 / 资源压缩 / release lint）job，并清掉 11 处未使用 import** | ✅ CI 通过（含 lint 与单测，**116 例**；5 项修复 run 34917992215 → IME 轮 34922807620 → ①②③ 轮 34925050661 → 建议批 34927070965 → 性能批 **34930559493**；CI 门禁批的 workflow 与三件套均已入库 master 并跑通（PR #7 已 squash 合并为 `58534fd`；门禁收尾四处改动见 §14.10） |
| [2026-08-22](2026-08-22.md) | 修复跨零点 LocalToday 残留 (B-01) + 封面压缩 (B-04) + 引入 Version Catalog (B-02) + 升级过期依赖 (B-03) + 双主题状态层抽离与去重 (B-08) + README/LICENSE (B-05) + 状态层单测补全 (B-07) + 卡片紧凑布局与日期 (yyyy.MM.dd) + 首页清理撤销与触感振动 + 全局动效补齐 + **批量修改存放位置 + CSV 导出 + 本地内部滚动冷备 + 设置页排版精简 + MIUIX 独立 Window 弹窗** | ✅ CI 通过（含 lint 与单测） |
| [2026-08-21](2026-08-21.md) | Tab 连滑 → miuix-nav 卡片滑 → 撤销 6 秒 → History 圆环去指针 → 清冗余依赖与过时文档 → **构建/兼容/数据安全四阶段修复（release 签名失效 / minSdk→26 / 解码失败不再清空数据 / flow 去重+移出主线程）** | ✅ CI 通过（含 lint） |
| [2026-08-20](2026-08-20.md) | v2.8 引入 Miuix（HyperOS 风格）：安装 miuix-skill + 工具链升级（Kotlin 2.4.10/AGP 9.3.1/Gradle 9.6.1）+ ThemeStyle 枚举/持久化/LocalThemeStyle + 设置页双实现（MD3 + Miuix 组件）+ 文档同步 | ⚠️ 当时未验证（沙箱无 Android 工具链）→ ✅ **此后已由 CI 多轮 `assembleDebug` + 单测 + lint 验证**，Gradle 亦已升到 9.7.1 |
| [2026-08-01](2026-08-01.md) | v2.4 修复 7 项：孤儿图片清理 / 密码 Keystore 加密 / 消耗记录聚合 / 归档恢复去重 / 自动同步间隔 / 主题渐变 / 到期日历 → 日历并入统计页（紧急度彩点+滑动换月）/ R8 无混淆体积优化（release 2.6MB）→ 日历圆点高饱和 dot 色修复 → release 正式签名（keystore.properties 分离凭据）→ 修复 CI debug.keystore 缺失 → **v2.7 坚果云多版本备份轮转（云端保留 3 份+恢复选版本）** | ✅ 成功 |
| [2026-07-31](2026-07-31.md) | v1.0 基础版 → 需求沟通 → v2.0 功能增量 → 文档体系 → v2.1 薄荷绿改版/更名"吃了么" → v2.2 多主题/导航动画/CheckSwitch → v2.3 MD3 审计修复（62→91） → v2.3.1 用户反馈 7 项 → v2.3.2 用户反馈 8 项 → v2.4 吃完自动归档/OCR 增强/坚果云同步/标题简化 → v2.5 移除 OCR/长按批量归档/自定义矢量图标 → v2.5.1 图标留白/历史录入完整匹配 → v2.5.2 联想覆盖全部食品（含归档） | ✅ 成功 |

## 当前待办总览

> **2026-09-16 文档对账轮（现行待办以此段为准）**：本轮不改运行时行为，只把文档与代码对齐，详见 [2026-09-16 日志](2026-09-16.md)。
> - **已完成/已关闭**：~~`Common.kt`（983 行）拆分~~（2026-09-16 拆成 8 个按职责命名的文件，同包纯搬运、签名与实现未改）；~~`build.yml` 手动入库~~（`fbeb5dc` 已入库，且**顺带把 ktlint 恢复为 block 模式**）；~~`material-icons-extended` 迁移~~（已决策**保留** + 注释记录理由，实测 34 个图标而非 40）；~~文档漂移~~（本轮再修 12 份文档共 30+ 处，含 `REQUIREMENTS.md` 补登记 F22–F43、`ARCHITECTURE.md` 的 FileProvider authority 硬编码错误、`DESIGN_SPEC.md` 的「7 套配色」实为 15 套、三处「统计页保留 MD3」的错误约束）。
> - **已完成/已关闭（追加）**：~~批量「移动存放位置」弹窗的键盘避让~~（`MainActivity.kt:650` 补 `decorFitsSystemWindows = false` + `imePadding()`，**用户 2026-09-16 真机复测通过**）。
> - **仍待做（中）**：`photoPath` 绝对路径 + `File.exists()` 组合期调用（**行号已漂移：`FoodAvatar.kt:43`、`EditFoodScreen.kt:284`**）；`nutstorePasswordFlow` 明文常驻内存。
> - **仍待做（低 / 一行改动）**：**detekt 由 report 切 block**（`tools/ci-gates.sh:43`，基线已双零）；ktlint 全量规则集；详情页「吃掉一份」无撤销（`FoodDetailState.kt:48-50`）；归档 `take(200)` 独立计数器；恢复路径集成测试；三键导航 IME 复测；`ObsoleteSdkInt` 3 处；README 截图。
> - **结构性（部分已动）**：~~`Common.kt` 拆分~~ ✅ 09-16 已完成；~~第二批 `MainActivity` 拆分（1,123 行）~~ ✅ 09-16 已完成（→ 6 个文件，见 [09-16 日志](2026-09-16.md) §13）；**只剩第三批 9 项，完整方案见 [09-16 日志](2026-09-16.md)「🧭 拆分路线图」**（含每项的前置、实测证据与验收）；🟡 **进行中：第三批 #3 双主题去重 + App 级组件层**（8 对渲染层约 7,200 行，**已合并 5 对**：消耗记录 §15 / 归档 §16 / 详情 §17 / 首页 §18 / 列表 §19，累计净 -263 行，剩管理·统计·设置 3 对）；仍未动：错误模型统一（`Channel<UiEvent>` 全项目 0 处）、凭据拆库 + 备份规则调整、字符串资源化 + 无障碍（`contentDescription = null` 61 处 / `semantics` 7 处）、CI 供应链加固与 `versionCode` 改用仓库内版本文件。
> - **需用户决策**：`targetSdk 36→37`；Miuix `0.9.4-rc01`→稳定版；**备份规则的两面决策**（新发现：`device-transfer` 放行 `covers/` 会产生孤儿封面；`filesDir/snapshots/` 未被排除会随系统云备份走）；Splash 图标发糊；统计图表 semantics；对比度档位等 4 项体验增强。
> - **已按用户指示暂缓**：MIUIX 配色入口（`MiuixSettingsScreen` 的 KDoc「功能对等」声明本轮已改正，功能仍缺）、剩余统计口径（「本周」文案 / TOP5 按单位 / 分类占比）。
> - ⛔ **不要当待办推进**：`chileme-review.md` P3-1 建议的 Glance 桌面小组件 —— `REQUIREMENTS.md` §4「明确不做」第 3 条就是 Widget，需先由用户推翻边界。

> 2026-09-15 新增：审查报告 `docs/audits/2026-09-15-code-review.md`（自查）与 `docs/audits/2026-09-15-third-party-review-verification.md`（第三方复核）列出的待办，详见 [2026-09-15 日志](2026-09-15.md) 的「待办」表。已完成：~~IME 未处理~~、~~守卫粒度过粗~~、~~消耗记录月度聚合标记~~、~~文档漂移与 `docs/audits/ci/*` 清理~~、~~同一冷流 3 处收集~~、~~`SettingsState` 28-key 整体重组~~、~~仓储层守卫缺集成测试~~；当前最靠前的候选：`File.exists()` 组合期调用（`Common.kt:262`、`EditFoodScreen.kt:263`）+ `photoPath` 绝对路径、详情页「吃掉一份」无撤销、ktlint 全量规则集与 detekt 切拦截模式（见 [2026-09-15 日志](2026-09-15.md) §14.3/§14.4）、`build.yml` 手动入库（GitHub App 缺 `workflows` 权限）、`material-icons-extended` 迁移（约 40 个图标）；已按用户指示暂缓：MIUIX 配色入口、剩余统计口径（「本周」文案 / TOP5 按单位 / 分类占比）。
> *（上行是 09-15 当时的记录，其中行号、`build.yml` 入库状态与图标数已被上面 2026-09-16 段订正，保留仅为追溯。）*

> 2026-07-31 补充：日历圆点问题已修复（四档紧急度 + luminance 判深浅），详见当日日志第 7 轮。

| 优先级 | 事项 | 来源 | 状态 |
|---|---|---|---|
| ~~高~~ | ~~下个 Release 必须在说明中注明「签名已变更，请先导出备份、卸载旧版后再安装」~~ | 2026-08-21 阶段 1 | ✅ 已确认（发布时随附） |
| ~~中~~ | ~~fix-plan 阶段 5：纯函数单测基线~~（statusFor / compactConsumption / restoreArchived→planRestore / parsePropfind / BackupData v1→v2 兼容 / Decoded 三态） | 2026-08-21 | ✅ 已完成（新增 25 例，第 30 轮） |
| ~~中~~ | ~~fix-plan 阶段 6：跨零点刷新~~（可注入 today 的 `*At` + `LocalToday` CompositionLocal） | 2026-08-21 | ✅ 已完成（第 30 轮） |
| ~~中~~ | ~~fix-plan 阶段 6 其余：README / LICENSE 补充 (B-05)~~ | 2026-08-22 | ✅ 已完成（Apache-2.0） |
| ~~中~~ | ~~状态容器纯逻辑单测补全 (B-07)~~（Stats / FoodList / Archive / Home 纯函数单测） | 2026-08-22 | ✅ 已完成（当时新增 16 例；**2026-09-16 校正：仓库现有单测 116 例**，`grep -rh '@Test' app/src/test \| wc -l`，此前本行写的 91 例是 09-15 中途的数字） |
| 中 | fix-plan 阶段 6 其余：统计图表 semantics、Splash 图标发糊 | 2026-08-21 | 待讨论 |
| ~~中~~ | ~~fix-plan 阶段 7：MD3 / Miuix 双实现去重~~（8 对文件状态容器抽离，消除 ~1200 行重复代码） | 2026-08-21 | ✅ 已完成（2026-08-22） |
| ~~高~~ | ~~本地构建验证 v2.8~~（工具链升级 Kotlin 2.4.10/AGP 9.3.1/Gradle 9.6.1 + Miuix 0.9.4-rc01 依赖） | 2026-08-20 | ✅ 已由 CI 验证（此后每轮 PR 都跑 `assembleDebug` + 单测 + lint） |
| ~~低~~ | ~~归档恢复同名/同 ID 冲突策略~~ | 2026-07-31 | ✅ 已修复（v2.4，同批次合并数量/同 ID 防重复） |
| ~~低~~ | ~~移除封面时旧图片文件未清理~~ | 2026-07-31 | ✅ 已修复（v2.4，启动时 cleanupOrphanCovers） |
| ~~低~~ | ~~消耗记录上限 1000 条裁剪失真~~ | 2026-07-31 | ✅ 已修复（v2.4，90 天明细 + 月度聚合） |
| ~~低~~ | ~~主题切换颜色瞬切~~ | 2026-07-31 | ✅ 已修复（v2.4，animateColorScheme 全角色 450ms 渐变） |
| ~~低~~ | ~~WindowSizeClass API 未引入~~ | 2026-07-31 审计 | ❌ 不做（v2.6 用户确认无折叠屏，840dp widthIn 保留） |
| ~~低~~ | ~~折叠屏铰链姿态（tabletop/book）未适配~~ | 2026-07-31 审计 | ❌ 不做（v2.6 用户确认） |
| ~~低~~ | ~~归档搜索结果的恢复操作无撤销提示~~ | 2026-07-31 v2.3.2 | ✅ 已修复（全链路支持 6 秒撤销） |
| 低 | 高对比度模式（MD3 3 级对比度）未提供 | 2026-07-31 审计 | 待评估 |
| ~~高~~ | ~~【审计】CheckSwitch 缺 Role.Switch 语义~~ | MD3审计 | ✅ 已修复（v2.3，toggleable + Role.Switch） |
| ~~高~~ | ~~【审计】<48dp 触摸目标（QuantityStepper/设置步进器）~~ | MD3审计 | ✅ 已修复（v2.3，IconButton 恢复默认 48dp + minimumInteractiveComponentSize） |
| ~~中~~ | ~~【审计】shapes 未 token 化~~ | MD3审计 | ✅ 已修复（v2.3，自定义半径注册进 MaterialTheme.shapes） |
| ~~低~~ | ~~【审计】图表色不随主题变化~~ | MD3审计 | ✅ 已修复（v2.3，rememberChartColors 基于主题生成） |
| ~~中~~ | ~~【审计】悬浮导航未选中项仅图标无标签~~ | MD3审计 | ✅ 已修复（v2.6，图标+labelSmall 常显竖排） |
| ~~中~~ | ~~【审计】无窗口尺寸类适配：列表页 Medium+ 未切双栏 list-detail~~ | MD3审计 | ❌ 不做（v2.6 用户确认无折叠屏） |
| ~~低~~ | ~~【审计】悬浮导航槽位高 44dp~~ | MD3审计 | ✅ 已修复（v2.6，48dp） |
| 低 | 【审计】统计图表无 semantics，屏幕阅读器无法读取数据 | MD3审计 | 待评估 |
| ~~低~~ | ~~【审计】部分转场仍用线性 tween~~ | MD3审计 | ✅ 已修复（v2.6，Motion.kt 统一 MD3 缓动，无缓动 tween 清零） |
| 低 | 【审计】无 medium/high 对比度档位 | MD3审计 | 待评估 |
| 低 | 删除分类后孤儿记录回退显示“其他”，可考虑提供批量重新归类入口 | 2026-07-31 v2.3.1 | 待评估 |
| ~~低~~ | ~~坚果云同步无自动定时~~ | 2026-07-31 v2.4 | ✅ 已修复（2026-08-01，启动时按间隔自动上传，关/每天/3天/每周） |
| ~~低~~ | ~~坚果云应用密码明文存储~~ | 2026-07-31 v2.4 | ✅ 已修复（2026-08-01，Keystore AES-GCM 加密 + 旧数据自动迁移） |
| 低 | 日历视图可考虑增加周视图密度选项 | 2026-08-01 v2.4 | 待评估 |
| 低 | 自动同步可增加“仅 Wi-Fi”开关（当前备份体积小，暂不必要） | 2026-08-01 v2.4 | 待评估 |
| ~~低~~ | ~~上架前需配置正式 release 签名~~ | 2026-08-01 第2轮 | ✅ 已完成（第4轮，release.keystore + keystore.properties） |
| 中 | `photoPath` 存绝对路径（换机/清数据后悬空）+ `File.exists()` 在组合期同步调用 | 2026-09-16 复核 | 待做（`FoodAvatar.kt:43`、`EditFoodScreen.kt:284`） |
| 中 | `nutstorePasswordFlow` 把明文推进 StateFlow 常驻内存 | 2026-09-15 | 待做（09-15 只修了「加密失败不再静默」） |
| 低 | **detekt 由 report 切 block**（基线已双零，一行改动 + 一轮 CI 确认） | 2026-09-15 §14.4 | 待做（`tools/ci-gates.sh:43`）；ktlint 已于 `fbeb5dc` 恢复 block |
| 低 | 详情页「吃掉一份」无撤销（列表页减号反而有） | 2026-09-16 复核 | 待做（`FoodDetailState.kt:48-50`） |
| 低 | 归档 `take(200)` 静默丢最老记录 → 需独立计数器 + UI 提示 | 2026-09-15 | 待做（`FoodRepository.kt:487`、`:630`；09-15 只修了口径） |
| 低 | 恢复路径（导入/快照/云端）无集成测试，仍是静态断言 + 真机确认 | 2026-09-15 | 待做 |
| 低 | 三键导航下 IME 未单独复测（双重 padding 最易暴露） | 2026-09-15 | 待真机 |
| 低 | README / 商店截图缺失（B6/B7 的遗留部分） | 2026-09-16 复核 | 待做 |
| 需决策 | **备份规则的两面决策**：① `device-transfer` 放行 `covers/` 却排除 `datastore/` → 换机后产生孤儿封面；② `filesDir/snapshots/` 未被任何规则排除 → 每日快照随系统云备份走 | 2026-09-16 新发现 | 已登记进 `ARCHITECTURE.md` + README 如实说明，**行为未改**（等用户定方向） |
| 需决策 | CI 加固：`paths-ignore`、供应链（`verification-metadata.xml` / `dependency-review-action` / `gitleaks` / `zizmor`）、APK 体积基线、`bundleRelease`(AAB)、`versionCode` 改用仓库内版本文件（现为 `github.run_number`，`release.yml:112`） | 2026-09-16 复核 | 待做 |
| ⛔ | Glance 桌面小组件（`chileme-review.md` P3-1 的建议） | 2026-09-16 复核 | **不做**：撞 `REQUIREMENTS.md` §4「明确不做」第 3 条，需用户先推翻边界 |
