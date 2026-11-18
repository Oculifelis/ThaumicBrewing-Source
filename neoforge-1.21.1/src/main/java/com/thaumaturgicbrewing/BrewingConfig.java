package com.thaumaturgicbrewing;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Common config: whether to build crucible recipes from the brewing registry, and potions to skip. */
public final class BrewingConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue GENERATE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("brewing");
        GENERATE = b.comment("Make crucible recipes for every potion the brewing stand can brew, modded ones included.",
                        "Potions that already have a crucible recipe keep it. Applies on world load and /reload.")
                .define("generateFromBrewing", true);
        BLACKLIST = b.comment("Potions to skip, by id (\"somemod:some_potion\"), or every potion from a mod (\"somemod:*\").")
                .defineListAllowEmpty("blacklist", List.of(), () -> "", o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private BrewingConfig() {}

    static boolean enabled() {
        try {
            return GENERATE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    static Set<String> blacklist() {
        try {
            return new HashSet<>(BLACKLIST.get());
        } catch (IllegalStateException notLoaded) {
            return Set.of();
        }
    }
}
