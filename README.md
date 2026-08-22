# 吃了么 (Chileme)

> 🍃 本地优先的家庭食品与零食库存管理 Android App。记录保质期、智能临期提醒、减少食物浪费。

---

## ✨ 核心特性

- 🍱 **食品库存与保质期管理**
  - 记录食品名称、分类、存放位置、生产日期与保质期。
  - 自动计算到期日与剩余天数，实时展示新鲜度进度条与三色状态（安全 / 临期 / 过期）。
  - 支持拍照 / 相册封面，自动下采样与 EXIF 旋转校正。
- 🎨 **双主题 UI 体验**
  - **Material Design 3** 与 **Miuix (HyperOS)** 风格一键切换。
  - 支持跟随系统 / 深色 / 浅色模式，以及 Android 12+ 动态取色（Material You）。
- 😋 **消耗打卡与智能归档**
  - 详情页“吃掉一份”连击动效与消耗记录打卡。
  - 吃完/过期自动归档，支持按原因筛选与一键撤销恢复。
- 📊 **统计分析与到期日历**
  - 本周/本月消耗与浪费统计、近 7 天消耗趋势柱状图、库存分类环形图与 TOP5 消耗榜。
  - 统计页内置到期日历，按紧急度彩点标注到期食品。
- ☁️ **本地与云端备份**
  - **本地备份**：基于系统 SAF 导出 / 导入标准 JSON 文件。
  - **坚果云 WebDAV**：支持多版本轮转云端备份（保留最近 3 个历史版本），支持选择版本恢复。
  - **凭据安全**：WebDAV 应用密码基于 Android Keystore (AES-GCM) 硬件加密存储。
- 🔒 **本地优先与隐私保护**
  - 无需注册账号、无第三方广告、无后台追踪，数据 100% 存储于设备本地。

---

## 🛠️ 技术栈

- **语言 / 工具链**：Kotlin 2.4.10 / Gradle 9.7.1 (Version Catalog)
- **UI 框架**：Jetpack Compose (BOM 2026.08.00)
- **设计系统**：Material 3 / Miuix KMP (`top.yukonga.miuix.kmp`) / MaterialKolor
- **持久化**：AndroidX DataStore Preferences + Kotlinx Serialization
- **网络与安全**：OkHttp 5 (WebDAV) + AndroidX Security Crypto (Keystore AES-GCM)
- **图片加载**：Coil 3

---

## 📦 编译与构建

### 环境要求
- JDK 17+
- Android SDK (compileSdk 37, minSdk 26)

### 构建 Debug 包
```bash
./gradlew assembleDebug
```

### 运行单元测试
```bash
./gradlew testDebugUnitTest
```

---

## 📝 开源协议

本项目采用 [Apache-2.0](LICENSE) 协议开源。
