package io.izzel.arclight.common.mod.util;

import io.izzel.arclight.common.mod.server.ArclightServer;
import io.izzel.arclight.i18n.ArclightConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class DistValidate {

    private static final Marker MARKER = MarkerManager.getMarker("EXT_LOGIC");

    public static boolean isValid(UseOnContext context) {
        return context != null && isValid(context.getLevel());
    }

    public static boolean isValid(LevelAccessor level) {
        return level != null
            && !level.isClientSide()
            && isLogicWorld(level);
    }

    public static boolean isValid(BlockGetter getter) {
        return getter instanceof LevelAccessor level && isValid(level);
    }

    private static boolean isLogicWorld(LevelAccessor level) {
        var cl = level.getClass();
        return cl == ServerLevel.class || cl == WorldGenRegion.class
            || isLogicWorld(cl);
    }

    // 使用 ClassValue 替代 ConcurrentHashMap，ClassValue 会在 Class 被 GC 时自动清理值
    // 这解决了 PluginClassLoader 无法回收的问题
    private static final ClassValue<Boolean> LOGIC_WORLD_CACHE = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            var name = type.getName();
            var result = ArclightConfig.spec().getCompat().getExtraLogicWorlds().contains(name);
            ArclightServer.LOGGER.warn(MARKER, "Level class {} treated as logic world: {}", name, result);
            return result;
        }
    };

    private static boolean isLogicWorld(Class<?> cl) {
        return LOGIC_WORLD_CACHE.get(cl);
    }
}
