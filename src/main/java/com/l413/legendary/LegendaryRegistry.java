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
                        Component.text("Sneak + right-click: Astral Ward grants Absorption for 6 seconds and Regeneration I for 5 seconds (30s cooldown).", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
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
                "mjolnir",
                "Mjolnir",
                Material.MACE,
                "legendary:mjolnir",
                12.0,
                0.7,
                List.of(
                        Component.text("The one and only Storm God's weapon.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("A mighty mace forged from four Heavy Cores. Wind Burst II.", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                        Component.text("On hit: real lightning strikes your target, with four surrounding visual bolts (15s cooldown).", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Sneak + right-click air while looking up: a vertical launch surrounded by repeated spiraling sky lightning (90s cooldown).", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "HNH",
                            "AMA",
                            "HNH"
                    );
                    recipe.setIngredient('H', Material.HEAVY_CORE);
                    recipe.setIngredient('N', Material.NETHER_STAR);
                    recipe.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
                    recipe.setIngredient('M', Material.MACE);
                }
        ));

        register(new LegendaryItem(
                "storm_shuriken",
                "Storm Shuriken",
                Material.SNOWBALL,
                "legendary:storm_shuriken",
                1.0,
                1.0,
                List.of(
                        Component.text("One of One.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Right-click: throw a 3-shuriken burst (1-second cooldown).", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Sneak + right-click the top of a block: throw 12 shurikens radially and knock back nearby creatures (8s cooldown).", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Each shuriken hit deals 6 damage.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Durability: 10% above a Netherite sword.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "PBP",
                            "ESE",
                            "P P"
                    );
                    recipe.setIngredient('P', Material.PRISMARINE_CRYSTALS);
                    recipe.setIngredient('B', Material.BREEZE_ROD);
                    recipe.setIngredient('E', Material.ECHO_SHARD);
                    recipe.setIngredient('S', Material.SNOWBALL);
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
                        Component.text("While worn: Night Vision and Water Breathing.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Full set: Regeneration and Fire Resistance.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "ESE",
                            "N H",
                            " E "
                    );
                    recipe.setIngredient('E', Material.ECHO_SHARD);
                    recipe.setIngredient('S', Material.NETHER_STAR);
                    recipe.setIngredient('N', Material.NETHERITE_HELMET);
                    recipe.setIngredient('H', Material.HEAVY_CORE);
                },
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
                        Component.text("+2 maximum health.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Full set: Regeneration and Fire Resistance.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "EHE",
                            "NSN",
                            "E E"
                    );
                    recipe.setIngredient('E', Material.ECHO_SHARD);
                    recipe.setIngredient('H', Material.HEAVY_CORE);
                    recipe.setIngredient('N', Material.NETHER_STAR);
                    recipe.setIngredient('S', Material.NETHERITE_CHESTPLATE);
                },
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
                        Component.text("Efficiency V; mines a 3x3 plane matching the face hit.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Sneak + right-click: Haste C for 10 seconds.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Durability: 9x a Netherite pickaxe.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
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
                "astral_leggings",
                "Astral Greaves",
                Material.NETHERITE_LEGGINGS,
                "legendary:astral_leggings",
                0.0,
                0.0,
                List.of(
                        Component.text("Astral armor set piece.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("While worn: Speed I and Jump Boost I.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Full set: Regeneration and Fire Resistance.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "ENE",
                            "ELE",
                            "N N"
                    );
                    recipe.setIngredient('E', Material.ECHO_SHARD);
                    recipe.setIngredient('N', Material.NETHER_STAR);
                    recipe.setIngredient('L', Material.NETHERITE_LEGGINGS);
                },
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
                        Component.text("Sneak while falling to gain extended Slow Falling.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Full set: Regeneration and Fire Resistance.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ),
                recipe -> {
                    recipe.shape(
                            "E E",
                            "NBN",
                            "E E"
                    );
                    recipe.setIngredient('E', Material.ECHO_SHARD);
                    recipe.setIngredient('N', Material.NETHER_STAR);
                    recipe.setIngredient('B', Material.NETHERITE_BOOTS);
                },
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