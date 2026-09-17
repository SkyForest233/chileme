# 吃了么 (Chileme)

> 🍃 本地优先的家庭食品与零食库存管理 Android App。记录保质期、应用内临期提示、减少食物浪费。
>
> （临期提示只存在于应用内：首页提醒 / 三色状态 / 到期日历。**没有系统推送通知**，这是明确的产品取舍，见 [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) §4。）

---

## ✨ 核心特性

- 🍱 **食品库存与保质期管理**
  - 记录食品名称、分类、存放位置、生产日期与保质期。
  - 自动计算到期日与剩余天数，实时展示新鲜度进度条与三色状态（安全 / 临期 / 过期）。
  - 支持拍照 / 相册封面，自动下采样与 EXIF 旋转校正。
- 🎨 **双主题 UI 体验**
  - **Material Design 3** 与 **Miuix (HyperOS)** 风格一键切换（除编辑页外每屏都有两套实现）。
  - 支持跟随系统 / 深色 / 浅色模式，以及 Android 12+ 动态取色（Material You）。
  - 15 套食物主题配色方案（薄荷 / 抹茶 / 蜜橘 / 蓝莓 / 黑芝麻…），由 MD3 种子色生成整套色板。
  - 悬浮胶囊导航可切换为全宽底栏；二级页支持 Android 13+ 预测性返回手势。
- 😋 **消耗打卡与智能归档**
  - 详情页“吃掉一份”连击动效与消耗记录打卡，消耗流水可在专门的记录页查看与修正。
  - 吃完/过期自动归档，支持按原因筛选、关键词搜索、一键撤销恢复。
  - 列表长按多选，可批量归档或批量修改存放位置；90 天前的消耗记录自动按「年×月×名称×单位」聚合，聚合条目受保护不可误删。
- 📊 **统计分析与到期日历**
  - 近 7 天 / 本月消耗与过期浪费统计（浪费按**件数**计）、近 7 天消耗趋势柱状图、库存分类环形图与 TOP5 消耗榜。
  - 统计页内置到期日历，按紧急度彩点标注到期食品。
- ☁️ **本地与云端备份**
  - **本地备份**：基于系统 SAF 导出 / 导入标准 JSON 文件；导入前会预览条数与导出日期、二次确认，并自动留存一份「导入前快照」供回退。
  - **本地滚动快照**：每天自动在应用私有目录留一份全量快照，保留最近 3 份，可手动留存与还原。
  - **CSV 导出**：把当前库存导出为表格（仅库存，不含归档与消耗记录）。
  - **坚果云 WebDAV**：支持多版本轮转云端备份（保留最近 3 个历史版本），支持选择版本恢复。
  - **凭据安全**：WebDAV 应用密码基于 Android Keystore (AES-GCM) 硬件加密存储。
- 🛡️ **数据健壮性**
  - 解析失败时区分「没有数据」与「数据损坏」，损坏态下拒绝写入 / 拒绝导出 / 不清理封面，避免一次异常摧毁全部数据；损坏原文留档并提供「放弃这部分数据」的自救入口。
  - 三条恢复路径（文件导入 / 云端恢复 / 本地快照）都会先自动留一份操作前快照。
- 🔒 **本地优先与隐私保护**
  - 无需注册账号、无第三方广告、无后台追踪；数据默认只存本机，仅当你主动配置坚果云账号后才会上传加密备份。
  - 若你在 **Android 系统设置**里开启了「备份到我的 Google 账号」，每日快照会随系统备份进入你自己的 Google 账号（应用不参与其中，也不上传到任何自有服务器）；坚果云凭据与 DataStore 已被排除在系统备份之外。

---

## 🛠️ 技术栈

- **语言 / 工具链**：Kotlin 2.4.10 / Gradle 9.7.1 (Version Catalog)
- **UI 框架**：Jetpack Compose (BOM 2026.08.00)
- **设计系统**：Material 3 / Miuix KMP (`top.yukonga.miuix.kmp`) / MaterialKolor
- **持久化**：AndroidX DataStore Preferences + Kotlinx Serialization
- **网络与安全**：OkHttp 4.12.0（坚果云 WebDAV，可选功能）+ AndroidKeyStore AES-GCM（自实现 `SecureStore`，未引入 security-crypto 依赖）
- **图片加载**：Coil 3

---

## 📦 编译与构建

### 环境要求
- JDK 17+（构建脚本用 `jvmToolchain(21)`，Gradle 会自行拉取 JDK 21 编译；CI 直接用 Temurin 21）
- Android SDK (compileSdk 37, minSdk 26, targetSdk 36)

### 构建 Debug 包
```bash
./gradlew assembleDebug
```

### 运行单元测试
```bash
./gradlew testDebugUnitTest     # 121 例纯 JVM 单测，无需模拟器
```

### 静态门禁（ktlint + detekt，不需要 Android SDK）
```bash
bash tools/ci-gates.sh          # 默认：ktlint 拦截 + detekt 报告
```
规则与理由见 `.editorconfig` / `detekt.yml` 文件头，流程见 [`docs/WORKFLOW.md`](docs/WORKFLOW.md) §3。

### 给 AI 助手 / 贡献者
本仓库有一套完整的工程约定文档，动手前请先读 [`CLAUDE.md`](CLAUDE.md)（总入口）与 `docs/` 下的
[`REQUIREMENTS.md`](docs/REQUIREMENTS.md)（需求边界）·
[`ARCHITECTURE.md`](docs/ARCHITECTURE.md)（分层与实现约定）·
[`DESIGN_SPEC.md`](docs/DESIGN_SPEC.md)（视觉规范）·
[`WORKFLOW.md`](docs/WORKFLOW.md)（开发流程）。
每轮开发都要写 `devlog/YYYY-MM-DD.md` 并更新 `devlog/INDEX.md`。

---

## 📝 开源协议

本项目采用 [Apache-2.0](LICENSE) 协议开源。
