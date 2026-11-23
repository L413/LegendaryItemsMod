package com.l413.legendary;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.function.Consumer;

public record LegendaryItem(
        String id,
        String displayName,
        Material baseMaterial,
        String resourceModelKey,
        double attackDamage,
        double attackSpeed,
        List<Component> lore,
        Consumer<ShapedRecipe> recipeConfigurator
) {
    public ItemStack createItemStack(LegendaryPlugin plugin) {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(displayName, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(lore);
        meta.setUnbreakable(true);

        meta.getPersistentDataContainer().set(plugin.getItemKey(), PersistentDataType.STRING, id);

        NamespacedKey dmgKey = new NamespacedKey(plugin, id + "_damage");
        NamespacedKey spdKey = new NamespacedKey(plugin, id + "_speed");

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(dmgKey, attackDamage - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(spdKey, attackSpeed - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        item.setItemMeta(meta);

        if (resourceModelKey != null && !resourceModelKey.isEmpty()) {
            NamespacedKey modelKey = NamespacedKey.fromString(resourceModelKey);
            if (modelKey != null) {
                item.setData(DataComponentTypes.ITEM_MODEL, modelKey);
            }
        }

        return item;
    }
}