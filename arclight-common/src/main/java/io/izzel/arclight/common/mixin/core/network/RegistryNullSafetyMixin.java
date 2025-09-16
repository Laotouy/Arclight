package io.izzel.arclight.common.mixin.core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 修复注册表同步中的 null 条目问题
 */
@Mixin(value = FriendlyByteBuf.class, priority = 999)
public class RegistryNullSafetyMixin {


    /**
     * @author IzzelAliz
     * @reason 安全处理 ResourceLocation 写入，防止 null 导致崩溃
     */
    @Overwrite
    public FriendlyByteBuf writeResourceLocation(ResourceLocation resourceLocation) {
        if (resourceLocation == null) {
            LoggerFactory.getLogger("Arclight").error("=== NULL ResourceLocation FIX APPLIED ===");
            LoggerFactory.getLogger("Arclight").error("Prevented crash from null ResourceLocation");

            // 打印简短的堆栈信息找出问题来源
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 3; i < Math.min(10, stack.length); i++) {
                String element = stack[i].toString();
                if (element.contains("fabric") || element.contains("forge") ||
                    element.contains("Packet") || element.contains("mod")) {
                    LoggerFactory.getLogger("Arclight").error("  Problem source: {}", element);
                    break;
                }
            }

            // 使用占位符代替 null，防止崩溃
            resourceLocation = ResourceLocation.withDefaultNamespace("arclight_null_fix");
            LoggerFactory.getLogger("Arclight").error("  Using placeholder: minecraft:arclight_null_fix");
            LoggerFactory.getLogger("Arclight").error("==========================================");
        }

        // 正常写入
        ((FriendlyByteBuf)(Object)this).writeUtf(resourceLocation.toString());
        return (FriendlyByteBuf)(Object)this;
    }
}