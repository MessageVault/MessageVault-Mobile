# MessageVault Mobile

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="100" alt="MessageVault Logo">
</p>

<div align="center">
  <strong>安全备份和恢复Android短信和通话记录</strong>
</div>
<div align="center">
  使用Material You设计的现代化备份解决方案
</div>

<div align="center">
  <sub>使用现代Android开发技术构建 • 由JetBrains和Claude 3.7 Sonnet提供支持</sub>
</div>

<br />

<div align="center">
  <!-- 构建状态 -->
  <a href="#构建状态">
    <img src="https://img.shields.io/badge/构建-passing-brightgreen.svg"
      alt="构建状态" />
  </a>
  <!-- API版本 -->
  <a href="#API版本">
    <img src="https://img.shields.io/badge/API-24%2B-blue.svg"
      alt="API 24+" />
  </a>
  <!-- 许可证 -->
  <a href="https://github.com/imken/messagevault/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/许可证-MIT-blue.svg"
      alt="MIT许可证" />
  </a>
</div>

## 📱 功能概览

MessageVault Mobile是一个Android应用，用于读取SMS短信和通话记录，将数据安全备份并支持恢复功能。

### ✨ 主要功能亮点

- 📤 **备份功能**：安全备份短信、通话记录和联系人
- 📥 **恢复功能**：将备份数据恢复至设备
- 🎨 **Material You**：现代化Material Design 3界面，支持动态颜色
- 🌐 **多语言支持**：内置中文和英文界面
- 🔐 **隐私优先**：本地优先处理，无需云存储
- 🔄 **组件间状态同步**：确保恢复过程无缝进行

## 🚧 当前开发状态

**重要提示：** 本项目处于积极开发阶段，功能尚未完全实现。

### 功能完成度

- ✅ **备份功能**：基本可用，支持短信、通话记录和联系人备份
- ✅ **短信恢复**：已实现，支持进度跟踪和状态同步  
- ✅ **通话记录恢复**：已实现，包含详细进度报告
- ⏳ **联系人恢复**：开发中，基本框架已完成
- ✅ **默认SMS应用检测**：优化完成，支持Android 7.0-14

### 测试注意事项

- 在测试前请使用设备自带的备份软件保存您的数据
- 建议在虚拟机环境中测试恢复功能
- 恢复短信需要临时将应用设为默认短信应用

## 🛠 开发环境设置

### 系统要求

- **JDK**: 版本17 (OpenJDK 17.0.14)
- **Gradle**: 版本8.2
- **Android Studio**: 最新版本推荐（Iguana 或更高版本）
- **Android SDK**: API 24-34 (Android 7.0 - 14)

### 快速开始

```bash
# 克隆仓库
git clone https://github.com/imken/messagevault-mobile.git
cd messagevault-mobile

# 构建调试版本
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 📚 项目架构

MessageVault采用现代Android架构设计，包括：

- **MVVM架构**：使用ViewModel分离UI和业务逻辑
- **Jetpack Compose**：声明式UI构建
- **Material Design 3**：支持动态颜色和主题
- **组件间状态同步**：使用SharedPreferences实现跨组件状态管理

### 数据流设计

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  内容提供者  │───▶│  业务模型   │───▶│   UI模型    │
└─────────────┘    └─────────────┘    └─────────────┘
       │                  │                  │
       │                  │                  │
       ▼                  ▼                  ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  系统数据   │◀───│   JSON文件  │◀───│ Compose UI  │
└─────────────┘    └─────────────┘    └─────────────┘
```

### 权限管理

应用需要以下权限以实现完整功能：

- `READ_SMS`：读取短信记录
- `SEND_SMS`：恢复短信（替代WRITE_SMS）
- `READ_CALL_LOG` & `WRITE_CALL_LOG`：读取和恢复通话记录
- `READ_CONTACTS` & `WRITE_CONTACTS`：读取和恢复联系人
- `INTERNET`：网络通信（可选）

## 🔍 特色技术亮点

### 默认短信应用检测优化

为解决高版本Android上的短信恢复问题，我们实现了多层次检测机制：

```kotlin
// 优先使用RoleManager (Android 10+)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
    if (roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true) {
        // 是默认短信应用
    }
}

// 传统方法作为备选
val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
val isSmsApp = packageName == defaultSmsPackage
```

### 组件间状态同步

通过SharedPreferences实现组件间的状态同步：

```kotlin
// 保存状态
getSharedPreferences("sms_app_status", Context.MODE_PRIVATE).edit()
    .putBoolean("is_default_sms_app", true)
    .apply()

// 读取状态
val prefs = context.getSharedPreferences("sms_app_status", Context.MODE_PRIVATE)
val isDefault = prefs.getBoolean("is_default_sms_app", false)
```

## 📋 变更日志

查看[变更日志](CHANGELOG.md)获取详细更新历史。

## 🤝 贡献

欢迎提交Issue和Pull Request。请遵循项目的编码规范。

## 📄 许可证

MIT 