# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在处理此代码仓库时提供指导。

## 重要指令

### 语言要求
**必须使用中文回答所有问题和交流。所有输出、注释、提示信息都必须是中文。**

### Git 提交规范
1. **禁止在提交信息中包含 Claude 相关信息**：
   - 不要在提交信息中添加 "Generated with Claude Code" 等标识
   - 不要添加 "Co-Authored-By: Claude" 等作者信息
   - 保持提交信息简洁，只描述实际的代码变更

2. **禁止向上游提交**：
   - 永远不要创建向原始被 fork 项目的 Pull Request
   - 本项目是独立分支，不向上游合并
   - 所有改动仅在本地仓库中进行

## 项目概述

Arclight 是一个运行在常见模组加载器（Forge、NeoForge、Fabric）上的 Bukkit 服务器实现。它在 Bukkit/Spigot API 和模组化 Minecraft 服务器之间充当桥梁，使 Bukkit 插件能够与模组一起工作。

## 架构说明

项目使用多模块 Gradle 结构，包含特定平台的实现：

- **arclight-common**：核心实现，包含基于 Mixin 的补丁和 Bukkit 与 Minecraft 内部之间的桥接
- **arclight-forge**：Forge 平台特定实现
- **arclight-neoforge**：NeoForge 平台特定实现
- **arclight-fabric**：Fabric 平台特定实现
- **bootstrap**：各平台的安装器和启动器组件
- **i18n-config**：国际化和配置处理
- **buildSrc**：自定义 Gradle 插件和构建逻辑

代码库广泛使用：
- Mixin 框架进行字节码操作
- 桥接模式进行 Bukkit 和 Minecraft 之间的 API 转换
- 访问转换器（bukkit.at）访问 Minecraft 内部类

## 构建命令

```bash
# 清理构建产物
./gradlew clean

# 构建所有平台 JAR
./gradlew build

# 构建并收集所有平台 JAR 到 build/libs
./gradlew collect

# 运行测试
./gradlew test

# 上传构建产物（需要凭据）
./gradlew uploadFiles
```

## 开发指南

在处理此代码库时：

1. **Mixin 开发**：所有对 Minecraft 类的补丁都应通过相应模块的 mixin 包中的 Mixins 完成
2. **桥接模式**：使用 `io.izzel.arclight.common.bridge` 中的桥接接口访问原版类的自定义方法
3. **平台兼容性**：对 arclight-common 的更改必须与所有三个平台（Forge、NeoForge、Fabric）兼容
4. **版本管理**：Minecraft 和加载器版本在 `gradle/libs.versions.toml` 中管理

## 关键技术细节

- **Minecraft 版本**：1.21.1
- **Java 版本**：21
- **支持的加载器**：Forge 52.1.3、NeoForge 21.1.206、Fabric 0.17.2
- **主分支**：FeudalKings
- **版本命名**：使用代号（当前："feudal-kings"）

## 最近添加的功能

### 插件管理命令
项目新增了内置的插件管理命令 `/arclightplugin`（别名：`/aplugin`, `/ap`），支持：

- `reload <插件>` - 强制重载插件（从 JAR 文件重新读取）
- `unload <插件>` - 卸载插件
- `load <插件.jar>` - 从 plugins 文件夹加载插件

功能特点：
- 依赖检查：防止卸载被其他插件依赖的插件
- 大小写不敏感的插件名匹配
- 智能插件名建议
- 强制重载模式：确保读取最新的 JAR 内容而非缓存

相关文件：
- `arclight-common/src/main/java/io/izzel/arclight/common/mod/server/ArclightPluginCommand.java`
- `arclight-common/src/main/java/io/izzel/arclight/common/mod/util/PluginReloader.java`
- `arclight-common/src/main/java/io/izzel/arclight/common/mixin/bukkit/plugin/PluginClassLoaderMixin.java`