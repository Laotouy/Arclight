package io.izzel.arclight.common.mod.server;

import io.izzel.arclight.common.bridge.bukkit.PluginClassLoaderBridge;
import io.izzel.arclight.common.mod.util.PluginReloader;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v.CraftServer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arclight 内置插件重载命令
 */
public class ArclightPluginCommand extends Command {

    public ArclightPluginCommand() {
        super("arclightplugin");
        this.description = "Arclight 插件管理命令";
        this.usageMessage = "/arclightplugin <reload|unload|load> <插件> - 管理插件（强制重载模式）";
        this.setAliases(List.of("aplugin", "ap"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!sender.hasPermission("arclight.plugin.reload")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /" + commandLabel + " <reload|unload|load> <插件>");
            sender.sendMessage(ChatColor.GRAY + "  reload - 重载插件");
            sender.sendMessage(ChatColor.GRAY + "  unload - 卸载插件");
            sender.sendMessage(ChatColor.GRAY + "  load - 从文件加载插件");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if ("reload".equals(subCommand)) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "请指定插件名称！");
                sender.sendMessage(ChatColor.YELLOW + "用法: /" + commandLabel + " reload <插件>");
                return true;
            }

            String pluginName = args[1];
            Plugin currentPlugin = findPluginByName(pluginName);

            if (currentPlugin == null) {
                sender.sendMessage(ChatColor.RED + "找不到插件: " + pluginName);
                sender.sendMessage(ChatColor.YELLOW + "当前已加载插件数: " +
                    Bukkit.getPluginManager().getPlugins().length);
                // 尝试提供相似的插件名建议
                String suggestions = findSimilarPlugins(pluginName);
                if (!suggestions.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "你是否要找: " + suggestions + "?");
                }
                return true;
            }

            // 检查依赖
            Set<String> dependents = findDependents(currentPlugin.getName());
            if (!dependents.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "无法重载插件: " + currentPlugin.getName());
                sender.sendMessage(ChatColor.RED + "以下插件依赖于它: " + String.join(", ", dependents));
                sender.sendMessage(ChatColor.YELLOW + "请先卸载依赖的插件。");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "正在重载插件: " + currentPlugin.getName());

            // 检查当前类加载器状态
            if (currentPlugin.getClass().getClassLoader() instanceof PluginClassLoaderBridge) {
                PluginClassLoaderBridge bridge = (PluginClassLoaderBridge) currentPlugin.getClass().getClassLoader();
                sender.sendMessage(ChatColor.GRAY + "当前插件类加载器: " +
                    currentPlugin.getClass().getClassLoader().getClass().getSimpleName());
            }

            try {
                // 执行重载
                long startTime = System.currentTimeMillis();
                Plugin reloadedPlugin = PluginReloader.reloadPlugin(currentPlugin);
                long elapsed = System.currentTimeMillis() - startTime;

                if (reloadedPlugin != null) {
                    sender.sendMessage(ChatColor.GREEN + "成功重载插件: " + reloadedPlugin.getName());
                    sender.sendMessage(ChatColor.GREEN + "版本: " + reloadedPlugin.getDescription().getVersion());
                    sender.sendMessage(ChatColor.GRAY + "重载耗时: " + elapsed + "ms");

                    // 检查新的类加载器状态
                    if (reloadedPlugin.getClass().getClassLoader() instanceof PluginClassLoaderBridge) {
                        sender.sendMessage(ChatColor.GRAY + "新的类加载器创建成功");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "重载插件失败: " + pluginName);
                    sender.sendMessage(ChatColor.RED + "请查看控制台获取详细信息");
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "重载插件出错: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        } else if ("unload".equals(subCommand)) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "请指定插件名称！");
                sender.sendMessage(ChatColor.YELLOW + "用法: /" + commandLabel + " unload <插件>");
                return true;
            }

            String pluginName = args[1];
            Plugin currentPlugin = findPluginByName(pluginName);

            if (currentPlugin == null) {
                sender.sendMessage(ChatColor.RED + "找不到插件: " + pluginName);
                String suggestions = findSimilarPlugins(pluginName);
                if (!suggestions.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "你是否要找: " + suggestions + "?");
                }
                return true;
            }

            // 检查依赖
            Set<String> dependents = findDependents(currentPlugin.getName());
            if (!dependents.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "无法卸载插件: " + currentPlugin.getName());
                sender.sendMessage(ChatColor.RED + "以下插件依赖于它: " + String.join(", ", dependents));
                sender.sendMessage(ChatColor.YELLOW + "请先卸载依赖的插件。");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "正在卸载插件: " + currentPlugin.getName());

            try {
                boolean success = PluginReloader.unloadPlugin(currentPlugin);
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "成功卸载插件: " + currentPlugin.getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "卸载插件失败: " + currentPlugin.getName());
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "卸载插件出错: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        } else if ("load".equals(subCommand)) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "请指定插件文件名！");
                sender.sendMessage(ChatColor.YELLOW + "用法: /" + commandLabel + " load <插件.jar>");
                sender.sendMessage(ChatColor.GRAY + "示例: /" + commandLabel + " load PokeSpawn.jar");
                return true;
            }

            String fileName = args[1];
            if (!fileName.endsWith(".jar")) {
                fileName += ".jar";
            }

            File pluginsFolder = new File("plugins");
            File pluginFile = new File(pluginsFolder, fileName);

            if (!pluginFile.exists()) {
                sender.sendMessage(ChatColor.RED + "找不到插件文件: " + fileName);
                sender.sendMessage(ChatColor.YELLOW + "查找路径: " + pluginFile.getAbsolutePath());
                return true;
            }

            // 检查是否已经加载
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                File pFile = PluginReloader.getPluginFile(p);
                if (pFile != null && pFile.getName().equals(fileName)) {
                    sender.sendMessage(ChatColor.RED + "插件已加载: " + p.getName());
                    sender.sendMessage(ChatColor.YELLOW + "使用 'reload' 命令来重载它。");
                    return true;
                }
            }

            sender.sendMessage(ChatColor.YELLOW + "正在加载插件: " + fileName);

            try {
                Plugin loadedPlugin = PluginReloader.loadPlugin(pluginFile);
                if (loadedPlugin != null) {
                    sender.sendMessage(ChatColor.GREEN + "成功加载插件: " + loadedPlugin.getName());
                    sender.sendMessage(ChatColor.GREEN + "版本: " + loadedPlugin.getDescription().getVersion());
                } else {
                    sender.sendMessage(ChatColor.RED + "加载插件失败: " + fileName);
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "加载插件出错: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "未知的子命令: " + subCommand);
        sender.sendMessage(ChatColor.YELLOW + "用法: /" + commandLabel + " <reload|unload|load> <插件>");
        return true;
    }

    /**
     * 查找插件，支持大小写不敏感匹配
     */
    private Plugin findPluginByName(String name) {
        // 先尝试精确匹配
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin != null) {
            return plugin;
        }

        // 尝试大小写不敏感匹配
        String lowerName = name.toLowerCase();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().toLowerCase().equals(lowerName)) {
                return p;
            }
        }

        // 尝试部分匹配（插件名包含输入的名字）
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().toLowerCase().contains(lowerName)) {
                return p;
            }
        }

        return null;
    }

    /**
     * 查找依赖于指定插件的其他插件
     */
    private Set<String> findDependents(String pluginName) {
        Set<String> dependents = new HashSet<>();

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.isEnabled()) {
                PluginDescriptionFile desc = plugin.getDescription();

                // 检查硬依赖
                if (desc.getDepend() != null && desc.getDepend().contains(pluginName)) {
                    dependents.add(plugin.getName());
                }

                // 检查软依赖
                if (desc.getSoftDepend() != null && desc.getSoftDepend().contains(pluginName)) {
                    dependents.add(plugin.getName());
                }
            }
        }

        return dependents;
    }

    /**
     * 查找相似的插件名，用于提供建议
     */
    private String findSimilarPlugins(String name) {
        String lowerName = name.toLowerCase();
        List<String> similar = new ArrayList<>();

        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            String pluginName = p.getName();
            String lowerPluginName = pluginName.toLowerCase();

            // 如果插件名包含输入，或输入包含插件名
            if (lowerPluginName.contains(lowerName) || lowerName.contains(lowerPluginName)) {
                similar.add(pluginName);
            }
            // 如果前几个字符匹配
            else if (lowerName.length() >= 3 && lowerPluginName.length() >= 3) {
                if (lowerPluginName.substring(0, Math.min(3, lowerPluginName.length()))
                    .equals(lowerName.substring(0, Math.min(3, lowerName.length())))) {
                    similar.add(pluginName);
                }
            }
        }

        if (similar.size() > 3) {
            similar = similar.subList(0, 3);  // 最多显示3个建议
        }

        return String.join(", ", similar);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "unload", "load").stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if ("reload".equals(subCommand) || "unload".equals(subCommand)) {
                // 对于 reload 和 unload，补全已加载的插件名
                String partial = args[1].toLowerCase();
                return Bukkit.getPluginManager().getPlugins().length > 0
                    ? java.util.Arrays.stream(Bukkit.getPluginManager().getPlugins())
                        .map(Plugin::getName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .sorted()
                        .collect(Collectors.toList())
                    : new ArrayList<>();
            } else if ("load".equals(subCommand)) {
                // 对于 load，补全 plugins 文件夹中的 jar 文件
                String partial = args[1].toLowerCase();
                File pluginsFolder = new File("plugins");
                if (pluginsFolder.exists() && pluginsFolder.isDirectory()) {
                    File[] files = pluginsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
                    if (files != null) {
                        List<String> availableJars = new ArrayList<>();
                        for (File file : files) {
                            String fileName = file.getName();
                            // 排除已经加载的插件
                            boolean alreadyLoaded = false;
                            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                                File pFile = PluginReloader.getPluginFile(p);
                                if (pFile != null && pFile.getName().equals(fileName)) {
                                    alreadyLoaded = true;
                                    break;
                                }
                            }
                            if (!alreadyLoaded && fileName.toLowerCase().startsWith(partial)) {
                                availableJars.add(fileName);
                            }
                        }
                        return availableJars.stream().sorted().collect(Collectors.toList());
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    public static void registerCommand() {
        try {
            var server = (CraftServer) Bukkit.getServer();
            var commandMap = server.getCommandMap();
            var command = new ArclightPluginCommand();
            commandMap.register("arclight", command);
            ArclightServer.LOGGER.info("已注册 Arclight 插件管理命令: /arclightplugin (别名: /aplugin, /ap)");
        } catch (Exception e) {
            ArclightServer.LOGGER.error("注册 Arclight 插件命令失败", e);
        }
    }
}