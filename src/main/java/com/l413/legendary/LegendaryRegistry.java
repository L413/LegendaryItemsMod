package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegendaryRegistry {
    private final LegendaryPlugin plugin;
    private final Map<String, LegendaryItem> items = new LinkedHashMap<>();

    public LegendaryRegistry(LegendaryPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    private void registerDefaults() {
        register(new LegendaryItem(
                "astral_sovereign",
                "Astral Sovereign",
                Material.NETHERITE_SWORD,
                "legendary:astral_sovereign",
                14.0,
                1.8,
                List.of(
                        Component.text("Only ONE exists on the server.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Held: Speed II, Jump Boost II, Night Vision.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("On hit: Regeneration for 3 seconds.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("25% chance to deal 4 bonus damage with an astral burst.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Projects a sky beacon and reveals your location.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "DHD",
                            "NSN",
                            "DED"
                    );
                    recipe.setIngredient('D', Material.DIAMOND_BLOCK);   // 4 Diamond Blocks
                    recipe.setIngredient('H', Material.HEAVY_CORE);      // 1 Heavy Core
                    recipe.setIngredient('N', Material.NETHER_STAR);     // 2 Nether Stars
                    recipe.setIngredient('S', Material.NETHERITE_SWORD); // 1 Netherite Sword
                    recipe.setIngredient('E', Material.END_ROD); // 1 End Rod
                }
        ));

        register(new LegendaryItem(
                "void_cleaver",
                "Void Cleaver",
                Material.NETHERITE_AXE,
                "legendary:void_cleaver",
                16.0,
                1.1,
                List.of(
                        Component.text("One of One.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Held: Resistance I and Fire Resistance.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("On hit: Wither II (3s), Slowness (2s), and a void pull.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Heals you for 3 HP and releases Dragon's Breath.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "CSC",
                            "EAE",
                            "C C"
                    );
                    recipe.setIngredient('C', Material.CRYING_OBSIDIAN); // 4 Crying Obsidian
                    recipe.setIngredient('S', Material.NETHER_STAR);     // 1 Nether Star
                    recipe.setIngredient('E', Material.ECHO_SHARD);      // 2 Echo Shards
                    recipe.setIngredient('A', Material.NETHERITE_AXE);   // 1 Netherite Axe
                }
        ));

        register(new LegendaryItem(
                "storm_piercer",
                "Storm Piercer",
                Material.TRIDENT,
                "legendary:storm_piercer",
                12.0,
                1.6,
                List.of(
                        Component.text("One of One.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Held: Water Breathing, Dolphin's Grace, Speed I.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Melee or thrown hit: lightning effect and 4 bonus damage.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Gain Slow Falling for 4s when airborne on hit.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "PBP",
                            "H R",
                            "P P"
                    );
                    recipe.setIngredient('P', Material.PRISMARINE_CRYSTALS); // 4 Prismarine Crystals
                    recipe.setIngredient('B', Material.BREEZE_ROD);          // 1 Breeze Rod
                    recipe.setIngredient('H', Material.HEAVY_CORE);          // 1 Heavy Core
                    recipe.setIngredient('R', Material.TRIDENT);             // 1 Trident
                }
        ));

        register(new LegendaryItem(
                "astral_helmet",
                "Astral Crown",
                Material.NETHERITE_HELMET,
                "legendary:astral_helmet",
                0.0,
                0.0,
                List.of(
                        Component.text("Astral armor set piece.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("While worn: Night Vision and Water Breathing.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                ),
                null,
                EquipmentSlot.HEAD,
                0.0
        ));

        register(new LegendaryItem(
                "astral_chestplate",
                "Astral Chestplate",
                Material.NETHERITE_CHESTPLATE,
                "legendary:astral_chestplate",
                0.0,
                0.0,
                List.of(
                        Component.text("Astral armor set piece.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("While worn: Resistance I.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("+4 maximum health.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                ),
                null,
                EquipmentSlot.CHEST,
                4.0
        ));

        register(new LegendaryItem(
                "astral_leggings",
                "Astral Greaves",
                Material.NETHERITE_LEGGINGS,
                "legendary:astral_leggings",
                0.0,
                0.0,
                List.of(
                        Component.text("Astral armor set piece.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("While worn: Speed I and Jump Boost I.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                ),
                null,
                EquipmentSlot.LEGS,
                0.0
        ));

        register(new LegendaryItem(
                "astral_boots",
                "Astral Striders",
                Material.NETHERITE_BOOTS,
                "legendary:astral_boots",
                0.0,
                0.0,
                List.of(
                        Component.text("Astral armor set piece.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("While worn: Dolphin's Grace; prevents fall damage.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                ),
                null,
                EquipmentSlot.FEET,
                0.0
        ));
    }

    public void register(LegendaryItem item) {
        items.put(item.id(), item);

        if (item.recipeConfigurator() == null) {
            return;
        }
        NamespacedKey recipeKey = new NamespacedKey(plugin, "craft_" + item.id());
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, item.createItemStack(plugin));
        item.recipeConfigurator().accept(recipe);
        Bukkit.addRecipe(recipe);
    }

    public LegendaryItem getById(String id) {
        return items.get(id);
    }

    public Collection<LegendaryItem> getAll() {
        return items.values();
    }
}