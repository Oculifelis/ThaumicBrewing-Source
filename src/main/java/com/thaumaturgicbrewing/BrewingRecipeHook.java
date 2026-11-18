package com.thaumaturgicbrewing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Potion recipe generation on 26.1.
 * Recipe JSON is read before item components are bound, and many mods build potion stacks inside their brewing event,
 * so the brewing registry can't be read yet. This listener only keeps the crucible recipe JSON being loaded; the
 * recipe manager mixin generates the potion recipes once components are bound (world load and /reload).
 */
public final class BrewingRecipeHook {
    private static volatile List<JsonElement> pending;

    private BrewingRecipeHook() {}

    static void onModifyRecipeJsons(ModifyRecipeJsonsEvent event) {
        List<JsonElement> crucible = new ArrayList<>();
        for (JsonElement el : event.getRecipeJsons().values()) {
            if (el.isJsonObject() && el.getAsJsonObject().get("type") != null
                    && "thaumaturge:crucible".equals(el.getAsJsonObject().get("type").getAsString())) crucible.add(el);
        }
        pending = crucible;
    }

    /** Called once per recipe load, after components are bound. Empty when there was no recipe load to follow. */
    public static Map<String, JsonObject> takeGenerated() {
        List<JsonElement> existing = pending;
        pending = null;
        if (existing == null) return Map.of();
        return PotionRecipeGenerator.generate(existing, "neoforge:ingredient_type");
    }
}
