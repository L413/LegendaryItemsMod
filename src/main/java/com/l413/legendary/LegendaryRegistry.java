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
                10.0,
                1.6,
                List.of(
                        Component.text("Only ONE exists on the server.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Held: Speed I, Jump Boost I, Night Vision.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("On hit: Regeneration for 2 seconds.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("15% chance to deal 2 bonus damage with an astral burst.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
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
                12.0,
                1.0,
                List.of(
                        Component.text("One of One.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Held: Resistance I.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("On hit: Wither I (2s), Slowness (1.5s), and a void pull.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Heals you for 1.5 HP and releases Dragon's Breath.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
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
                9.0,
                1.4,
                List.of(
                        Component.text("One of One.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Held: Water Breathing and Dolphin's Grace.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Melee or thrown hit: lightning effect and 2 bonus damage.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Gain Slow Falling for 3s when airborne on hit.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
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
                        Component.text("+2 maximum health.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                ),
                null,
                EquipmentSlot.CHEST,
                2.0
        ));

        register(new LegendaryItem(
                "astral_excavator",
                "Astral Excavator",
                Material.NETHERITE_PICKAXE,
                "legendary:astral_excavator",
                7.0,
                1.2,
                List.of(
                        Component.text("Only ONE exists on the server.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Instantly breaks blocks that can be mined.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "GNG",
                            "EPE",
                            "G G"
                    );
                    recipe.setIngredient('G', Material.GOLD_BLOCK);
                    recipe.setIngredient('N', Material.NETHER_STAR);
                    recipe.setIngredient('E', Material.ECHO_SHARD);
                    recipe.setIngredient('P', Material.NETHERITE_PICKAXE);
                }
        ));

        register(new LegendaryItem(
                "titan_pickaxe",
                "Titan Pickaxe",
                Material.NETHERITE_PICKAXE,
                "legendary:titan_pickaxe",
                7.0,
                1.2,
                List.of(
                        Component.text("Only ONE exists on the server.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Efficiency V; mines a 3x3 plane matching the face hit.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "DID",
                            " P ",
                            "I I"
                    );
                    recipe.setIngredient('D', Material.DIAMOND_BLOCK);
                    recipe.setIngredient('I', Material.IRON_BLOCK);
                    recipe.setIngredient('P', Material.NETHERITE_PICKAXE);
                }
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
                        Component.text("While worn: Dolphin's Grace.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Sneak while falling to gain Slow Falling.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
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