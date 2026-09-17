# 开发日志索引（devlog/INDEX.md）

> 维护规则见 `CLAUDE.md` 第 3 节：每轮开发完成（构建成功）后写当日日志，并在本索引更新一行。
>
> **本文件是索引，不是副本**（2026-09-17 起严格执行）：日志列表一天一行，细节留在当日日志；
> 待办只在本文件维护一份。此前 09-15 / 09-16 / 09-17 三行被写成了日志的浓缩副本
> （09-17 那格 37 行 ≈ 当日日志篇幅的 24%，07-31 那格 ≈ 50%），单元格里还嵌了换行，
> 导致 markdown 表格从第 10 行起断裂（那些行不以 `|` 开头，GitHub 上渲染是乱的）；
> 同一件事又在「待办总览」散文和「优先级表」两处各写一遍，单测数就因此在
> 91 → 116 → 117 → 119 → 120 之间被反复加注修正。
>
> 压缩前做过**信息保全核对**：原先只存在于本文件、当日日志里没有的 5 个 CI run 号与 6 个提交哈希，
> 已先补进 [2026-09-17 日志](2026-09-17.md) 末节「本日提交与 CI 台账」，本文件此后只留指针。

## 日志列表（新 → 旧）

| 日期 | 主题 | 状态 |
| --- | --- | --- |
| [2026-09-17](2026-09-17.md) | 真机复测收尾：① MD3 输入弹窗粘性键盘避让（`stickyImePadding`）② Miuix 表单弹窗输入框与按钮零间距 ③ Miuix 弹窗主要动作按钮改蓝底白字；随后形参窄化、`ImeHandlingTest` 加固、`AppDialogs.kt` 搬进组件层；**+ 文档审计轮 P0–P4**（INDEX 重写 -70%、6 份报告补批注、抽出 `docs/ROADMAP.md`、三份组件清单合一、新增 `tools/doc-metrics.sh` 口径脚本、删 1 个孤儿文件） | ✅ CI 最终全绿（当日 3 次红均已各自定位并修复）；**提交与 run 的计数不在本行手抄** —— 台账见日志末节（附取数命令）；单测 **121** 例 |
| [2026-09-16](2026-09-16.md) | 文档对账轮（12 份文档、30+ 处，不改运行时行为）+ `Common.kt` 拆 8 文件 + `MainActivity.kt`(1,123 行) 拆 6 文件 + 双主题 8 对全数合并（§15–§22）+ 写入拆分路线图 | ✅ CI 绿（中间红过一次，真因见日志 §13） |
| [2026-09-15](2026-09-15.md) | 两轮审查交叉验证后的 5 项修复 + IME 键盘避让（A 方案）+ 写守卫按 key 粒度 + 性能批（`DecodeCache` / 精确订阅）+ CI 门禁上线（`tools/ci-gates.sh` + 两个新 job） | ✅ CI 绿（当时单测 116 例） |
| [2026-08-22](2026-08-22.md) | 跨零点残留(B-01) / 封面压缩(B-04) / Version Catalog(B-02) / 依赖升级(B-03) / 双主题状态层抽离(B-08) / 批量改存放位置 / CSV 导出 / 本地滚动冷备 | ✅ CI 绿（含 lint 与单测） |
| [2026-08-21](2026-08-21.md) | Tab 连滑 → miuix-nav 卡片滑 / 撤销 6 秒 / 构建·兼容·数据安全四阶段修复（release 签名失效、minSdk→26、解码失败不再清空数据） | ✅ CI 绿（含 lint） |
| [2026-08-20](2026-08-20.md) | v2.8 引入 Miuix（HyperOS 风格）：技能库 + 工具链升级（Kotlin 2.4.10 / AGP 9.3.1）+ `ThemeStyle` 枚举与持久化 + 设置页双实现 | ⚠️ 当时未验证（沙箱无工具链）→ ✅ 此后 CI 多轮验证 |
| [2026-08-01](2026-08-01.md) | v2.4→v2.7：孤儿图片清理 / 密码 Keystore 加密 / 消耗记录聚合 / 归档恢复去重 / 到期日历并入统计页 / R8 体积优化 / release 正式签名 / 坚果云多版本备份轮转 | ✅ 成功 |
| [2026-07-31](2026-07-31.md) | v1.0 → v2.5.2：需求沟通、功能增量、文档体系、薄荷绿改版更名「吃了么」、MD3 审计修复（62→91）、用户反馈两批 | ✅ 成功 |

## 当前待办（2026-09-17 校准）

> **本节是唯一事实源（backlog）。** 分工：本节 = 完整待办清单；
> [`docs/ROADMAP.md`](../docs/ROADMAP.md) = 结构性改造 #1–#9 的**执行顺序 + 证据 + 验收 + 风险**；
> `tools/doc-metrics.sh` = 所有数字的**测量口径**。
> 路线图的历史原文在 [2026-09-16 日志](2026-09-16.md)「🧭 拆分路线图」（已冻结为快照，顶部有批注）；
> 历史 backlog 见 `docs/audits/2026-08-21-backlog.md`（顶部有 09-16 的状态校订表）。
> 括号里的数字是 2026-09-17 实测。**口径统一在一处**：`bash tools/doc-metrics.sh` 可一键复跑全部指标
> （作用域、出现次数 vs 命中行数、行数按 `wc -l`、零结果的阳性对照都写在脚本里）。
> 本文件与 docs/ 里的数字都应以该脚本为准 —— 此前正因手抄三份而漂移过（单测数、`corruptedKeys`、`now()` 三例）。

### 结构性（第三批 9 项；#1–#3 已完成，详见文末「已完成里程碑」）

- ⏳ **#4 错误模型统一** —— `Channel<UiEvent>` 全项目 **0** 处，错误提示目前靠 `MutableStateFlow<String?>`，
  多个订阅方会重复消费同一条。是 #5 的前置（Repository 拆分时顺带定型）。
- ⏳ **#5 Repository 拆分 + `Clock` 注入 + 轻量 DI** —— `FoodRepository.kt` **950 行**；`Application` 子类 **0** 个
  （DI 靠 `remember { … }` 现场构造）；java.time 的 `now()` 直接调用 **34** 处（其中 `LocalDate.now()` 26 处；时间不可注入 ⇒ 跨零点逻辑无法单测）。
  ⚠️ 会撞 `CorruptGuardTest`（它按缩进截函数体），**拆分与测试改动必须同一提交**。
- ⏳ **#6 派生数据下沉 VM + `WhileSubscribed`** —— `stateIn(` **20** 处但 `WhileSubscribed` **0** 处
  （全部 `Eagerly`，后台仍在算）。**本项范围已缩小**：屏幕层的聚合计算今日实测**已清零**
  （`sumOf {` / `groupBy {` / `count {` / `.sortedByDescending` 在 9 个屏幕文件里 0 处，`ScreenParityTest` 拦截；
  阳性对照：同一模式在 8 个 `*State.kt` 里命中 18 处）；宽口径只剩 5 处且都不是业务聚合
  （`EditFoodScreen` 3 处是输入框过滤数字字符、1 处是历史记录筛选，`StatsScreen:232` 是图表数据转换）。
  ⇒ 剩下的只是加 `WhileSubscribed` 与少量派生数据下沉，**且那是行为改动**（后台不再预热），需逐屏真机复测。
- ⏳ **#7 字符串资源化 + 无障碍** —— `strings.xml` **1** 条 vs 源码中文字面量 **583** 处；
  `contentDescription = null` **50** 处、`semantics` **1** 处。
  （09-16 记的 61 / 7 已随双主题去重下降，本行为 09-17 实测值。）
- ⏳ **#8 数据健康检查页 + 诊断包 + 许可清单** —— `corruptedKeys` 已被 **32** 处引用，但没有任何页面消费它，
  用户看不到「哪些数据坏了」。
- 🔶 **#9 CI 加固** —— 2026-09-17 用户指定只做「零件过期」与「依赖/密钥检查」两类：
  13 处 action 升级已改好、patch 已验证可用（`git apply --check` + `git diff` 核对），
  但沙箱的 GitHub App 令牌缺 `workflows` 权限 ⇒ **推不上去**，存为
  `docs/audits/2026-09-17-ci-actions-upgrade.patch` 待用户自己应用（详见 09-17 §10）。
  密钥泄漏已本地全历史扫描（207 提交）**确认干净**，故 gitleaks / zizmor / 依赖校验清单
  经成本收益复评**建议不做**（理由见 09-17 §10）。
  仍挂着未做：`paths-ignore`、APK 体积基线、`bundleRelease`(AAB)、
  `versionCode` 改用仓库内版本文件（现为 `github.run_number`，`release.yml:112`）。

### 代码级（中）

- `photoPath` 存绝对路径 + `File.exists()` 在组合期同步调用（`FoodAvatar.kt:43`、`EditFoodScreen.kt:284`）
  —— 主线程磁盘 IO，且换机/改包名后路径全废。
- `nutstorePasswordFlow` 把明文密码推进 `StateFlow` 常驻内存（Keystore 加密只覆盖了落盘，没覆盖内存）。

### 代码级（低）

- 详情页「吃掉一份」无撤销（`FoodDetailState.kt:48-49`；列表页的减号反而有 6 秒撤销）
- 归档 `take(200)` 静默丢最老记录（`FoodRepository.kt:487` / `:630`）→ 需独立计数器 + UI 提示
- 恢复路径（导入 / 快照 / 云端）无集成测试
- `ImeHandlingTest.codeOnly()` 改走词法状态机 —— 现用块注释正则，不认字符串字面量，
  `SettingsScreen.kt:379` 的 MIME 通配符会吞掉约 600 行真代码；`MiuixDialogContentTest` 里已有验证过的实现可抄
- `FoodCard.kt:161/251` 自己分流了一份进度条，与组件层 `AppLinearProgress` **不同构**（6dp vs `LinearProgressHeight = 8.dp`、`weight(1f)` vs `fillMaxWidth()`）⇒ 收编是**视觉改动**而非纯重构，需两主题真机复测（2026-09-17 文档审计时发现）
- ktlint 只开了 6 条规则（见 `.editorconfig`），全量规则集待评估
- `ObsoleteSdkInt` 3 处 · README / 商店截图缺失

### 体验（待评估）

- 统计图表无 semantics（屏幕阅读器读不出数据；MD3 审计遗留）
- Splash 图标 108dp 用低分辨率 drawable，深色模式下发糊
- MD3 对比度档位：medium / high（现只 hardcode 1 档）
- 日历周视图密度 · 自动同步「仅 Wi-Fi」开关 · 删除分类后孤儿记录的批量重新归类入口

### 需用户决策

- **备份规则两面**：① `device-transfer` 放行 `covers/` 却排除 `datastore/` ⇒ 换机后封面变孤儿；
  ② `filesDir/snapshots/` 未被排除 ⇒ 每日快照随系统云备份一起走（占空间）。
  已如实登记进 `ARCHITECTURE.md` + README，**行为未改**，等用户定调。
- `targetSdk 36 → 37`（detekt `OldTargetApi`）· Miuix `0.9.4-rc01` → 稳定版（等上游发版）

### 已决策不做（别再提）

- **Glance 桌面小组件** —— 撞 `REQUIREMENTS.md` §4「明确不做」第 3 条，需用户先推翻边界才能做
- **`WindowSizeClass` / 折叠屏铰链姿态 / 列表页 Medium+ 双栏** —— v2.6 用户确认无折叠屏
  （`840dp` 的 `widthIn` 保留，作为平板上的最大宽度约束）

### 已按用户指示暂缓

- MIUIX 配色入口：`MiuixRootTheme` 已打通种子色通道，但设置页还没给入口
- 剩余统计口径：「本周」文案与实际 7 天窗口不一致 / TOP5 按单位混排 / 分类占比分母

---

## 已完成里程碑（一行一条，细节见对应日志）

**2026-09-17**：`AppNavHost` 形参 9→6、`BatchMoveLocationDialog` 8→5（`df2f61c` 红一次，真因见 §7）·
`ImeHandlingTest` / `MiuixParityTest` 加固为「清单存在性 + canary」（单测 120→121）·
`AppDialogs.kt` → `ui/components/app/AppBatchMoveDialog.kt` · 三键导航下 IME 用户实机复测通过

**2026-09-16（第三批 #1–#3）**：detekt 由 `report` 切 **block**（`tools/ci-gates.sh:52`）+ `detekt_selftest` 防空转 ·
复杂度规则开启：首跑 73 条发现，其中 2 条（`ComplexCondition` / `LoopWithTooManyJumpStatements` 各 1 处）
**改代码修掉、规则保留**，前 4 条体量规则共 71 处**显式关闭**（`LongMethod` 33 / `LongParameterList` 24 /
`TooManyFunctions` 7 / `CyclomaticComplexMethod` 7），理由与命中清单见 `detekt.yml` 文件头· 双主题去重 + App 级组件层：`Miuix*Screen.kt` 双胞胎 **8 对 → 0**
（⚠️ 原验收「4,500 量级 / −24%」**未达成**，根因见 09-16 §22）

**2026-09-15**：`MiuixParityTest` 静态守卫上线 · CI 门禁（ktlint 6 条 + detekt 四规则集 + sha256 校验）

**2026-08-22（无障碍与备份批）**：`contentDescription = null` 112 处补齐（⚠️ 09-17 实测仍余 **50** 处，未清零）·
坚果云备份漏 `covers/` → `nutstore-backup` 加目录 + 恢复校验/迁移 · 导出 JSON 被系统媒体库收录 → 移入应用专属目录 +
清孤儿旧文件 · 坚果云同步失败静默 → 错误分类/诊断/失败留档 · 备份/导出文件不可见 → SAF 导出 + FileProvider 分享 ·
`device-transfer` 无二次确认 → 风险说明弹窗 · 备份无校验和 → `manifest.json` + SHA-256 + 损坏标记 ·
状态容器纯逻辑单测补全（B-07，+16 例）· fix-plan 阶段 6 其余：README / LICENSE（Apache-2.0）·
fix-plan 阶段 7：8 对双实现状态容器抽离（−~1,200 行）

**2026-08-21**：fix-plan 阶段 5 纯函数单测基线（+25 例）· 阶段 6 跨零点刷新（可注入 `today` + `LocalToday`）·
release 签名失效 → 正式签名 + `keystore.properties` 分离凭据，并约定发布说明须注明
「签名已变更，请先导出备份、卸载旧版后再安装」

**2026-08-20**：v2.8 工具链升级本地无法验证 → 改由 CI 每轮 `assembleDebug` + 单测 + lint 验证

**v2.3 – v2.6（MD3 审计与用户反馈）**：归档恢复同名/同 ID 冲突策略（合并数量/防重复）· 移除封面时旧图片未清理
（启动 `cleanupOrphanCovers`）· 消耗记录上限 1000 条裁剪失真（90 天明细 + 月度聚合）· 主题切换颜色瞬切
（`animateColorScheme` 全角色 450ms）· 归档搜索结果的恢复操作无撤销（全链路 6 秒撤销）·
`CheckSwitch` 缺 `Role.Switch` · <48dp 触摸目标 · shapes 未 token 化 · 图表色不随主题 ·
悬浮导航未选中项无标签 · 槽位 44dp→48dp · 转场线性 tween → `Motion.kt` 统一 MD3 缓动

**坚果云**：同步无自动定时 → 启动时按间隔自动上传（关/每天/3 天/每周）· 应用密码明文存储 →
Keystore AES-GCM 加密 + 旧数据自动迁移

---

**单测数演变**：91（09-15 中途）→ 116 → 117 → 119 → 120（09-16/09-17）→ **121**（09-17，现值）。
此前这几个数字在本文件里被逐层加注修正过三次，现已收敛为一行；各时点的口径见对应日志。
