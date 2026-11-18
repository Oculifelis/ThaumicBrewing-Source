package com.thaumaturgicbrewing;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Thaumaturgic Brewing: Thaumic Brewing (Michael Bunting, Thaumcraft 6), rebuilt on Thaumaturge: potions brewed in the crucible from essentia.
 * Vanilla potions and research are data written by tools/gen_data.py. Every other brewable potion, modded ones included, gets a
 * crucible recipe from {@link PotionRecipeGenerator} when recipes load. See docs/COVERAGE.md.
 */
@Mod(ThaumaturgicBrewing.MODID)
public final class ThaumaturgicBrewing {
    public static final String MODID = "thaumaturgicbrewing";

    public ThaumaturgicBrewing(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BrewingConfig.SPEC);
    }
}
