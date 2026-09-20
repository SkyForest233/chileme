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

### 本地构建环境自检（2026-09-19，M1-5）

「沙箱里没有 JDK/SDK ⇒ CI 是唯一的编译裁判」一直是本仓的**口头约定**。它的代价不体现在构建上，
而体现在**决策上**：没有本地编译器，"改动量大但结构更对"的重构（#10c 每屏一 VM、DataStore→Room）
就永远排不进队列 —— 一次编译失败要烧一整轮 CI（约 4 分钟，且 `concurrency` 会取消同分支中间的 run），
错误还得从 actions 日志里人肉捞。于是这个环境事实必须先被**变成可判定的**，才谈得上摆脱它。

```bash
bash tools/bootstrap-build-env.sh              # --check：只读自检，非零退出码 = 这台机器编不了
bash tools/bootstrap-build-env.sh --install-sdk  # 补 Android SDK 命令行工具（不含 JDK）
bash tools/bootstrap-build-env.sh --verify       # 跑一次 :app:assembleDebug 作为唯一判据
bash tools/bootstrap-build-env.sh --bootstrap      # = 上面两条
```

- 它**只读 + 只装 SDK 目录**，不碰仓库、不改 `gradle.properties`。发现的三件事各归各位：
  JDK（`REQUIRED_JDK=21`）与 platform（`android-36`）都是**从别处抄来的常量**，脚本头部写明了
  改哪边必须同步改这里；`kotlin.compiler.execution.strategy=in-process` 那条只提示不动手
  （动它会让全部增量构建重编，属独立一轮）。
- 没有外网的机器（含 Agent 沙箱）会在 `--install-sdk` 第一步就明确报"这台机器无解，交给 CI"，
  **不给假的绿** —— 这个脚本的价值全在诚实：判据、缺哪个组件、怎么补，都当场给出来。
- ⚠️ 它**不进 CI**：CI 已经有 JDK 与 SDK（`setup-java` + `android-actions/setup-android`），
  在 CI 里跑一遍只会多一个"自检也依赖网络"的假失败面。门禁仍然只有 `tools/ci-gates.sh` 那两个工具。

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
- **CI 红了怎么拿日志**（2026-09-18 实测；同日第二轮补上「自己取签名 URL」这条路，**不必再打扰用户**）：
  `gh run view --log-failed` 与 `gh api …/jobs/<id>/logs` 都指向
  `results-receiver.actions.githubusercontent.com`，受限网络里 EOF；沙箱 `curl` 直连 Azure blob 也被墙
  （`SSL_ERROR_SYSCALL`）。**能用的路子（令牌活着时自己就能拿到全文）**：
  ① 向 api.github.com 要那条日志的 **302 跳转地址**（只取响应头、不跟随跳转）：
  `curl -sS -o /dev/null -D - -H "Authorization: Bearer $GH_TOKEN" https://api.github.com/repos/<owner>/<repo>/actions/jobs/<job-id>/logs`
  ⇒ 响应头 `location:` 就是签名 URL（`productionresultssa*.blob.core.windows.net/…/job-logs.txt?…sig=…`）；
  ② 把那条 URL **原样**交给**网页抓取工具**读（不是 `curl`）⇒ 拿到全文，含
  `e: file:///…:行:列 Unresolved reference` / `… cannot serve as a delegate` 这种精确错误。
  三个坑（都踩过）：签名 URL **约 10 分钟时效**（过期就重走 ①，别复用旧的）；URL 必须**逐字**传给抓取工具
  （传成别的地址会回一个 `SignatureDoesNotMatch` 的 XML，看着像"日志端点被墙"，其实是自己传错了）；
  日志**分段返回**（这次一个 job 分 5 段，报错在 `> Task :app:compileDebugKotlin FAILED` 那一段附近）。
  **09-19 #11 再验一次有效**（`arrayArrayOf` 与 `it is private in file` 两条 `e:` 就是这么拿到的，一次跑通、没打扰用户）；同一天撞到一个新的环境约束：**本 session 的 GitHub App 没有 `workflows` 权限** ⇒ 任何改 `.github/workflows/**` 的提交推不上去（`remote rejected: refusing to allow a GitHub App to create or update workflow ... without workflows permission`）⇒ 要动 CI 定义得由用户自己应用与推送；能自己改的只有 `tools/` 与构建脚本。**别为此写一个「失败时 echo annotation」的步骤**——日志本来就读得到，那一步是多余的。
  **规矩：CI 红了先要日志，别先按排除法猜**（09-18 那次先猜了 5 个方向全不中，日志到手 30 秒定位）。
  令牌也死了才退回旁证：`gh api …/check-runs/<job_id>/annotations`（例如「没有测试报告 / 没有 lint 报告」
  ⇒ 红在编译阶段、单测没跑起来）与 `gh run view --json jobs` 的步骤级结论 + 起止时间，以及请用户复制签名 URL。
- **`gh` 自己也 401 时**（令牌失效，但 `git push` 用的另一条凭据还活着 ⇒ 代码推上去了、run 也起来了，只是读不到结论；2026-09-18 第二次实测）：还有两条**不需要登录**的公开通道 ——
  ① **badge**：`https://github.com/<owner>/<repo>/actions/workflows/build.yml/badge.svg?branch=<分支>` 直接给出 `Build - passing / failing`（拿 `?branch=master` 当对照，证明 badge 本身没坏）；
  ② **run 页面的 HTML**（`…/actions/runs/<id>`，用网页抓取工具读）：`Status`、逐 job 结论与时长、Annotations 全文 —— "Process completed with exit code 1" 带 `#step:N:M` 指向具体步骤，产物步骤的 "No files were found" 能反推"红在编译期、单测没跑起来"。
  **次序（令牌死时）：badge → run 页面 HTML → 才请用户复制签名 URL**（这次没打扰用户就定位到了阶段与步骤）。
  令牌活着时不必降到旁证：走上一条「自己取签名 URL」就能拿到编译器原文。
- 升级工具版本：改 `tools/ci-gates.sh` 里的版本号 + sha256，并同步改 `build.yml` 里 `actions/cache` 的 key。
- release 侧另有 `release-r8` job：每个 PR 都跑 `assembleRelease -PallowUnsignedRelease=true`（R8 + 资源压缩 + `lintRelease`），因为这类问题只在 release 构建出现。
- 提交代码前建议先跑一次门禁，比等 CI 反馈快。

## 4. 常见错误处理

| 错误 | 原因与处理 |
|---|---|
| Unresolved reference 'X' | 缺 import 或拼写错误；检查文件顶部导入。⚠️ 搬代码进新文件时**第二种**最容易漏的是「新引入的类型名」：参数表里写了原文件从没 import 过的类型（`List<Color>`）⇒ 以「原文件 import 表」为全集的检查结构上看不见它，只有 kotlinc 会红 ⇒ `python3 tools/kt-name-audit.py <新文件…>`。⚠️ **搬代码进新文件时最容易漏的是「别名 import」**：本仓双主题组件层有 25 条 `import top.yukonga.miuix.kmp.basic.Text as MiuixText` 这类别名，按「简单名 = 路径最后一段」算依赖的脚本看不见它们（09-18 #10a-1 因此红了 24 处）。本地 `python3 tools/kt-lexcheck.py` 的判据 4 专查这条。⚠️ **09-18 #10a-2 又漏了另一半**：按「大写开头、不含下划线」算依赖的脚本看不见**小写扩展函数/属性**（`dp` / `padding` / `fillMaxWidth` / `launch`）、**全大写常量**（`CLOUD_BACKUP_KEEP`）与**点号后面的大写成员**（`Icons.Rounded.Cloud`），5 个文件少 41 条 import ⇒ 同样红在编译，而 `kt-lexcheck` 判据 1–4 全绿。搬完代码跑 `python3 tools/move-importcheck.py --old <搬家前的 git 引用> --new <搬家后的文件…>`（拿旧文件的 import 表当参照物；三次历史真红都能逐条复现，`--selftest` 有 8 个合成对照，`--apply` 直接补齐）。它常伴着一串「`@Composable` invocations can only happen from the context of a `@Composable` function」——那是未解析的 Composable 尾随 lambda 引起的**连带错**，补好 import 就一起消失，别当成第二个问题去修。⚠️ **09-19 #11b 补第三种漏法，也是 import 查不出来的那种**：被搬走的那段代码调用了**同包另一个文件的 `private` 顶层成员**（Kotlin 的 `private` 是**文件**级可见，同包也不行 ⇒ `Cannot access 'fun AppBarIconButton(...)': it is private in file`）。⇒ 搬家的判据不能只问「import 要不要改」，还得问「这个名字够不够得着」：跑 `tools/move-importcheck.py` 之外，补跑守卫 `CrossFilePrivateRefTest`（09-19 起专钉这条，整棵 `ui/` 树扫跨文件 private 引用）|

| `Unresolved reference 'arrayArrayOf'`（外加一串看不懂的 `Cannot infer type parameter 'R'` / `joinToString` 未解析） | 本仓 Kotlin **2.4.10** 已**移除** stdlib 的 `arrayArrayOf`（2.2 起弃用）⇒ 别用它拼路径，写 `listOf("a", "b").map(::File)` 就好。09-19 #11a 的守卫测试红在这上面；**后面那三条连带错都源于第一个未解析符号**，改第一处即可，别逐条去修 —— 这也是「一次编译只盯最上面那条 `e:`」的原因 |
| Unresolved reference: R | res/ 文件有误（常见 strings.xml / xml 格式） |
| `Type 'MutableState<T>' has no method 'getValue(…)' / 'setValue(…)', so it cannot serve as a delegate` | 缺 `androidx.compose.runtime.getValue` / `setValue`。⚠️ 这是搬代码的**第二类盲区**：`var x by remember { mutableStateOf(…) }` 里这两个名字**从不以标识符出现**，所以「按名字算依赖」的脚本看不见它 —— 09-18 #10a-2 第二轮就红在这一处（`SettingsScreen.kt:138:23`；搬运脚本算"保留 import"时把它们当死 import 从入口删了，而 `move-importcheck` 判据 3/4 也放过：小写名只认调用形，`by` 一个调用形都没有）。`move-importcheck` 判据 6 用**结构判据**补上：代码里有属性委托（排除 `by lazy` / `by Delegates.`）⇒ 需要 `getValue`，其中有 `var` ⇒ 另需 `setValue`。注意这类错**一次只报一处**（同一行的 get/set 两侧共 2 行 `e:`），与上一行那种"24 处批量漏"不是一个量级，别照着找第二处 |
| `Unresolved reference: viewModelScope`，或搬走的函数名（如 `buildCsvExport`） | 两类都是**搬代码进新文件**时漏 import：① 小写扩展**属性当接收者用**（`viewModelScope.launch { }` ⇒ 点号前那个名字也要 import）；② 新文件里的声明与某条 import **同名**（`internal fun AppViewModel.buildCsvExport()` 体内 `repo.buildCsvExport()` 要的是 data 层那个同名扩展）。`tools/move-importcheck.py` 的判据 4 第 4 形状与判据 7 专治这两类，搬完必跑；⚠️ 两类都是本地四项检查全绿、只有 CI 抓得到（2026-09-19 #10b-1 开工前实测） |
| @Composable invocations... | 在非 Composable 作用域（onClick/协程）调了 Composable；把值提前在组合作用域获取 |
| Platform declaration clash | 为 `var x` 又手写了 `fun setX()`；删掉手写 setter |
| mergeDebugResources 失败 | XML 格式错误，检查最近改过的 res 文件 |
| 同错误重复 2 次+ | 停下来读完整报错 → read_file 定位 → 换思路；必要时 `./gradlew clean --no-daemon` |
| ktlint 报 `Not a valid Kotlin file (N:M expecting an expression)` | **这不是格式规则，是解析失败** ⇒ 先 grep 行首 `#`：从 markdown / shell 串过来的注释习惯，Kotlin 只认 `//` 和 `/* */`。查法 `grep -rn "^[[:space:]]*#" app/src --include=*.kt`。09-19 #11c 就栽在这（一行 `# ② 协议层分家…`），ktlint 与 kotlinc 同时红，而 `tools/kt-lexcheck.py` 放过去了 —— 它查大括号、别名表、import 双向，**不查注释符**。 |
| 搬完代码后 `Unresolved reference` 找不到源头（属性委托那一类） | `by remember` / `by animateFloatAsState` 需要 `import androidx.compose.runtime.getValue`，但**代码里永远不会出现 `getValue` 这个名字** ⇒ 按名字收敛 import 的脚本（含 `tools/move-importcheck.py`，它对属性形是「跳过」不报）双向都看不见它。⇒ 只要搬走的代码或剩下的代码里有 `by` 委托，**两侧都要各自 grep 一遍**：`grep -c " by " <文件>` > 0 ⇒ 该文件必须留着 `getValue`（`setValue` 同理，`var` 委托才需要）。09-19 #11d ① 差一点就红在 `StatsScreen.kt` 的 `val animFraction by animateFloatAsState(...)` 那一处。 |
| `Repeated 'internal'.`（kotlinc，只有它报） | 搬运脚本给新文件加可见性时改了两行：`@Composable` 前面加一个 `internal`、`fun` 前面又加一个 ⇒ 同一声明两个修饰符。全仓写法是 **`@Composable` 单独一行 + 下一行 `internal fun`**（34 处先例，例如 #11b 的 `AppBarActions.kt` 里 `internal fun AppBarIconButton` 那处）⇒ 改完必须自查「同一声明重复修饰符」：`grep -nE "^(internal|private|public)(@| )" <新文件>` 只许命中一处。ktlint / detekt 都不看这个（09-19 #11d ① 就是这么红的：静态门禁 ✅、两个 variant 编译 ❌）。 |
| 抽取函数时**新引入的类型名**，原文件从没 import 过它 | 09-19 #11d②：把统计页区块抽成 `StatsCategorySection.kt` 时新加参数 `chartColors: List<Color>`；原文件是 `val chartColors = remember { appChartColors() }`，**类型靠推断、`Color` 从未出现在 import 表** ⇒ 任何「按原文件 import 表复制 / 收敛」的做法结构上看不见它 ⇒ CI 报 `Unresolved reference 'Color'.`（job2 静态门禁全绿，**只有 kotlinc 会红**）。⇒ 搬完跑 `python3 tools/kt-name-audit.py 新文件…`（只看签名的类型位置；判据与假警边界写在工具头部） |
| 文档表格行被粘成一行 | 在带引号的 heredoc（形如 '<<'PY'，即定界符带单引号）里给 python 写字符串时，反斜杠不会被展开 ⇒ 源码里的 "\n" 到 python 手里就是**字面的反斜杠 + n**，表格行没换行、任何门禁也不会红（09-19 一次写出三处：ROADMAP 风险表、WORKFLOW §4、devlog 台账）。⇒ 追加表格行改用"真实换行 + 行列表 insert"，别拼字符串；自查：'grep -Fn "\n|" docs/ devlog/' 只许命中"讲解这条规则本身"的那几行 |
| 搬走使用点后，老文件留下**死私有成员** | 把某段代码搬去同包另一个文件时，它读过的那些 `private` 顶层常量 / 私有成员在老文件里可能就此没人用（09-19 #11f：`AppViewModel.kt` 的 `private const val TAG` 唯一读者是搬走的那句 `Log.w` ⇒ detekt `UnusedPrivateProperty` 红；同批 `SnackbarCopyTest` 里改了期望路径却没删旧常量，也红一条）。本仓这两个 detekt 规则是 active 的 ⇒ CI 一定抓得到；但 `kt-lexcheck`（查"import 了没用"）与 `kt-name-audit`（查"用了没 import"）都**不查这个方向**，本地又没构建 ⇒ 唯一稳的做法：**搬完立刻 `grep -n "^private\|^    private " 老文件`，逐个数字法**，为 0 的当场处理（删掉，或像 `TAG` 那样把定义跟着使用点一起搬） |
| 搬运脚本把 import **剪多了** | 取"简单名"写成 `l.split()[-1]` ⇒ 对 `import a.b.C` 返回的是整串 `a.b.C`，按 `\bC\b` 找用法永远不命中 ⇒ 40 条 import 一次剪光，而**本仓没有任何门禁能拦这种"删多了"**：ktlint 的 `no-unused-imports` 因误报率高被明确关掉（`.editorconfig` 顶部写了原因）、detekt `UnusedImports` 默认 `false`、`kt-lexcheck` 只查"import 了没用"、`kt-name-audit` 只查"用了没 import"（两者对"缺"敏感、对"多删"失明）⇒ 唯一有效做法：**先把每条 import 的用法计数逐行打印再剪**，让人对着计数过一眼；剪完复跑两个工具 + `git diff --stat`（09-19 #11f 首版就是这样，第二次才剪对） |
| 测试写成"永远绿" | 期望值是**把被测实现的那几个公式再算一遍** ⇒ 断言的其实是"代码等于它自己"，抄错的公式照样过。⇒ 期望值要么写**字面常量**、要么找**独立参照**（纯历法这类用另一个实现：09-19 #11e 的月网格拿 Python `calendar.monthcalendar` 逐月核过），并配一条"探测本身失效就红"的对照（扫到 0 个文件 = 红） |
| detekt `LoopWithTooManyJumpStatements` | 阈值是 **1**：一个 `for` 里两条 `continue`（或 `break`）就报。改成 `filter` + `mapNotNull` + `forEach`（既有先例就是 `NutstoreWebdav.parsePropfind`，注释里写着当年为什么换）。⚠️ **守卫与工具测试的循环一样受管** —— 别觉得测试代码可以裸写。 |

## 5. 文档维护责任

| 改动类型 | 需同步更新 |
|---|---|
| 新增/删减功能 | REQUIREMENTS.md 功能清单 + devlog |
| 数据模型/存储 key/路由/依赖变化 | ARCHITECTURE.md 对应表格 |
| 新增复用组件/调整视觉规范 | DESIGN_SPEC.md |
| 流程/规范本身调整 | WORKFLOW.md + CLAUDE.md |
| CI 门禁规则调整（`.editorconfig` / `detekt.yml` / `tools/ci-gates.sh`） | WORKFLOW.md §3 + devlog |
| 任何一轮开发完成 | devlog/YYYY-MM-DD.md + devlog/INDEX.md（强制） |
| 一个多笔立项（如 #10 / #11）收官 | **`docs/ROADMAP.md` 顶部主表那一行的状态词与 CI 列** + 小节 + `devlog` 台账 + PR 描述。
  ⚠️ 别只翻小节：`tools/doc-metrics.sh` 比对的是写死的数字与文件位置，**不比对 ⏳/✅ 这类状态词** ⇒ 主表漏翻不会有任何
  门禁报警（09-19 #11 六笔全做完、主表还挂着「⏳ 未开始」，是记账时通读才发现的）|
