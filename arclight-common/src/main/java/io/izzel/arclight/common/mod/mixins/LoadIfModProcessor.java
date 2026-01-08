package io.izzel.arclight.common.mod.mixins;

import io.izzel.arclight.common.mod.ArclightCommon;
import io.izzel.arclight.common.mod.mixins.annotation.LoadIfMod;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Objects;

public class LoadIfModProcessor {

    private static final String TYPE = Type.getDescriptor(LoadIfMod.class);

    static boolean shouldApply(ClassNode node) {
        if (node.invisibleAnnotations == null) {
            return true;
        }
        for (var ann : node.invisibleAnnotations) {
            if (ann.desc.equals(TYPE)) {
                var loadIfModData = parse(ann);
                var api = ArclightCommon.api();
                // 如果 API 未初始化，无法进行模组检测
                // 这种情况在正常流程中不应该发生（ArclightMixinPlugin.onLoad 会初始化）
                // 但如果发生了，安全地禁用此 mixin 以避免潜在的模组冲突
                if (api == null) {
                    System.err.println("[Arclight] Warning: ArclightCommon.api() is null when checking @LoadIfMod for " + node.name);
                    System.err.println("[Arclight] Cannot detect mod presence, disabling mixin to prevent potential conflicts.");
                    return false;
                }
                return switch (loadIfModData.condition()) {
                    case ABSENT -> {
                        for (var modid : loadIfModData.modids()) {
                            if (api.isModLoaded(modid)) {
                                yield false;
                            }
                        }
                        yield true;
                    }
                    case PRESENT -> {
                        for (var modid : loadIfModData.modids()) {
                            if (api.isModLoaded(modid)) {
                                yield true;
                            }
                        }
                        yield false;
                    }
                };
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static LoadIfModData parse(AnnotationNode ann) {
        LoadIfMod.ModCondition condition = null;
        List<String> modids = null;
        for (int i = 0; i < ann.values.size(); i += 2) {
            var name = ((String) ann.values.get(i));
            var value = ann.values.get(i + 1);
            switch (name) {
                case "condition" -> {
                    var condName = ((String[]) value)[1];
                    condition = LoadIfMod.ModCondition.valueOf(condName);
                }
                case "modid" -> modids = ((List<String>) value);
            }
        }
        return new LoadIfModData(Objects.requireNonNull(condition, "condition"),
            Objects.requireNonNull(modids, "modid"));
    }

    private record LoadIfModData(LoadIfMod.ModCondition condition, List<String> modids) {
    }
}
