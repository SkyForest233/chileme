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
├─ MainActivity.kt              # 单 Activity：深浅色/风格分流、启动放行超时（READY_TIMEOUT_MS）、splash、CompositionLocalProvider
├─ MainApp.kt                   # App 外壳：backStack / pagerState / 多选 / Snackbar 收集 / nestedScroll + Scaffold（底栏槽位、FAB）+ Snackbar 覆盖层
├─ AppNavGraph.kt               # 全 App 唯一的 NavDisplay + 8 个 entry<AppRoute.*>（外层 Box 限宽 840dp 居中）
├─ BatchBars.kt                 # 批量操作栏：悬浮 / 常驻两条（BatchActionBar + 3 个按钮）
├─ NavChrome.kt                 # TabSpec / MainTabs + MainTabsPager + 4 套底栏（MD3/MIUIX × 常驻/悬浮）
#   ↑ 以上 5 个文件同属包 com.agon.app（App 外壳），2026-09-16 由原 MainActivity.kt（1,123 行）按职责拆出；
#     拆出的第 6 份是弹窗 AppDialogs.kt，2026-09-17 又搬去 ui/components/app/AppBatchMoveDialog.kt（见下）；
#     跨文件引用的顶层声明由 private 放宽为 internal（模块内可见，非公开 API；R8 照常裁剪）
├─ data/                        # 数据层（无 UI 依赖）
│   ├─ FoodModels.kt            # 数据模型 + 派生属性（过期计算/状态判定）+ 纯函数（compactConsumptionAt 等）
│   ├─ FoodRepository.kt        # 唯一持久化入口（DataStore）；含 Decoded 三态、写守卫、DecodeCache
│   ├─ BackupFile.kt            # 备份文件读写：readBackupText（IO 线程 + 20MB 上限）/ previewBackup / fileStamp
│   ├─ CsvExport.kt             # 库存 CSV 导出（含公式注入防护 escapeCsvField）
│   ├─ LocalSnapshotStore.kt    # 本地滚动快照 filesDir/snapshots/（保留最近 3 份，全部 suspend + Dispatchers.IO）
│   ├─ ImageStore.kt            # 封面图片复制到私有目录（下采样 + EXIF 旋转校正）+ 孤儿封面清理
│   ├─ CloudSync.kt             # 坚果云 WebDAV（NutstoreSync 单例）
│   └─ SecureStore.kt           # Keystore AES-GCM 密码
├─ viewmodel/                   # 9 个文件（2026-09-19 #10b-1 … #10b-7 七轮收官：VM 的领域函数按领域搬成同包 internal 扩展函数）
│   ├─ AppViewModel.kt          # 全局共享 VM（AndroidViewModel），StateFlow 暴露；277 行 🎯 < 400 达标（#10b-1 前 700、#10b-2 前 590、#10b-3 前 469、#10b-4 前 431、#10b-5 前 363、#10b-6 前 322、#10b-7 前 304；七轮搬出 48/50 个函数，只剩 init + 2 个 private 策略函数 + 44 个属性）
│   ├─ UiEvent.kt               # 一次性事件：sealed interface UiEvent（6 类，#4a 建 4 类 / #4c 扩 2 类）+ enum DataOp（5 值）+ enum UiSurface（4 值）
│   ├─ AppViewModelBackup.kt    # VM 的备份领域 9 个函数（导出/文件导入/导入前快照/本地快照；#10b-1，2026-09-19）
│   ├─ AppViewModelCloud.kt     # VM 的坚果云同步领域 4 个函数 + 顶层常量 NO_CREDENTIALS_MESSAGE（凭据/上传/列表/下载；#10b-2，2026-09-19）
│   ├─ AppViewModelArchiveUndo.kt # VM 的归档与消耗撤销领域 6 个函数（单件归档/恢复 + 消耗记录删除与撤销；#10b-3，2026-09-19）
│   ├─ AppViewModelFood.kt      # VM 的食物 CRUD 与批量领域 11 个函数（新增/编辑、数量增减、批量归档与恢复、清空与放弃损坏数据；#10b-4，2026-09-19）
│   ├─ AppViewModelCategoryLocation.kt # VM 的分类与位置领域 7 个函数（分类增删改与阈值、位置增删与批量改位置；#10b-5，2026-09-19）
│   ├─ AppViewModelSettings.kt   # VM 的设置领域 6 个一行体函数（自动同步天数 + 5 个外观/主题开关；6 个名字与 data/FoodSettings.kt 全撞；#10b-6，2026-09-19）
│   └─ AppViewModelUiState.kt    # VM 的 UI 状态与事件领域 5 个函数（FAB 抑制 / 多选集三件套 / emit 唯一发送点；#10b-7，2026-09-19）
└─ ui/
    ├─ navigation/              # AppRoute（miuix-nav 二级页栈；转场用库预设 NavTransitions.MiuixDefault）
    ├─ theme/                   # Palettes.kt（15 套种子色方案）/ Color.kt（仅状态语义色）/ Theme.kt（MaterialKolor 生成 + animateColorScheme）/ ThemeStyle.kt（MATERIAL3/MIUIX 风格枚举 + LocalThemeStyle）/ MiuixRootTheme.kt（MiuixTheme + MaterialTheme 桥接，v2.8）/ Motion.kt（MD3 缓动与时长 token）/ TodayProvider.kt（LocalToday，跨零点刷新）
    ├─ components/              # 复用组件（2026-09-16 由 Common.kt 拆成 8 个按职责命名的文件；同包、签名与实现未改）：
    │                           #   StatusUi.kt（状态语义层：StatusUi/rememberStatusUi/ExpiryUrgency/urgencyForAt/urgencyDotColor）
    │                           #   Badges.kt（StatusBadge/LocationTag）· FoodAvatar.kt（FoodAvatar/EmojiAvatar）· QuantityStepper.kt
    │                           #   FoodCard.kt · Controls.kt（SelectIndicator/CheckSwitch/EmptyState）· DataCorrupt.kt（corruptKeyNames/DataCorruptBanner）
    │                           #   MiuixDialog.kt（WindowDialog 封装）；另有原本就独立的 UndoSnackbar.kt · ExpiryCalendar.kt
    │   └─ app/                 # App 级双主题骨架（2026-09-16 第三批 #3 新建 10 个文件 2,746 行；2026-09-17 增至 12）：
    │                           #   AppChrome.kt（AppScaffold / AppTopBar / AppSnackbarHost + Placement + Form / AppMessageScreen / 顶栏动作）
    │                           #   AppText.kt（AppTextScale 13 档语义字号 + AppText / AppEmojiText / AppMutedText + 主题色访问器）
    │                           #   AppSurface.kt · AppListRow.kt · AppControls.kt · AppButtons.kt · AppInfo.kt
    │                           #   AppConfirmDialog.kt · AppFormDialog.kt · AppOptionDialog.kt（确认 / 带输入框 / 选项列表 三类弹窗）
    │                           #   AppIme.kt（stickyImePadding()：MD3 输入弹窗的粘性键盘避让，2026-09-17 新增）
    │                           #   AppBatchMoveDialog.kt（批量「移动存放位置」弹窗，2026-09-17 由包根 AppDialogs.kt 搬来）
    └─ screens/                 # 每屏一文件，自带 Scaffold。两层结构（2026-08-22 B-08 起）：
                                # ① *State.kt 状态容器（8 个）：remember*UiState + 纯计算函数，双主题共用、单测覆盖
                                # ② 渲染层（9 个屏幕文件 + **12 个**从设置页 / 编辑页搬出来的文件 ⇒ 不含 *State.kt 共 **21** 个；① 的 8 个 *State.kt 另计）：
                                #    ConsumptionLog / Archive / FoodDetail / Home /
                                #    FoodList / Manage（阈值·分类·位置）/ Stats / Settings —— **八对已于 2026-09-16 全数合并为
                                #    单文件双主题**，Miuix*Screen.kt 双胞胎全部删除（外壳差异走 ui/components/app/）；
                                #    屏幕本体 17 文件 7,541 行（2026-09-16 前基线）→ 9 文件（-44%）；
                                #    ⚠️ #10a（2026-09-18）起该目录另有 **7 个**从 SettingsScreen.kt 逐字搬出去的文件
                                #    （都不是新增屏幕）：10a-1 的 3 个**弹窗文件** SettingsBackupDialogs /
                                #    SettingsCloudDialogs / SettingsSnapshotDialogs，10a-2 的 4 个 **body / 小组件文件**
                                #    SettingsBodyMd3 / SettingsBackupMd3 / SettingsBodyMiuix / SettingsMd3Widgets
                                #    ⚠️ #10e（2026-09-19）起另有 **5 个**从 EditFoodScreen.kt 抽出去的**区块文件**
                                #    （同样不是新增屏幕）：EditFoodCoverSection / EditFoodNameSection /
                                #    EditFoodFieldSections / EditFoodThresholdSection / EditFoodChrome
                                #    ⇒ 该目录（不含 *State.kt）16 → **21** 文件；编辑页入口行数不在此写死（M1-2 又加过 13 行），
#      现值看 doc-metrics 的「#10e 五个区块文件行数」与「屏幕目录最大文件」两行
                                #    ⇒ 所以「文件数」不再等于「屏幕数」；
                                #    **行数账不在本文件维护**
                                #    （此前这里手抄的 4,204 / 2,746 / 6,950 三个数已过期，且与本文件 §5 的「12 文件」自相矛盾），
                                #    现值见 docs/DESIGN_SPEC.md §7 的「口径」行，复核跑 bash tools/doc-metrics.sh
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
| `ArchivedItem` | `archived_items` | 归档：原 item + 归档日 + 原因(DELETED/CONSUMED/EXPIRED)；保留上限是常量 `FoodArchive.ARCHIVE_RETENTION`（两处写入点共用 `trimArchiveRetention`），被上限挤掉的条数累计记在 `archive_overflow_total` |
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

> 表中「屏幕」列写的是**屏幕组件名，不是文件名**：三个管理页（`ThresholdManageScreen` / `CategoryManageScreen` /
> `LocationManageScreen`）同在 `ManageScreens.kt` 里，`AppRoute.Main` 那行列的是 pager 的四个页面（对应
> `HomeScreen.kt` / `FoodListScreen.kt` / `StatsScreen.kt` / `SettingsScreen.kt`）。**除编辑页外，每个路由都有 Miuix 实现**，运行时按 `LocalThemeStyle` 分流；**除编辑页外的 8 对屏幕已于 2026-09-16 全数合并为单文件双主题**（`ConsumptionLogScreen` / `ArchiveScreen` / `FoodDetailScreen` / `HomeScreen` / `FoodListScreen` / `ManageScreens` / `StatsScreen` / `SettingsScreen`，外壳差异走 `ui/components/app/` 的骨架组件；首页 / 列表页 / 统计页 / 设置页这四份由 `NavChrome.kt` 的 pager 直接调用，其余由 `AppNavGraph.kt` 直接调用 —— **这两个文件都已不含任何 `LocalThemeStyle` 分支**，`Miuix*Screen.kt` 双胞胎全部删除）。合并后主题分支只剩两处合法落点：`ui/components/app/` 的骨架组件，以及设置页那种「两版排版习语根本不同」的 body（`Md3SettingsBody` / `MiuixSettingsBody`，见 `devlog/2026-09-16.md` §15–§22）。无论一份还是两份，业务数据都必须来自同一份 `*State.kt` 状态容器，禁止在 UI 文件里重写业务计算（`ScreenParityTest` 静态拦截）。

- 底栏 Tab：`AppRoute.Main` 内 HorizontalPager（home → list → stats → settings）；点击 Tab 用 `folmeSpring` 连滑，跨页会经过中间页。二级页走 miuix-nav `NavDisplay` + `NavTransitions.MiuixDefault`（全宽卡片滑 + 1/4 视差 + 圆角 dim），隐藏底栏与 FAB（`showChrome`）
- FAB（添加食品）仅在 home 与 list（Pager 第 0/1 页）显示
- 新增路由：在 `AppRoute` 加类型 + 在 `AppNavGraph.kt` 的 `NavDisplay` 里注册 `entry` + 按需更新 `onTabs`/`showChrome`（两者都在 `MainApp.kt`），并更新本表

## 5. 关键实现约定

- **删除 = 归档**：业务删除一律调 `repo.archiveItems(ids, reason)`；只有归档页的“彻底删除/清空”真正移除数据
- **消耗记录**：`changeQuantity(id, delta)` 在 delta<0 时自动写 ConsumptionRecord，调用方无需额外处理
- **吃完自动归档（v2.4）**：`changeQuantity` 在消耗导致数量归零时自动移入归档（CONSUMED）并返回 true；UI 可依此提示/返回
- **OCR**：已移除，不要再加 `DateOcr` / ML Kit
- **坚果云同步（v2.4，v2.7 多版本轮转）**：`NutstoreSync` 单例（OkHttp），WebDAV MKCOL+PUT/GET/PROPFIND/DELETE；账号存 DataStore（nutstore_account / last_sync_time）；**密码经 `SecureStore`（Android Keystore AES-GCM）加密后存 `nutstore_password_enc`，启动时 `migratePlaintextPassword()` 自动迁移旧明文**；上传内容即 buildBackupJson() 产物，下载走 importBackupJson()
  - **凭据 key 的实际落点是 `credentials_store`，不是 `pantry_store`**（2026-09-19 M1-1）：读写都必须带 `store = credentialsStore`，启动时 `migrateLegacyCredentials()` 先把旧位置搬过来。业务数据那份文件现在随系统备份走，密钥一个字都不能进
  - **`SecureStore` 三条约定**（同日 M1-4）：① **只有写侧建钥** —— `encrypt` 走 `getOrCreateKey()`、`decrypt` 走 `readKey()`（读侧建钥是纯伤害：换机后白占 `KEY_ALIAS`、旧密文照样解不开，还会与并发加密抢建）；② **`KeyAlreadyExistsException` 不是故障** —— 它是"别人刚建好了"，读回来复用；此前启动期 `migratePlaintextPassword()` 与设置页保存会同时撞进 `getOrCreateKey()`，后到的那个抛异常→被 `encrypt` 的 `catch` 吞成 null→调用方误判"Keystore 不可用"→**把明文密码落盘并提示用户安全性降级**（密钥其实好好的）；现在「读→建」整体 `synchronized` + 撞上后重读，两层都要有（锁只在本进程有效）；③ 失败一律返回 `null` 而不是空串（既有守卫 `CorruptGuardTest.凭据加密失败必须可区分且调用方按非空判定` 钉着，新增的两条钉 ①②）
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
- **备份排除规则（2026-08-21 · 2026-09-16 校正 · 2026-09-19 M1-1 改口径）**：**凭据与业务数据分住两个 DataStore 文件**（`FoodRepository` 的 `credentialsStore` = `credentials_store.preferences_pb`，`dataStore` = `pantry_store.preferences_pb`，都在 `filesDir/datastore/` 下），排除规则因此可以**精确到文件**：
  - `res/xml/backup_rules.xml`（API 26–30，`fullBackupContent`）与 `data_extraction_rules.xml`（API 31+）的 `<cloud-backup>` 排除清单**逐条相同**：`datastore/credentials_store.preferences_pb` + `covers/` + `corrupt/` + `snapshots/`
  - `<device-transfer>` 同上但**放行 `covers/`**（唯一被承认的不对称，理由见下）
  - ⇒ **业务数据（库存/归档/消耗/历史）现在进备份**；坚果云凭据不进（Keystore AES-GCM 密文的密钥不跨设备，恢复后必然解不开；`nutstoreCredentialBrokenFlow` 仍保留，用来兜"密文比账号先到位"这类残留状态）
  - **旧口径为什么必须废掉**：此前凭据与业务数据同住 `pantry_store` 一个文件，为了挡一个密钥只能 `<exclude domain="file" path="datastore/" />` 整目录排除 ⇒ **用户的库存数据也不在备份里**，换机/重装后归零，只能靠当初手动导出过 JSON 或点过坚果云同步。官方 DataStore 文档给的正是"敏感与非敏感拆成不同文件、再按文件配 `data_extraction_rules.xml`"这条路
  - **老用户不丢凭据**：启动期 `migrateLegacyCredentials()`（`FoodCredentials.kt`）先把业务文件里的 3 个 nutstore key 搬到凭据文件、再从业务文件删除；顺序是先写新后删旧 ⇒ 中途被杀最坏是两处各一份（读侧只看新文件），不会丢凭据。`CredentialsStoreTest` 5 条钉住读写接线与迁移幂等
  - 2026-09-16 记的两条"已知不一致"**同时消解**：① `device-transfer` 放行 `covers/` 却排除 `datastore/` ⇒ 图到了 JSON 没到 ⇒ `cleanupOrphanCovers()` 把封面当孤儿删；现在两者一起到位。② `snapshots/` 未排除 ⇒ 每日全量快照进用户 Google 账号（与 README 的"只存本机"表述冲突）；现在显式排除，README 那句已按新行为改写
  - 守卫：`BackupRulesTest`（两份 XML 逐条比对，含"不许再出现整目录 `datastore/` 排除"这条反向断言）；`CredentialsStoreTest`（含"Keystore 不可用时的明文回退也只进凭据文件"）
- **归档恢复去重（v2.4）**：`restoreArchived()` —— 同 ID 只移除归档；同名+同生产日期合并数量（返回 merged 供 UI 提示）；否则新增，数量 0 恢复为 1
- **主题渐变（v2.4）**：Theme.kt `animateColorScheme()` 对全部 **36** 个颜色角色 450ms tween（`animatedColor(target.*)` 逐角色调用，正好是 `ColorScheme` 的完整参数表；
  2026-09-17 复核：此前写 37 是错的，重数跑 `grep -c "animatedColor(target\." app/src/main/java/com/agon/app/ui/theme/Theme.kt`）；新增颜色角色时需同步加入该函数
- **图片存储**：封面统一通过 `copyImageToCovers()` 落盘到 `filesDir/covers/`，FoodItem 只存绝对路径；展示用 `FoodAvatar`，优先级：照片 > coverText > 分类 emoji
  - **落盘是 tmp→rename 两步（2026-09-19，M1-2）**：字节先写 `covers/.tmp-<uuid>.jpg`，写完才 `renameTo` 成正式名（同目录改名在 Linux 上原子）；`finally` 里删掉残留的 tmp。为什么：调用点在 `rememberCoroutineScope` 里，用户选完图立刻返回会让协程被取消，直接写正式名会留下一份**截断的 JPEG** —— 而 `exists()` 为真、`photoPath` 指着它 ⇒ 渲染侧判定通过、Coil 解码失败 = 永久坏图。现在任何早退/取消只留下一个不被任何记录引用的临时名 ⇒ 下次启动 `cleanupOrphanCovers()` 收走（⚠️ 那个函数**不许**加"只删 .jpg 结尾 / 跳过点开头"的过滤，`EditFoodSaveGuardTest` 钉着）；`Bitmap.compress` 返回 false 也按失败处理（文件写完了但内容不合法，`exists()` 看不出来）
  - **编辑页保存要等落盘（同轮 M1-2）**：`EditFoodScreen` 的 `onSave` 从「`viewModel.upsert(item)` + 立刻 `onBack()`」改成 `viewModel.upsertAndAwait(item)` 拿到 `Job` → `join()` → 再 `onBack()`，期间按钮 `enabled = !saving` 挡住二次点击（连点两下在新增模式里会生成两个 UUID = 两条重复记录）。旧写法今天能写进去**只是因为 VM 是 Activity 级**、`viewModelScope` 不随屏幕出栈而取消；#10c 一旦拆成每屏一 VM，它就是「点保存 = 丢数据」。写协程仍起在 `viewModelScope` 上（`join` 被取消也不影响写完）
  - **已知待办**：① `photoPath` 存**绝对路径**，换设备/清数据后必然悬空，应改存文件名（`covers/<uuid>.jpg` 的 basename）运行时用 `context.filesDir` 拼；② 存在性判断目前在**组合期**同步调 `File.exists()`（`FoodAvatar.kt` 的 `File(item.photoPath).exists()`、`EditFoodCoverSection.kt` 的 `File(photoPath).exists()`；后者 #10e 前在 `EditFoodScreen.kt`），列表滚动每帧重算 syscall，应移到 VM/IO 侧或改由 Coil `onError` 回落 emoji。见 `docs/audits/chileme-review.md` P1-8
- **进度条语义**：一律用 `elapsedRatio`（正相关，时间过去多少走多少），禁止再用 freshness 直接作进度
- **键盘避让（2026-09-15）**：Android 15+ 强制 edge-to-edge 后 manifest 的 `adjustResize` **不再缩窗口**，键盘只是叠在窗口上，必须自己消费 `WindowInsets.ime`。三条硬规则：① **含输入框的屏幕**一律 `Scaffold(modifier = Modifier.imePadding())`（整屏缩到键盘之上，滚动区同步变矮）；② **不在 Scaffold 内的 App 级浮层**用 `.navigationBarsPadding().imePadding()` 两段式（等价于旧的 `navigationBarsWithImePadding()`，内层只补差额，不会叠加成一条大空隙）——**底栏按产品决定不跟随抬升（A 方案）**，是全 App 唯一「键盘弹出时允许被遮挡」的元素；③ **MD3 弹窗**是独立浮动窗口，必须 `DialogProperties(decorFitsSystemWindows = false)` 才会把 IME inset 透给内容，否则底部按钮被键盘盖住。**Miuix `WindowDialog` 例外**：库内 `DialogContent` 根节点自带 `imePadding()`（窗口属性 `decorFitsSystemWindows = false` + `usePlatformDefaultWidth = false`），本项目**不要**传 `defaultWindowInsetsPadding = false`，也不要给 Miuix 弹窗再加 padding。**带输入框的 MD3 弹窗自 2026-09-17 起改用 `stickyImePadding()`**（`ui/components/app/AppIme.kt`，三处：`AppFormDialog` / `AppBatchMoveDialog` 批量移动位置 / `SettingsCloudDialogs` 坚果云 —— #10a-1 前在 `SettingsScreen`）：焦点在同一弹窗的两个输入框之间切换时平台会 restartInput、输入法窗口整个消失再出现，`WindowInsets.ime` 瞬时归零，居中弹窗随之上下坠一下（用户真机报告；类型相同的两个文本框也会，故与 `keyboardOptions` 无关）。粘性避让在 inset 变小时先按住 `holdMillis`（默认 300ms）再平滑落回，焦点切换因此零位移，代价是主动收起键盘时弹窗晚 300ms 才落回居中。**Miuix 侧无法同样处理**：库的 `imePadding()` 在 `DialogContentLayout.DialogContent` 内部，唯一开关 `defaultWindowInsetsPadding = false` 会连带关掉 `navigationBarsPadding` / `captionBarPadding`（弹窗底边掉到导航栏下、圆角被压），且已查证上游 pinned tag v0.9.4-rc01 全库只有两处用 ime（`.imePadding()` 与关闭弹窗时 `keyboardController.hide()`），无任何 IME 平滑能力 —— 按用户 2026-09-17 决定 Miuix 侧不动，要根治需上游支持。屏幕级 `AppScaffold(modifier = Modifier.imePadding())` 刻意不改（内容高、幅度小，且要动第 1 条整份屏幕清单）**content 必须是单一根节点**：库把 title / summary / `content()` 放进一个不带 `verticalArrangement` 的 Column，两个平级节点之间是 0dp（2026-09-17 真机复测踩到，`MiuixDialogContentTest` 静态拦截）。回归守卫见 `ImeHandlingTest`
- **统计口径（2026-09-15）**：「过期浪费」= `calculateWastedTotal(archived)`，按**件数**（`sumOf { item.quantity }`）而非归档条数，与同屏按件求和的「本周消耗」保持一致。注意该指标仍受归档保留上限影响（`FoodArchive.ARCHIVE_RETENTION`，被挤掉的旧归档不计入，见 `StatsState` 的口径注释）；2026-09-19（M1-3）已把「静默截断」变成「有账可查」——上限抬高到常量所指的值，且每次挤掉的条数累计写入 `archive_overflow_total`（`FoodRepository.archiveOverflowFlow`，目前无 UI 消费，接提示时不必再动仓库层），长期不失真那一步（独立计数器）即由此达成
- **「件」与「条」的用词规则（2026-09-15）**：写给用户的**带「件」的文案必须是 `quantity` 求和**（`BackupData.itemQuantity` / `HomeScreenState.quantityOfStatus` / `StatsState` 的按件字段），`items.size` 只能出现在「条/记录」语境或**不带单位**的筛选计数里（首页三张统计卡不带单位、点进去是记录列表）。归档/消耗/历史一律「条」。已按此修正：首页新鲜度横幅、一键清理按钮及其撤销提示、导入预览的库存位数
- **屏幕只换外壳（2026-09-15 立规，2026-09-16 扩大范围）**：屏幕文件（`*Screen.kt` / `*Screens.kt`）必须调用 `remember*UiState` 复用已测状态容器，**禁止在 UI 文件里重写聚合计算**；`ScreenParityTest`（原名 `MiuixParityTest`，因双主题合并后文件名不再带 `Miuix` 前缀，按前缀枚举会漏掉刚合并的屏幕，故改为覆盖全部屏幕文件；⚠️ #10a-1 起两条规则的扫描面**不再同宽** —— 规则 1（必须调 `remember*UiState`）仍只按 `*Screen.kt` / `*Screens.kt` 点名，规则 2（禁止内联聚合）加宽到目录内除 `*State.kt` 的所有文件，否则搬进 `*Dialogs.kt` 的弹窗代码会静默逃出守卫）会静态拦截（此前 `MiuixStatsScreen` 手抄了一份统计逻辑，导致 `StatsStateTest` 测的是 MIUIX 下不执行的代码）
- **App 级组件层（2026-09-16，第三批 #3）**：屏幕骨架下沉到 `ui/components/app/`（12 文件；**行数不在本文件维护**，实测见 doc-metrics「App 级组件层」那行），屏幕本体只保留一份业务结构，主题分流全部发生在组件层内部（`LocalThemeStyle`）。**组件清单与每个组件的关键约定（含已踩过的坑）见 `docs/DESIGN_SPEC.md` §4.1 —— 那是唯一事实源**。本条此前是一份 4,254 字符的逐轮累加清单，与 `CLAUDE.md` §5、`DESIGN_SPEC.md` §7 的两份手抄清单互为重复（三份分别提到 56 / 43 / 55 个组件名，CLAUDE 那份 93% 与本文重叠，而本文这份还**漏了 `AppStatusCard`**），2026-09-17 合并为一处。这里只留**架构层**的三条约定：
  - **为什么要有这一层**：合并前加一个功能要「改两个文件 + 一处 if/else」；组件层让主题差异只有一个合法落点，边际成本降到「改一个文件」。八对合并后 `AppNavGraph.kt` 与 `NavChrome.kt` 都已零主题分支，唯一保留双份的是设置页 body（理由见 `DESIGN_SPEC.md` §7）。
  - **跨主题的宿主对象不得漏回屏幕层**：MD3 与 Miuix 的 `SnackbarHostState` 是**两个不相干的类型**，故 `AppSnackbarHostState` 对外只暴露 `showUndoSnackbar(): Boolean`，免得两个主题的 `SnackbarResult` 顺着签名漏回屏幕层（`isMiuix` 参与 `remember` key 的坑见 §4.1）。
  - **取色例外只有一个**：`appChartColors()`（统计页图表调色板）是「屏幕侧取色」**唯一**被承认的例外，其余一律走组件层的取色访问器；Miuix 色板没有 `tertiary` / `inversePrimary`，两份 8 色清单照抄不统一。
  - **四条从上游源码核出来的硬约束**：① Miuix 的 `Card` 与 `Surface` **不等价** —— `Card`（v0.9.4-rc01，`Card.kt:50`）圆角默认值同为 16dp，但内部还套一层 `Column(Modifier.padding(CardDefaults.InsideMargin))` 且 content 是 `ColumnScope`。合并前两版**两种都在用**（消耗记录行 = `Surface`，归档行与详情页两张信息卡 = `Card`），所以组件层留了两个：`AppCard`（无内衬）与 `AppPaddedCard`（Miuix 侧官方 `Card`、多一层内衬）。**谁用哪个由「合并前那一版用的是什么」决定，不许顺手统一** —— 统一掉就是只有真机看得出来的视觉改动。② 两主题的字号档位不是一一对应：Miuix 的 `body1` 同时承接 MD3 的 `titleMedium` 与 `bodyLarge`（`MiuixFoodDetailScreen` 原本就这么用），档位表按「合并前两版各自的取值」逐项登记，便于逐行核对等价性。③ 弹窗必须放在 `AppScaffold` 的 content lambda **里面**并无条件调用、靠 `show` 控制（Miuix `WindowDialog` 的库约束，见 `MiuixDialog.kt`）；MD3 的 `AlertDialog` 放里放外都是独立窗口、渲染无差别，故统一按 Miuix 的要求落位。④ Miuix `TopAppBar` 的 `subtitle: String = ""`（v0.9.4-rc01 `TopAppBar.kt:100` 已核对），所以「传空串」与「不传」逐字等价 —— `AppTopBar` 用一个 `subtitle` 参数喂两边：Miuix 直接吃，MD3 侧「带副标题」即折叠式 `LargeTopAppBar` + `nestedScroll` + `fillMaxSize`（合并前 MD3 首页正是这么写的）。Miuix 也有 `largeTitle` + `scrollBehavior` 可做折叠顶栏，但合并前没用，**不替它开**。⑤ 撤销条落位有两种且不是一个数字能表达的：二级页 MD3 侧 `navigationBarsPadding() + 24dp`、Miuix 侧不抬；Tab 页两版都抬 `84dp` 且 MD3 侧**不再**叠 `navigationBarsPadding`。故做成具名枚举 `AppSnackbarPlacement` 而不是 `Dp` 参数。⑥ **只在一个主题生效的参数必须在 KDoc 里点名哪边不生效**：`AppCardTone.ContainerLow` 只改 MD3 底色（Miuix 两版都是库 `Card` 默认色，上游 `colors` 默认走 `CardDefaults.cardColors()`，不替它猜「低一档」是哪个色）、`AppChipTone` 只有 `Primary` 显式指定选中态文字色、`AppTopBar(selectionMode = true)` 只让 MD3 换底色。「参数被静默忽略」与「参数 documented 地单边生效」是两回事，前者迟早有人踩（这条规矩是第 2 对给 `AppTopBar.subtitle` 立的，第 5 对起写进总约束）。
  - **键盘避让仍由屏幕自己声明**：`AppScaffold` 不无条件加 `imePadding()`（没有输入框的屏幕不需要），需要的屏幕传 `modifier = Modifier.imePadding()`。这与路线图原先设想的「组件层统一吃掉 inset、守卫改查 `AppScaffold` 一处」不同 —— 显式声明让「哪一屏要避让」在屏幕文件里可见，`ImeHandlingTest` 第 1 条也仍能按屏幕点名，且合并后**一个条目覆盖两套主题**（清单由 5 项缩到 4 项）
- **启动门控**：MainActivity 用 core-splashscreen `setKeepOnScreenCondition` 持住启动画面，直到 `viewModel.ready`（DataStore 首发）才渲染，避免主题/内容闪烁；新增首屏依赖的 Flow 时要加入 ready 的 combine
- **拍照**：FileProvider authority 一律用占位符 —— Manifest 写 `${applicationId}.fileprovider`（实际值 `com.chileme.pantry.fileprovider`），代码写 `${context.packageName}.fileprovider`（`EditFoodScreen.kt` 里 `FileProvider.getUriForFile(…)` 那句），**禁止硬编码 `com.agon.app.fileprovider`**（namespace 与 applicationId 是分离的，硬编码会在运行时抛 `IllegalArgumentException`）。临时文件写 `cacheDir/camera/`（`TakePicture` 回调后已清理，2026-09-15），paths 配置见 `res/xml/file_paths.xml`。该文件已由 `path="."`（暴露整个私有目录）收窄为仅 `camera/` 与 `covers/` 两个子目录，新增共享目录需显式登记
- **备份**：导出用 `CreateDocument("application/json")`，导入用 `OpenDocument`；导入是整体替换而非合并（流程加固见上方「导入备份加固」条）
- **列表批量操作**：长按卡片进入多选模式（selectedIds 非空即多选）；顶栏切换为选择态（退出/全选），底部滑入批量归档栏；BackHandler 退出多选；批量操作走 `archiveBatch`/`restoreArchivedBatch`；多选期间 FAB 隐藏（fabSuppressed）
- **应用图标**：自适应图标 `mipmap-anydpi-v26/ic_launcher.xml`（前景 `drawable-*/ic_launcher_foreground.png` + 纯色背景 `#FBF6E9` + `monochrome` 供 Android 13+ 主题图标）；源图由用户 SVG 处理而来（已去黑边，主体缩放至 66dp 安全区）。**legacy `mipmap-*/ic_launcher.png` 已于 2026-08-22（B14）删除** —— minSdk 26 起没有设备会用到它们，`@mipmap/ic_launcher` 现只解析到 `anydpi-v26`
- **Snackbar**：带悬浮导航栏的屏幕（首页 / 设置）撤销条必须抬到悬浮栏之上，已封进 `AppSnackbarPlacement.FloatingNav`（两主题都是 `padding(bottom = 84.dp)`）；二级页走默认的 `SystemBars`（MD3 侧 `navigationBarsPadding() + 24dp`）
- **撤销 Snackbar**：`ui/components/UndoSnackbar.kt` 的 `showUndoSnackbar`。MD3 自绘 Material History 圆环 path（去指针），变换到圆心后再叠粗数字。消耗记录撤销：`DeletedConsumption(record, index)`，`addConsumption(record, index)` 插回删除前在日期倒序列表中的位置，避免 `listOf(record)+records` 提到最前；LazyColumn `animateItem` 带 placementSpec。MD3 宿主的 `navigationBarsPadding` + 24dp 抬升已封进 `ui/components/app/AppChrome.kt` 的 `AppSnackbarHost`（`AppSnackbarPlacement.SystemBars`，消耗记录页 / 归档页在用），Miuix 侧仍走库默认宿主；带悬浮导航栏的 Tab 页改用 `AppScaffold(snackbarPlacement = FloatingNav)`（两主题都抬 84dp，首页在用），零星额外避让才用 `snackbarModifier`。设置页那一对（2026-09-16 第 8 对）已把「MD3 `SettingsScreen` 用普通 `SnackbarHost` 而非可滑掉的 `SwipeDismissSnackbarHost`」这处差异处置掉：本页所有提示都没有撤销动作，`SwipeDismissSnackbarHost` 会把文案截成一行并画倒计时环，故 `AppSnackbarHost` 新增 `form: AppSnackbarForm` 参数，设置页传 `AppScaffold(snackbarPlacement = FloatingNav, snackbarForm = Plain)`（**`form` 只影响 MD3 分支**，Miuix 侧恒为库的官方宿主；见 `AppSnackbarForm` 的 KDoc）。覆盖层 Box 必须 `fillMaxWidth`。MIUIX 宿主保持库默认 `canSwipeToDismiss=true`
- **批量操作（v2.3 起，取代早期「两段式滑动归档」）**：列表项**长按进入多选**（`BatchBars.kt` 的 `BatchActionBar`，由 `MainApp.kt` 的 `bottomBar` 槽位挂载），选中集合通过 `AppViewModel.selectedIds` 暴露；底部操作栏提供 归档 / 改存放位置 / 取消，归档时按 `ArchiveReason.DELETED` 记因。**不要**再按滑动归档实现新功能（`SwipeToDismissBox` 现在只用于 `UndoSnackbar` 的提示条滑动）
- **FAB 与撤销**：Snackbar 展示“撤销”期间调 `viewModel.setFabSuppressed(true)` 隐藏 FAB（finally 复位），避免遮挡撤销按钮
- **底栏自动隐藏**：`MainApp.kt` 定义的 NestedScrollConnection（±8f 阈值）由 `AppNavGraph.kt` 挂到 `NavDisplay` 的 modifier 上，监听列表滚动，下滑隐藏底栏+FAB（slideOutVertically），上滑/切页恢复
