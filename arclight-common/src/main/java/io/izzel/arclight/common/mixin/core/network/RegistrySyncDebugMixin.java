package io.izzel.arclight.common.mixin.core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 调试 Mixin - 捕获并记录注册表同步中的 null 条目
 */
@Mixin(FriendlyByteBuf.class)
public class RegistrySyncDebugMixin {

    @Inject(method = "writeResourceLocation", at = @At("HEAD"))
    private void arclight$debugWriteResourceLocation(ResourceLocation resourceLocation, CallbackInfoReturnable<FriendlyByteBuf> cir) {
        if (resourceLocation == null) {
            LoggerFactory.getLogger("Arclight").error("=== NULL ResourceLocation DETECTED ===");
            LoggerFactory.getLogger("Arclight").error("Attempting to write null ResourceLocation to buffer!");

            // 打印堆栈跟踪以找出来源
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 0; i < Math.min(15, stack.length); i++) {
                String element = stack[i].toString();
                if (element.contains("fabric") || element.contains("forge") ||
                    element.contains("Packet") || element.contains("Registry") ||
                    element.contains("Tag") || element.contains("cobble")) {
                    LoggerFactory.getLogger("Arclight").error("  -> {}", element);
                }
            }

            LoggerFactory.getLogger("Arclight").error("Thread: {}", Thread.currentThread().getName());
            LoggerFactory.getLogger("Arclight").error("=====================================");
        }
    }

    @Inject(method = "writeUtf(Ljava/lang/String;)Lnet/minecraft/network/FriendlyByteBuf;", at = @At("HEAD"))
    private void arclight$debugWriteString(String string, CallbackInfoReturnable<FriendlyByteBuf> cir) {
        if (string == null) {
            LoggerFactory.getLogger("Arclight").error("Attempting to write null String to buffer!");
            LoggerFactory.getLogger("Arclight").error("Stack trace:", new Exception());
        }
    }
}