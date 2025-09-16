package io.izzel.arclight.common.mixin.core.network;

import com.mojang.authlib.properties.Property;
import io.izzel.arclight.common.bridge.core.network.NetworkManagerBridge;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Mixin(Connection.class)
public class ConnectionMixin implements NetworkManagerBridge {

    @Shadow @Final private static org.slf4j.Logger LOGGER;
    @Shadow public boolean disconnectionHandled;
    public java.util.UUID spoofedUUID;
    public com.mojang.authlib.properties.Property[] spoofedProfile;
    public String hostname;

    @Override
    public UUID bridge$getSpoofedUUID() {
        return spoofedUUID;
    }

    @Override
    public void bridge$setSpoofedUUID(UUID spoofedUUID) {
        this.spoofedUUID = spoofedUUID;
    }

    @Override
    public Property[] bridge$getSpoofedProfile() {
        return spoofedProfile;
    }

    @Override
    public void bridge$setSpoofedProfile(Property[] spoofedProfile) {
        this.spoofedProfile = spoofedProfile;
    }

    @Override
    public String bridge$getHostname() {
        return hostname;
    }

    @Override
    public void bridge$setHostname(String hostname) {
        this.hostname = hostname;
    }

    @Inject(method = "handleDisconnection", at = @At("HEAD"), cancellable = true)
    private void arclight$noDisconnectTwiceWarn(CallbackInfo ci) {
        if (disconnectionHandled) {
            ci.cancel();
        }
    }

    /**
     * @author Arclight
     * @reason 修复网络同步导致的 Watchdog 超时
     */
    @Overwrite
    private static void syncAfterConfigurationChange(ChannelFuture channelFuture) {
        try {
            // 使用5秒超时而不是无限等待
            if (!channelFuture.await(5001, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("[Arclight] 网络 operation timeout after 5 seconds during configuration change");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[Arclight] Interrupted during network configuration change");
        }
    }
}
