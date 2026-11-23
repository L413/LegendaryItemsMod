package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class LegendaryListener implements Listener {
    private final LegendaryPlugin plugin;

    public LegendaryListener(LegendaryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removeBossBar(event.getPlayer());
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        String id = plugin.getLegendaryId(event.getInventory().getResult());
        if (id != null && plugin.isCrafted(id)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onAutoCrafter(CrafterCraftEvent event) {
        String id = plugin.getLegendaryId(event.getResult());
        if (id != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        String id = plugin.getLegendaryId(result);
        if (id == null) return;

        if (plugin.isCrafted(id)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(Component.text("This legendary item has already been claimed!", NamedTextColor.RED));
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(Component.text("Click normally (no shift-click) to forge a Legendary item.", NamedTextColor.YELLOW));
            return;
        }

        plugin.markCrafted(id, true);
        LegendaryItem def = plugin.getRegistry().getById(id);
        String itemName = def != null ? def.displayName() : id;

        Bukkit.broadcast(Component.text("⚡ ", NamedTextColor.GOLD)
                .append(Component.text(event.getWhoClicked().getName(), NamedTextColor.YELLOW))
                .append(Component.text(" has forged the ONLY ", NamedTextColor.GOLD))
                .append(Component.text(itemName, NamedTextColor.RED))
                .append(Component.text(" on the server!", NamedTextColor.GOLD)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.0f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        if (plugin.getLegendaryId(itemEntity.getItemStack()) != null) {
            itemEntity.setGlowing(true);
            itemEntity.setUnlimitedLifetime(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStoreAttempt(InventoryClickEvent event) {
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.ENDER_CHEST && topType != InventoryType.SHULKER_BOX) return;

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (plugin.getLegendaryId(clicked) != null || plugin.getLegendaryId(cursor) != null) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(Component.text("Legendary items cannot be stored in Ender Chests or Shulker Boxes!", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item itemEntity) {
            if (plugin.getLegendaryId(itemEntity.getItemStack()) != null) {
                event.setCancelled(true);
                if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                    Location safeLoc = itemEntity.getLocation().clone();
                    safeLoc.setY(100);
                    itemEntity.teleport(safeLoc);
                    itemEntity.setVelocity(itemEntity.getVelocity().zero());
                }
            }
        }
    }
}