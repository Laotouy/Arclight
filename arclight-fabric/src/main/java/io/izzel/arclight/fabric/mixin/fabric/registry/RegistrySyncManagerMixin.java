package io.izzel.arclight.fabric.mixin.fabric.registry;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

/**
 * Mixin to filter out Bukkit plugin-registered entities from Fabric registry sync.
 * This prevents Fabric clients from disconnecting due to unknown registry entries
 * like mypet_* entities registered by the MyPet plugin.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager", remap = false)
public class RegistrySyncManagerMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Arclight-RegistrySync");
    
    // Prefixes to filter from registry sync (Bukkit plugins that register custom entities)
    private static final Set<String> FILTERED_PREFIXES = Set.of(
        "mypet_"  // MyPet plugin
        // Add more prefixes here as needed
    );

    /**
     * Filter out Bukkit plugin-registered entities from the registry sync map.
     * This runs after createAndPopulateRegistryMap() returns and modifies the result.
     */
    @Inject(method = "createAndPopulateRegistryMap", at = @At("RETURN"), cancellable = true)
    private static void arclight$filterBukkitPluginEntries(CallbackInfoReturnable<Map<ResourceLocation, Object2IntMap<ResourceLocation>>> cir) {
        Map<ResourceLocation, Object2IntMap<ResourceLocation>> map = cir.getReturnValue();
        
        if (map == null) {
            return;
        }
        
        // Find the entity_type registry
        ResourceLocation entityTypeRegistry = ResourceLocation.tryParse("minecraft:entity_type");
        if (entityTypeRegistry == null) {
            return;
        }
        
        Object2IntMap<ResourceLocation> entityTypeMap = map.get(entityTypeRegistry);
        if (entityTypeMap == null) {
            return;
        }
        
        // Create a filtered copy
        Object2IntMap<ResourceLocation> filteredMap = new Object2IntLinkedOpenHashMap<>();
        int filteredCount = 0;
        
        for (Object2IntMap.Entry<ResourceLocation> entry : entityTypeMap.object2IntEntrySet()) {
            ResourceLocation id = entry.getKey();
            String path = id.getPath();
            
            boolean shouldFilter = false;
            for (String prefix : FILTERED_PREFIXES) {
                if (path.startsWith(prefix)) {
                    shouldFilter = true;
                    break;
                }
            }
            
            if (shouldFilter) {
                filteredCount++;
                LOGGER.debug("Filtered registry entry from sync: {}", id);
            } else {
                filteredMap.put(id, entry.getIntValue());
            }
        }
        
        if (filteredCount > 0) {
            LOGGER.info("Filtered {} Bukkit plugin registry entries from Fabric sync", filteredCount);
            map.put(entityTypeRegistry, filteredMap);
        }
    }
}
