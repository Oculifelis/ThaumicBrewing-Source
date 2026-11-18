package com.tbpotiontest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.TreeSet;

/** Dev-only test mod: three brewable modded potions, one of them through a custom IBrewingRecipe. Not shipped. */
@Mod("tbpotiontest")
public final class PotionTestMod {
    private static final Logger LOG = LoggerFactory.getLogger("tbpotiontest");
    private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, "tbpotiontest");
    static final DeferredHolder<Potion, Potion> FORTUNE = POTIONS.register("fortune", () -> new Potion("fortune", new MobEffectInstance(MobEffects.LUCK, 3600)));
    static final DeferredHolder<Potion, Potion> LONG_FORTUNE = POTIONS.register("long_fortune", () -> new Potion("fortune", new MobEffectInstance(MobEffects.LUCK, 9600)));
    static final DeferredHolder<Potion, Potion> STRONG_FORTUNE = POTIONS.register("strong_fortune", () -> new Potion("fortune", new MobEffectInstance(MobEffects.LUCK, 1800, 1)));
    static final DeferredHolder<Potion, Potion> GLOW = POTIONS.register("glow", () -> new Potion("glow", new MobEffectInstance(MobEffects.GLOWING, 3600)));

    public PotionTestMod(IEventBus modBus) {
        POTIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(PotionTestMod::brewing);
        if (Boolean.getBoolean("tbpotiontest.report")) NeoForge.EVENT_BUS.addListener(PotionTestMod::report);
    }

    private static void brewing(RegisterBrewingRecipesEvent e) {
        e.getBuilder().addMix(Potions.AWKWARD, Items.AMETHYST_SHARD, FORTUNE);
        e.getBuilder().addMix(FORTUNE, Items.REDSTONE, LONG_FORTUNE);
        e.getBuilder().addMix(FORTUNE, Items.GLOWSTONE_DUST, STRONG_FORTUNE);
        // Custom IBrewingRecipe route, like many mods use.
        e.getBuilder().addRecipe(Ingredient.of(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD).getItem()),
                Ingredient.of(Items.GLOW_BERRIES), PotionContents.createItemStack(Items.POTION, GLOW));
    }

    private static void report(ServerStartedEvent e) {
        var server = e.getServer();
        count(server, "start");
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenCompleteAsync((v, t) -> {
            if (t != null) LOG.error("POTIONTEST reload failed", t);
            count(server, "after /reload");
            server.halt(false);
        }, server);
    }

    private static void count(net.minecraft.server.MinecraftServer server, String when) {
        TreeSet<String> ids = new TreeSet<>();
        int crucible = 0;
        for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
            String id = String.valueOf(h.id());
            if (id.contains("thaumaturgicbrewing:")) crucible++;
            if (id.contains("thaumaturgicbrewing:generated/")) ids.add(id.replaceAll(".*thaumaturgicbrewing:generated/", "").replace("]", "") + " <- " + h.value().getClass().getSimpleName());
        }
        LOG.info("POTIONTEST {}: thaumaturgicbrewing recipes loaded: {} ({} generated)", when, crucible, ids.size());
        if (when.equals("start")) for (String s : ids) LOG.info("POTIONTEST generated {}", s);
    }
}
