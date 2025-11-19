package io.izzel.arclight.common.mod.util;

import io.izzel.arclight.common.bridge.bukkit.PluginClassLoaderBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 插件重载工具类
 * 提供了重载单个插件的功能，用于开发调试
 */
public class PluginReloader {

    private static final Logger LOGGER = Bukkit.getLogger();

    /**
     * 重载指定的插件
     * @param plugin 要重载的插件
     * @return 重载后的新插件实例，失败返回null
     */
    public static Plugin reloadPlugin(Plugin plugin) {
        if (plugin == null) {
            return null;
        }

        String pluginName = plugin.getName();
        File pluginFile = getPluginFile(plugin);

        if (pluginFile == null || !pluginFile.exists()) {
            LOGGER.warning("Cannot find plugin file for: " + pluginName);
            return null;
        }

        try {
            // 先卸载插件
            doUnloadPlugin(plugin);

            // 然后重新加载插件
            return loadPlugin(pluginFile);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to reload plugin: " + pluginName, e);
            return null;
        }
    }

    /**
     * 卸载插件
     * @param plugin 要卸载的插件
     * @return 是否成功卸载
     */
    public static boolean unloadPlugin(Plugin plugin) {
        if (plugin == null) {
            return false;
        }

        try {
            doUnloadPlugin(plugin);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to unload plugin: " + plugin.getName(), e);
            return false;
        }
    }

    /**
     * 内部卸载插件方法
     */
    private static void doUnloadPlugin(Plugin plugin) throws Exception {
        String pluginName = plugin.getName();
        SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

        // 取消所有任务调度器任务（包括异步任务）
        Bukkit.getScheduler().cancelTasks(plugin);
        LOGGER.info("已取消插件 " + pluginName + " 的所有任务调度器任务");
        
        // 等待异步任务完成
        try {
            // 通过反射获取异步任务执行器
            java.lang.reflect.Field asyncSchedulerField = Bukkit.getScheduler().getClass().getDeclaredField("asyncScheduler");
            asyncSchedulerField.setAccessible(true);
            Object asyncScheduler = asyncSchedulerField.get(Bukkit.getScheduler());
            
            if (asyncScheduler != null) {
                // 获取任务映射
                java.lang.reflect.Field runnersField = asyncScheduler.getClass().getDeclaredField("runners");
                runnersField.setAccessible(true);
                Object runners = runnersField.get(asyncScheduler);
                
                if (runners instanceof java.util.Map) {
                    java.util.Map<?, ?> runnerMap = (java.util.Map<?, ?>) runners;
                    int taskCount = 0;
                    
                    // 统计该插件的任务
                    for (Object runner : runnerMap.values()) {
                        java.lang.reflect.Field ownerField = runner.getClass().getDeclaredField("owner");
                        ownerField.setAccessible(true);
                        Plugin taskOwner = (Plugin) ownerField.get(runner);
                        if (taskOwner == plugin) {
                            taskCount++;
                        }
                    }
                    
                    if (taskCount > 0) {
                        LOGGER.info("等待 " + taskCount + " 个异步任务完成...");
                        Thread.sleep(500); // 等待500ms让异步任务完成
                    }
                }
            }
        } catch (Exception e) {
            // 如果反射失败，至少等待一段时间
            LOGGER.info("等待异步任务完成...");
            Thread.sleep(200);
        }

        // 禁用插件（这也会调用 plugin.onDisable()）
        pluginManager.disablePlugin(plugin);

        // 使用反射访问私有字段
        Field pluginsField = SimplePluginManager.class.getDeclaredField("plugins");
        Field lookupNamesField = SimplePluginManager.class.getDeclaredField("lookupNames");
        Field commandMapField = SimplePluginManager.class.getDeclaredField("commandMap");

        pluginsField.setAccessible(true);
        lookupNamesField.setAccessible(true);
        commandMapField.setAccessible(true);

        List<Plugin> plugins = (List<Plugin>) pluginsField.get(pluginManager);
        Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(pluginManager);
        CommandMap commandMap = (CommandMap) commandMapField.get(pluginManager);

        // 从插件列表中移除
        plugins.remove(plugin);
        lookupNames.remove(pluginName.toLowerCase());

        // 注销命令
        try {
            // 打印 commandMap 的实际类型
            LOGGER.info("CommandMap 类型: " + commandMap.getClass().getName());
            LOGGER.info("CommandMap 父类: " + commandMap.getClass().getSuperclass().getName());
            
            // 尝试不同的字段名
            Field knownCommandsField = null;
            Map<String, Command> knownCommands = null;

            // 首先尝试从实际类获取
            Class<?> cmdMapClass = commandMap.getClass();
            while (cmdMapClass != null && knownCommands == null) {
                try {
                    knownCommandsField = cmdMapClass.getDeclaredField("knownCommands");
                    knownCommandsField.setAccessible(true);
                    knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
                    LOGGER.info("在 " + cmdMapClass.getName() + " 中找到 knownCommands 字段");
                    break;
                } catch (NoSuchFieldException e) {
                    // 继续尝试父类
                }
                cmdMapClass = cmdMapClass.getSuperclass();
            }
            
            if (knownCommands == null) {
                LOGGER.warning("通过继承链未找到 knownCommands，尝试遍历所有字段");
                // 遍历 commandMap 实际类的所有字段
                cmdMapClass = commandMap.getClass();
                while (cmdMapClass != null && knownCommands == null) {
                    LOGGER.info("检查类 " + cmdMapClass.getName() + " 的字段:");
                    for (Field field : cmdMapClass.getDeclaredFields()) {
                        if (Map.class.isAssignableFrom(field.getType())) {
                            LOGGER.info("  发现 Map 字段: " + field.getName());
                            field.setAccessible(true);
                            try {
                                Object value = field.get(commandMap);
                                if (value instanceof Map) {
                                    Map<?, ?> map = (Map<?, ?>) value;
                                    if (!map.isEmpty()) {
                                        Map.Entry<?, ?> entry = map.entrySet().iterator().next();
                                        LOGGER.info("    内容类型: " + entry.getKey().getClass().getSimpleName() + 
                                                   " -> " + entry.getValue().getClass().getSimpleName());
                                        if (entry.getKey() instanceof String && entry.getValue() instanceof Command) {
                                            knownCommands = (Map<String, Command>) value;
                                            LOGGER.info("  使用字段 " + field.getName() + " 作为命令映射!");
                                            break;
                                        }
                                    } else {
                                        LOGGER.info("    字段为空");
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.warning("    无法访问字段 " + field.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                    if (knownCommands == null) {
                        cmdMapClass = cmdMapClass.getSuperclass();
                    }
                }
            }

            if (knownCommands != null) {
                // 收集所有需要删除的命令键（包括别名）
                List<String> keysToRemove = new ArrayList<>();
                for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                    if (entry.getValue() instanceof PluginCommand) {
                        PluginCommand command = (PluginCommand) entry.getValue();
                        if (command.getPlugin() == plugin) {
                            keysToRemove.add(entry.getKey());
                            
                            // 记录正在注销的命令
                            LOGGER.info("正在注销命令: " + entry.getKey() + " (插件: " + pluginName + 
                                       ", 插件对象: " + System.identityHashCode(plugin) + 
                                       ", 命令对象: " + System.identityHashCode(command) + ")");
                        }
                    }
                }
                
                // 批量删除所有相关命令
                for (String key : keysToRemove) {
                    Command cmd = knownCommands.remove(key);
                    if (cmd != null) {
                        cmd.unregister(commandMap);
                    }
                }
                
                if (!keysToRemove.isEmpty()) {
                    LOGGER.info("已注销插件 " + pluginName + " 的 " + keysToRemove.size() + " 个命令");
                }
                
                // 再次验证命令确实被移除
                for (String key : keysToRemove) {
                    if (knownCommands.containsKey(key)) {
                        LOGGER.warning("警告：命令 " + key + " 未被完全移除！");
                    }
                }
            } else {
                LOGGER.warning("Could not find command map field, commands may not be unregistered properly");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to unregister commands for plugin: " + pluginName, e);
        }

        // 清理事件监听器
        try {
            // 通过反射获取 HandlerList
            Class<?> handlerListClass = Class.forName("org.bukkit.event.HandlerList");
            java.lang.reflect.Method unregisterAllMethod = handlerListClass.getDeclaredMethod("unregisterAll", Plugin.class);
            unregisterAllMethod.invoke(null, plugin);
            LOGGER.info("已注销插件 " + pluginName + " 的所有事件监听器");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "无法注销事件监听器: " + pluginName, e);
        }

        // 清理服务注册
        Bukkit.getServicesManager().unregisterAll(plugin);
        LOGGER.info("已注销插件 " + pluginName + " 的所有服务");

        // 先清理桥接类加载器的特殊状态（在关闭前）
        if (plugin.getClass().getClassLoader() instanceof PluginClassLoaderBridge) {
            try {
                PluginClassLoaderBridge bridge = (PluginClassLoaderBridge) plugin.getClass().getClassLoader();
                // 设置为非强制重载模式
                bridge.arclight$setForceReload(false);
                LOGGER.info("已重置插件 " + pluginName + " 的桥接类加载器状态");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "无法重置桥接类加载器: " + pluginName, e);
            }
        }

        // 等待一小段时间，确保所有异步任务完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        // 最后关闭类加载器
        if (plugin.getClass().getClassLoader() instanceof URLClassLoader) {
            try {
                URLClassLoader classLoader = (URLClassLoader) plugin.getClass().getClassLoader();
                classLoader.close();
                LOGGER.info("已关闭插件 " + pluginName + " 的类加载器");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to close class loader", e);
            }
        }

        LOGGER.info("Unloaded plugin: " + pluginName);
    }

    /**
     * 加载插件（使用强制重载模式）
     * @param file 插件文件
     * @return 加载后的插件实例，失败返回null
     */
    public static Plugin loadPlugin(File file) {
        if (file == null || !file.exists()) {
            LOGGER.warning("Plugin file does not exist: " + (file != null ? file.getAbsolutePath() : "null"));
            return null;
        }

        try {
            return doLoadPlugin(file);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load plugin from: " + file.getName(), e);
            return null;
        }
    }

    /**
     * 内部加载插件方法
     */
    private static Plugin doLoadPlugin(File file) throws Exception {
        SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

        // 先标记下一个加载的插件需要使用重载模式
        setNextPluginReloadMode(true);

        try {
            // 加载插件
            Plugin plugin = pluginManager.loadPlugin(file);

            if (plugin != null) {
                // 调用 onLoad
                plugin.onLoad();

                // 确保插件被正确添加到插件管理器
                ensurePluginRegistered(plugin);

                // 先注册命令，但不设置执行器（让插件在 onEnable 中设置）
                preparePluginCommands(plugin);

                // 启用插件（插件的 onEnable 会通过 getCommand() 获取命令并设置执行器）
                pluginManager.enablePlugin(plugin);

                // 验证命令是否正确设置
                verifyPluginCommands(plugin);

                LOGGER.info("Loaded and enabled plugin: " + plugin.getName());
            }

            return plugin;
        } finally {
            // 恢复正常模式
            setNextPluginReloadMode(false);
        }
    }

    /**
     * 设置下一个加载的插件使用重载模式
     * 这需要在 JavaPluginLoaderMixin 中配合实现
     */
    private static void setNextPluginReloadMode(boolean enable) {
        // 这个标志会在 JavaPluginLoaderMixin 中读取
        System.setProperty("arclight.plugin.forceReload", String.valueOf(enable));
    }

    /**
     * 获取插件的 JAR 文件
     * @param plugin 插件实例
     * @return 插件的JAR文件，如果获取失败返回null
     */
    public static File getPluginFile(Plugin plugin) {
        try {
            if (plugin instanceof JavaPlugin) {
                // 使用反射获取 file 字段
                Field fileField = JavaPlugin.class.getDeclaredField("file");
                fileField.setAccessible(true);
                File originalFile = (File) fileField.get(plugin);

                // 创建一个新的 File 实例来强制刷新
                if (originalFile != null) {
                    return new File(originalFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get plugin file", e);
        }
        return null;
    }

    /**
     * 通过插件名称重载插件
     */
    public static Plugin reloadPlugin(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            LOGGER.warning("Plugin not found: " + pluginName);
            return null;
        }
        return reloadPlugin(plugin);
    }

    /**
     * 确保插件被正确注册到插件管理器
     * @param plugin 插件实例
     */
    private static void ensurePluginRegistered(Plugin plugin) {
        if (plugin == null) {
            return;
        }

        try {
            SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

            // 获取插件列表和查找映射
            Field pluginsField = SimplePluginManager.class.getDeclaredField("plugins");
            Field lookupNamesField = SimplePluginManager.class.getDeclaredField("lookupNames");

            pluginsField.setAccessible(true);
            lookupNamesField.setAccessible(true);

            List<Plugin> plugins = (List<Plugin>) pluginsField.get(pluginManager);
            Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(pluginManager);

            String pluginName = plugin.getName();

            // 确保插件在列表中
            if (!plugins.contains(plugin)) {
                plugins.add(plugin);
                LOGGER.info("已将插件 " + pluginName + " 添加到插件列表");
            }

            // 确保插件在查找映射中
            lookupNames.put(pluginName.toLowerCase(), plugin);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "无法确保插件正确注册: " + plugin.getName(), e);
        }
    }

    /**
     * 准备插件命令（注册但不设置执行器）
     * @param plugin 插件实例
     */
    private static void preparePluginCommands(Plugin plugin) {
        if (plugin == null) {
            return;
        }

        try {
            SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

            // 获取命令映射字段
            Field commandMapField = SimplePluginManager.class.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(pluginManager);

            // 获取 knownCommands 字段
            Map<String, Command> knownCommands = getKnownCommands(commandMap);

            // 获取插件描述文件中的命令
            PluginDescriptionFile description = plugin.getDescription();
            Map<String, Map<String, Object>> commands = description.getCommands();

            if (commands != null && !commands.isEmpty()) {
                for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
                    String commandName = entry.getKey();

                    if (commandName.contains(":")) {
                        LOGGER.warning("Command " + commandName + " contains ':' - skipping");
                        continue;
                    }

                    // 清理可能存在的旧命令
                    if (knownCommands != null) {
                        String fullName = commandName.toLowerCase();
                        Command oldCmd = knownCommands.remove(fullName);
                        if (oldCmd != null) {
                            oldCmd.unregister(commandMap);
                        }
                    }

                    // 创建新的 PluginCommand 实例
                    PluginCommand command = createPluginCommand(commandName, plugin);

                    if (command != null) {
                        // 设置命令属性但不设置执行器
                        Map<String, Object> commandData = entry.getValue();
                        if (commandData != null) {
                            setCommandProperties(command, commandData);
                        }

                        // 注册命令
                        commandMap.register(plugin.getName().toLowerCase(), command);
                        
                        // 确保命令在 knownCommands 中正确映射
                        if (knownCommands != null) {
                            knownCommands.put(commandName.toLowerCase(), command);
                            knownCommands.put(plugin.getName().toLowerCase() + ":" + commandName.toLowerCase(), command);
                            
                            // 添加别名
                            for (String alias : command.getAliases()) {
                                knownCommands.put(alias.toLowerCase(), command);
                                knownCommands.put(plugin.getName().toLowerCase() + ":" + alias.toLowerCase(), command);
                            }
                        }
                        
                        LOGGER.info("准备命令: " + commandName + " (插件: " + plugin.getName() + ")");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "无法准备插件命令: " + plugin.getName(), e);
        }
    }

    /**
     * 验证插件命令是否正确设置
     * @param plugin 插件实例
     */
    private static void verifyPluginCommands(Plugin plugin) {
        if (plugin == null) {
            return;
        }

        try {
            PluginDescriptionFile description = plugin.getDescription();
            Map<String, Map<String, Object>> commands = description.getCommands();

            if (commands != null && !commands.isEmpty()) {
                for (String commandName : commands.keySet()) {
                    // 通过插件的 getCommand 方法获取命令
                    try {
                        java.lang.reflect.Method getCommand = JavaPlugin.class.getDeclaredMethod("getCommand", String.class);
                        getCommand.setAccessible(true);
                        PluginCommand cmd = (PluginCommand) getCommand.invoke(plugin, commandName);
                        
                        if (cmd != null) {
                            if (cmd.getExecutor() == null || cmd.getExecutor() == plugin) {
                                LOGGER.warning("命令 " + commandName + " 的执行器未正确设置");
                            } else {
                                LOGGER.info("命令 " + commandName + " 的执行器已设置: " + 
                                          cmd.getExecutor().getClass().getSimpleName());
                            }
                        } else {
                            LOGGER.warning("无法通过 getCommand 获取命令: " + commandName);
                        }
                    } catch (Exception e) {
                        LOGGER.warning("验证命令时出错: " + commandName + " - " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "验证插件命令时出错: " + plugin.getName(), e);
        }
    }

    /**
     * 获取 knownCommands 字段
     */
    private static Map<String, Command> getKnownCommands(SimpleCommandMap commandMap) {
        try {
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            return (Map<String, Command>) knownCommandsField.get(commandMap);
        } catch (Exception e) {
            LOGGER.warning("无法获取 knownCommands: " + e.getMessage());
            return null;
        }
    }

    /**
     * 设置命令属性
     */
    private static void setCommandProperties(PluginCommand command, Map<String, Object> commandData) {
        Object descriptionObj = commandData.get("description");
        Object usageObj = commandData.get("usage");
        Object aliasesObj = commandData.get("aliases");
        Object permissionObj = commandData.get("permission");
        Object permissionMessageObj = commandData.get("permission-message");

        if (descriptionObj != null) {
            command.setDescription(descriptionObj.toString());
        }
        if (usageObj != null) {
            command.setUsage(usageObj.toString());
        }
        if (aliasesObj != null) {
            List<String> aliases = new ArrayList<>();
            if (aliasesObj instanceof List) {
                for (Object alias : (List<?>) aliasesObj) {
                    aliases.add(alias.toString());
                }
            } else {
                aliases.add(aliasesObj.toString());
            }
            command.setAliases(aliases);
        }
        if (permissionObj != null) {
            command.setPermission(permissionObj.toString());
        }
        if (permissionMessageObj != null) {
            command.setPermissionMessage(permissionMessageObj.toString());
        }
    }

    /**
     * 同步插件命令到服务器命令系统
     * @param plugin 插件实例
     */
    private static void syncPluginCommands(Plugin plugin) {
        if (plugin == null) {
            return;
        }

        try {
            SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

            // 获取命令映射字段
            Field commandMapField = SimplePluginManager.class.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(pluginManager);

            // 获取 knownCommands 字段用于清理旧命令
            Field knownCommandsField = null;
            Map<String, Command> knownCommands = null;
            try {
                knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
                LOGGER.info("syncPluginCommands: 成功获取 knownCommands 字段");
            } catch (NoSuchFieldException e) {
                LOGGER.warning("syncPluginCommands: 未找到 knownCommands 字段，尝试遍历");
                // 尝试查找其他可能的字段名
                for (Field field : SimpleCommandMap.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(commandMap);
                        if (value instanceof Map && !((Map<?, ?>) value).isEmpty()) {
                            Map.Entry<?, ?> entry = ((Map<?, ?>) value).entrySet().iterator().next();
                            if (entry.getKey() instanceof String && entry.getValue() instanceof Command) {
                                knownCommands = (Map<String, Command>) value;
                                LOGGER.info("syncPluginCommands: 使用字段 " + field.getName());
                                break;
                            }
                        }
                    }
                }
            }

            // 获取插件描述文件中的命令
            PluginDescriptionFile description = plugin.getDescription();
            Map<String, Map<String, Object>> commands = description.getCommands();

            if (commands != null && !commands.isEmpty()) {
                for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
                    String commandName = entry.getKey();

                    if (commandName.contains(":")) {
                        LOGGER.warning("Command " + commandName + " contains ':' - skipping");
                        continue;
                    }

                    // 先清理可能存在的同名旧命令
                    if (knownCommands != null) {
                        // 清理主命令名
                        String fullName = commandName.toLowerCase();
                        String prefixedName = plugin.getName().toLowerCase() + ":" + fullName;
                        
                        Command oldCommand = knownCommands.remove(fullName);
                        if (oldCommand != null) {
                            oldCommand.unregister(commandMap);
                            LOGGER.info("清理旧命令: " + fullName);
                        }
                        
                        oldCommand = knownCommands.remove(prefixedName);
                        if (oldCommand != null) {
                            oldCommand.unregister(commandMap);
                            LOGGER.info("清理旧命令: " + prefixedName);
                        }
                        
                        // 清理别名
                        Map<String, Object> commandData = entry.getValue();
                        if (commandData != null) {
                            Object aliasesObj = commandData.get("aliases");
                            if (aliasesObj != null) {
                                List<String> aliases = new ArrayList<>();
                                if (aliasesObj instanceof List) {
                                    for (Object alias : (List<?>) aliasesObj) {
                                        aliases.add(alias.toString().toLowerCase());
                                    }
                                } else {
                                    aliases.add(aliasesObj.toString().toLowerCase());
                                }
                                
                                for (String alias : aliases) {
                                    oldCommand = knownCommands.remove(alias);
                                    if (oldCommand != null) {
                                        oldCommand.unregister(commandMap);
                                        LOGGER.info("清理旧命令别名: " + alias);
                                    }
                                    
                                    String prefixedAlias = plugin.getName().toLowerCase() + ":" + alias;
                                    oldCommand = knownCommands.remove(prefixedAlias);
                                    if (oldCommand != null) {
                                        oldCommand.unregister(commandMap);
                                        LOGGER.info("清理旧命令别名: " + prefixedAlias);
                                    }
                                }
                            }
                        }
                    }

                    // 创建新的 PluginCommand 实例
                    PluginCommand command = createPluginCommand(commandName, plugin);

                    if (command != null) {
                        LOGGER.info("创建命令 " + commandName + " 插件引用: " + command.getPlugin().getName() + 
                                   " (已启用: " + command.getPlugin().isEnabled() + 
                                   ", 插件对象: " + System.identityHashCode(command.getPlugin()) + 
                                   ", 新插件对象: " + System.identityHashCode(plugin) + ")");
                        // 设置命令属性
                        Map<String, Object> commandData = entry.getValue();
                        if (commandData != null) {
                            Object descriptionObj = commandData.get("description");
                            Object usageObj = commandData.get("usage");
                            Object aliasesObj = commandData.get("aliases");
                            Object permissionObj = commandData.get("permission");
                            Object permissionMessageObj = commandData.get("permission-message");

                            if (descriptionObj != null) {
                                command.setDescription(descriptionObj.toString());
                            }
                            if (usageObj != null) {
                                command.setUsage(usageObj.toString());
                            }
                            if (aliasesObj != null) {
                                List<String> aliases = new ArrayList<>();
                                if (aliasesObj instanceof List) {
                                    for (Object alias : (List<?>) aliasesObj) {
                                        aliases.add(alias.toString());
                                    }
                                } else {
                                    aliases.add(aliasesObj.toString());
                                }
                                command.setAliases(aliases);
                            }
                            if (permissionObj != null) {
                                command.setPermission(permissionObj.toString());
                            }
                            if (permissionMessageObj != null) {
                                command.setPermissionMessage(permissionMessageObj.toString());
                            }
                        }

                        // 先强制移除可能存在的旧命令实例（使用 commandMap.register 可能不会覆盖）
                        if (knownCommands != null) {
                            // 先查看当前映射的命令
                            Command oldCmd = knownCommands.get(commandName.toLowerCase());
                            if (oldCmd != null) {
                                LOGGER.info("发现旧命令: " + commandName + 
                                           " (对象: " + System.identityHashCode(oldCmd) + ")");
                                if (oldCmd instanceof PluginCommand) {
                                    PluginCommand oldPC = (PluginCommand) oldCmd;
                                    LOGGER.info("  旧插件: " + oldPC.getPlugin().getName() + 
                                               " (启用: " + oldPC.getPlugin().isEnabled() + 
                                               ", 对象: " + System.identityHashCode(oldPC.getPlugin()) + ")");
                                }
                            }
                        }
                        
                        // 注册命令
                        commandMap.register(plugin.getName().toLowerCase(), command);
                        
                        // 设置默认的执行器为插件本身
                        try {
                            command.setExecutor(plugin);
                            LOGGER.info("设置命令 " + commandName + " 的执行器为插件");
                        } catch (Exception e) {
                            LOGGER.warning("无法设置命令执行器: " + commandName + " - " + e.getMessage());
                        }
                        
                        // 确保命令在 knownCommands 中正确映射
                        if (knownCommands != null) {
                            // 添加主命令
                            knownCommands.put(commandName.toLowerCase(), command);
                            knownCommands.put(plugin.getName().toLowerCase() + ":" + commandName.toLowerCase(), command);
                            
                            // 添加别名
                            for (String alias : command.getAliases()) {
                                knownCommands.put(alias.toLowerCase(), command);
                                knownCommands.put(plugin.getName().toLowerCase() + ":" + alias.toLowerCase(), command);
                            }
                            
                            // 验证命令确实被添加
                            Command verifyCmd = knownCommands.get(commandName.toLowerCase());
                            if (verifyCmd instanceof PluginCommand) {
                                PluginCommand pc = (PluginCommand) verifyCmd;
                                LOGGER.info("验证命令映射: " + commandName + " -> 插件: " + pc.getPlugin().getName() + 
                                           " (启用: " + pc.getPlugin().isEnabled() + 
                                           ", 插件对象: " + System.identityHashCode(pc.getPlugin()) + 
                                           ", 命令对象: " + System.identityHashCode(pc) + 
                                           ", 应为: " + System.identityHashCode(command) + ")");
                                
                                if (pc != command) {
                                    LOGGER.warning("警告：命令对象不匹配！实际: " + System.identityHashCode(pc) + 
                                                  ", 期望: " + System.identityHashCode(command));
                                }
                            }
                        }
                        
                        LOGGER.info("重新注册命令: " + commandName + " (插件: " + plugin.getName() + ")");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "无法同步插件命令: " + plugin.getName(), e);
        }
    }

    /**
     * 创建 PluginCommand 实例
     * @param name 命令名称
     * @param plugin 插件实例
     * @return PluginCommand 实例，失败返回null
     */
    private static PluginCommand createPluginCommand(String name, Plugin plugin) {
        try {
            // 使用反射创建 PluginCommand（构造函数是 protected）
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance(name, plugin);
            
            // 命令已经在构造函数中正确设置了 owningPlugin
            // 验证插件引用是否正确
            if (command.getPlugin() != plugin) {
                LOGGER.warning("命令 " + name + " 的插件引用不匹配，尝试使用 Unsafe 修复");
                
                // 使用 Unsafe 来修改 final 字段
                try {
                    Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafeField.setAccessible(true);
                    sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafeField.get(null);
                    
                    Field owningPluginField = PluginCommand.class.getDeclaredField("owningPlugin");
                    long offset = unsafe.objectFieldOffset(owningPluginField);
                    unsafe.putObject(command, offset, plugin);
                    
                    LOGGER.info("使用 Unsafe 修复命令 " + name + " 的 owningPlugin");
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "无法使用 Unsafe 修改 owningPlugin: " + ex.getMessage());
                }
            }
            
            return command;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "无法创建命令: " + name, e);
            return null;
        }
    }
}