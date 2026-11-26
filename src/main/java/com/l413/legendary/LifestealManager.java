package com.l413.legendary;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LifestealManager implements Listener {
    private static final String CONFIG_ROOT = "lifesteal.";
    private static final String MENU_TITLE = "Lifesteal Settings";
    private static final int HEART_UNITS_VERSION = 2;
    private static final int DEFAULT_STARTING_HEARTS = 20;
    private static final int MAX_CONFIGURED_HEARTS = 80;
    private final LegendaryPlugin plugin;
    private final NamespacedKey heartKey;
    private final Map<UUID, Inventory> openMenus = new HashMap<>();

    public LifestealManager(LegendaryPlugin plugin) {
        this.plugin = plugin;
        this.heartKey = new NamespacedKey(plugin, "life_heart");
        plugin.getConfig().addDefault(CONFIG_ROOT + "min-hearts", 0);
        plugin.getConfig().addDefault(CONFIG_ROOT + "max-hearts", 40);
        plugin.getConfig().addDefault(CONFIG_ROOT + "starting-hearts", DEFAULT_STARTING_HEARTS);
        plugin.getConfig().addDefault(CONFIG_ROOT + "extract-enabled", false);
        plugin.getConfig().addDefault(CONFIG_ROOT + "zero-hearts-action", "ban");
        plugin.getConfig().addDefault(CONFIG_ROOT + "zero-hearts-ban-seconds", 86400);
        plugin.getConfig().addDefault(CONFIG_ROOT + "players", null);
        migrateHeartCountsToHalfHearts();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public void registerHeartRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "craft_life_heart");
        Bukkit.removeRecipe(recipeKey);
        LegendaryRegistry.registerConfiguredRecipe(
                plugin,
                recipeKey,
                createHeartItem(),
                "heart_of_life",
                recipe -> {
                    recipe.shape("ESE", "DHD", "ESE");
                    recipe.setIngredient('E', Material.ECHO_SHARD);
                    recipe.setIngredient('S', Material.NETHER_STAR);
                    recipe.setIngredient('D', Material.DIAMOND_BLOCK);
                    recipe.setIngredient('H', Material.HEART_OF_THE_SEA);
                }
        );
    }

    public ItemStack createHeartItem() {
        ItemStack heart = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = heart.getItemMeta();
        meta.displayName(Component.text("Heart of Life", NamedTextColor.RED));
        meta.lore(java.util.List.of(
                Component.text("Right-click to gain one permanent half-heart.", NamedTextColor.GRAY),
                Component.text("Place in an anvil with damaged legendary gear to restore "
                        + Math.round(plugin.getConfig().getDouble("item-stats.heart-repair-fraction", 0.33) * 100.0)
                        + "% durability.", NamedTextColor.GOLD),
                Component.text("Crafted with two Nether Stars, four Echo Shards, two Diamond Blocks, and a Heart of the Sea.", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(heartKey, PersistentDataType.BYTE, (byte) 1);
        heart.setItemMeta(meta);
        heart.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        heart.setData(DataComponentTypes.ITEM_MODEL, NamespacedKey.fromString("legendary:life_heart"));
        return heart;
    }

    public boolean isHeart(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(heartKey, PersistentDataType.BYTE);
    }

    public void giveHeart(Player player) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(createHeartItem());
        overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        player.sendMessage(Component.text("You received a Heart of Life.", NamedTextColor.GREEN));
    }

    public boolean extractHearts(Player player, int amount) {
        if (!plugin.getConfig().getBoolean(CONFIG_ROOT + "extract-enabled", false)) {
            player.sendMessage(Component.text("Heart extraction is currently disabled.", NamedTextColor.RED));
            return false;
        }
        if (amount <= 0) {
            player.sendMessage(Component.text("Enter a positive number of half-hearts to extract.", NamedTextColor.RED));
            return false;
        }

        int hearts = getHeartCount(player);
        int minimum = Math.max(1, getMinHearts());
        if (amount > hearts - minimum) {
            player.sendMessage(Component.text(
                    "You must keep at least " + minimum + " half-heart(s). You can extract at most "
                            + Math.max(0, hearts - minimum) + ".",
                    NamedTextColor.RED
            ));
            return false;
        }

        setHeartCount(player, hearts - amount);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(
                java.util.stream.IntStream.range(0, amount)
                        .mapToObj(ignored -> createHeartItem())
                        .toArray(ItemStack[]::new)
        );
        overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        player.sendMessage(Component.text(
                "Extracted " + amount + " half-heart(s) into " + amount
                        + " Heart(s) of Life. You now have " + (hearts - amount) + " half-heart(s).",
                NamedTextColor.GREEN
        ));
        return true;
    }

    public void setHeartCount(OfflinePlayer target, int hearts) {
        int bounded = Math.max(getMinHearts(), Math.min(getMaxHearts(), hearts));
        plugin.getConfig().set(CONFIG_ROOT + "players." + target.getUniqueId() + ".hearts", bounded);
        plugin.saveConfig();
        if (target.getPlayer() != null) {
            applyHeartCount(target.getPlayer(), bounded);
        }
    }

    public void resetHeartCount(OfflinePlayer target) {
        plugin.getConfig().set(CONFIG_ROOT + "players." + target.getUniqueId() + ".hearts", null);
        plugin.saveConfig();
        if (target.getPlayer() != null) {
            applyHeartCount(target.getPlayer(), getStartingHearts());
        }
    }

    public void openSettings(Player player) {
        if (!player.isOp()) {
            player.sendMessage(Component.text("Only operators can edit Lifesteal settings.", NamedTextColor.RED));
            return;
        }
        Inventory menu = Bukkit.createInventory(null, 27, Component.text(MENU_TITLE));
        openMenus.put(player.getUniqueId(), menu);
        updateMenu(menu);
        player.openInventory(menu);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sanitizeInventory(event.getPlayer().getInventory());
        sanitizeInventory(event.getPlayer().getEnderChest());
        applyHeartCount(event.getPlayer(), getHeartCount(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClickSanitize(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || openMenus.get(player.getUniqueId()) == event.getView().getTopInventory()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            sanitizeInventory(player.getInventory());
            sanitizeInventory(player.getEnderChest());
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        int hearts = getHeartCount(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                applyHeartCount(event.getPlayer(), hearts);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeartUse(PlayerInteractEvent event) {
        if (event.getHand() == null
                || (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (useHeart(event.getPlayer(), event.getHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeartUseOnEntity(PlayerInteractEntityEvent event) {
        if (useHeart(event.getPlayer(), event.getHand())) {
            event.setCancelled(true);
        }
    }

    private boolean useHeart(Player player, org.bukkit.inventory.EquipmentSlot hand) {
        ItemStack held = hand == org.bukkit.inventory.EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (!isHeart(held)) {
            return false;
        }
        int hearts = getHeartCount(player);
        if (hearts >= getMaxHearts()) {
            player.sendActionBar(Component.text("You are already at the maximum heart limit.", NamedTextColor.YELLOW));
            return true;
        }

        setHeartCount(player, hearts + 1);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(Math.min(maxHealth.getValue(), player.getHealth() + 1.0));
        }
        consumeHeldHeart(player, hand);
        playHeartUseEffects(player);
        player.sendMessage(Component.text("You gained half a heart. (" + (hearts + 1) + "/" + getMaxHearts() + " half-hearts)", NamedTextColor.RED));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            int previousHearts = getHeartCount(victim);
            int newHearts = Math.max(getMinHearts(), previousHearts - 1);
            boolean lostHeart = newHearts < previousHearts;
            boolean reachedZero = previousHearts > getMinHearts() && newHearts == 0;
            if (lostHeart) {
                setHeartCount(victim, newHearts);
                Bukkit.broadcast(Component.text(victim.getName() + " lost half a heart. (" + newHearts + " half-hearts remaining)", NamedTextColor.RED));
                giveHeart(killer);
            }
            if (reachedZero) {
                punishAtZeroHearts(victim);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack input = anvil.getItem(0);
        ItemStack ingredient = anvil.getItem(1);
        String legendaryId = plugin.getLegendaryId(input);

        if (legendaryId != null && isHeart(ingredient)) {
            ItemStack repaired = input.clone();
            sanitizeLegendaryItem(repaired);
            int maxDamage = repaired.getDataOrDefault(DataComponentTypes.MAX_DAMAGE, 0);
            int currentDamage = repaired.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
            if (maxDamage <= 0 || currentDamage <= 0) {
                event.setResult(null);
                event.getView().setRepairCost(0);
                return;
            }
            double repairFraction = plugin.getConfig().getDouble("item-stats.heart-repair-fraction", 0.33);
            int repairAmount = (int) Math.ceil(maxDamage * Math.max(0.0, Math.min(1.0, repairFraction)));
            repaired.setData(DataComponentTypes.DAMAGE, Math.max(0, currentDamage - repairAmount));
            event.setResult(repaired);
            event.getView().setRepairCost(1);
            return;
        }

        if (legendaryId != null || plugin.getLegendaryId(ingredient) != null) {
            event.setResult(null);
            event.getView().setRepairCost(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilRepairTake(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL
                || event.getRawSlot() != 2
                || !(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory() instanceof AnvilInventory anvil)) {
            return;
        }

        ItemStack input = anvil.getItem(0);
        ItemStack ingredient = anvil.getItem(1);
        ItemStack output = event.getCurrentItem();
        if (plugin.getLegendaryId(input) == null || !isHeart(ingredient) || output == null || output.getType().isAir()) {
            return;
        }
        event.setCancelled(true);

        ItemStack repaired = output.clone();
        if (event.getClick() == ClickType.LEFT && event.getCursor().getType().isAir()) {
            event.setCursor(repaired);
        } else {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(repaired);
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }

        anvil.setItem(0, null);
        consumeAnvilHeart(anvil, ingredient);
        anvil.setItem(2, null);
        if (event.getView() instanceof org.bukkit.inventory.view.AnvilView view) {
            view.setRepairCost(0);
        }
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (plugin.getLegendaryId(event.getInventory().getItem(0)) != null
                || plugin.getLegendaryId(event.getInventory().getItem(1)) != null) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (plugin.getLegendaryId(ingredient) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLegendaryPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        ItemStack pickedUp = event.getItem().getItemStack();
        sanitizeLegendaryItems(new ItemStack[]{pickedUp});
        event.getItem().setItemStack(pickedUp);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (plugin.getLegendaryId(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (plugin.getLegendaryId(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSettingsMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || openMenus.get(player.getUniqueId()) != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);
        if (!player.isOp()) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        switch (slot) {
            case 10 -> setMinHearts(Math.max(0, getMinHearts() - 1));
            case 12 -> setMinHearts(Math.min(getMaxHearts(), getMinHearts() + 1));
            case 14 -> setMaxHearts(Math.max(getMinHearts(), getMaxHearts() - 1));
            case 16 -> setMaxHearts(Math.min(MAX_CONFIGURED_HEARTS, getMaxHearts() + 1));
            case 20 -> cycleZeroHeartsAction();
            case 22 -> setBanSeconds(Math.max(60, getBanSeconds() - 3600));
            case 24 -> setBanSeconds(Math.min(2_592_000, getBanSeconds() + 3600));
            default -> {
                return;
            }
        }
        updateMenu(event.getView().getTopInventory());
    }

    @EventHandler
    public void onSettingsMenuClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && openMenus.get(player.getUniqueId()) == event.getInventory()) {
            openMenus.remove(player.getUniqueId());
        }
    }

    private void updateMenu(Inventory menu) {
        menu.clear();
        menu.setItem(10, menuItem(Material.RED_DYE, "Minimum half-hearts: " + getMinHearts(), "Click to decrease"));
        menu.setItem(11, menuItem(Material.PAPER, "Minimum: " + getMinHearts() + " half-hearts", "Loss occurs on player kills only"));
        menu.setItem(12, menuItem(Material.LIME_DYE, "Increase minimum", "Click to increase"));
        menu.setItem(14, menuItem(Material.RED_DYE, "Decrease maximum", "Click to decrease"));
        menu.setItem(15, menuItem(Material.PAPER, "Maximum: " + getMaxHearts() + " half-hearts", "Default is double normal health"));
        menu.setItem(16, menuItem(Material.LIME_DYE, "Increase maximum", "Click to increase"));
        menu.setItem(20, menuItem(Material.BARRIER, "Zero hearts: " + getZeroHeartsAction(), "Click to cycle ban, kick, none"));
        menu.setItem(22, menuItem(Material.CLOCK, "Shorter ban", "Decrease by one hour"));
        menu.setItem(23, menuItem(Material.PAPER, "Ban duration: " + formatDuration(getBanSeconds()), "Used when zero-heart action is ban"));
        menu.setItem(24, menuItem(Material.CLOCK, "Longer ban", "Increase by one hour"));
    }

    private ItemStack menuItem(Material material, String name, String detail) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        meta.lore(java.util.List.of(Component.text(detail, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private void setMinHearts(int hearts) {
        plugin.getConfig().set(CONFIG_ROOT + "min-hearts", hearts);
        if (hearts > plugin.getConfig().getInt(CONFIG_ROOT + "max-hearts", 40)) {
            plugin.getConfig().set(CONFIG_ROOT + "max-hearts", hearts);
        }
        saveSettingsAndClampPlayers();
    }

    private void setMaxHearts(int hearts) {
        hearts = Math.max(1, Math.min(MAX_CONFIGURED_HEARTS, hearts));
        plugin.getConfig().set(CONFIG_ROOT + "max-hearts", hearts);
        if (hearts < getMinHearts()) {
            plugin.getConfig().set(CONFIG_ROOT + "min-hearts", hearts);
        }
        saveSettingsAndClampPlayers();
    }

    private void setBanSeconds(int seconds) {
        plugin.getConfig().set(CONFIG_ROOT + "zero-hearts-ban-seconds", seconds);
        plugin.saveConfig();
    }

    private void cycleZeroHeartsAction() {
        String action = getZeroHeartsAction();
        String next = switch (action) {
            case "ban" -> "kick";
            case "kick" -> "none";
            default -> "ban";
        };
        plugin.getConfig().set(CONFIG_ROOT + "zero-hearts-action", next);
        plugin.saveConfig();
    }

    private void saveSettingsAndClampPlayers() {
        plugin.saveConfig();
        for (Player online : Bukkit.getOnlinePlayers()) {
            int hearts = getHeartCount(online);
            setHeartCount(online, hearts);
        }
    }

    private void punishAtZeroHearts(Player player) {
        String action = getZeroHeartsAction();
        if ("ban".equals(action)) {
            Date expires = new Date(System.currentTimeMillis() + getBanSeconds() * 1000L);
            Bukkit.getBanList(BanList.Type.NAME).addBan(
                    player.getName(),
                    "You lost all Lifesteal hearts.",
                    expires,
                    "Legends and Lifesteal"
            );
        }
        if ("ban".equals(action) || "kick".equals(action)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.kick(Component.text("You reached zero Lifesteal hearts.", NamedTextColor.RED));
                }
            });
        }
    }

    private int getHeartCount(Player player) {
        String path = CONFIG_ROOT + "players." + player.getUniqueId() + ".hearts";
        int hearts = plugin.getConfig().getInt(path, getStartingHearts());
        return Math.max(getMinHearts(), Math.min(getMaxHearts(), hearts));
    }

    private int getStartingHearts() {
        return Math.max(getMinHearts(), Math.min(
                getMaxHearts(),
                plugin.getConfig().getInt(CONFIG_ROOT + "starting-hearts", DEFAULT_STARTING_HEARTS)
        ));
    }

    private void setHeartCount(Player player, int hearts) {
        int bounded = Math.max(getMinHearts(), Math.min(getMaxHearts(), hearts));
        plugin.getConfig().set(CONFIG_ROOT + "players." + player.getUniqueId() + ".hearts", bounded);
        plugin.saveConfig();
        applyHeartCount(player, bounded);
    }

    private void migrateHeartCountsToHalfHearts() {
        if (plugin.getConfig().getInt(CONFIG_ROOT + "heart-units-version", 1) >= HEART_UNITS_VERSION) {
            return;
        }
        for (String key : new String[]{"min-hearts", "max-hearts", "starting-hearts"}) {
            String path = CONFIG_ROOT + key;
            if (plugin.getConfig().contains(path, true)) {
                plugin.getConfig().set(path, Math.min(MAX_CONFIGURED_HEARTS, plugin.getConfig().getInt(path) * 2));
            }
        }
        var players = plugin.getConfig().getConfigurationSection(CONFIG_ROOT + "players");
        if (players != null) {
            for (String playerId : players.getKeys(false)) {
                String path = CONFIG_ROOT + "players." + playerId + ".hearts";
                if (plugin.getConfig().contains(path, true)) {
                    plugin.getConfig().set(path, Math.min(MAX_CONFIGURED_HEARTS, plugin.getConfig().getInt(path) * 2));
                }
            }
        }
        plugin.getConfig().set(CONFIG_ROOT + "heart-units-version", HEART_UNITS_VERSION);
    }

    private void applyHeartCount(Player player, int hearts) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.setBaseValue(Math.max(1.0, hearts));
        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private void playHeartUseEffects(Player player) {
        var world = player.getWorld();
        var center = player.getLocation().add(0.0, 1.0, 0.0);
        world.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EAT, 1.0f, 0.85f);
        world.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.25f);
        world.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
        world.spawnParticle(Particle.HEART, center, 18, 0.65, 0.8, 0.65, 0.0);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 32, 0.5, 0.7, 0.5, 0.08);
        world.spawnParticle(
                Particle.DUST,
                center,
                24,
                0.45,
                0.65,
                0.45,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFD700), 1.2f)
        );
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("HEART INFUSED", NamedTextColor.RED),
                Component.text("+ half a heart", NamedTextColor.GOLD)
        ));
    }

    private int getMinHearts() {
        return Math.max(0, Math.min(
                MAX_CONFIGURED_HEARTS,
                plugin.getConfig().getInt(CONFIG_ROOT + "min-hearts", 0)
        ));
    }

    private int getMaxHearts() {
        return Math.max(1, Math.max(getMinHearts(), Math.min(
                MAX_CONFIGURED_HEARTS,
                plugin.getConfig().getInt(CONFIG_ROOT + "max-hearts", 40)
        )));
    }

    private String getZeroHeartsAction() {
        String action = plugin.getConfig().getString(CONFIG_ROOT + "zero-hearts-action", "ban");
        return "ban".equals(action) || "kick".equals(action) ? action : "none";
    }

    private int getBanSeconds() {
        return Math.max(60, Math.min(
                2_592_000,
                plugin.getConfig().getInt(CONFIG_ROOT + "zero-hearts-ban-seconds", 86400)
        ));
    }

    private String formatDuration(int seconds) {
        long hours = seconds / 3600L;
        return hours > 0 ? hours + " hour(s)" : (seconds / 60) + " minute(s)";
    }

    private void consumeHeldHeart(Player player, org.bukkit.inventory.EquipmentSlot hand) {
        ItemStack stack = hand == org.bukkit.inventory.EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        decrementHeart(stack);
        if (hand == org.bukkit.inventory.EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(stack.getAmount() <= 0 ? null : stack);
        } else {
            player.getInventory().setItemInOffHand(stack.getAmount() <= 0 ? null : stack);
        }
    }

    private void consumeAnvilHeart(AnvilInventory anvil, ItemStack stack) {
        decrementHeart(stack);
        anvil.setItem(1, stack.getAmount() <= 0 ? null : stack);
    }

    private void decrementHeart(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }

    private void sanitizeLegendaryItems(ItemStack[] items) {
        for (ItemStack item : items) {
            sanitizeLegendaryItem(item);
        }
    }

    private void sanitizeInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (sanitizeLegendaryItem(item)) {
                inventory.setItem(slot, item);
            }
        }
    }

    private boolean sanitizeLegendaryItem(ItemStack item) {
        String legendaryId = plugin.getLegendaryId(item);
        if (legendaryId == null || !item.hasItemMeta()) {
            return false;
        }
        LegendaryItem definition = plugin.getRegistry().getById(legendaryId);
        if (definition == null) {
            return false;
        }
        definition.refreshConfigurableData(item, plugin);
        return true;
    }
}
