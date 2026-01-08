package io.izzel.arclight.common.mod;

import io.izzel.arclight.common.mod.mixins.*;
import io.izzel.arclight.mixin.MixinTools;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ArclightMixinPlugin implements IMixinConfigPlugin {

    private final List<MixinProcessor> preProcessors = List.of(
    );

    private final List<MixinProcessor> postProcessors = List.of(
        new RenameIntoProcessor(),
        new TransformAccessProcessor(),
        new CreateConstructorProcessor(),
        new InlineMethodProcessor(),
        new InlineFieldProcessor(),
        new InvokeSpecialProcessor()
    );

    @Override
    public void onLoad(String mixinPackage) {
        // 确保 ArclightCommon.api() 已初始化，用于 @LoadIfMod 检测
        // 当多个 mixin 配置使用不同的 plugin 时，加载顺序不保证
        // 需要在这里也尝试初始化，以确保模组检测能正常工作
        if (ArclightCommon.api() == null) {
            tryInitializeApi();
        }
    }

    /**
     * 尝试检测平台并初始化 API
     * 这是一个备用机制，用于处理 mixin 配置加载顺序问题
     */
    protected void tryInitializeApi() {
        try {
            // 尝试检测 Fabric
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            var implClass = Class.forName("io.izzel.arclight.fabric.mod.FabricCommonImpl");
            ArclightCommon.setInstance((ArclightCommon.Api) implClass.getDeclaredConstructor().newInstance());
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            // 尝试检测 NeoForge
            Class.forName("net.neoforged.fml.ModList");
            var implClass = Class.forName("io.izzel.arclight.neoforge.mod.NeoForgeCommonImpl");
            ArclightCommon.setInstance((ArclightCommon.Api) implClass.getDeclaredConstructor().newInstance());
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            // 尝试检测 Forge
            Class.forName("net.minecraftforge.fml.ModList");
            var implClass = Class.forName("io.izzel.arclight.forge.mod.ForgeCommonImpl");
            ArclightCommon.setInstance((ArclightCommon.Api) implClass.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ShouldApplyProcessor.shouldApply(mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        for (var processor : this.preProcessors) {
            processor.accept(targetClassName, targetClass, mixinInfo);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        for (var processor : this.postProcessors) {
            processor.accept(targetClassName, targetClass, mixinInfo);
        }
        MixinTools.onPostMixin(targetClass);
    }
}
