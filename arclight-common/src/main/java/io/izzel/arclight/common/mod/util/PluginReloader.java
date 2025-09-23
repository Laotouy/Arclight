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

        // 取消所有任务调度器任务
        Bukkit.getScheduler().cancelTasks(plugin);
        LOGGER.info("已取消插件 " + pluginName + " 的所有任务调度器任务");

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
            // 尝试不同的字段名
            Field knownCommandsField = null;
            Map<String, Command> knownCommands = null;

            // 尝试 "knownCommands"
            try {
                knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            } catch (NoSuchFieldException e1) {
                // 尝试其他可能的字段名
                for (Field field : commandMap.getClass().getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(commandMap);
                        if (value instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) value;
                            if (!map.isEmpty()) {
                                Map.Entry<?, ?> entry = map.entrySet().iterator().next();
                                if (entry.getKey() instanceof String && entry.getValue() instanceof Command) {
                                    knownCommands = (Map<String, Command>) value;
                                    LOGGER.info("Found command map field: " + field.getName());
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (knownCommands != null) {
                Iterator<Map.Entry<String, Command>> it = knownCommands.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Command> entry = it.next();
                    if (entry.getValue() instanceof PluginCommand) {
                        PluginCommand command = (PluginCommand) entry.getValue();
                        if (command.getPlugin() == plugin) {
                            command.unregister(commandMap);
                            it.remove();
                        }
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

        // 关闭类加载器
        if (plugin.getClass().getClassLoader() instanceof URLClassLoader) {
            try {
                URLClassLoader classLoader = (URLClassLoader) plugin.getClass().getClassLoader();
                classLoader.close();
                LOGGER.info("已关闭插件 " + pluginName + " 的类加载器");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to close class loader", e);
            }
        }

        // 清理桥接类加载器的特殊状态
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

                // 启用插件
                pluginManager.enablePlugin(plugin);

                // 确保插件被正确添加到插件管理器
                ensurePluginRegistered(plugin);

                // 重新同步命令到服务器命令系统
                syncPluginCommands(plugin);

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

                    // 创建新的 PluginCommand 实例
                    PluginCommand command = createPluginCommand(commandName, plugin);

                    if (command != null) {
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

                        // 注册命令
                        commandMap.register(plugin.getName().toLowerCase(), command);
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
            return constructor.newInstance(name, plugin);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "无法创建命令: " + name, e);
            return null;
        }
    }
}