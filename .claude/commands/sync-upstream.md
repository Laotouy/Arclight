# 上游同步命令

执行完整的 Arclight 上游同步分析与合并流程。

## 任务描述

请执行以下完整的上游同步工作流程：

### 1. 环境准备
- 检查并配置 upstream 远程仓库 (https://github.com/IzzelAliz/Arclight.git)
- 获取最新的远程数据
- 确认当前分支状态

### 2. 差异分析
- 统计本地 (Laotouy/Arclight) 与上游 (IzzelAliz/Arclight) 的提交差异
- 详细分析上游的每一次未合并提交，说明其修改内容
- 分析本地的独有修改，标记类型（bug修复/功能增强/其他）

### 3. 冲突预检
- 创建临时分支模拟合并
- 列出所有潜在冲突文件
- 分析每个冲突的具体原因
- 检查本地的以下重要补丁在上游的状态：
  - 玩家个人时间同步机制 (MinecraftServerMixin, ServerPlayerMixin)
  - 插件管理命令 (ArclightPluginCommand, PluginReloader)
  - 其他 Mixin 修复

### 4. 执行合并
- 正式执行 `git merge upstream/FeudalKings`
- 处理合并冲突（优先使用上游代码）
- 记录被覆盖的本地修改
- 对于上游未修复的本地补丁，重新应用

### 5. 验证
- 运行 `./gradlew clean build collect` 验证编译
- 使用 debugger 检查合并结果
- 验证 Mixin 注入点和 Bridge 接口

### 6. 生成总结报告
包含以下内容：
- 同步概览（提交数量、文件数量）
- 冲突处理记录
- 本地补丁状态（已被上游修复/需重新应用/可能不兼容）
- 构建验证结果
- 建议的后续操作

## 注意事项
- 所有输出使用中文
- 提交信息不包含 Claude 相关标识
- 不向上游提交 PR
- 保留本地所有独有的功能修复

请开始执行上游同步流程。
