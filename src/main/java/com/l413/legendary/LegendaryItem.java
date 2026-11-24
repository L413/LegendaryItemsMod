package com.l413.legendary;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.datacomponent.item.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Consumer;

public record LegendaryItem(
        String id,
        String displayName,
        Material baseMaterial,
        String resourceModelKey,
        double attackDamage,
        double attackSpeed,
        List<Component> lore,
        Consumer<ShapedRecipe> recipeConfigurator,
        EquipmentSlot equipmentSlot,
        double maxHealthBonus
) {
    public LegendaryItem(
            String id,
            String displayName,
            Material baseMaterial,
            String resourceModelKey,
            double attackDamage,
            double attackSpeed,
            List<Component> lore,
            Consumer<ShapedRecipe> recipeConfigurator
    ) {
        this(id, displayName, baseMaterial, resourceModelKey, attackDamage, attackSpeed, lore, recipeConfigurator, null, 0.0);
    }

    public ItemStack createItemStack(LegendaryPlugin plugin) {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        double configuredDamage = plugin.getConfig().getDouble("item-stats.items." + id + ".attack-damage", attackDamage);
        double configuredSpeed = plugin.getConfig().getDouble("item-stats.items." + id + ".attack-speed", attackSpeed);
        double configuredHealthBonus = plugin.getConfig().getDouble(
                "item-stats.items." + id + ".max-health-bonus",
                maxHealthBonus
        );

        meta.displayName(Component.text(displayName, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> itemLore = new ArrayList<>(lore);
        Map<Enchantment, Integer> requiredEnchantments = requiredEnchantments(plugin);
        if (requiredEnchantments.isEmpty()) {
            itemLore.add(Component.text("Cannot be enchanted.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            String enchantmentLore = requiredEnchantments.entrySet().stream()
                    .map(entry -> entry.getKey().getKey().getKey().replace('_', ' ') + " " + entry.getValue())
                    .collect(Collectors.joining(", "));
            itemLore.add(Component.text("Fixed enchantment(s): " + enchantmentLore + ". No other enchants allowed.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        int repairPercent = (int) Math.round(plugin.getConfig().getDouble("item-stats.heart-repair-fraction", 0.33) * 100.0);
        itemLore.add(Component.text("Repair with a Heart of Life in an anvil (+" + repairPercent + "% max durability).", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(itemLore);
        for (Enchantment enchantment : meta.getEnchants().keySet()) {
            meta.removeEnchant(enchantment);
        }
        for (Map.Entry<Enchantment, Integer> enchantment : requiredEnchantments.entrySet()) {
            meta.addEnchant(enchantment.getKey(), enchantment.getValue(), true);
        }
        meta.setEnchantmentGlintOverride(!requiredEnchantments.isEmpty());

        meta.getPersistentDataContainer().set(plugin.getItemKey(), PersistentDataType.STRING, id);

        if (equipmentSlot == null) {
            NamespacedKey dmgKey = new NamespacedKey(plugin, id + "_damage");
            NamespacedKey spdKey = new NamespacedKey(plugin, id + "_speed");

            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(dmgKey, configuredDamage - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(spdKey, configuredSpeed - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        } else if (configuredHealthBonus != 0.0) {
            NamespacedKey healthKey = new NamespacedKey(plugin, id + "_max_health");
            meta.addAttributeModifier(Attribute.MAX_HEALTH,
                    new AttributeModifier(healthKey, configuredHealthBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        }
        item.setItemMeta(meta);
        int baseDurability = baseMaterial.getMaxDurability();
        if (baseDurability <= 0) {
            baseDurability = Material.NETHERITE_SWORD.getMaxDurability();
        }
        double durabilityMultiplier = plugin.getConfig().getDouble(
                "item-stats.durability-multipliers." + id,
                plugin.getConfig().getDouble("item-stats.durability-multipliers.default", id.equals("astral_excavator") ? 9.0 : 1.1)
        );
        if (!Double.isFinite(durabilityMultiplier) || durabilityMultiplier <= 0.0) {
            plugin.getLogger().warning("Invalid durability multiplier for " + id + "; using 1.1.");
            durabilityMultiplier = 1.1;
        }
        item.setData(DataComponentTypes.MAX_DAMAGE, (int) Math.ceil(baseDurability * durabilityMultiplier));
        item.setData(DataComponentTypes.DAMAGE, 0);
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);

        if (resourceModelKey != null && !resourceModelKey.isEmpty()) {
            NamespacedKey modelKey = NamespacedKey.fromString(resourceModelKey);
            if (modelKey != null) {
                item.setData(DataComponentTypes.ITEM_MODEL, modelKey);
            }
        }

        if (equipmentSlot != null) {
            NamespacedKey assetKey = NamespacedKey.fromString("legendary:astral");
            NamespacedKey modelKey = NamespacedKey.fromString("legendary:" + id);
            if (modelKey != null) {
                item.setData(DataComponentTypes.ITEM_MODEL, modelKey);
            }
            if (assetKey != null) {
                item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(equipmentSlot).assetId(assetKey).build());
            }
        }

        return item;
    }

    public void refreshConfigurableData(ItemStack item, LegendaryPlugin plugin) {
        if (!id.equals(plugin.getLegendaryId(item))) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        Map<Enchantment, Integer> enchantments = requiredEnchantments(plugin);
        for (Enchantment existing : new ArrayList<>(meta.getEnchants().keySet())) {
            meta.removeEnchant(existing);
        }
        enchantments.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
        meta.setEnchantmentGlintOverride(!enchantments.isEmpty());

        removeConfiguredModifier(meta, Attribute.ATTACK_DAMAGE, plugin, id + "_damage");
        removeConfiguredModifier(meta, Attribute.ATTACK_SPEED, plugin, id + "_speed");
        removeConfiguredModifier(meta, Attribute.MAX_HEALTH, plugin, id + "_max_health");

        if (equipmentSlot == null) {
            double damage = plugin.getConfig().getDouble("item-stats.items." + id + ".attack-damage", attackDamage);
            double speed = plugin.getConfig().getDouble("item-stats.items." + id + ".attack-speed", attackSpeed);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_damage"),
                    damage - 1.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            ));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_speed"),
                    speed - 4.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            ));
        } else {
            double healthBonus = plugin.getConfig().getDouble(
                    "item-stats.items." + id + ".max-health-bonus",
                    maxHealthBonus
            );
            if (healthBonus != 0.0) {
                meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                        new NamespacedKey(plugin, id + "_max_health"),
                        healthBonus,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.CHEST
                ));
            }
        }
        item.setItemMeta(meta);

        int baseDurability = baseMaterial.getMaxDurability();
        if (baseDurability <= 0) {
            baseDurability = Material.NETHERITE_SWORD.getMaxDurability();
        }
        double fallbackMultiplier = id.equals("astral_excavator") ? 9.0 : 1.1;
        double multiplier = plugin.getConfig().getDouble(
                "item-stats.durability-multipliers." + id,
                plugin.getConfig().getDouble("item-stats.durability-multipliers.default", fallbackMultiplier)
        );
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
            multiplier = 1.1;
        }
        int maximumDamage = (int) Math.ceil(baseDurability * multiplier);
        int currentDamage = item.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        item.setData(DataComponentTypes.MAX_DAMAGE, maximumDamage);
        item.setData(DataComponentTypes.DAMAGE, Math.min(currentDamage, maximumDamage - 1));
    }

    private void removeConfiguredModifier(ItemMeta meta, Attribute attribute, LegendaryPlugin plugin, String modifierName) {
        NamespacedKey key = new NamespacedKey(plugin, modifierName);
        var modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null) {
            return;
        }
        for (AttributeModifier modifier : new ArrayList<>(modifiers)) {
            if (key.equals(modifier.getKey())) {
                meta.removeAttributeModifier(attribute, modifier);
            }
        }
    }

    public Map<Enchantment, Integer> requiredEnchantments(LegendaryPlugin plugin) {
        var section = plugin.getConfig().getConfigurationSection("item-stats.enchantments." + id);
        if (section == null) {
            return Map.of();
        }

        Map<Enchantment, Integer> configured = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(java.util.Locale.ROOT)));
            int level = section.getInt(key);
            if (enchantment == null || level <= 0) {
                plugin.getLogger().warning("Ignoring invalid enchantment configuration for " + id + ": " + key + "=" + level);
                continue;
            }
            configured.put(enchantment, level);
        }
        return Map.copyOf(configured);
    }
}