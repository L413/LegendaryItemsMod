package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LegendaryStorageListener implements Listener {
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
    private static final Component EJECT_MESSAGE = Component.text(
            "Legendary items cannot be stored; the item was ejected.",
            NamedTextColor.RED
    );

    private final LegendaryPlugin plugin;
    private final Map<UUID, Location> lastContainerLocations = new HashMap<>();

    public LegendaryStorageListener(LegendaryPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::ejectLegendaryItemFrames, 20L, 20L);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isStorage(topInventory) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Location ejectLocation = getStorageLocation(player, topInventory);
        ItemStack current = event.getCurrentItem();
        int rawSlot = event.getRawSlot();

        if (event.isShiftClick()
                && event.getClickedInventory() == event.getView().getBottomInventory()
                && isLegendary(current)) {
            event.setCancelled(true);
            int playerSlot = event.getSlot();
            ejectPlayerSlotNextTick(player, playerSlot, current, ejectLocation);
            notifyEjected(player);
            return;
        }

        if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (isLegendary(cursor)) {
            event.setCancelled(true);
            ejectCursorNextTick(player, cursor, ejectLocation);
            notifyEjected(player);
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            if (isLegendary(hotbarItem)) {
                event.setCancelled(true);
                ejectPlayerSlotNextTick(player, hotbarSlot, hotbarItem, ejectLocation);
                notifyEjected(player);
            }
        } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (isLegendary(offhand)) {
                event.setCancelled(true);
                ejectOffhandNextTick(player, offhand, ejectLocation);
                notifyEjected(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isStorage(topInventory)
                || !(event.getWhoClicked() instanceof Player player)
                || !isLegendary(event.getOldCursor())
                || event.getRawSlots().stream().noneMatch(slot -> slot >= 0 && slot < topInventory.getSize())) {
            return;
        }

        event.setCancelled(true);
        ejectCursorNextTick(player, event.getOldCursor(), getStorageLocation(player, topInventory));
        notifyEjected(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!isLegendary(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Inventory source = event.getSource();
        Location location = event.getDestination().getLocation();
        if (location == null) {
            location = source.getLocation();
        }
        if (location == null) {
            plugin.getLogger().warning("Could not eject a legendary item from an inventory without a world location.");
            return;
        }
        Location ejectLocation = location.clone();
        ItemStack moving = event.getItem().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Map<Integer, ItemStack> leftovers = source.removeItem(moving);
            int left = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int removed = moving.getAmount() - left;
            if (removed > 0) {
                ItemStack ejected = moving.clone();
                ejected.setAmount(removed);
                drop(ejectLocation, ejected);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        ItemStack framed = frame.getItem();
        if (isLegendary(framed)) {
            event.setCancelled(true);
            frame.setItem(new ItemStack(Material.AIR));
            drop(frame.getLocation(), framed);
            notifyEjected(event.getPlayer());
            return;
        }

        ItemStack held = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (isLegendary(held)) {
            event.setCancelled(true);
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                ejectPlayerSlotNextTick(event.getPlayer(), event.getPlayer().getInventory().getHeldItemSlot(), held, frame.getLocation());
            } else {
                ejectOffhandNextTick(event.getPlayer(), held, frame.getLocation());
            }
            notifyEjected(event.getPlayer());
        }
    }

    private void ejectLegendaryItemFrames() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                ItemStack item = frame.getItem();
                if (isLegendary(item)) {
                    frame.setItem(new ItemStack(Material.AIR));
                    drop(frame.getLocation(), item);
                }
            }
        }
    }

    private boolean isStorage(Inventory inventory) {
        return STORAGE_TYPES.contains(inventory.getType()) || inventory.getHolder() instanceof Container;
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

    private Location getStorageLocation(Player player, Inventory inventory) {
        Location location = inventory.getLocation();
        if (location == null) {
            location = lastContainerLocations.remove(player.getUniqueId());
        } else {
            lastContainerLocations.remove(player.getUniqueId());
        }
        return location == null ? player.getLocation() : location;
    }

    private void ejectPlayerSlotNextTick(Player player, int slot, ItemStack expected, Location location) {
        String id = plugin.getLegendaryId(expected);
        ItemStack copy = expected.clone();
        Location destination = location.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack present = player.getInventory().getItem(slot);
            if (id.equals(plugin.getLegendaryId(present))) {
                player.getInventory().setItem(slot, null);
                drop(destination, copy);
            }
        });
    }

    private void ejectOffhandNextTick(Player player, ItemStack expected, Location location) {
        String id = plugin.getLegendaryId(expected);
        ItemStack copy = expected.clone();
        Location destination = location.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack present = player.getInventory().getItemInOffHand();
            if (id.equals(plugin.getLegendaryId(present))) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                drop(destination, copy);
            }
        });
    }

    private void ejectCursorNextTick(Player player, ItemStack expected, Location location) {
        String id = plugin.getLegendaryId(expected);
        ItemStack copy = expected.clone();
        Location destination = location.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack cursor = player.getOpenInventory().getCursor();
            if (id.equals(plugin.getLegendaryId(cursor))) {
                player.getOpenInventory().setCursor(new ItemStack(Material.AIR));
                drop(destination, copy);
            }
        });
    }

    private void drop(Location location, ItemStack item) {
        if (location.getWorld() != null) {
            location.getWorld().dropItemNaturally(location.clone().add(0.5, 0.2, 0.5), item);
        }
    }

    private void notifyEjected(Player player) {
        player.sendMessage(EJECT_MESSAGE);
        player.sendActionBar(EJECT_MESSAGE);
    }
}
