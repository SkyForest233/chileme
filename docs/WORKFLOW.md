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
- 二级页栈：`rememberNavBackStack` + `NavDisplay` 只在 MainActivity 声明一次，向下传回调而非 backStack 本身。底栏 Tab 不走导航栈（HorizontalPager）

### 项目约定
- 数据读写只走 `FoodRepository`；新增持久化字段时：模型加默认值（保证旧数据兼容，Json 已配 `ignoreUnknownKeys`）→ Repository 增方法 → ViewModel 暴露 → UI
- 删除类操作走归档 `archiveItems()`，不得直接从库存 JSON 中移除（归档页除外）
- 状态判定用 `item.statusFor(thresholds)`，禁止硬编码 7 天
- 颜色不得在屏幕代码写死主题色值；状态色走 `rememberStatusUi()`；统计图表调色板例外（见 StatsScreen 的 chartColors）
- 字符串目前直接写在代码中（中文单语言）；app_name 必须在 strings.xml 维护

### 新增依赖
1. 先用 `search_maven` 确认坐标；2. 加入 `app/build.gradle.kts`；3. 立即构建验证；4. 在 `docs/ARCHITECTURE.md` 依赖清单登记

## 3. 构建与验证

- 构建命令：`./gradlew assembleDebug`（沙箱无 JDK/SDK 时看 GitHub Actions `Build`）
- 产物：`app/build/outputs/apk/debug/app-debug.apk`
- 频率：每完成一个功能模块就构建，不要积攒大量改动后一次性构建

## 4. 常见错误处理

| 错误 | 原因与处理 |
|---|---|
| Unresolved reference 'X' | 缺 import 或拼写错误；检查文件顶部导入 |
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
| 任何一轮开发完成 | devlog/YYYY-MM-DD.md + devlog/INDEX.md（强制） |
