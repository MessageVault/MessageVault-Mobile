# MessageVault Mobile

MessageVault Mobile是一个Android应用，用于读取SMS短信和通话记录，将数据发送到后端服务器，并支持备份和恢复功能。

## 当前开发状态

**重要提示：** 本项目目前处于积极开发阶段，功能尚未完全实现。请注意以下事项：

1. **测试风险警告**：
   - 在测试前请务必使用设备生产商自带的备份还原软件备份您的短信、通话记录和联系人
   - 本软件可能会出现不可预见的错误，可能导致数据丢失
   - 建议仅使用备份功能，恢复功能请在虚拟机环境中测试

2. **功能完成度**：
   - 备份功能：基本可用，支持短信、通话记录和联系人备份
   - 恢复功能：目前仅支持通话记录恢复，短信和联系人恢复功能尚未实现
   - 界面：基本完成，使用Material Design 3实现

## 功能

- 读取设备上的短信记录
- 读取设备上的通话记录
- 将数据安全地发送到MessageVault服务器
- 支持备份和恢复操作
- 详细功能列表请参见[features.md](features.md)文件
- 支持Material You设计（Material Design 3）

## 开发环境设置

### 系统要求

- **JDK**: 版本17 (OpenJDK 17.0.14)
- **Gradle**: 版本8.2
- **Android Studio**: 最新版本推荐
- **Android SDK**: API 24-34

### 配置步骤

1. 安装JDK 17
2. 安装Android Studio
3. 克隆此仓库
4. 在Android Studio中打开项目
5. Gradle会自动下载必要的依赖
6. 构建并运行应用

### Gradle配置说明

项目使用Gradle 8.2和Android Gradle插件8.1.0。配置文件位于：
- `gradle/wrapper/gradle-wrapper.properties`: Gradle版本配置
- `build.gradle`: 项目级构建文件，定义通用配置
- `app/build.gradle`: 应用模块构建文件

Gradle命令示例：
```bash
# 清理构建
./gradlew clean

# 构建调试版本
./gradlew assembleDebug

# 运行单元测试
./gradlew test

# 运行设备测试
./gradlew connectedAndroidTest
```

## 权限说明

此应用需要以下权限：

- `READ_SMS`: 读取短信记录
- `READ_CALL_LOG`: 读取通话记录
- `READ_CONTACTS`: 读取联系人
- `SEND_SMS`: 发送短信（用于恢复功能）
- `WRITE_CALL_LOG`: 写入通话记录（用于恢复功能）
- `WRITE_CONTACTS`: 写入联系人（用于恢复功能）
- `INTERNET`: 网络通信
- `ACCESS_NETWORK_STATE`: 检查网络状态
- `WRITE_EXTERNAL_STORAGE`: 保存备份文件（仅API 28及以下）

请确保在运行时授予这些权限。

## 配置

应用使用`Config`类管理配置参数，避免硬编码值。主要配置包括：

```kotlin
// 获取配置实例
val config = Config.getInstance(context)

// 设置服务器URL
config.setServerUrl("https://your-server-url.com/api")

// 获取完整API URL
val apiUrl = config.getFullApiUrl()

// 设置语言
config.setLanguage("zh-rCN")
```

详细配置选项请参见`app/src/main/java/imken/messagevault/mobile/config/Config.kt`文件。

## 构建与安装

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

- `app/src/main/java/imken/messagevault/mobile/`: 源代码
  - `MainActivity.kt`: 主活动，使用Jetpack Compose和Material Design 3
  - `MessageVaultApp.kt`: 应用类
  - `config/Config.kt`: 配置类
  - `data/`: 数据处理相关类
    - `BackupManager.kt`: 备份管理器
    - `RestoreManager.kt`: 恢复管理器
  - `model/`: 数据模型
    - `Message.kt`: 短信模型
    - `CallLog.kt`: 通话记录模型
    - `Contact.kt`: 联系人模型
    - `BackupData.kt`: 备份数据容器
  - `models/`: UI数据模型
    - `MessageData.kt`: 短信UI数据模型
    - `CallLogData.kt`: 通话记录UI数据模型
    - `BackupFileInfo.kt`: 备份文件信息UI数据模型
  - `data/entity/`: 数据库实体
    - `MessageEntity.kt`: 短信数据库实体
    - `CallLogsEntity.kt`: 通话记录数据库实体
    - `ContactsEntity.kt`: 联系人数据库实体
  - `ui/theme/`: Material You主题相关类
    - `Theme.kt`: 实现Material Design 3主题，支持动态颜色
  - `ui/screens/`: 各个功能屏幕的Compose组件
  - `ui/viewmodels/`: MVVM架构中的ViewModel类
  - `data/api/`: API接口
- `app/src/main/res/`: 资源文件
  - `values/strings.xml`: 默认字符串资源
  - `values-zh-rCN/strings.xml`: 中文字符串资源
  - `values-en/strings.xml`: 英文字符串资源
- `app/src/main/AndroidManifest.xml`: 应用配置
- `app/src/test/`: 单元测试目录
- `app/src/androidTest/`: 设备测试目录

## 数据模型架构

### 数据模型层次结构

本应用使用了三层数据模型结构，以支持不同场景下的数据处理需求：

1. **业务模型** (`model/`目录)：
   - 用于应用内部业务逻辑处理
   - 包括`Message`, `CallLog`, `Contact`, `BackupData`等类
   - 直接映射到备份文件JSON结构

2. **UI模型** (`models/`目录)：
   - 专为UI层展示准备的数据模型
   - 包括`MessageData`, `CallLogData`, `BackupFileInfo`等类
   - 可能包含额外UI展示需要的字段

3. **数据库实体** (`data/entity/`目录)：
   - 用于本地数据库存储的实体类
   - 包括`MessageEntity`, `CallLogsEntity`, `ContactsEntity`等
   - 带有Room数据库相关注解

### 模型转换

- `BackupManager.kt`中包含从Android系统内容提供者到业务模型的转换逻辑
- `RestoreManager.kt`中包含业务模型与UI模型和数据库实体之间的相互转换
- 为保持一致性，所有转换逻辑都使用扩展函数实现(如`Message.toMessageEntity()`)

### 数据流向

1. **备份流程**:
   系统数据 → 业务模型 → JSON → 文件存储/服务器

2. **恢复流程**:
   文件/服务器 → JSON → 业务模型 → 系统内容提供者

3. **展示流程**:
   业务模型 → UI模型 → Compose UI组件

## 技术栈

- **编程语言**: Kotlin、Java 17
- **界面框架**: Jetpack Compose、Material Design 3 (Material You)
  - 支持Android 12+的动态颜色
  - 深色模式支持
  - 自适应布局
- **网络库**: Retrofit 2.9.0、OkHttp 4.11.0
- **序列化**: Gson 2.10.1
- **日志库**: Timber 5.0.1
- **数据管理**: Room 2.6.1
- **测试工具**: JUnit 4.13.2、Mockito 4.11.0、Espresso 3.5.1

## 测试设置

### 单元测试

项目使用JUnit和Mockito进行单元测试。测试文件位于`app/src/test/`目录。

运行单元测试：

```bash
./gradlew test
```

### 设备测试

设备测试使用AndroidX Test框架和Espresso。测试文件位于`app/src/androidTest/`目录。

运行设备测试：

```bash
./gradlew connectedAndroidTest
```

## 变更日志与AI编辑日志

- [变更日志](CHANGELOG.md)：记录版本更新和功能变化
- [AI编辑日志](AI_EDIT_LOG.md)：记录AI辅助的编辑和生成内容

## Material Design 3 (Material You) 实现

本项目遵循Google的Material Design 3设计规范：

- 实现动态颜色系统（Android 12+）
- 提供精心设计的深色/浅色主题
- 使用最新的Material 3组件
- 自适应布局支持不同屏幕尺寸
- 优先考虑用户体验和可访问性

## 可扩展性

项目设计考虑了未来扩展需求：

- 支持更多数据类型（如MMS、联系人）
- 支持不同的存储后端
- 支持更高级的数据分析功能
- 支持多语言和本地化

## 贡献

欢迎提交Issue和Pull Request。请遵循项目的[编码规范](../NOTICE.md)。

## 许可证

MIT 