# 技术架构说明（ARCHITECTURE）

## 1. 技术栈与版本（不得随意升级）

- Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.7.1 / **JDK 21**（miuix-nav inline 函数为 JVM 21 bytecode，本模块必须同目标；Gradle `jvmToolchain(21)` 可在 CI Java 17 镜像上自拉 21）
- **依赖管理（v2.8.1 起）**：全项目依赖与插件统一收拢至 `gradle/libs.versions.toml`（Version Catalog）
- compileSdk 37（v2.8 起，Miuix 0.9.4-rc01 要求 ≥37）、**minSdk 26**、targetSdk 36
  - **minSdk 24 → 26（2026-08-21）**：全项目 28 处 `java.time`（API 26 引入）此前未开启 core library desugaring，API 24/25 设备进首页即 `NoClassDefFoundError`（`daysLeft` 走 `ChronoUnit`）。选择提升 minSdk 而非加脱糖，省去包体与构建开销
  - **签名凭据**：优先读环境变量（`RELEASE_KEYSTORE_PATH` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`），回退根目录 `keystore.properties`（均不入库）。**四项缺一则 release 构建直接失败**，不再静默回退 debug 签名；本地仅做构建验证可加 `-PallowUnsignedRelease=true`
  - **versionCode / versionName**：由 CI 注入（`APP_VERSION_CODE` = `github.run_number`，`APP_VERSION_NAME` = tag 名），本地回落 `1` / `"1.0"`。设置页「关于」展示的 `吃了么 v1.0` 是硬编码字符串、不读 `BuildConfig`，故与 `CLAUDE.md` §5 的展示版本号锁定不冲突
  - **lint**：`NewApi` / `InlinedApi` 提为 error，CI（`build.yml`）跑 `lintDebug` 并上传报告 —— 这是防止"用了高于 minSdk 的 API、只在老设备崩"复发的机制
- **applicationId `com.chileme.pantry`**（v2.1 起）；namespace / 代码包名保持 `com.agon.app` 不变。FileProvider authority 使用 `${applicationId}.fileprovider` 占位符，代码中用 `${context.packageName}.fileprovider`
- **Release 构建（v2.5 起）**：`isMinifyEnabled = true` + `isShrinkResources = true`（R8 代码/资源压缩），但 `proguard-rules.pro` 中 `-dontobfuscate` **禁用混淆**——类名/方法名/字段名全保留，堆栈可读无需 mapping。规则文件另含 kotlinx-serialization keep 规则（data 包 serializer 反射）与 OkHttp/Coil dontwarn。release APK ≈ **2.4 MB**（2026-09-15 CI `release-r8` job 实测；早期记录为 2.6 MB），debug ≈ 63 MB
- **到期日历（v2.5 起）**：不再是独立路由，作为 `ExpiryCalendarCard`（ui/components/ExpiryCalendar.kt）嵌入统计页；支持手势左右滑动切换月份，圆点颜色 = 紧急度（去重后最多 3 点）
- Compose BOM 2026.08.00（material3、icons-extended）
- miuix-nav 0.9.4-rc01（`NavDisplay` 二级页路由与预测性返回；替代 Navigation Compose NavHost）、Lifecycle/ViewModel Compose 2.11.0、Activity Compose 1.13.0
- DataStore Preferences 1.2.0 + kotlinx-serialization-json 1.11.0
- Coil 3.3.0（照片封面加载）
- ~~ML Kit OCR~~ 已于 v2.5 移除（识别率低），`DateOcr.kt` 已删除
- MaterialKolor `com.materialkolor:material-kolor:4.0.1`（MD3 种子色生成主题，v2.2 起）
- Miuix `miuix-ui` / `miuix-preference` / `miuix-icons` 0.9.4-rc01（common 坐标，Gradle 解析到 android 变体）；导航用 `miuix-nav-android:0.9.4-rc01`
- OkHttp `com.squareup.okhttp3:okhttp:4.12.0`（坚果云 WebDAV 同步，v2.4 起；只声明一次）

## 2. 分层结构

```
app/src/main/java/com/agon/app/
├─ MainActivity.kt              # 单 Activity；主题接入、miuix-nav NavDisplay、底栏、FAB
├─ data/                        # 数据层（无 UI 依赖）
│   ├─ FoodModels.kt            # 数据模型 + 派生属性（过期计算/状态判定）+ 纯函数（compactConsumptionAt 等）
│   ├─ FoodRepository.kt        # 唯一持久化入口（DataStore）；含 Decoded 三态、写守卫、DecodeCache
│   ├─ BackupFile.kt            # 备份文件读写：readBackupText（IO 线程 + 20MB 上限）/ previewBackup / fileStamp
│   ├─ CsvExport.kt             # 库存 CSV 导出（含公式注入防护 escapeCsvField）
│   ├─ LocalSnapshotStore.kt    # 本地滚动快照 filesDir/snapshots/（保留最近 3 份，全部 suspend + Dispatchers.IO）
│   ├─ ImageStore.kt            # 封面图片复制到私有目录（下采样 + EXIF 旋转校正）+ 孤儿封面清理
│   ├─ CloudSync.kt             # 坚果云 WebDAV（NutstoreSync 单例）
│   └─ SecureStore.kt           # Keystore AES-GCM 密码
├─ viewmodel/
│   └─ AppViewModel.kt          # 全局共享 VM（AndroidViewModel），StateFlow 暴露
└─ ui/
    ├─ navigation/              # AppRoute（miuix-nav 二级页栈；转场用库预设 NavTransitions.MiuixDefault）
    ├─ theme/                   # Palettes.kt（15 套种子色方案）/ Color.kt（仅状态语义色）/ Theme.kt（MaterialKolor 生成 + animateColorScheme）/ ThemeStyle.kt（MATERIAL3/MIUIX 风格枚举 + LocalThemeStyle）/ MiuixRootTheme.kt（MiuixTheme + MaterialTheme 桥接，v2.8）/ Motion.kt（MD3 缓动与时长 token）/ TodayProvider.kt（LocalToday，跨零点刷新）
    ├─ components/              # 复用组件（2026-09-16 由 Common.kt 拆成 8 个按职责命名的文件；同包、签名与实现未改）：
    │                           #   StatusUi.kt（状态语义层：StatusUi/rememberStatusUi/ExpiryUrgency/urgencyForAt/urgencyDotColor）
    │                           #   Badges.kt（StatusBadge/LocationTag）· FoodAvatar.kt（FoodAvatar/EmojiAvatar）· QuantityStepper.kt
    │                           #   FoodCard.kt · Controls.kt（SelectIndicator/CheckSwitch/EmptyState）· DataCorrupt.kt（corruptKeyNames/DataCorruptBanner）
    │                           #   MiuixDialog.kt（WindowDialog 封装）；另有原本就独立的 UndoSnackbar.kt · ExpiryCalendar.kt
    └─ screens/                 # 每屏一文件，自带 Scaffold。两层结构（2026-08-22 B-08 起）：
                                # ① *State.kt 状态容器（8 个）：remember*UiState + 纯计算函数，双主题共用、单测覆盖
                                # ② 渲染层：XxxScreen.kt（MD3）与 MiuixXxxScreen.kt（Miuix）成对存在，共 8 对 ——
                                #    Settings / Home / FoodList / FoodDetail / Archive / Manage（阈值·分类·位置）/ Stats / ConsumptionLog
                                # 仅编辑页（EditFoodScreen）与 CheckSwitch 刻意保留 MD3+桥接：DatePicker 无 Miuix 对应；
                                # CheckSwitch 是项目特色打勾/打叉样式（规范禁止 material3 Switch），自绘 + 颜色桥接。
                                # 统计页与消耗记录页**已 Miuix 化**（图表仍为 Canvas 自绘，但外壳/组件走 Miuix）
```

**规则**：UI → ViewModel → Repository → DataStore，单向依赖；UI 绝不直接访问 DataStore；所有写操作在 `viewModelScope` 内执行。

## 3. 数据模型（均 @Serializable，存为 JSON 字符串）

| 模型 | 存储 key | 说明 |
|---|---|---|
| `FoodItem` | `food_items` | 库存记录；日期存 epochDay(Long)；`category` 存 CategoryDef.id 字符串；`expiringThresholdDays: Int?` 为单条阈值覆盖；`coverText` 自定义封面 emoji/短文字（≤4 字符） |
| `CategoryDef` | `custom_categories` | 可编辑分类(id/label/emoji)；默认 8 个 id 沿用旧枚举名，新增用 UUID；孤儿 id 由 `byId()` 回退 FallbackCategory("其他") |
| `List<String>` | `custom_locations` | 可编辑位置预设列表 |
| `ArchivedItem` | `archived_items` | 归档：原 item + 归档日 + 原因(DELETED/CONSUMED/EXPIRED)；上限 200 |
| `ConsumptionRecord` | `consumption_records` | 消耗流水（减库存时自动记录）；90 天内逐笔，更早按「年×月×名称×单位」聚合成一条并标记 `aggregated=true`（见 `compactConsumptionAt`），无条数硬上限 |
| `HistoryEntry` | `history_entries` | 录入历史（名称去重，上限 50） |
| `Map<String,Int>` | `category_thresholds` | 分类临期阈值；key 为 `CategoryDef.id`（不是枚举名/显示名） |
| `BackupData` | （导出文件） | 以上全部数据的聚合，version=`BACKUP_VERSION`(=2)（含 categories/locations；v1 文件可兼容导入）。导入前必须经 `previewBackup()` 校验（含 `items` 键） |

其他 key：`seeded`(Boolean)、`dynamic_color`(Boolean)、`dark_mode`(Int: 0跟随/1浅/2深)、`palette`(String: AppPalette 枚举名，默认 "MINT")、`theme_style`(String: ThemeStyle 枚举名，默认 "MATERIAL3")、`floating_nav`(Boolean: 悬浮导航开关，默认 true)（v2.8）。

**状态判定逻辑**（FoodModels.kt）：`statusForAt(thresholds, today)` — 过期: daysLeft<0；临期: daysLeft<=有效阈值；有效阈值 = 单条覆盖 ?: 分类设置 ?: 7。UI 一律用 `statusForAt`（`today` 取 `LocalToday`，跨零点才会刷新），不要自行比较天数，也不要再用内部取 `LocalDate.now()` 的旧属性。

## 4. 导航路由表

| 路由 | 屏幕 | 说明 |
|---|---|---|
| `AppRoute.Main` | Home / List / Stats / Settings（HorizontalPager） | 底栏四个 Tab，按索引左右连滑；起始页。首页卡片写入 `listFilter` 后滑到食品页 |
| `AppRoute.Detail(id)` | FoodDetailScreen | 食品详情 |
| `AppRoute.Edit(id?)` | EditFoodScreen | 新增 / 编辑 |
| `AppRoute.Archive` | ArchiveScreen | 归档（设置/食品列表可进入，带搜索） |
| `AppRoute.Consumption` | ConsumptionLogScreen | 消耗记录（统计页二级） |
| `AppRoute.ManageThresholds` | ThresholdManageScreen | 临期阈值管理（设置二级页） |
| `AppRoute.ManageCategories` | CategoryManageScreen | 分类管理（设置二级页） |
| `AppRoute.ManageLocations` | LocationManageScreen | 存放位置管理（设置二级页） |

> 表中「屏幕」列写的是 MD3 实现名。**除编辑页外，每个路由都有成对的 `Miuix*Screen.kt`**（含统计页与消耗记录页），运行时按 `LocalThemeStyle` 分流；两者必须共用同一份 `*State.kt` 状态容器，禁止在 UI 文件里重写业务计算（`MiuixParityTest` 静态拦截）。

- 底栏 Tab：`AppRoute.Main` 内 HorizontalPager（home → list → stats → settings）；点击 Tab 用 `folmeSpring` 连滑，跨页会经过中间页。二级页走 miuix-nav `NavDisplay` + `NavTransitions.MiuixDefault`（全宽卡片滑 + 1/4 视差 + 圆角 dim），隐藏底栏与 FAB（`showChrome`）
- FAB（添加食品）仅在 home 与 list（Pager 第 0/1 页）显示
- 新增路由：在 `AppRoute` 加类型 + `NavDisplay` 注册 `entry` + 按需更新 `onTabs`/`showChrome`，并更新本表

## 5. 关键实现约定

- **删除 = 归档**：业务删除一律调 `repo.archiveItems(ids, reason)`；只有归档页的“彻底删除/清空”真正移除数据
- **消耗记录**：`changeQuantity(id, delta)` 在 delta<0 时自动写 ConsumptionRecord，调用方无需额外处理
- **吃完自动归档（v2.4）**：`changeQuantity` 在消耗导致数量归零时自动移入归档（CONSUMED）并返回 true；UI 可依此提示/返回
- **OCR**：已移除，不要再加 `DateOcr` / ML Kit
- **坚果云同步（v2.4，v2.7 多版本轮转）**：`NutstoreSync` 单例（OkHttp），WebDAV MKCOL+PUT/GET/PROPFIND/DELETE；账号存 DataStore（nutstore_account / last_sync_time）；**密码经 `SecureStore`（Android Keystore AES-GCM）加密后存 `nutstore_password_enc`，启动时 `migratePlaintextPassword()` 自动迁移旧明文**；上传内容即 buildBackupJson() 产物，下载走 importBackupJson()
  - **多版本轮转（v2.7）**：上传文件名 `chileme_backup_yyyyMMdd_HHmmss.json`，上传后 PROPFIND 列目录、自动 DELETE 多余旧版本，云端保留最近 `CLOUD_BACKUP_KEEP`（=3）份；自动同步走同一 upload 入口，同样轮转
  - **恢复选择（v2.7）**：`listBackups()` 返回 `CloudBackup(fileName, sizeBytes)` 列表（新→旧），UI 弹窗选择具体版本后 `download(fileName)` 恢复；旧版单文件 `chileme_backup.json` 兼容显示在列表末尾且不参与轮转删除
- **自动同步（v2.4）**：`auto_sync_days`（0=关/1/3/7）+ `last_auto_sync_epoch_day`；ViewModel init 时 `maybeAutoSync()` —— 间隔到且凭据完整则静默上传，成功后 Home 页 Snackbar 提示，失败静默下次重试；无 WorkManager 无后台任务
- **孤儿图片清理（v2.4）**：启动时 `cleanupOrphanCovers()` 删除 covers/ 中不被库存/归档引用的文件
- **消耗记录压缩（v2.4，2026-09-15 加固）**：`compactConsumptionAt(records, today)` —— 近 90 天逐笔保留，更早按「**年×月×名称×单位**」聚合（`unit` 必须参与分组，否则「牛奶 3 瓶 + 2 箱」会被加成「5 瓶」）；禁止恢复 take(N) 粗暴裁剪。**聚合出的记录必须生成新 `id`**（2026-08-21 修复：此前 id 为 null，导致消耗记录页删除按钮静默无效），并标记 `aggregated = true`（2026-09-15）：一条聚合记录代表整月合计，`isDeletable()` 返回 false，仓库层 `deleteConsumption` 拒删 + 两套 UI 都不给删除按钮（双重拦截）
- **数据完整性守卫（2026-08-21）**：解码分三态 `Decoded.Ok / Empty / Corrupt`
  - 「用户资产型」key（`food_items` / `archived_items` / `consumption_records` / `history_entries`）走 `decodeStrict`，解析失败即 `Corrupt`
  - **所有写路径必须先 `isCorrupt(...)` 守卫，命中则 `return@edit` 放弃写入**。理由：此前 `Corrupt` 与 `Empty` 都回落 `emptyList()`，随后任一写操作会把空表 encode 覆盖，一次解析异常即永久摧毁全部数据。新增写方法时**必须**加守卫
  - **守卫按 key 粒度（2026-09-15）**：守卫只覆盖**本次写入真正会覆盖的 key**，且**主数据必须先判、辅助数据按需判**。已落地的三处：`upsert` 在 `history_entries` 损坏时跳过历史写入、库存照常保存；`changeQuantity` 分别判 `consumption_records`（跳过消耗记录，`consumptionId` 返回 null 因而不给撤销入口）与 `archived_items`（归档不可写时**保留 0 数量记录而不删库存**，避免「删了却归档不进去」的数据丢失）；`undoConsumption` 只在「需要从归档恢复」的分支才要求归档可写。**禁止退回 `isCorrupt(a, b, c)` 式一起判**：辅助数据损坏会连带锁死核心功能（`CorruptGuardTest` 静态拦截）
  - 例外（允许在损坏态执行，因其本身就是恢复/丢弃手段）：`clearAll()` / `clearArchive()` / `importBackupJson()` / `discardCorrupt()`，执行后解除对应标记
  - **放弃损坏数据入口（2026-09-15）**：`discardCorrupt(keys)` 删除这些 key 的内容并解除损坏标记，让写入恢复；**不动 `filesDir/corrupt/` 里的留档**。UI 入口是首页横幅上的「放弃这部分数据」按钮（MD3 用 `AlertDialog`、Miuix 用 `MiuixDialog` 二次确认），文案由 `corruptKeyNames()` 复用生成
  - `buildBackupJson()` 在损坏态**抛异常**，避免生成残缺备份；调用方需捕获（两个设置页提示用户，自动同步/手动上传则放弃本次上传，防止残缺备份覆盖云端完好版本）
  - 损坏原始串留档 `filesDir/corrupt/<key>-<时间戳>.json`；`corruptedKeys: StateFlow<Set<String>>` 暴露给 UI，首页顶部显示 `DataCorruptBanner`（`ui/components/DataCorrupt.kt`，MD3 / Miuix 共用）
  - 「配置型」key（thresholds / categories / locations）丢失可重设，维持回落默认值的旧行为，不阻断写入
- **读流兜底（2026-09-15）**：所有 DataStore 读流统一经 `FoodRepository.resilientRead()` —— `IOException` 先退避重试 2 次（间隔 300ms），仍失败则记日志并回落默认值（`rawFlow` 解码 `null` = Empty、`lightFlow` 用传入的 fallback）。理由：19 个 `stateIn` 全是 `SharingStarted.Eagerly` 且 `viewModelScope` 未挂 `CoroutineExceptionHandler`，读异常会终止共享协程→交给默认处理器→**杀进程**。**新增读流一律走 `rawFlow()` / `lightFlow()`，禁止直接 `dataStore.data`**
- **启动放行超时（2026-09-15）**：`MainActivity.READY_TIMEOUT_MS`（3s）。`ready`（DataStore 首发）在 3 秒内未达成也强制渲染首帧——否则「读阻塞/异常」会让启动画面永久停留，用户只能杀进程。`contentReady` 用 `mutableStateOf`，因为 composition 里读它
- **封面清理守卫（2026-09-15）**：`cleanupOrphanCovers()` **仅在 `corruptedKeys` 为空时执行**。损坏态下 items/archive 解码回落空集，照常清理会把 `covers/` 下所有文件当孤儿删除，而图片无法从 `corrupt/` 的 JSON 留档恢复
- **恢复前置快照（2026-09-15）**：三条恢复路径（文件导入 / 坚果云整版本恢复 / 本地快照还原）都先经 `AppViewModel.snapshotBeforeRestore()` 留一份「操作前状态」，用户可回退。**顺序陷阱**：本地快照还原必须**先读出目标快照内容、再写前置快照**——反过来在快照已满 3 份时会按修改时间把目标挤掉
- **本地快照 IO（2026-09-15）**：`LocalSnapshotStore` 的 `saveSnapshot` / `listSnapshots` / `readSnapshot` 均为 `suspend` + `withContext(Dispatchers.IO)`（此前在主线程写盘/读盘）；列表条数用 `countItemsInSnapshot()` **解析 JSON** 得到，禁止再用正则数 `"id":`（会把归档/消耗/历史里的 id 也算进去）
- **导入备份加固（2026-09-15）**：SAF 选文件 → `readBackupText()`（IO 线程，20 MB 上限）→ `previewBackup()`（必须含 `items` 键且可解析，否则拒收）→ 二次确认弹窗（导出日期 / 各表条数 / schema 版本）→ `importBackupWithSnapshot()`（**先写一份本地快照兜底**再整体替换）。禁止退回「选完即覆盖」
- **Flow 读取规约（2026-08-21）**：DataStore 每次 `edit` 都会重发整份 Preferences。所有重型（需 JSON 解码）key 一律走 `rawFlow()` —— 先取原始串 → `distinctUntilChanged()` → 解码 → `flowOn(Dispatchers.Default)`；轻量 key 走 `lightFlow()`（仅去重）。**禁止**直接 `dataStore.data.map { decodeXxx(...) }`：那会让改一次主题色就重新解析全部 JSON 并产生新 List 实例（全屏重组），且解码发生在 `viewModelScope`（`Main.immediate`）即主线程
- **备份排除规则（2026-08-21，2026-09-16 校正）**：`res/xml/backup_rules.xml`（API ≤30，`fullBackupContent`）排除 `datastore/` + `covers/` + `corrupt/`；`res/xml/data_extraction_rules.xml`（API 31+，`dataExtractionRules`）分两条通道——`cloud-backup` 排除 `datastore/` + `covers/` + `corrupt/`，`device-transfer` 排除 `datastore/` + `corrupt/`（**放行 `covers/`**）。坚果云密码是 Keystore AES-GCM 密文，**密钥不跨设备**，备份恢复后必然解不开，故两条通道都必须排除 `datastore/`；`nutstoreCredentialBrokenFlow` 检测该状态并在设置页提示重新填写
  - **已知不一致（待决策，见 `devlog/2026-09-16.md`）**：① `device-transfer` 放行 `covers/` 却排除 `datastore/` → 换机直传后封面图到了、引用它的库存 JSON 没到，新设备启动时 `cleanupOrphanCovers()` 会把它们当孤儿删掉（不致命，但白传一轮图片）；② `filesDir/snapshots/`（每日全量库存 JSON，最近 3 份）**两份规则都没排除** → 会随 Android 系统备份进入用户自己的 Google 账号，README「数据默认只存本机」的表述未覆盖这条通道
- **归档恢复去重（v2.4）**：`restoreArchived()` —— 同 ID 只移除归档；同名+同生产日期合并数量（返回 merged 供 UI 提示）；否则新增，数量 0 恢复为 1
- **主题渐变（v2.4）**：Theme.kt `animateColorScheme()` 对全部 **37** 个颜色角色 450ms tween（`animatedColor(target.*)` 逐角色调用，2026-09-16 实测计数）；新增颜色角色时需同步加入该函数
- **图片存储**：封面统一通过 `copyImageToCovers()` 落盘到 `filesDir/covers/`，FoodItem 只存绝对路径；展示用 `FoodAvatar`，优先级：照片 > coverText > 分类 emoji
  - **已知待办**：① `photoPath` 存**绝对路径**，换设备/清数据后必然悬空，应改存文件名（`covers/<uuid>.jpg` 的 basename）运行时用 `context.filesDir` 拼；② 存在性判断目前在**组合期**同步调 `File.exists()`（`FoodAvatar.kt:43`、`EditFoodScreen.kt:284`），列表滚动每帧重算 syscall，应移到 VM/IO 侧或改由 Coil `onError` 回落 emoji。见 `docs/audits/chileme-review.md` P1-8
- **进度条语义**：一律用 `elapsedRatio`（正相关，时间过去多少走多少），禁止再用 freshness 直接作进度
- **键盘避让（2026-09-15）**：Android 15+ 强制 edge-to-edge 后 manifest 的 `adjustResize` **不再缩窗口**，键盘只是叠在窗口上，必须自己消费 `WindowInsets.ime`。三条硬规则：① **含输入框的屏幕**一律 `Scaffold(modifier = Modifier.imePadding())`（整屏缩到键盘之上，滚动区同步变矮）；② **不在 Scaffold 内的 App 级浮层**用 `.navigationBarsPadding().imePadding()` 两段式（等价于旧的 `navigationBarsWithImePadding()`，内层只补差额，不会叠加成一条大空隙）——**底栏按产品决定不跟随抬升（A 方案）**，是全 App 唯一「键盘弹出时允许被遮挡」的元素；③ **MD3 弹窗**是独立浮动窗口，必须 `DialogProperties(decorFitsSystemWindows = false)` 才会把 IME inset 透给内容，否则底部按钮被键盘盖住。**Miuix `WindowDialog` 例外**：库内 `DialogContent` 根节点自带 `imePadding()`（窗口属性 `decorFitsSystemWindows = false` + `usePlatformDefaultWidth = false`），本项目**不要**传 `defaultWindowInsetsPadding = false`，也不要给 Miuix 弹窗再加 padding。回归守卫见 `ImeHandlingTest`
- **统计口径（2026-09-15）**：「过期浪费」= `calculateWastedTotal(archived)`，按**件数**（`sumOf { item.quantity }`）而非归档条数，与同屏按件求和的「本周消耗」保持一致。注意该指标仍受归档上限 `take(200)` 影响，长期不失真需独立计数器（见 `docs/audits/2026-09-15-code-review.md` §1.8）
- **「件」与「条」的用词规则（2026-09-15）**：写给用户的**带「件」的文案必须是 `quantity` 求和**（`BackupData.itemQuantity` / `HomeScreenState.quantityOfStatus` / `StatsState` 的按件字段），`items.size` 只能出现在「条/记录」语境或**不带单位**的筛选计数里（首页三张统计卡不带单位、点进去是记录列表）。归档/消耗/历史一律「条」。已按此修正：首页新鲜度横幅、一键清理按钮及其撤销提示、导入预览的库存位数
- **Miuix 屏幕只换外壳（2026-09-15）**：`Miuix*Screen.kt` 必须调用 `remember*UiState` 复用已测状态容器，**禁止在 UI 文件里重写聚合计算**；`MiuixParityTest` 会静态拦截（此前 `MiuixStatsScreen` 手抄了一份统计逻辑，导致 `StatsStateTest` 测的是 MIUIX 下不执行的代码）
- **启动门控**：MainActivity 用 core-splashscreen `setKeepOnScreenCondition` 持住启动画面，直到 `viewModel.ready`（DataStore 首发）才渲染，避免主题/内容闪烁；新增首屏依赖的 Flow 时要加入 ready 的 combine
- **拍照**：FileProvider authority 一律用占位符 —— Manifest 写 `${applicationId}.fileprovider`（实际值 `com.chileme.pantry.fileprovider`），代码写 `${context.packageName}.fileprovider`（`EditFoodScreen.kt:189`），**禁止硬编码 `com.agon.app.fileprovider`**（namespace 与 applicationId 是分离的，硬编码会在运行时抛 `IllegalArgumentException`）。临时文件写 `cacheDir/camera/`（`TakePicture` 回调后已清理，2026-09-15），paths 配置见 `res/xml/file_paths.xml`。该文件已由 `path="."`（暴露整个私有目录）收窄为仅 `camera/` 与 `covers/` 两个子目录，新增共享目录需显式登记
- **备份**：导出用 `CreateDocument("application/json")`，导入用 `OpenDocument`；导入是整体替换而非合并（流程加固见上方「导入备份加固」条）
- **列表批量操作**：长按卡片进入多选模式（selectedIds 非空即多选）；顶栏切换为选择态（退出/全选），底部滑入批量归档栏；BackHandler 退出多选；批量操作走 `archiveBatch`/`restoreArchivedBatch`；多选期间 FAB 隐藏（fabSuppressed）
- **应用图标**：自适应图标 `mipmap-anydpi-v26/ic_launcher.xml`（前景 `drawable-*/ic_launcher_foreground.png` + 纯色背景 `#FBF6E9` + `monochrome` 供 Android 13+ 主题图标）；源图由用户 SVG 处理而来（已去黑边，主体缩放至 66dp 安全区）。**legacy `mipmap-*/ic_launcher.png` 已于 2026-08-22（B14）删除** —— minSdk 26 起没有设备会用到它们，`@mipmap/ic_launcher` 现只解析到 `anydpi-v26`
- **Snackbar**：带悬浮导航栏的屏幕，SnackbarHost 必须加 `padding(bottom = 84.dp)` 避免遮挡
- **撤销 Snackbar**：`ui/components/UndoSnackbar.kt` 的 `showUndoSnackbar`。MD3 自绘 Material History 圆环 path（去指针），变换到圆心后再叠粗数字。消耗记录撤销：`DeletedConsumption(record, index)`，`addConsumption(record, index)` 插回删除前在日期倒序列表中的位置，避免 `listOf(record)+records` 提到最前；LazyColumn `animateItem` 带 placementSpec。消耗记录页 MD3 宿主加 `navigationBarsPadding` + 24dp。覆盖层 Box 必须 `fillMaxWidth`。MIUIX 宿主保持库默认 `canSwipeToDismiss=true`
- **批量操作（v2.3 起，取代早期「两段式滑动归档」）**：列表项**长按进入多选**（`MainActivity` 的 `BatchActionBar`），选中集合通过 `AppViewModel.selectedIds` 暴露；底部操作栏提供 归档 / 改存放位置 / 取消，归档时按 `ArchiveReason.DELETED` 记因。**不要**再按滑动归档实现新功能（`SwipeToDismissBox` 现在只用于 `UndoSnackbar` 的提示条滑动）
- **FAB 与撤销**：Snackbar 展示“撤销”期间调 `viewModel.setFabSuppressed(true)` 隐藏 FAB（finally 复位），避免遮挡撤销按钮
- **底栏自动隐藏**：MainApp 的 NestedScrollConnection 监听列表滚动，下滑隐藏底栏+FAB（slideOutVertically），上滑/切页恢复
