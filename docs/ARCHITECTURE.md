# 技术架构说明（ARCHITECTURE）

## 1. 技术栈与版本（不得随意升级）

- Kotlin 2.2.21 / AGP 8.10.1 / Gradle 8.11.1 / JDK 17
- compileSdk 36、minSdk 24、targetSdk 36
- **applicationId `com.chileme.pantry`**（v2.1 起）；namespace / 代码包名保持 `com.agon.app` 不变。FileProvider authority 使用 `${applicationId}.fileprovider` 占位符，代码中用 `${context.packageName}.fileprovider`
- **Release 构建（v2.5 起）**：`isMinifyEnabled = true` + `isShrinkResources = true`（R8 代码/资源压缩），但 `proguard-rules.pro` 中 `-dontobfuscate` **禁用混淆**——类名/方法名/字段名全保留，堆栈可读无需 mapping。规则文件另含 kotlinx-serialization keep 规则（data 包 serializer 反射）与 OkHttp/Coil dontwarn。release APK ≈ 2.6 MB（debug ≈ 63 MB）
- **到期日历（v2.5 起）**：不再是独立路由，作为 `ExpiryCalendarCard`（ui/components/ExpiryCalendar.kt）嵌入统计页；支持手势左右滑动切换月份，圆点颜色 = 紧急度（去重后最多 3 点）
- Compose BOM 2026.01.01（material3、icons-extended）
- Navigation Compose 2.9.7、Lifecycle/ViewModel Compose 2.10.0
- DataStore Preferences 1.2.0 + kotlinx-serialization-json 1.9.0
- Coil 3.3.0（照片封面加载）
- ~~ML Kit OCR~~ 已于 v2.3 移除（识别率低），`DateOcr.kt` 已删除
- MaterialKolor `com.materialkolor:material-kolor:4.0.1`（MD3 种子色生成主题，v2.2 起）
- OkHttp `com.squareup.okhttp3:okhttp:4.12.0`（坚果云 WebDAV 同步，v2.4 起）

## 2. 分层结构

```
app/src/main/java/com/agon/app/
├─ MainActivity.kt              # 单 Activity；主题接入、NavHost、底栏、FAB
├─ data/                        # 数据层（无 UI 依赖）
│   ├─ FoodModels.kt            # 数据模型 + 派生属性（过期计算/状态判定）
│   ├─ FoodRepository.kt        # 唯一持久化入口（DataStore）
│   └─ ImageStore.kt            # 封面图片复制到私有目录
├─ viewmodel/
│   └─ AppViewModel.kt          # 全局共享 VM（AndroidViewModel），StateFlow 暴露
└─ ui/
    ├─ theme/                   # Palettes.kt（种子色方案）/ Color.kt（仅状态语义色）/ Theme.kt（MaterialKolor 生成）
    ├─ components/Common.kt     # 复用组件：StatusBadge/FoodAvatar/FoodCard/QuantityStepper/EmptyState/LocationTag
    └─ screens/                 # 每屏一文件，自带 Scaffold
```

**规则**：UI → ViewModel → Repository → DataStore，单向依赖；UI 绝不直接访问 DataStore；所有写操作在 `viewModelScope` 内执行。

## 3. 数据模型（均 @Serializable，存为 JSON 字符串）

| 模型 | 存储 key | 说明 |
|---|---|---|
| `FoodItem` | `food_items` | 库存记录；日期存 epochDay(Long)；`category` 存 CategoryDef.id 字符串；`expiringThresholdDays: Int?` 为单条阈值覆盖；`coverText` 自定义封面 emoji/短文字（≤4 字符） |
| `CategoryDef` | `custom_categories` | 可编辑分类(id/label/emoji)；默认 8 个 id 沿用旧枚举名，新增用 UUID；孤儿 id 由 `byId()` 回退 FallbackCategory("其他") |
| `List<String>` | `custom_locations` | 可编辑位置预设列表 |
| `ArchivedItem` | `archived_items` | 归档：原 item + 归档日 + 原因(DELETED/CONSUMED/EXPIRED)；上限 200 |
| `ConsumptionRecord` | `consumption_records` | 消耗流水（减库存时自动记录）；上限 1000 |
| `HistoryEntry` | `history_entries` | 录入历史（名称去重，上限 50） |
| `Map<String,Int>` | `category_thresholds` | 分类临期阈值；key 为 FoodCategory.name |
| `BackupData` | （导出文件） | 以上全部数据的聚合，version=2（含 categories/locations；v1 文件可兼容导入） |

其他 key：`seeded`(Boolean)、`dynamic_color`(Boolean)、`dark_mode`(Int: 0跟随/1浅/2深)、`palette`(String: AppPalette 枚举名，默认 "MINT")。

**状态判定逻辑**（FoodModels.kt）：`statusFor(thresholds)` — 过期: daysLeft<0；临期: daysLeft<=有效阈值；有效阈值 = 单条覆盖 ?: 分类设置 ?: 7。UI 一律用 `statusFor`，不要自行比较天数。

## 4. 导航路由表

| 路由 | 屏幕 | 说明 |
|---|---|---|
| `home` | HomeScreen | 首页 Dashboard（起始页） |
| `list?filter={filter}` | FoodListScreen | filter: null/expiring/expired |
| `stats` | StatsScreen | 统计 |
| `detail/{id}` | FoodDetailScreen | 食品详情 |
| `edit` / `edit?id={id}` | EditFoodScreen | 新增 / 编辑 |
| `archive` | ArchiveScreen | 归档（设置/食品列表可进入，带搜索） |
| `settings` | SettingsScreen | 设置 |
| `manage_thresholds` | ThresholdManageScreen | 临期阈值管理（设置二级页） |
| `manage_categories` | CategoryManageScreen | 分类管理（设置二级页） |
| `manage_locations` | LocationManageScreen | 存放位置管理（设置二级页） |


- 底栏 Tab：home / list / stats / settings；`detail`/`edit`/`archive` 隐藏底栏与 FAB（通过 `showChrome` 控制）
- FAB（添加食品）仅在 home 与 list 显示
- 新增路由：在 MainActivity 的 NavHost 注册 + 按需更新 `tabRoutes`/`showChrome` 逻辑，并更新本表

## 5. 关键实现约定

- **删除 = 归档**：业务删除一律调 `repo.archiveItems(ids, reason)`；只有归档页的“彻底删除/清空”真正移除数据
- **消耗记录**：`changeQuantity(id, delta)` 在 delta<0 时自动写 ConsumptionRecord，调用方无需额外处理
- **吃完自动归档（v2.4）**：`changeQuantity` 在消耗导致数量归零时自动移入归档（CONSUMED）并返回 true；UI 可依此提示/返回
- **OCR（v2.4）**：`recognizeDates()` 返回 `OcrDates(production, expiry)`；正则按优先级匹配并占用文本区间防重复；日期前 14 字符上下文匹配 EXP/MFG 等语义标记分类角色
- **坚果云同步（v2.4，v2.7 多版本轮转）**：`NutstoreSync` 单例（OkHttp），WebDAV MKCOL+PUT/GET/PROPFIND/DELETE；账号存 DataStore（nutstore_account / last_sync_time）；**密码经 `SecureStore`（Android Keystore AES-GCM）加密后存 `nutstore_password_enc`，启动时 `migratePlaintextPassword()` 自动迁移旧明文**；上传内容即 buildBackupJson() 产物，下载走 importBackupJson()
  - **多版本轮转（v2.7）**：上传文件名 `chileme_backup_yyyyMMdd_HHmmss.json`，上传后 PROPFIND 列目录、自动 DELETE 多余旧版本，云端保留最近 `CLOUD_BACKUP_KEEP`（=3）份；自动同步走同一 upload 入口，同样轮转
  - **恢复选择（v2.7）**：`listBackups()` 返回 `CloudBackup(fileName, sizeBytes)` 列表（新→旧），UI 弹窗选择具体版本后 `download(fileName)` 恢复；旧版单文件 `chileme_backup.json` 兼容显示在列表末尾且不参与轮转删除
- **自动同步（v2.4）**：`auto_sync_days`（0=关/1/3/7）+ `last_auto_sync_epoch_day`；ViewModel init 时 `maybeAutoSync()` —— 间隔到且凭据完整则静默上传，成功后 Home 页 Snackbar 提示，失败静默下次重试；无 WorkManager 无后台任务
- **孤儿图片清理（v2.4）**：启动时 `cleanupOrphanCovers()` 删除 covers/ 中不被库存/归档引用的文件
- **消耗记录压缩（v2.4）**：`compactConsumption()` —— 近 90 天逐笔保留，更早按「月×名称」聚合；禁止恢复 take(N) 粗暴裁剪
- **归档恢复去重（v2.4）**：`restoreArchived()` —— 同 ID 只移除归档；同名+同生产日期合并数量（返回 merged 供 UI 提示）；否则新增，数量 0 恢复为 1
- **主题渐变（v2.4）**：Theme.kt `animateColorScheme()` 对全部 35 个颜色角色 450ms tween；新增颜色角色时需同步加入该函数
- **图片存储**：封面统一通过 `copyImageToCovers()` 落盘到 `filesDir/covers/`，FoodItem 只存绝对路径；展示用 `FoodAvatar`，优先级：照片 > coverText > 分类 emoji
- **进度条语义**：一律用 `elapsedRatio`（正相关，时间过去多少走多少），禁止再用 freshness 直接作进度
- **启动门控**：MainActivity 用 core-splashscreen `setKeepOnScreenCondition` 持住启动画面，直到 `viewModel.ready`（DataStore 首发）才渲染，避免主题/内容闪烁；新增首屏依赖的 Flow 时要加入 ready 的 combine
- **拍照**：FileProvider authority 固定 `com.agon.app.fileprovider`，临时文件写 `cacheDir/camera/`，paths 配置见 `res/xml/file_paths.xml`
- **备份**：导出用 `CreateDocument("application/json")`，导入用 `OpenDocument`；导入是整体替换而非合并
- **列表批量操作**：长按卡片进入多选模式（selectedIds 非空即多选）；顶栏切换为选择态（退出/全选），底部滑入批量归档栏；BackHandler 退出多选；批量操作走 `archiveBatch`/`restoreArchivedBatch`；多选期间 FAB 隐藏（fabSuppressed）
- **应用图标**：自适应图标 `mipmap-anydpi-v26/ic_launcher.xml`（前景 `drawable-*/ic_launcher_foreground.png` + 纯色背景 `#FBF6E9`）；legacy 兰容图标在 `mipmap-*/ic_launcher.png`；源图由用户 SVG 处理而来（已去黑边，主体缩放至 66dp 安全区）
- **Snackbar**：带悬浮导航栏的屏幕，SnackbarHost 必须加 `padding(bottom = 84.dp)` 避免遮挡
- **滑动归档（两段式）**：SwipeToDismissBoxState 用 `remember(item.id)` 手动构造（禁止 rememberSaveable，防撤销后复用脏状态循环触发）；第一滑弹回进入 armed 待确认（3.5s 超时解除），第二滑才确认滑出；`deleted` 标志保证 onDelete 只触发一次；列表项配 `animateItem(fadeIn 280/fadeOut 200)`
- **FAB 与撤销**：Snackbar 展示“撤销”期间调 `viewModel.setFabSuppressed(true)` 隐藏 FAB（finally 复位），避免遮挡撤销按钮
- **底栏自动隐藏**：MainApp 的 NestedScrollConnection 监听列表滚动，下滑隐藏底栏+FAB（slideOutVertically），上滑/切页恢复
