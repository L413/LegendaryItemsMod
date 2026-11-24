package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LegendarySettingsMenu implements Listener {
    private static final String CONFIG_ROOT = "legendary.";
    private static final List<String> LOGOUT_BEHAVIORS = List.of("beacon", "drop", "inventory");

    private final LegendaryPlugin plugin;
    private final Map<UUID, Inventory> openMenus = new java.util.HashMap<>();

    public LegendarySettingsMenu(LegendaryPlugin plugin) {
        this.plugin = plugin;
        plugin.getConfig().addDefault(CONFIG_ROOT + "logout-behavior", "beacon");
        plugin.getConfig().addDefault(CONFIG_ROOT + "tracker-coordinate-rounding", 250);
        plugin.getConfig().addDefault(CONFIG_ROOT + "overload-rule", "armor_tool");
        plugin.saveConfig();
    }

    public void openSettings(Player player) {
        if (!player.isOp()) {
            player.sendMessage(Component.text("Only operators can change Legendary settings.", NamedTextColor.RED));
            return;
        }
        Inventory menu = Bukkit.createInventory(null, 27, Component.text("Legendary Settings", NamedTextColor.GOLD));
        updateMenu(menu);
        openMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || openMenus.get(player.getUniqueId()) != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);
        if (!player.isOp()) {
            player.closeInventory();
            return;
        }

        switch (event.getRawSlot()) {
            case 11 -> cycleLogoutBehavior();
            case 14 -> setCoordinateRounding(getCoordinateRounding() - 50);
            case 16 -> setCoordinateRounding(getCoordinateRounding() + 50);
            case 20 -> cycleOverloadRule();
            default -> {
                return;
            }
        }
        updateMenu(event.getView().getTopInventory());
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && openMenus.get(player.getUniqueId()) == event.getInventory()) {
            openMenus.remove(player.getUniqueId());
        }
    }

    private void updateMenu(Inventory menu) {
        menu.clear();
        menu.setItem(11, menuItem(
                Material.BEACON,
                "Logout behavior: " + getLogoutBehavior(),
                switch (getLogoutBehavior()) {
                    case "drop" -> "Drop legendary items at the logout location.";
                    case "inventory" -> "Keep items in inventory without an offline beacon.";
                    default -> "Keep items; leave a beacon at logout, without tracking them.";
                },
                "Click to cycle behavior"
        ));
        menu.setItem(14, menuItem(Material.RED_DYE, "Decrease coordinate rounding", "Reduce by 50 blocks"));
        menu.setItem(15, menuItem(Material.MAP, "Tracker rounding: " + getCoordinateRounding() + " blocks", "Approximate coordinates"));
        menu.setItem(16, menuItem(Material.LIME_DYE, "Increase coordinate rounding", "Increase by 50 blocks"));
        menu.setItem(20, menuItem(
                Material.IRON_CHESTPLATE,
                "Carry rule: " + (getOverloadRule().equals("armor_tool") ? "one armor + one tool" : "one overall"),
                getOverloadRule().equals("armor_tool")
                        ? "Allows one equipped legendary armor and one held legendary tool."
                        : "Allows one legendary item total.",
                "Click to switch rule"
        ));
    }

    private ItemStack menuItem(Material material, String name, String... details) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        meta.lore(java.util.Arrays.stream(details)
                .map(detail -> Component.text(detail, NamedTextColor.GRAY))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private String getLogoutBehavior() {
        String behavior = plugin.getConfig().getString(CONFIG_ROOT + "logout-behavior", "beacon");
        return LOGOUT_BEHAVIORS.contains(behavior) ? behavior : "beacon";
    }

    private void cycleLogoutBehavior() {
        int current = LOGOUT_BEHAVIORS.indexOf(getLogoutBehavior());
        plugin.getConfig().set(CONFIG_ROOT + "logout-behavior", LOGOUT_BEHAVIORS.get((current + 1) % LOGOUT_BEHAVIORS.size()));
        plugin.saveConfig();
    }

    private int getCoordinateRounding() {
        int configured = plugin.getConfig().getInt(CONFIG_ROOT + "tracker-coordinate-rounding", 250);
        return Math.max(50, Math.min(1000, configured));
    }

    private void setCoordinateRounding(int value) {
        plugin.getConfig().set(CONFIG_ROOT + "tracker-coordinate-rounding", Math.max(50, Math.min(1000, value)));
        plugin.saveConfig();
    }

    private String getOverloadRule() {
        return "one".equalsIgnoreCase(plugin.getConfig().getString(CONFIG_ROOT + "overload-rule", "armor_tool"))
                ? "one"
                : "armor_tool";
    }

    private void cycleOverloadRule() {
        plugin.getConfig().set(
                CONFIG_ROOT + "overload-rule",
                getOverloadRule().equals("armor_tool") ? "one" : "armor_tool"
        );
        plugin.saveConfig();
    }
}
