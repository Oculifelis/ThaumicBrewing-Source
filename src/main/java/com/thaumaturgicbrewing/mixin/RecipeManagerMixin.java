package com.thaumaturgicbrewing.mixin;

import com.google.gson.JsonElement;
import com.thaumaturgicbrewing.PotionRecipeGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Map;

/** Adds the generated potion recipes to the recipe JSON before the recipe manager parses it (world load and /reload). */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
    private void thaumaturgicbrewing$addPotionRecipes(Map<ResourceLocation, JsonElement> jsons, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        PotionRecipeGenerator.generate(new ArrayList<>(jsons.values()), "type")
                .forEach((id, json) -> jsons.putIfAbsent(ResourceLocation.parse(id), json));
    }
}
