# 变更日志

本文件记录MessageVault-Mobile组件的所有重要变更。

## [未发布]

### 新增
- 配置Gradle 8.2和JDK 17
- 添加详细的features.md功能清单
- 更新README.md，添加开发环境设置说明
- 添加项目级build.gradle文件
- 添加Gradle Wrapper配置和脚本
- 强化Material You (Material Design 3)实现
  - 添加完整的Material 3颜色系统实现
  - 优化深色/浅色主题支持
  - 添加Material 3 window-size-class支持
- 完善短信和通话记录备份恢复功能
  - 通过SharedPreferences实现组件间状态同步
  - 使用RoleManager优化Android 10+默认短信应用检测
  - 提供详细的恢复进度跟踪和UI更新
  - 添加备份文件预检查和内容分析

### 修改
- 更新app/build.gradle中的Compose编译器配置
- 更新依赖版本管理到项目级build.gradle
- 优化Material Design 3组件的使用
- 更新XML主题文件使用Material 3属性
- 升级Material库到最新版本(1.10.0)
- 重构BackupManager和RestoreManager
  - 使用正确的数据模型类型
  - 改进备份和恢复流程
  - 增强错误处理

### 修复
- 添加AGP 8.0+兼容性所需的namespace
- 修复Material 3相关主题问题
- 修复颜色系统和各组件之间的一致性
- 修复数据模型类型不匹配问题
  - 修复Message和MessageData之间的转换
  - 修复CallLog和CallLogData之间的转换
  - 修复Contact和ContactsEntity之间的转换
  - 修正BackupFile构造函数参数
- 修复重复的方法定义
- 修复备份和恢复功能中的错误处理
- 修复权限问题
  - 将WRITE_SMS权限改为SEND_SMS
  - 添加WRITE_CALL_LOG和WRITE_CONTACTS权限
  - 更新权限检查逻辑
- 修复JDK兼容性问题
  - 设置使用标准OpenJDK 17而非GraalVM
  - 更新gradle.properties中的JDK路径设置
- 修复高版本Android上的默认短信应用检测问题
  - 处理getDefaultSmsPackage返回null的情况
  - 添加RoleManager作为主要检测方法(Android 10+)
  - 使用SharedPreferences同步状态
  - 添加详细日志以便调试问题

## [0.1.4] - 2025-04-17

### 新增
- 添加SMS和通话记录功能的单元测试
- 添加SMS和通话记录功能的设备测试
- 添加Mockito和测试依赖库
- 实现测试日志格式化，符合NOTICE.md规范

### 修改
- 更新build.gradle，添加测试依赖和配置
- 优化测试目录结构

### 修复
- 无

## [0.1.3] - 2025-04-17

### 新增
- 添加多语言支持，实现中文（默认和zh-rCN）和英文（en）语言
- 创建可扩展的语言切换接口，支持社区贡献更多语言
- 更新Config类，添加语言管理方法
- 添加语言初始化和切换时的日志记录
- 更新NOTICE.md，添加多语言支持实现指南

### 修改
- 重构UI，使用字符串资源替代硬编码文本
- 更新MainActivity，支持基于用户首选项的语言切换
- 将文本和语言逻辑从UI代码中分离

### 修复
- 硬编码文本导致的国际化问题

## [0.1.2] - 2025-04-17

### 新增
- 配置API级别（minSdkVersion 24, targetSdkVersion 34, compileSdkVersion 34）
- 导入Jetpack Compose框架替代传统View系统
- 实现Material Design 3 (MD3)主题，支持动态颜色（API 31+）和兼容性回退
- 添加Timber日志系统，支持文件日志和格式化输出
- 完全重构MainActivity，使用Compose组件

### 修改
- 将包名从com.messagevault.mobile更改为imken.messagevault.mobile
- 将Java代码迁移到Kotlin
- 更新所有依赖库到最新版本
- 更新.gitignore以排除logs/目录

### 修复
- 兼容性问题：支持Android 6.0（API 24）及以上版本

## [0.1.0] - 2025-04-16

### 新增
- 初始项目结构设置
- 基本的AndroidManifest.xml文件，包含必要权限（READ_SMS、READ_CALL_LOG、INTERNET）
- MainActivity基本实现，支持权限请求和基本界面
- 基本布局文件（activity_main.xml）
- 字符串资源文件（strings.xml）
- Gradle构建配置 