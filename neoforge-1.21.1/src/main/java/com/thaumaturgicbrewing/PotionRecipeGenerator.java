package com.thaumaturgicbrewing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds crucible recipes for every potion the brewing stand can make, vanilla or modded. It reads the game's own
 * brewing registry (vanilla mixes plus everything mods add in RegisterBrewingRecipesEvent) and writes recipe JSON,
 * so the recipes go through the normal loader. It runs only while recipes load (world load and /reload).
 * Potions that already have a crucible recipe (ours or a datapack's) are left alone.
 */
public final class PotionRecipeGenerator {
    private static final Logger LOG = LoggerFactory.getLogger("Thaumaturgic Brewing");
    private static final String NS = ThaumaturgicBrewing.MODID;
    private static final String CRUCIBLE = "thaumaturge:crucible";
    private static final String POTION_CONTENTS = "minecraft:potion_contents";
    private static final String POTION = "minecraft:potion";
    private static final String SPLASH = "minecraft:splash_potion";
    private static final String LINGERING = "minecraft:lingering_potion";
    private static final String WATER = "minecraft:water";
    private static final int MAIN = 5;

    /** Aspects for effects we know; anything else falls back on its effect category. */
    private static final Map<String, List<String>> EFFECT_ASPECTS = new HashMap<>();
    /** Added (amount 2) when two recipes would otherwise need the same catalyst and aspects. */
    private static final List<String> TIEBREAK = List.of("praecantatio", "cognitio", "permutatio", "auram", "vitreus",
            "herba", "terra", "ignis", "spiritus", "desiderium", "sensus", "bestia", "metallum", "fabrico");

    static {
        e("night_vision", "sensus");
        e("invisibility", "vacuos");
        e("fire_resistance", "gelum");
        e("speed", "motus");
        e("water_breathing", "aer");
        e("instant_health", "victus");
        e("regeneration", "ordo");
        e("strength", "aversio");
        e("jump_boost", "volatus");
        e("poison", "perditio");
        e("weakness", "perditio");
        e("slowness", "perditio");
        e("instant_damage", "perditio");
        e("slow_falling", "volatus", "aer");
        e("resistance", "praemunio");
        e("luck", "desiderium");
        e("unluck", "perditio", "desiderium");
        e("wind_charged", "aer", "motus");
        e("weaving", "fabrico");
        e("oozing", "bestia", "aqua");
        e("infested", "bestia", "terra");
        e("haste", "instrumentum", "motus");
        e("mining_fatigue", "perditio", "instrumentum");
        e("nausea", "perditio", "sensus");
        e("blindness", "tenebrae");
        e("darkness", "tenebrae", "sensus");
        e("hunger", "perditio", "desiderium");
        e("wither", "mortuus", "perditio");
        e("health_boost", "victus", "potentia");
        e("absorption", "victus", "praemunio");
        e("saturation", "victus", "desiderium");
        e("glowing", "lux", "sensus");
        e("levitation", "volatus", "motus");
        e("conduit_power", "aqua", "potentia");
        e("dolphins_grace", "aqua", "motus");
        e("bad_omen", "vitium");
        e("trial_omen", "vitium", "ordo");
        e("raid_omen", "vitium", "humanus");
        e("hero_of_the_village", "humanus", "desiderium");
    }

    private static void e(String effect, String... aspects) {
        EFFECT_ASPECTS.put("minecraft:" + effect, List.of(aspects));
    }

    private PotionRecipeGenerator() {}

    /**
     * @param existing     every recipe JSON about to load
     * @param ingredientTypeKey the JSON key for a custom ingredient type ("type" on 1.21.1, "neoforge:ingredient_type" on 26.1)
     * @return new recipe id ("ns:path") to JSON
     */
    public static Map<String, JsonObject> generate(Iterable<JsonElement> existing, String ingredientTypeKey) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        if (!BrewingConfig.enabled()) {
            LOG.info("Thaumaturgic Brewing: recipe generation from brewing is off in the config");
            return out;
        }
        Set<String> blacklist = BrewingConfig.blacklist();

        PotionBrewing brewing;
        try {
            brewing = PotionBrewing.bootstrap(FeatureFlags.DEFAULT_FLAGS, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        } catch (Throwable t) {
            LOG.warn("Thaumaturgic Brewing: couldn't read the brewing registry, no potion recipes generated", t);
            return out;
        }

        // What crucible recipes already make, and which catalyst + aspect sets are taken.
        Set<String> made = new HashSet<>();
        Set<String> taken = new HashSet<>();
        for (JsonElement el : existing) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (!CRUCIBLE.equals(str(o, "type"))) continue;
            JsonObject result = obj(o, "result");
            if (result != null) {
                String p = potionOf(obj(result, "components"));
                if (p != null) made.add(str(result, "id") + "|" + p);
            }
            JsonObject cat = obj(o, "catalyst");
            String catPotion = cat == null ? null : potionOf(obj(cat, "components"));
            Set<String> asp = new TreeSet<>();
            if (o.get("aspects") instanceof JsonArray arr) {
                for (JsonElement a : arr) if (a.isJsonObject()) asp.add(String.valueOf(str(a.getAsJsonObject(), "aspect")));
            }
            taken.add(catPotion + "|" + asp);
        }

        // Every item the brewing stand takes as an ingredient, sorted by id so the result is stable.
        List<ItemStack> ingredients = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack s = new ItemStack(item);
            if (s.isEmpty()) continue;
            try {
                if (brewing.isIngredient(s)) ingredients.add(s);
            } catch (Throwable ignored) {}
        }
        ingredients.sort(Comparator.comparing(s -> String.valueOf(BuiltInRegistries.ITEM.getKey(s.getItem()))));

        List<Holder.Reference<Potion>> potions = new ArrayList<>(BuiltInRegistries.POTION.holders().toList());
        potions.sort(Comparator.comparing(Holder::getRegisteredName));
        // Base brews (water, awkward, mundane...) first, so a potion is made from water when it can be.
        List<Holder.Reference<Potion>> sources = new ArrayList<>(potions);
        sources.sort(Comparator.comparing((Holder.Reference<Potion> h) -> !h.value().getEffects().isEmpty()));

        // result potion -> the potion it is brewed from
        Map<String, Holder<Potion>> from = new LinkedHashMap<>();
        Map<String, Holder<Potion>> byId = new HashMap<>();
        for (Holder.Reference<Potion> src : sources) {
            ItemStack srcStack = PotionContents.createItemStack(Items.POTION, src);
            for (ItemStack ing : ingredients) {
                ItemStack res;
                try {
                    res = brewing.mix(ing, srcStack);
                } catch (Throwable t) {
                    continue;
                }
                if (res == null || res.isEmpty() || res == srcStack || !res.is(Items.POTION)) continue;
                PotionContents pc = res.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                if (pc == null || pc.potion().isEmpty()) continue;
                Holder<Potion> r = pc.potion().get();
                String rid = r.getRegisteredName();
                if (rid.equals(src.getRegisteredName()) || r.value().getEffects().isEmpty()) continue;
                byId.put(rid, r);
                from.putIfAbsent(rid, src);
            }
        }

        int base = 0, forms = 0, modded = 0;
        Set<String> moddedPotions = new TreeSet<>();
        for (Map.Entry<String, Holder<Potion>> en : from.entrySet()) {
            String rid = en.getKey();
            if (blocked(rid, blacklist)) continue;
            Holder<Potion> r = byId.get(rid);
            Holder<Potion> f = en.getValue();
            boolean isModded = !rid.startsWith("minecraft:");
            boolean harmful = harmful(r.value());

            if (!made.contains(POTION + "|" + rid)) {
                String catalyst = f.value().getEffects().isEmpty() ? WATER : f.getRegisteredName();
                String research;
                List<String> aspects;
                Change change = change(f.value(), r.value());
                if (change == Change.STRONGER) {
                    aspects = List.of("potentia");
                    research = harmful ? "strong_harmful_potions" : "strong_advanced_potions";
                } else if (change == Change.LONGER) {
                    aspects = List.of("lux");
                    research = harmful ? "long_harmful_potions" : "long_advanced_potions";
                } else {
                    aspects = aspectsFor(r.value());
                    research = harmful ? "harmful_potions" : "advanced_potions";
                }
                List<String> unique = unique(catalyst, aspects, taken);
                out.put(id(rid, ""), recipe(catalyst, unique, aspects.size(), research, POTION, rid, ingredientTypeKey));
                made.add(POTION + "|" + rid);
                base++;
                if (isModded) moddedPotions.add(rid);
            }
            forms += form(out, made, taken, rid, SPLASH, "aqua", "splash_potions", "_splash", ingredientTypeKey);
            forms += form(out, made, taken, rid, LINGERING, "spiritus", "lingering_potions", "_lingering", ingredientTypeKey);
            if (isModded) modded++;
        }
        LOG.info("Thaumaturgic Brewing: generated {} crucible recipes from brewing ({} potions, {} splash/lingering; {} modded potions{})",
                out.size(), base, forms, modded, moddedPotions.isEmpty() ? "" : ": " + String.join(", ", moddedPotions));
        return out;
    }

    private static int form(Map<String, JsonObject> out, Set<String> made, Set<String> taken, String rid, String item,
                            String aspect, String research, String suffix, String key) {
        if (made.contains(item + "|" + rid)) return 0;
        List<String> unique = unique(rid, List.of(aspect), taken);
        out.put(id(rid, suffix), recipe(rid, unique, 1, research, item, rid, key));
        made.add(item + "|" + rid);
        return 1;
    }

    private enum Change { STRONGER, LONGER, OTHER }

    /** Same effects with a higher level is a strengthening; same effects lasting longer is an extension. */
    private static Change change(Potion from, Potion to) {
        List<MobEffectInstance> a = from.getEffects(), b = to.getEffects();
        if (a.isEmpty() || a.size() != b.size()) return Change.OTHER;
        boolean stronger = false, longer = false;
        for (int i = 0; i < a.size(); i++) {
            MobEffectInstance x = a.get(i), y = b.get(i);
            if (!x.getEffect().equals(y.getEffect())) return Change.OTHER;
            if (y.getAmplifier() > x.getAmplifier()) stronger = true;
            if (y.getDuration() > x.getDuration()) longer = true;
        }
        return stronger ? Change.STRONGER : longer ? Change.LONGER : Change.OTHER;
    }

    private static boolean harmful(Potion p) {
        List<MobEffectInstance> fx = p.getEffects();
        if (fx.isEmpty()) return false;
        long bad = fx.stream().filter(i -> i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL).count();
        return bad * 2 > fx.size() || (bad > 0 && fx.size() == 1);
    }

    /** Up to two aspects from the potion's effects; unknown effects go by category. */
    private static List<String> aspectsFor(Potion p) {
        Set<String> s = new LinkedHashSet<>();
        for (MobEffectInstance i : p.getEffects()) {
            Holder<MobEffect> eff = i.getEffect();
            List<String> known = EFFECT_ASPECTS.get(eff.getRegisteredName());
            if (known != null) s.addAll(known);
            else s.add(switch (eff.value().getCategory()) {
                case HARMFUL -> "perditio";
                case BENEFICIAL -> "victus";
                default -> "ordo";
            });
        }
        List<String> l = new ArrayList<>(s);
        return l.size() > 2 ? l.subList(0, 2) : l;
    }

    /** Adds a tiebreak aspect until no other recipe uses the same catalyst and aspect set. */
    private static List<String> unique(String catalyst, List<String> aspects, Set<String> taken) {
        if (taken.add(catalyst + "|" + keySet(aspects))) return aspects;
        for (String extra : TIEBREAK) {
            if (aspects.contains(extra)) continue;
            List<String> l = new ArrayList<>(aspects);
            l.add(extra);
            if (taken.add(catalyst + "|" + keySet(l))) return l;
        }
        return aspects;
    }

    private static Set<String> keySet(List<String> aspects) {
        Set<String> s = new TreeSet<>();
        for (String a : aspects) s.add("thaumaturge:" + a);
        s.add("thaumaturge:alkimia");
        return s;
    }

    private static JsonObject recipe(String catalystPotion, List<String> aspects, int mainCount, String research, String item,
                                     String resultPotion, String key) {
        JsonObject o = new JsonObject();
        o.addProperty("type", CRUCIBLE);
        JsonArray asp = new JsonArray();
        for (int i = 0; i < aspects.size(); i++) {
            JsonObject a = new JsonObject();
            // The main aspect(s) take 5; a tiebreak aspect added after them takes 2.
            a.addProperty("amount", i < mainCount ? MAIN : 2);
            a.addProperty("aspect", "thaumaturge:" + aspects.get(i));
            asp.add(a);
        }
        JsonObject alk = new JsonObject();
        alk.addProperty("amount", 1);
        alk.addProperty("aspect", "thaumaturge:alkimia");
        asp.add(alk);
        o.add("aspects", asp);
        JsonObject cat = new JsonObject();
        cat.addProperty(key, "neoforge:components");
        cat.addProperty("items", POTION);
        cat.add("components", contents(catalystPotion));
        o.add("catalyst", cat);
        JsonObject r = new JsonObject();
        r.addProperty("entry", NS + ":" + research);
        o.add("research", r);
        JsonObject res = new JsonObject();
        res.addProperty("id", item);
        res.add("components", contents(resultPotion));
        o.add("result", res);
        return o;
    }

    private static JsonObject contents(String potion) {
        JsonObject pc = new JsonObject();
        pc.addProperty("potion", potion);
        JsonObject c = new JsonObject();
        c.add(POTION_CONTENTS, pc);
        return c;
    }

    private static String id(String potionId, String suffix) {
        String[] p = potionId.split(":", 2);
        return NS + ":generated/" + p[0] + "/" + p[1] + suffix;
    }

    private static boolean blocked(String potionId, Set<String> blacklist) {
        if (blacklist.contains(potionId)) return true;
        int c = potionId.indexOf(':');
        return c > 0 && blacklist.contains(potionId.substring(0, c) + ":*");
    }

    private static String potionOf(JsonObject components) {
        if (components == null) return null;
        JsonElement pc = components.get(POTION_CONTENTS);
        if (pc == null) return null;
        if (pc.isJsonPrimitive()) return norm(pc.getAsString());
        if (pc.isJsonObject() && pc.getAsJsonObject().get("potion") != null && pc.getAsJsonObject().get("potion").isJsonPrimitive())
            return norm(pc.getAsJsonObject().get("potion").getAsString());
        return null;
    }

    private static String norm(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? norm(e.getAsString()) : null;
    }

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
}
