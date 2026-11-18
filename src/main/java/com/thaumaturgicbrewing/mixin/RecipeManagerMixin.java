package com.thaumaturgicbrewing.mixin;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.thaumaturgicbrewing.BrewingRecipeHook;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adds the generated potion recipes once item components are bound, before the recipe manager indexes its recipes. */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    private static final Logger THAUMATURGICBREWING$LOG = LoggerFactory.getLogger("Thaumaturgic Brewing");

    @Shadow private RecipeMap recipes;
    @Shadow @Final private HolderLookup.Provider registries;

    @Inject(method = "finalizeRecipeLoading", at = @At("HEAD"))
    private void thaumaturgicbrewing$addPotionRecipes(FeatureFlagSet enabledFlags, CallbackInfo ci) {
        Map<String, JsonObject> generated = BrewingRecipeHook.takeGenerated();
        if (generated.isEmpty()) return;
        List<RecipeHolder<?>> all = new ArrayList<>(recipes.values());
        Set<ResourceKey<Recipe<?>>> have = new HashSet<>();
        for (RecipeHolder<?> h : all) have.add(h.id());
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        int added = 0;
        for (Map.Entry<String, JsonObject> e : generated.entrySet()) {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.parse(e.getKey()));
            if (have.contains(key)) continue;
            var parsed = Recipe.CODEC.parse(ops, e.getValue());
            if (parsed.isError()) {
                THAUMATURGICBREWING$LOG.warn("Thaumaturgic Brewing: skipped {}: {}", e.getKey(), parsed.error().get().message());
                continue;
            }
            all.add(new RecipeHolder<>(key, parsed.getOrThrow()));
            added++;
        }
        recipes = RecipeMap.create(all);
        THAUMATURGICBREWING$LOG.info("Thaumaturgic Brewing: added {} potion recipes to the crucible", added);
    }
}
