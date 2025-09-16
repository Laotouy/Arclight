package io.izzel.arclight.common.mod.util;

import io.izzel.arclight.common.bridge.bukkit.PluginClassLoaderBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
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

        // 禁用插件
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

        // 关闭类加载器
        if (plugin.getClass().getClassLoader() instanceof URLClassLoader) {
            try {
                URLClassLoader classLoader = (URLClassLoader) plugin.getClass().getClassLoader();
                classLoader.close();
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

                // 启用插件
                pluginManager.enablePlugin(plugin);

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
        if (enable) {
            LOGGER.info("Enabled force reload mode for next plugin load");
        } else {
            LOGGER.info("Disabled force reload mode");
        }
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
}