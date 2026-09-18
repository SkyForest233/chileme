# 开发流程与代码规范（WORKFLOW）

## 1. 标准执行步骤（每个开发任务）

1. **读指引**：读 `CLAUDE.md`，确认任务涉及的标准文档并阅读
2. **对需求**：对照 `docs/REQUIREMENTS.md`：
   - 在范围内 → 直接开发
   - 超出范围（尤其命中“明确不做”清单）→ 先与用户沟通确认，确认后更新需求文档再动手
3. **设计方案**：确定改动范围（数据模型？路由？新屏幕？），对照 `docs/ARCHITECTURE.md` 的分层与约定
4. **编码**：按本文件第 2 节代码规范与 `docs/DESIGN_SPEC.md` UI 规范实现；改动顺序建议：data → viewmodel → components → screens → 导航
5. **构建验证**：`./gradlew assembleDebug` 必须成功（沙箱无工具链时靠 CI）；失败则按第 4 节排错后重建，禁止以失败状态交付
6. **写日志**：按 `CLAUDE.md` 第 3 节规则写 `devlog/YYYY-MM-DD.md` 并更新 `devlog/INDEX.md`
7. **同步文档**：若改动影响需求/架构/设计规范，同步更新对应 docs 文件
8. **总结**：向用户简要汇报完成内容与关键决策

## 2. 代码规范

### Kotlin / Compose
- 包名：一律 `com.agon.app` 或子包；文件首行 package 声明必须正确
- 只用 material3 + Miuix 两套 UI，禁止混入 material2
- 导入显式写全；每个 Modifier 扩展、图标、组件单独 import
- 局部状态 `remember { mutableStateOf() }`；需进程重建保持用 `rememberSaveable`
- 列表用 LazyColumn/LazyRow + `items(key = ...)`，不用 forEach 堆叠
- 协程：组合内用 LaunchedEffect；事件回调里用 `rememberCoroutineScope().launch`；禁止在 onClick 中直接调 @Composable
- 二级页栈：`rememberNavBackStack` 只在 `MainApp.kt` 声明一次（导航状态源唯一）；全 App 唯一的 `NavDisplay` 在 `AppNavGraph.kt` 的 `AppNavHost` 里，由 `MainApp.kt` 的内容槽位调用一次。往下只传回调（`navigate`/`popRoute`），不传 backStack 本身。底栏 Tab 不走导航栈（HorizontalPager，在 `NavChrome.kt`）

### 项目约定
- 数据读写只走 `FoodRepository`；新增持久化字段时：模型加默认值（保证旧数据兼容，Json 已配 `ignoreUnknownKeys`）→ Repository 增方法 → ViewModel 暴露 → UI
- 删除类操作走归档 `archiveItems()`，不得直接从库存 JSON 中移除（归档页除外）
- 状态判定用 `item.statusFor(thresholds)`，禁止硬编码 7 天
- 颜色不得在屏幕代码写死主题色值；状态色走 `rememberStatusUi()`；统计图表调色板例外（见 `ui/components/app/AppText.kt` 的 `appChartColors()`，两主题各一份 8 色清单）
- 字符串目前直接写在代码中（中文单语言）；app_name 必须在 strings.xml 维护

### 新增依赖
1. 先用 `search_maven` 确认坐标；2. 加入 `app/build.gradle.kts`；3. 在 `gradle/libs.versions.toml` 登记版本与别名（依赖坐标不在 `app/build.gradle.kts` 里写死）；4. 立即构建验证；5. 在 `docs/ARCHITECTURE.md` 依赖清单登记

## 3. 构建与验证

- 构建命令：`./gradlew assembleDebug`（沙箱无 JDK/SDK 时看 GitHub Actions `Build`）
- 产物：`app/build/outputs/apk/debug/app-debug.apk`
- 频率：每完成一个功能模块就构建，不要积攒大量改动后一次性构建

### CI 静态门禁（ktlint + detekt，2026-09-15 起）

- 入口脚本：`tools/ci-gates.sh`（工具版本与 sha256 固定在此文件里）；规则配置：`.editorconfig`（ktlint）、`detekt.yml`（detekt）
  **这三个文件必须同时在目标分支上**：缺 `.editorconfig` 时 ktlint 会退回默认全量规则集（一次报上百条格式违规），缺 `detekt.yml` 时 detekt CLI 直接抛异常——脚本已加防呆检查并给出明确报错，但根因是文件没凑齐，不要靠防呆兜着走
- 本地跑法（首次会下载约 136 MB 工具到 `~/.cache/chileme-gates`，之后走缓存；不需要 Android SDK）：

  ```bash
  bash tools/ci-gates.sh                     # 默认：ktlint 与 detekt 都拦截（2026-09-16 起）
  GATES_MODE=report bash tools/ci-gates.sh   # 两个都只报告（收敛规则时用）
  DETEKT_MODE=report bash tools/ci-gates.sh  # 只把 detekt 降回报告
  ```

- CI：`.github/workflows/build.yml` 的 `static-gates` job（PR 与 master 推送都会跑）。报告写 `build/reports/gates/`，CI 里同时上传为 `gate-reports` artifact 并打到日志。
- **两个门禁现在都是 block**（2026-09-16 起）：detekt 从 report 切 block 的前提是清单归零 —— 修好空转问题后第一次拿到真实清单是 **73 条 / 6 个规则**，其中 2 条改代码修掉、71 条体量指标（`LongMethod` 33 / `LongParameterList` 24 / `TooManyFunctions` 7 / `CyclomaticComplexMethod` 7）在 `detekt.yml` 里**显式关闭并写明实测数字与归口**（路线图 #3 去重、#5 拆 Repository/VM）。**2026-09-17 复评**：App 级组件里的 `AppNavHost` 已由 9 参收窄到 6、`BatchMoveLocationDialog` 由 8 参收窄到 5（两者都是 09-16 文件头里写好的方案），但 `MainTabsPager` 11 参与 `*State.kt` 状态容器（刻意摊平）仍在清单里 ⇒ `LongParameterList` **维持关闭**，重开需要先给状态容器那类做规则豁免。`potential-bugs` / `coroutines` / `empty-blocks` / `performance` / `exceptions` / `style` 六类**全为 0**，即「疑似缺陷」那部分本来就是干净的。
- **`detekt_selftest`（门禁自身健康检查）**：每次门禁先用一个含必然命中违规的临时文件（生成在 `build/reports/gates/selftest/`，不在 `app/src` 下，故不会被 ktlint 主扫描收到）跑一遍 detekt；**命中 0 条即 `::error::` 并判红，与 `DETEKT_MODE` 无关**（哨兵码 91）。这条是为了防止 2026-09-16 那种「门禁静默空转、报告恒为 0」再发生一次 —— 空转的门禁比没有门禁更糟，它提供虚假的安全感。若你有意关掉了自测里用到的规则，请同步改自测文件。
- 规则边界（为什么只开这几条）写在 `.editorconfig` 与 `detekt.yml` 的文件头，改规则前先读；**未开启 ≠ 遗漏**，多为「已有明确后续计划」或「对本项目属主观项」。
- ⚠️ **`detekt.yml` 是覆盖层，必须与 `--build-upon-default-config` 同用**（2026-09-16 实测）：只给 `--config` 时 detekt 不拿默认配置当基线，**文件里没逐条列出的规则一律不激活** —— 规则集写着 `active: true` 也白搭，报告恒为 0 条，门禁静默空转。脚本已固化该参数，并加了 `detekt_selftest`：每次门禁先用一个含 3 类必然命中违规的临时文件（放在 `build/reports/gates/selftest/`，不进 `app/src`，故不被 ktlint 主扫描收到）验一遍，**命中 0 条即判红，与 `DETEKT_MODE` 无关**。
- 发现清单怎么读：CI 日志与 artifact 都托管在 `results-receiver` / `*.blob.core.windows.net`，受限网络里下载不到；脚本已把 ktlint/detekt 的发现**按规则聚合**成 check-run annotation（`gh api repos/SkyForest233/chileme/check-runs/<job-id>/annotations`），另有「detekt 自测」「控制台尾部」「报告文件行数」三条 notice 作为门禁健康度探针。GitHub 每级别最多留 10 条 annotation，故聚合而非逐条，完整清单仍以 artifact 为准。
- **CI 红了怎么拿日志**（2026-09-18 实测）：`gh run view --log-failed` 与 `gh api …/jobs/<id>/logs` 都指向
  `results-receiver.actions.githubusercontent.com`，受限网络里 EOF；沙箱 `curl` 直连 Azure blob 也被墙
  （`SSL_ERROR_SYSCALL`）。**能用的路子**：请用户从 Actions 页面复制那个 job 的日志签名 URL
  （`productionresultssa*.blob.core.windows.net/…/job-logs.txt?sig=…`，约 10 分钟时效），
  再用**网页抓取工具**读（不是 `curl`）⇒ 能拿到全文，含 `e: file:///…:行:列 Unresolved reference` 这种精确错误。
  **规矩：CI 红了先要日志，别先按排除法猜**（09-18 那次先猜了 5 个方向全不中，日志到手 30 秒定位）。
  拿不到日志时还有两个旁证：`gh api …/check-runs/<job_id>/annotations`（例如「没有测试报告 / 没有 lint 报告」
  ⇒ 红在编译阶段、单测没跑起来）与 `gh run view --json jobs` 的步骤级结论 + 起止时间。
- **`gh` 自己也 401 时**（令牌失效，但 `git push` 用的另一条凭据还活着 ⇒ 代码推上去了、run 也起来了，只是读不到结论；2026-09-18 第二次实测）：还有两条**不需要登录**的公开通道 ——
  ① **badge**：`https://github.com/<owner>/<repo>/actions/workflows/build.yml/badge.svg?branch=<分支>` 直接给出 `Build - passing / failing`（拿 `?branch=master` 当对照，证明 badge 本身没坏）；
  ② **run 页面的 HTML**（`…/actions/runs/<id>`，用网页抓取工具读）：`Status`、逐 job 结论与时长、Annotations 全文 —— "Process completed with exit code 1" 带 `#step:N:M` 指向具体步骤，产物步骤的 "No files were found" 能反推"红在编译期、单测没跑起来"。
  **次序：badge → run 页面 HTML → 才请用户复制签名 URL**（这次没打扰用户就定位到了阶段与步骤）。
- 升级工具版本：改 `tools/ci-gates.sh` 里的版本号 + sha256，并同步改 `build.yml` 里 `actions/cache` 的 key。
- release 侧另有 `release-r8` job：每个 PR 都跑 `assembleRelease -PallowUnsignedRelease=true`（R8 + 资源压缩 + `lintRelease`），因为这类问题只在 release 构建出现。
- 提交代码前建议先跑一次门禁，比等 CI 反馈快。

## 4. 常见错误处理

| 错误 | 原因与处理 |
|---|---|
| Unresolved reference 'X' | 缺 import 或拼写错误；检查文件顶部导入。⚠️ **搬代码进新文件时最容易漏的是「别名 import」**：本仓双主题组件层有 25 条 `import top.yukonga.miuix.kmp.basic.Text as MiuixText` 这类别名，按「简单名 = 路径最后一段」算依赖的脚本看不见它们（09-18 #10a-1 因此红了 24 处）。本地 `python3 tools/kt-lexcheck.py` 的判据 4 专查这条。⚠️ **09-18 #10a-2 又漏了另一半**：按「大写开头、不含下划线」算依赖的脚本看不见**小写扩展函数/属性**（`dp` / `padding` / `fillMaxWidth` / `launch`）、**全大写常量**（`CLOUD_BACKUP_KEEP`）与**点号后面的大写成员**（`Icons.Rounded.Cloud`），5 个文件少 41 条 import ⇒ 同样红在编译，而 `kt-lexcheck` 判据 1–4 全绿。搬完代码跑 `python3 tools/move-importcheck.py --old <搬家前的 git 引用> --new <搬家后的文件…>`（拿旧文件的 import 表当参照物；两次历史真红都能逐条复现，`--selftest` 有 4 个合成对照，`--apply` 直接补齐）。它常伴着一串「`@Composable` invocations can only happen from the context of a `@Composable` function」——那是未解析的 Composable 尾随 lambda 引起的**连带错**，补好 import 就一起消失，别当成第二个问题去修 |
| Unresolved reference: R | res/ 文件有误（常见 strings.xml / xml 格式） |
| @Composable invocations... | 在非 Composable 作用域（onClick/协程）调了 Composable；把值提前在组合作用域获取 |
| Platform declaration clash | 为 `var x` 又手写了 `fun setX()`；删掉手写 setter |
| mergeDebugResources 失败 | XML 格式错误，检查最近改过的 res 文件 |
| 同错误重复 2 次+ | 停下来读完整报错 → read_file 定位 → 换思路；必要时 `./gradlew clean --no-daemon` |

## 5. 文档维护责任

| 改动类型 | 需同步更新 |
|---|---|
| 新增/删减功能 | REQUIREMENTS.md 功能清单 + devlog |
| 数据模型/存储 key/路由/依赖变化 | ARCHITECTURE.md 对应表格 |
| 新增复用组件/调整视觉规范 | DESIGN_SPEC.md |
| 流程/规范本身调整 | WORKFLOW.md + CLAUDE.md |
| CI 门禁规则调整（`.editorconfig` / `detekt.yml` / `tools/ci-gates.sh`） | WORKFLOW.md §3 + devlog |
| 任何一轮开发完成 | devlog/YYYY-MM-DD.md + devlog/INDEX.md（强制） |
