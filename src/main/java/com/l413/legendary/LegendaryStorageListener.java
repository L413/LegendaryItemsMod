package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LegendaryStorageListener implements Listener {
    private static final Set<InventoryType> STORAGE_TYPES = Set.of(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.SHULKER_BOX,
            InventoryType.ENDER_CHEST,
            InventoryType.HOPPER,
            InventoryType.DROPPER,
            InventoryType.DISPENSER,
            InventoryType.DECORATED_POT,
            InventoryType.CHISELED_BOOKSHELF,
            InventoryType.LECTERN
    );
    private static final Component STORAGE_WARNING = Component.text(
            "Legendary items cannot be stored! The container burst into flames!",
            NamedTextColor.RED,
            TextDecoration.BOLD
    );

    private final LegendaryPlugin plugin;
    private final Map<UUID, Location> lastContainerLocations = new HashMap<>();

    public LegendaryStorageListener(LegendaryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && isStorageBlock(event.getClickedBlock())) {
            lastContainerLocations.put(event.getPlayer().getUniqueId(), event.getClickedBlock().getLocation());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastContainerLocations.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isStorage(topInventory) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean storesLegendary = false;
        int rawSlot = event.getRawSlot();
        if (event.isShiftClick()
                && event.getClickedInventory() == event.getView().getBottomInventory()
                && isLegendary(event.getCurrentItem())) {
            storesLegendary = true;
        } else if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
            if (isLegendary(event.getCursor())) {
                storesLegendary = true;
            } else if (event.getClick() == ClickType.NUMBER_KEY
                    && isLegendary(player.getInventory().getItem(event.getHotbarButton()))) {
                storesLegendary = true;
            } else if (event.getClick() == ClickType.SWAP_OFFHAND
                    && isLegendary(player.getInventory().getItemInOffHand())) {
                storesLegendary = true;
            }
        }

        if (storesLegendary) {
            event.setCancelled(true);
            burnContainer(player, topInventory);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isStorage(topInventory)
                || !(event.getWhoClicked() instanceof Player player)
                || !isLegendary(event.getOldCursor())) {
            return;
        }

        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topInventory.getSize())) {
            event.setCancelled(true);
            burnContainer(player, topInventory);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!isLegendary(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        Inventory initiator = event.getInitiator();
        Location location = initiator == null ? null : initiator.getLocation();
        if (location == null) {
            location = event.getSource().getLocation();
        }
        if (location == null) {
            location = event.getDestination().getLocation();
        }
        igniteBlockAbove(location);
    }

    private boolean isStorage(Inventory inventory) {
        return STORAGE_TYPES.contains(inventory.getType());
    }

    private boolean isLegendary(ItemStack stack) {
        return plugin.getLegendaryId(stack) != null;
    }

    private boolean isStorageBlock(Block block) {
        Material type = block.getType();
        return type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL
                || type == Material.ENDER_CHEST
                || type == Material.HOPPER
                || type == Material.DROPPER
                || type == Material.DISPENSER
                || type == Material.DECORATED_POT
                || type == Material.CHISELED_BOOKSHELF
                || type == Material.LECTERN
                || type.name().endsWith("SHULKER_BOX");
    }

    private void burnContainer(Player player, Inventory inventory) {
        Location location = inventory.getLocation();
        if (location == null) {
            location = lastContainerLocations.remove(player.getUniqueId());
        } else {
            lastContainerLocations.remove(player.getUniqueId());
        }
        player.closeInventory();
        if (location == null) {
            plugin.getLogger().warning("Could not locate a storage container to ignite.");
            player.sendMessage(STORAGE_WARNING);
            player.sendActionBar(STORAGE_WARNING);
            return;
        }

        Block block = location.getBlock();
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            dropContainerContents(inventory, location);
            block.setType(Material.FIRE);
            playBurnEffects(location);
        } else {
            igniteBlockAbove(location);
        }

        player.sendMessage(STORAGE_WARNING);
        player.sendActionBar(STORAGE_WARNING);
    }

    private void dropContainerContents(Inventory inventory, Location location) {
        List<ItemStack> contents = java.util.Arrays.stream(inventory.getContents())
                .filter(stack -> stack != null && !stack.getType().isAir())
                .map(ItemStack::clone)
                .toList();
        inventory.clear();
        for (ItemStack stack : contents) {
            location.getWorld().dropItemNaturally(location, stack);
        }
    }

    private void igniteBlockAbove(Location location) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Could not locate a storage source block to ignite.");
            return;
        }

        Location centered = location.getBlock().getLocation().add(0.5, 0.5, 0.5);
        Block above = location.getBlock().getRelative(0, 1, 0);
        if (above.getType().isAir()) {
            above.setType(Material.FIRE);
        }
        playBurnEffects(centered);
    }

    private void playBurnEffects(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Location center = location.getBlock().getLocation().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.FLAME, center, 24, 0.35, 0.35, 0.35, 0.03);
        world.spawnParticle(Particle.LAVA, center, 8, 0.3, 0.3, 0.3, 0.0);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
    }
}
