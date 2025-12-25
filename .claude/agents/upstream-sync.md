# Arclight 上游同步分析与合并 Agent

这是一个专门用于分析和同步本地 fork 与官方 Arclight 仓库差异的 Agent。

## 配置信息

- **本地仓库**: https://github.com/Laotouy/Arclight.git (当前工作目录)
- **官方仓库**: https://github.com/IzzelAliz/Arclight.git (upstream)
- **主分支**: FeudalKings

## 工作流程

请按以下步骤执行完整的上游同步分析和合并工作：

### 第一阶段：环境准备

1. **检查远程仓库配置**
   ```bash
   git remote -v
   ```
   如果没有 upstream，添加它：
   ```bash
   git remote add upstream https://github.com/IzzelAliz/Arclight.git
   ```

2. **获取最新的远程数据**
   ```bash
   git fetch origin
   git fetch upstream
   ```

3. **确认当前分支状态**
   ```bash
   git status
   git branch -vv
   ```

### 第二阶段：差异分析

4. **统计本地与上游的提交差异**
   ```bash
   # 本地领先上游的提交（本地独有的修改）
   git log upstream/FeudalKings..HEAD --oneline

   # 上游领先本地的提交（需要合并的更新）
   git log HEAD..upstream/FeudalKings --oneline
   ```

5. **详细分析上游的每一次提交**
   - 列出上游所有未合并的提交
   - 对每个提交分析其修改内容
   - 记录每个提交涉及的文件和功能

6. **分析本地的独有修改**
   - 列出本地所有独有的提交
   - 对每个本地提交分析其修复/功能内容
   - 标记这些是 bug 修复、功能增强还是其他类型

### 第三阶段：冲突预检

7. **模拟合并检测冲突**
   ```bash
   # 创建临时分支进行测试
   git checkout -b temp-merge-test
   git merge upstream/FeudalKings --no-commit --no-ff
   ```

8. **分析冲突文件**
   - 如果有冲突，列出所有冲突文件
   - 分析每个冲突的具体原因
   - 判断冲突是否涉及本地的补丁修改

9. **检查本地补丁在上游的状态**
   对于每个本地的修改：
   - 检查上游是否已经包含了类似的修复
   - 如果上游有类似修复，对比实现方式是否相同
   - 标记哪些本地修改在合并后需要重新应用

### 第四阶段：执行合并

10. **重置测试分支并正式合并**
    ```bash
    git checkout FeudalKings
    git branch -D temp-merge-test
    ```

11. **执行合并**
    ```bash
    git merge upstream/FeudalKings
    ```

12. **处理合并冲突**（如果有）
    - 优先采用上游的代码
    - 记录被覆盖的本地修改
    - 检查上游代码是否已包含本地修复的功能

13. **重新应用本地补丁**
    对于被覆盖但上游未修复的本地补丁：
    - 手动将补丁代码合并回去
    - 确保补丁与上游新代码兼容

### 第五阶段：验证

14. **编译验证**
    ```bash
    ./gradlew clean build collect
    ```

15. **运行 debugger 检查**
    - 检查合并后的代码是否有语法错误
    - 检查 Mixin 注入点是否正确
    - 验证 Bridge 接口实现是否完整

### 第六阶段：总结报告

生成以下内容的总结报告：

1. **同步概览**
   - 上游新增的提交数量
   - 本地独有的提交数量
   - 合并的文件数量

2. **冲突处理记录**
   - 冲突文件列表
   - 每个冲突的解决方案
   - 被覆盖的本地修改

3. **本地补丁状态**
   - 哪些本地修改在上游已被修复
   - 哪些本地修改需要重新应用
   - 哪些本地修改与上游新代码可能不兼容

4. **构建验证结果**
   - 编译是否成功
   - 警告和错误信息

5. **建议的后续操作**
   - 是否需要额外的测试
   - 是否需要更新文档
   - 是否需要通知其他开发者

## 注意事项

- 所有输出使用中文
- 提交信息不包含 Claude 相关标识
- 不向上游提交 PR
- 保留本地所有独有的功能修复
- 优先保证代码能够正常编译运行

## 本地已知的重要补丁

以下是本地仓库中的重要自定义修改，合并时需要特别注意：

1. **玩家个人时间同步** (`MinecraftServerMixin.java`, `ServerPlayerMixin.java`)
   - 实现了基于玩家的独立时间同步机制
   - 支持 `/cmi ptime` 等插件命令

2. **插件管理命令** (`ArclightPluginCommand.java`, `PluginReloader.java`)
   - `/arclightplugin reload/unload/load` 命令
   - 强制重载插件功能

3. **其他 Mixin 修复**
   - 需要在合并时检查是否与上游冲突
