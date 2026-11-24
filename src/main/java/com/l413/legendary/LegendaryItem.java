package com.l413.legendary;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

        meta.displayName(Component.text(displayName, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> itemLore = new ArrayList<>(lore);
        if (requiredEnchantments().isEmpty()) {
            itemLore.add(Component.text("Cannot be enchanted.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            String enchantmentLore = id.equals("astral_excavator")
                    ? "Efficiency V"
                    : "Wind Burst I";
            itemLore.add(Component.text("Fixed enchantment(s): " + enchantmentLore + ". No other enchants allowed.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        itemLore.add(Component.text("Repair with a Heart of Life in an anvil (+33% max durability).", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(itemLore);
        for (Enchantment enchantment : meta.getEnchants().keySet()) {
            meta.removeEnchant(enchantment);
        }
        for (Map.Entry<Enchantment, Integer> enchantment : requiredEnchantments().entrySet()) {
            meta.addEnchant(enchantment.getKey(), enchantment.getValue(), true);
        }
        meta.setEnchantmentGlintOverride(!requiredEnchantments().isEmpty());

        meta.getPersistentDataContainer().set(plugin.getItemKey(), PersistentDataType.STRING, id);

        if (equipmentSlot == null) {
            NamespacedKey dmgKey = new NamespacedKey(plugin, id + "_damage");
            NamespacedKey spdKey = new NamespacedKey(plugin, id + "_speed");

            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(dmgKey, attackDamage - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(spdKey, attackSpeed - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        } else if (maxHealthBonus != 0.0) {
            NamespacedKey healthKey = new NamespacedKey(plugin, id + "_max_health");
            meta.addAttributeModifier(Attribute.MAX_HEALTH,
                    new AttributeModifier(healthKey, maxHealthBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        }
        item.setItemMeta(meta);
        int baseDurability = baseMaterial.getMaxDurability();
        if (baseDurability <= 0) {
            baseDurability = Material.NETHERITE_SWORD.getMaxDurability();
        }
        item.setData(DataComponentTypes.MAX_DAMAGE, (int) Math.ceil(baseDurability * 1.1));
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

    public Map<Enchantment, Integer> requiredEnchantments() {
        return switch (id) {
            case "astral_excavator" -> Map.of(Enchantment.EFFICIENCY, 5);
            case "mjolnir" -> Map.of(Enchantment.WIND_BURST, 1);
            default -> Map.of();
        };
    }
}