package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            InventoryType.CRAFTER,
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
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ejectLegendaryItemFrames();
            ejectLegendaryArmorStandItems();
        }, 20L, 20L);
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sanitizePlayerInventory(player);
        sanitizeStorageInventory(player.getEnderChest(), player.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStorageOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (!isStorage(inventory)) {
            return;
        }
        List<ItemStack> ejected = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isLegendary(item)) {
                inventory.setItem(slot, null);
                ejected.add(item.clone());
            } else if (sanitizeNestedContents(item, ejected, 0)) {
                inventory.setItem(slot, item);
            }
        }
        if (!ejected.isEmpty()) {
            Location location = inventory.getLocation();
            if (location == null && event.getPlayer() instanceof Player player) {
                location = player.getLocation();
            }
            if (location != null) {
                Location ejectLocation = location.clone();
                ejected.forEach(item -> drop(ejectLocation, item));
            } else {
                plugin.getLogger().warning("Could not eject legendary items from an inventory without a world location.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShulkerPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (!(event.getBlockPlaced().getState() instanceof Container container)) {
            return;
        }
        Inventory inventory = container.getInventory();
        List<ItemStack> ejected = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isLegendary(item)) {
                inventory.setItem(slot, null);
                ejected.add(item.clone());
            } else if (sanitizeNestedContents(item, ejected, 0)) {
                inventory.setItem(slot, item);
            }
        }
        ejected.forEach(item -> drop(event.getBlockPlaced().getLocation(), item));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (isBundleOrShulker(current) || isBundleOrShulker(cursor)) {
            if (isLegendary(current) || isLegendary(cursor)) {
                event.setCancelled(true);
                notifyEjected(player);
                return;
            }
            List<ItemStack> ejected = new ArrayList<>();
            boolean changed = sanitizeNestedContents(current, ejected, 0)
                    | sanitizeNestedContents(cursor, ejected, 0);
            if (changed) {
                event.setCancelled(true);
                if (current != null) {
                    event.setCurrentItem(current);
                }
                event.setCursor(cursor);
                ejected.forEach(item -> drop(player.getLocation(), item));
                notifyEjected(player);
                return;
            }
        }
        if (!isStorage(topInventory)) {
            return;
        }
        Location ejectLocation = getStorageLocation(player, topInventory);
        int rawSlot = event.getRawSlot();

        if (event.isShiftClick()
                && event.getClickedInventory() == event.getView().getBottomInventory()
                && (isLegendary(current) || containsLegendary(current, 0))) {
            event.setCancelled(true);
            if (isLegendary(current)) {
                ejectPlayerSlotNextTick(player, event.getSlot(), current, ejectLocation);
            } else {
                List<ItemStack> ejected = new ArrayList<>();
                sanitizeNestedContents(current, ejected, 0);
                player.getInventory().setItem(event.getSlot(), current);
                ejected.forEach(item -> drop(ejectLocation, item));
            }
            notifyEjected(player);
            return;
        }

        if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
            return;
        }
        if (isLegendary(cursor)) {
            event.setCancelled(true);
            ejectCursorNextTick(player, cursor, ejectLocation);
            notifyEjected(player);
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            if (isLegendary(hotbarItem) || containsLegendary(hotbarItem, 0)) {
                event.setCancelled(true);
                if (isLegendary(hotbarItem)) {
                    ejectPlayerSlotNextTick(player, hotbarSlot, hotbarItem, ejectLocation);
                } else {
                    List<ItemStack> ejected = new ArrayList<>();
                    sanitizeNestedContents(hotbarItem, ejected, 0);
                    player.getInventory().setItem(hotbarSlot, hotbarItem);
                    ejected.forEach(item -> drop(ejectLocation, item));
                }
                notifyEjected(player);
            }
        } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (isLegendary(offhand) || containsLegendary(offhand, 0)) {
                event.setCancelled(true);
                if (isLegendary(offhand)) {
                    ejectOffhandNextTick(player, offhand, ejectLocation);
                } else {
                    List<ItemStack> ejected = new ArrayList<>();
                    sanitizeNestedContents(offhand, ejected, 0);
                    player.getInventory().setItemInOffHand(offhand);
                    ejected.forEach(item -> drop(ejectLocation, item));
                }
                notifyEjected(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isStorage(topInventory)
                || !(event.getWhoClicked() instanceof Player player)
                || (!isLegendary(event.getOldCursor()) && !containsLegendary(event.getOldCursor(), 0))
                || event.getRawSlots().stream().noneMatch(slot -> slot >= 0 && slot < topInventory.getSize())) {
            return;
        }

        event.setCancelled(true);
        if (isLegendary(event.getOldCursor())) {
            ejectCursorNextTick(player, event.getOldCursor(), getStorageLocation(player, topInventory));
        } else {
            ItemStack cursor = event.getOldCursor();
            List<ItemStack> ejected = new ArrayList<>();
            sanitizeNestedContents(cursor, ejected, 0);
            player.getOpenInventory().setCursor(cursor);
            Location location = getStorageLocation(player, topInventory);
            ejected.forEach(item -> drop(location, item));
        }
        notifyEjected(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!isLegendary(event.getItem()) && !containsLegendary(event.getItem(), 0)) {
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
        List<ItemStack> nestedLegendaries = new ArrayList<>();
        sanitizeNestedContents(moving, nestedLegendaries, 0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Map<Integer, ItemStack> leftovers = source.removeItem(moving);
            int left = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int removed = moving.getAmount() - left;
            if (removed > 0) {
                ItemStack ejected = moving.clone();
                ejected.setAmount(removed);
                drop(ejectLocation, ejected);
                nestedLegendaries.forEach(item -> drop(ejectLocation, item));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        ItemStack pickedUp = event.getItem().getItemStack();
        if (containsLegendary(pickedUp, 0) && !isLegendary(pickedUp)) {
            List<ItemStack> ejected = new ArrayList<>();
            sanitizeNestedContents(pickedUp, ejected, 0);
            event.getItem().setItemStack(pickedUp);
            ejected.forEach(item -> drop(event.getItem().getLocation(), item));
        }
        if (isLegendary(pickedUp) || containsLegendary(pickedUp, 0)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNestedContainerItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (isLegendary(item) || !containsLegendary(item, 0)) {
            return;
        }
        List<ItemStack> ejected = new ArrayList<>();
        sanitizeNestedContents(item, ejected, 0);
        event.getEntity().setItemStack(item);
        ejected.forEach(stack -> drop(event.getLocation(), stack));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNonPlayerPickup(EntityPickupItemEvent event) {
        ItemStack pickedUp = event.getItem().getItemStack();
        if (!isLegendary(pickedUp) && containsLegendary(pickedUp, 0)) {
            List<ItemStack> ejected = new ArrayList<>();
            sanitizeNestedContents(pickedUp, ejected, 0);
            event.getItem().setItemStack(pickedUp);
            ejected.forEach(item -> drop(event.getItem().getLocation(), item));
        }
        if ((isLegendary(pickedUp) || containsLegendary(pickedUp, 0))
                && !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player player && containsLegendary(pickedUp, 0)) {
            List<ItemStack> ejected = new ArrayList<>();
            sanitizeNestedContents(pickedUp, ejected, 0);
            event.getItem().setItemStack(pickedUp);
            ejected.forEach(item -> drop(player.getLocation(), item));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBundleInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!isBundleOrShulker(mainHand) && !isBundleOrShulker(offHand)) {
            return;
        }
        if (isLegendary(mainHand) || isLegendary(offHand)) {
            event.setCancelled(true);
            notifyEjected(player);
            return;
        }
        List<ItemStack> ejected = new ArrayList<>();
        boolean changed = sanitizeNestedContents(mainHand, ejected, 0)
                | sanitizeNestedContents(offHand, ejected, 0);
        if (changed) {
            player.getInventory().setItemInMainHand(mainHand);
            player.getInventory().setItemInOffHand(offHand);
            ejected.forEach(item -> drop(player.getLocation(), item));
            event.setCancelled(true);
            notifyEjected(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ItemStack standItem = event.getArmorStandItem();
        if (isLegendary(standItem)) {
            event.setCancelled(true);
            event.getRightClicked().setItem(event.getSlot(), new ItemStack(Material.AIR));
            drop(event.getRightClicked().getLocation(), standItem);
            notifyEjected(event.getPlayer());
            return;
        }
        if (containsLegendary(standItem, 0)) {
            event.setCancelled(true);
            List<ItemStack> ejected = new ArrayList<>();
            sanitizeNestedContents(standItem, ejected, 0);
            event.getRightClicked().setItem(event.getSlot(), standItem);
            ejected.forEach(item -> drop(event.getRightClicked().getLocation(), item));
            notifyEjected(event.getPlayer());
            return;
        }
        ItemStack playerItem = event.getPlayerItem();
        if (isLegendary(playerItem)) {
            event.setCancelled(true);
            notifyEjected(event.getPlayer());
        } else if (containsLegendary(playerItem, 0)) {
            event.setCancelled(true);
            List<ItemStack> ejected = new ArrayList<>();
            sanitizeNestedContents(playerItem, ejected, 0);
            if (event.getHand() == EquipmentSlot.HAND) {
                event.getPlayer().getInventory().setItemInMainHand(playerItem);
            } else {
                event.getPlayer().getInventory().setItemInOffHand(playerItem);
            }
            ejected.forEach(item -> drop(event.getPlayer().getLocation(), item));
            notifyEjected(event.getPlayer());
        }
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
        if (containsLegendary(framed, 0)) {
            event.setCancelled(true);
            List<ItemStack> ejected = new ArrayList<>();
            sanitizeNestedContents(framed, ejected, 0);
            frame.setItem(framed);
            ejected.forEach(item -> drop(frame.getLocation(), item));
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
        } else if (containsLegendary(held, 0)) {
            event.setCancelled(true);
            List<ItemStack> ejected = new ArrayList<>();
            sanitizeNestedContents(held, ejected, 0);
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                event.getPlayer().getInventory().setItemInMainHand(held);
            } else {
                event.getPlayer().getInventory().setItemInOffHand(held);
            }
            ejected.forEach(item -> drop(frame.getLocation(), item));
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
                } else if (containsLegendary(item, 0)) {
                    List<ItemStack> ejected = new ArrayList<>();
                    sanitizeNestedContents(item, ejected, 0);
                    frame.setItem(item);
                    ejected.forEach(stack -> drop(frame.getLocation(), stack));
                }
            }
        }
    }

    private void ejectLegendaryArmorStandItems() {
        EquipmentSlot[] slots = {
                EquipmentSlot.HAND,
                EquipmentSlot.OFF_HAND,
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD
        };
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                for (EquipmentSlot slot : slots) {
                    ItemStack item = stand.getItem(slot);
                    if (isLegendary(item)) {
                        stand.setItem(slot, new ItemStack(Material.AIR));
                        drop(stand.getLocation(), item);
                    } else if (containsLegendary(item, 0)) {
                        List<ItemStack> ejected = new ArrayList<>();
                        sanitizeNestedContents(item, ejected, 0);
                        stand.setItem(slot, item);
                        ejected.forEach(stack -> drop(stand.getLocation(), stack));
                    }
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

    private void sanitizePlayerInventory(Player player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> ejected = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (sanitizeNestedContents(item, ejected, 0)) {
                inventory.setItem(slot, item);
            }
        }
        ejected.forEach(item -> drop(player.getLocation(), item));
    }

    private void sanitizeStorageInventory(Inventory inventory, Location location) {
        List<ItemStack> ejected = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isLegendary(item)) {
                inventory.setItem(slot, null);
                ejected.add(item.clone());
            } else if (sanitizeNestedContents(item, ejected, 0)) {
                inventory.setItem(slot, item);
            }
        }
        ejected.forEach(item -> drop(location, item));
    }

    private boolean isBundleOrShulker(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BundleMeta) {
            return true;
        }
        return meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.hasBlockState()
                && blockStateMeta.getBlockState() instanceof ShulkerBox;
    }

    private boolean containsLegendary(ItemStack stack, int depth) {
        if (stack == null || stack.getType().isAir() || depth >= 8) {
            return false;
        }
        if (isLegendary(stack)) {
            return true;
        }
        if (!stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            return bundleMeta.getItems().stream().anyMatch(item -> containsLegendary(item, depth + 1));
        }
        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.hasBlockState()
                && blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
            for (ItemStack item : shulkerBox.getInventory().getContents()) {
                if (containsLegendary(item, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean sanitizeNestedContents(ItemStack container, List<ItemStack> ejected, int depth) {
        if (container == null || !container.hasItemMeta() || depth >= 8) {
            return false;
        }
        ItemMeta meta = container.getItemMeta();
        boolean changed = false;
        if (meta instanceof BundleMeta bundleMeta) {
            List<ItemStack> retained = new ArrayList<>();
            for (ItemStack item : bundleMeta.getItems()) {
                if (isLegendary(item)) {
                    ejected.add(item.clone());
                    changed = true;
                } else {
                    changed |= sanitizeNestedContents(item, ejected, depth + 1);
                    retained.add(item);
                }
            }
            if (changed) {
                bundleMeta.setItems(retained);
                container.setItemMeta(bundleMeta);
            }
        } else if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.hasBlockState()
                && blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
            Inventory inventory = shulkerBox.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (isLegendary(item)) {
                    ejected.add(item.clone());
                    inventory.setItem(slot, null);
                    changed = true;
                } else if (sanitizeNestedContents(item, ejected, depth + 1)) {
                    inventory.setItem(slot, item);
                    changed = true;
                }
            }
            if (changed) {
                blockStateMeta.setBlockState(shulkerBox);
                container.setItemMeta(blockStateMeta);
            }
        }
        return changed;
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
                || type == Material.CRAFTER
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
