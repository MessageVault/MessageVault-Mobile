# 贡献指南

## 代码提交规范

为了保持代码库的整洁和高效，请遵循以下提交规范：

### 应该提交的文件

- 源代码文件 (*.java, *.kt, *.xml, 等)
- 构建配置文件 (build.gradle, gradle.properties, 等)
- 文档文件 (*.md, LICENSE, 等)
- 必要的资源文件 (res/ 目录中的文件)
- 项目配置文件 (.gitignore, .gitattributes, 等)

### 不应提交的文件

- 编译生成的文件 (build/, bin/, out/ 目录)
- IDE配置文件 (.idea/, *.iml)
- 本地配置文件 (local.properties)
- 调试和性能分析文件 (*.hprof, *.log)
- 大型二进制文件 (测试视频、音频等)
- 敏感信息 (密钥、证书、API密钥等)

### 提交前检查清单

1. 运行 `./clean.sh` 清理项目
2. 使用 `git status` 检查即将提交的文件
3. 使用 `git add -p` 有选择地暂存更改
4. 使用有意义的提交消息
