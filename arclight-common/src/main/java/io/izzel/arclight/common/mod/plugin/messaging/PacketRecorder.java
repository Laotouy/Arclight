package io.izzel.arclight.common.mod.plugin.messaging;

import io.izzel.arclight.common.mod.ArclightConstants;
import io.izzel.arclight.common.mod.server.ArclightServer;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

import java.util.stream.Collectors;

public class PacketRecorder {
    private final Object2IntArrayMap<ResourceLocation> unknown = new Object2IntArrayMap<>();
    private long lastUpdate = Util.getMillis();

    public PacketRecorder() {
        unknown.defaultReturnValue(0);
    }

    public void recordUnknown(ResourceLocation id) {
        if (id == null) {
            ArclightServer.LOGGER.warn("Received packet with null ResourceLocation id!");
            ArclightServer.LOGGER.warn("Stack trace:", new Exception("Null packet ID trace"));

            // 记录 null 出现的次数，使用特殊的占位符
            ResourceLocation placeholder = ResourceLocation.withDefaultNamespace("null_packet");
            int num = unknown.getInt(placeholder);
            unknown.put(placeholder, num + 1);
            return;
        }
        int num = unknown.getInt(id);
        unknown.put(id, num + 1);
    }

    public void update() {
        long now = Util.getMillis();
        if (Math.abs(now - lastUpdate) > ArclightConstants.PACKET_RECORDER_PERIOD_SEC *1000) {
            consumeAndLog();
            lastUpdate = now;
        }
    }

    public void consumeAndLog() {
        String unknowns = unknown.object2IntEntrySet().stream()
                .filter(it -> it.getKey() != null)  // 过滤掉 null key
                .map(it -> {
                    try {
                        return it.getKey().toString() + '(' + it.getIntValue() + ')';
                    } catch (Exception e) {
                        ArclightServer.LOGGER.warn("Error processing packet entry: {}", e.getMessage());
                        return "error(" + it.getIntValue() + ')';
                    }
                })
                .collect(Collectors.joining(", ", "unknown=[", "];"));

        // 检查并记录 null key
        long nullCount = unknown.object2IntEntrySet().stream()
                .filter(it -> it.getKey() == null)
                .count();
        if (nullCount > 0) {
            ArclightServer.LOGGER.warn("Found {} entries with null ResourceLocation in packet recorder", nullCount);
        }

        unknown.clear();

        ArclightServer.LOGGER.debug("Packet error statistics: {}", unknowns);
    }
}
