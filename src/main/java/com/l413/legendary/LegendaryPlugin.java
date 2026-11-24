package com.l413.legendary;

import com.destroystokyo.paper.ParticleBuilder;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class LegendaryPlugin extends JavaPlugin implements Listener {
    // Tune these two constants to adjust search area size and boss bar trigger range
    private static final int COORD_ROUND_STEP = 250;
    private static final double PROXIMITY_RADIUS = 250.0;

    private NamespacedKey itemKey;
    private NamespacedKey shurikenProjectileKey;
    private LegendaryRegistry registry;
    private LifestealManager lifestealManager;
    private LegendarySettingsMenu legendarySettingsMenu;
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private final Map<String, OfflineBeacon> offlineBeacons = new HashMap<>();
    private final Set<String> trackedGlowEntries = new HashSet<>();
    private final Map<String, Long> ownershipAnnouncementCooldowns = new HashMap<>();
    private final Set<String> configWarnings = new HashSet<>();

    private record TrackedTarget(String id, String displayName, Location location, UUID holderUuid, String statusLabel) {}
    private record OfflineBeacon(UUID ownerId, String worldName, double x, double y, double z) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergePackagedConfigDefaults();
        this.itemKey = new NamespacedKey(this, "legendary_id");
        this.shurikenProjectileKey = new NamespacedKey(this, "shuriken_projectile");
        this.registry = new LegendaryRegistry(this);
        this.lifestealManager = new LifestealManager(this);
        this.legendarySettingsMenu = new LegendarySettingsMenu(this);
        loadOfflineBeacons();
        this.lifestealManager.registerHeartRecipe();

        Bukkit.getPluginManager().registerEvents(new LegendaryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LegendaryStorageListener(this), this);
        Bukkit.getPluginManager().registerEvents(this.lifestealManager, this);
        Bukkit.getPluginManager().registerEvents(this.legendarySettingsMenu, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        registerCommands();
        refreshAllLoadedLegendaryItems();

        // Fast visual loop (every 10 ticks / 0.5s): sky beacons, glowing, and proximity boss bar
        Bukkit.getScheduler().runTaskTimer(this, this::updateBeaconsAndProximity, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, this::applyLegendaryOverloadPenalty, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::applyHeldAndArmorEffects, 20L, 20L);

        // Scoreboard refresh loop (every 40 ticks / 2s): updates rounded coordinates sidebar
        Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 20L, 40L);

        getLogger().info("Legends & Lifesteal enabled with " + registry.getAll().size() + " legendary item(s).");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeBossBar(player);
        }
        activeBossBars.clear();
    }

    @EventHandler
    public void onLegendaryOwnerQuit(PlayerQuitEvent event) {
        handleLegendaryLogout(event.getPlayer());
    }

    @EventHandler
    public void onLegendaryOwnerJoin(PlayerJoinEvent event) {
        refreshPlayerLegendaryItems(event.getPlayer());
        handleLegendaryLogin(event.getPlayer());
    }

    @EventHandler
    public void onLegendaryChunkLoad(ChunkLoadEvent event) {
        refreshLoadedChunk(event.getChunk());
    }

    private void registerCommands() {
        this.registerCommand("lifesteal", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (!source.getSender().isOp()) {
                    source.getSender().sendMessage(Component.text("You must be an operator to use this command.", NamedTextColor.RED));
                    return;
                }
                String subcommand = args.length == 0 ? "settings" : args[0].toLowerCase();
                switch (subcommand) {
                    case "settings" -> {
                        if (source.getSender() instanceof Player player) {
                            lifestealManager.openSettings(player);
                        } else {
                            source.getSender().sendMessage(Component.text("Only players can open the Lifesteal settings.", NamedTextColor.RED));
                        }
                    }
                    case "give", "giveheart" -> {
                        if (args.length != 1 || !(source.getSender() instanceof Player player)) {
                            source.getSender().sendMessage(Component.text("Usage: /lifesteal give", NamedTextColor.RED));
                            return;
                        }
                        lifestealManager.giveHeart(player);
                    }
                    case "set", "sethearts" -> setHeartsCommand(source, args);
                    default -> source.getSender().sendMessage(Component.text(
                            "Usage: /lifesteal <settings|give|set>",
                            NamedTextColor.YELLOW
                    ));
                }
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (args.length <= 1) {
                    return List.of("settings", "give", "set");
                }
                if (args.length == 2 && ("set".equalsIgnoreCase(args[0]) || "sethearts".equalsIgnoreCase(args[0]))) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                }
                return List.of();
            }
        });

        this.registerCommand("legendary", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (!source.getSender().isOp()) {
                    source.getSender().sendMessage(Component.text("You must be an operator to use this command.", NamedTextColor.RED));
                    return;
                }
                if (args.length == 0) {
                    source.getSender().sendMessage(Component.text(
                            "Usage: /legendary <give|destroyall|settings|reload>",
                            NamedTextColor.YELLOW
                    ));
                    return;
                }
                switch (args[0].toLowerCase()) {
                    case "settings" -> {
                        if (source.getSender() instanceof Player player) {
                            legendarySettingsMenu.openSettings(player);
                        } else {
                            source.getSender().sendMessage(Component.text("Only players can open Legendary settings.", NamedTextColor.RED));
                        }
                    }
                    case "give" -> giveLegendaryCommand(source, args);
                    case "destroyall" -> {
                        int destroyed = destroyAllLegendaries();
                        source.getSender().sendMessage(Component.text(
                                "Destroyed all legendary items and released " + destroyed + " item type(s).",
                                NamedTextColor.GREEN
                        ));
                    }
                    case "reload" -> {
                        reloadPluginConfiguration();
                        source.getSender().sendMessage(Component.text(
                                "Legends & Lifesteal configuration and recipes reloaded.",
                                NamedTextColor.GREEN
                        ));
                    }
                    default -> source.getSender().sendMessage(Component.text(
                            "Usage: /legendary <give|destroyall|settings|reload>",
                            NamedTextColor.YELLOW
                    ));
                }
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (args.length <= 1) {
                    return List.of("give", "destroyall", "settings", "reload");
                }
                if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
                    return registry.getAll().stream().map(LegendaryItem::id).toList();
                }
                return List.of();
            }
        });
    }

    private void mergePackagedConfigDefaults() {
        try (InputStream resource = Objects.requireNonNull(getResource("config.yml"), "Packaged config.yml is missing.")) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );
            getConfig().setDefaults(defaults);
            getConfig().options().copyDefaults(true);
            saveConfig();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load packaged config.yml defaults.", exception);
        }
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        configWarnings.clear();
        mergePackagedConfigDefaults();
        registry.reloadRecipes();
        lifestealManager.registerHeartRecipe();
        refreshAllLoadedLegendaryItems();
    }

    private void refreshAllLoadedLegendaryItems() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                refreshLoadedChunk(chunk);
            }
        }
    }

    private void refreshLoadedChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container container) {
                refreshInventory(container.getInventory());
            }
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player player) {
                refreshPlayerLegendaryItems(player);
            } else if (entity instanceof Item item) {
                ItemStack stack = item.getItemStack();
                refreshLegendaryStack(stack);
                item.setItemStack(stack);
            } else if (entity instanceof Trident trident) {
                ItemStack stack = trident.getItemStack();
                refreshLegendaryStack(stack);
                trident.setItemStack(stack);
            } else if (entity instanceof Snowball snowball
                    && snowball.getPersistentDataContainer().has(shurikenProjectileKey, PersistentDataType.BYTE)) {
                ItemStack stack = snowball.getItem();
                refreshLegendaryStack(stack);
                snowball.setItem(stack);
            } else if (entity instanceof ItemFrame itemFrame) {
                ItemStack stack = itemFrame.getItem();
                refreshLegendaryStack(stack);
                itemFrame.setItem(stack);
            } else if (entity instanceof ArmorStand armorStand && armorStand.getEquipment() != null) {
                ItemStack[] equipment = armorStand.getEquipment().getArmorContents();
                for (int slot = 0; slot < equipment.length; slot++) {
                    refreshLegendaryStack(equipment[slot]);
                }
                armorStand.getEquipment().setArmorContents(equipment);
                ItemStack hand = armorStand.getEquipment().getItemInMainHand();
                refreshLegendaryStack(hand);
                armorStand.getEquipment().setItemInMainHand(hand);
                ItemStack offhand = armorStand.getEquipment().getItemInOffHand();
                refreshLegendaryStack(offhand);
                armorStand.getEquipment().setItemInOffHand(offhand);
            } else if (entity instanceof InventoryHolder holder) {
                refreshInventory(holder.getInventory());
            }
        }
    }

    private void refreshPlayerLegendaryItems(Player player) {
        refreshInventory(player.getInventory());
        refreshInventory(player.getEnderChest());
    }

    private void refreshInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (getLegendaryId(stack) != null) {
                refreshLegendaryStack(stack);
                inventory.setItem(slot, stack);
            }
        }
    }

    private void refreshLegendaryStack(ItemStack stack) {
        String id = getLegendaryId(stack);
        LegendaryItem definition = id == null ? null : registry.getById(id);
        if (definition != null) {
            definition.refreshConfigurableData(stack, this);
        }
    }

    public void refreshLegendaryItemStack(ItemStack stack) {
        refreshLegendaryStack(stack);
    }

    public void applyConfiguredEffects(Player player, String path) {
        var effects = getConfig().getConfigurationSection(path);
        if (effects == null) {
            return;
        }
        for (String key : effects.getKeys(false)) {
            applyConfiguredEffect(player, path + "." + key);
        }
    }

    public void applyConfiguredEffect(Player player, String path) {
        var definition = getConfig().getConfigurationSection(path);
        if (definition == null) {
            warnConfigOnce(path, "Expected an effect configuration section.");
            return;
        }
        String typeName = definition.getString("type", "");
        NamespacedKey effectKey = NamespacedKey.minecraft(typeName.toLowerCase(java.util.Locale.ROOT));
        var type = Registry.POTION_EFFECT_TYPE.get(effectKey);
        int duration = definition.getInt("duration-ticks", 0);
        int amplifier = definition.getInt("amplifier", 0);
        if (type == null || duration <= 0 || amplifier < 0) {
            warnConfigOnce(path, "Invalid potion effect type, duration, or amplifier.");
            return;
        }
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }

    private void warnConfigOnce(String path, String message) {
        if (configWarnings.add(path)) {
            getLogger().warning("Invalid configuration at '" + path + "': " + message);
        }
    }

    private void setHeartsCommand(CommandSourceStack source, String[] args) {
        if (args.length != 3) {
            source.getSender().sendMessage(Component.text(
                    "Usage: /lifesteal set <player> <half-hearts|reset>",
                    NamedTextColor.RED
            ));
            return;
        }

        org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (target == null) {
            source.getSender().sendMessage(Component.text(
                    "Player not found. They must be online or have joined this server before.",
                    NamedTextColor.RED
            ));
            return;
        }

        if ("reset".equalsIgnoreCase(args[2])) {
            lifestealManager.resetHeartCount(target);
            source.getSender().sendMessage(Component.text(
                    "Reset " + args[1] + "'s heart count to the configured starting amount.",
                    NamedTextColor.GREEN
            ));
            return;
        }

        try {
            int halfHearts = Integer.parseInt(args[2]);
            if (halfHearts < 0) {
                throw new NumberFormatException();
            }
            lifestealManager.setHeartCount(target, halfHearts);
            source.getSender().sendMessage(Component.text(
                    "Set " + args[1] + "'s heart count to " + halfHearts + " half-hearts.",
                    NamedTextColor.GREEN
            ));
        } catch (NumberFormatException exception) {
            source.getSender().sendMessage(Component.text(
                    "Heart count must be a non-negative whole number of half-hearts or 'reset'.",
                    NamedTextColor.RED
            ));
        }
    }

    private void giveLegendaryCommand(CommandSourceStack source, String[] args) {
        if (args.length != 2 || !(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Usage: /legendary give <item_id> (players only)", NamedTextColor.RED));
            return;
        }
        LegendaryItem item = registry.getById(args[1].toLowerCase());
        if (item == null) {
            String available = registry.getAll().stream().map(LegendaryItem::id).collect(Collectors.joining(", "));
            source.getSender().sendMessage(Component.text("Unknown item ID. Available IDs: " + available, NamedTextColor.RED));
            return;
        }
        if (!claimLegendary(item.id(), player)) {
            player.sendMessage(Component.text("That legendary item is already claimed.", NamedTextColor.RED));
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.createItemStack(this));
        overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        player.sendMessage(Component.text("Gave " + item.displayName() + " (" + item.id() + ").", NamedTextColor.GREEN));
    }

    public void handleLegendaryLogout(Player player) {
        String behavior = getConfig().getString("legendary.logout-behavior", "beacon");
        Location logoutLocation = player.getLocation();
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            String id = getLegendaryId(stack);
            if (id == null) {
                continue;
            }
            if ("drop".equalsIgnoreCase(behavior)) {
                player.getWorld().dropItemNaturally(logoutLocation, stack.clone());
                player.getInventory().setItem(slot, null);
                changed = true;
            } else if ("beacon".equalsIgnoreCase(behavior)) {
                offlineBeacons.put(id, new OfflineBeacon(
                        player.getUniqueId(),
                        logoutLocation.getWorld().getName(),
                        logoutLocation.getX(),
                        logoutLocation.getY(),
                        logoutLocation.getZ()
                ));
                changed = true;
            } else {
                offlineBeacons.remove(id);
                changed = true;
            }
        }
        if (changed) {
            saveOfflineBeacons();
        }
    }

    public void handleLegendaryLogin(Player player) {
        boolean changed = offlineBeacons.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(player.getUniqueId()));
        if (changed) {
            saveOfflineBeacons();
        }
    }

    private void loadOfflineBeacons() {
        org.bukkit.configuration.ConfigurationSection players = getConfig().getConfigurationSection("legendary.offline-beacons");
        if (players == null) {
            return;
        }
        for (String ownerKey : players.getKeys(false)) {
            UUID ownerId;
            try {
                ownerId = UUID.fromString(ownerKey);
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Ignoring invalid offline beacon owner UUID: " + ownerKey);
                continue;
            }
            org.bukkit.configuration.ConfigurationSection items = players.getConfigurationSection(ownerKey);
            if (items == null) {
                continue;
            }
            for (String id : items.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection beacon = items.getConfigurationSection(id);
                if (beacon == null || beacon.getString("world") == null) {
                    continue;
                }
                offlineBeacons.put(id, new OfflineBeacon(
                        ownerId,
                        beacon.getString("world"),
                        beacon.getDouble("x"),
                        beacon.getDouble("y"),
                        beacon.getDouble("z")
                ));
            }
        }
    }

    private void saveOfflineBeacons() {
        getConfig().set("legendary.offline-beacons", null);
        for (Map.Entry<String, OfflineBeacon> entry : offlineBeacons.entrySet()) {
            OfflineBeacon beacon = entry.getValue();
            String path = "legendary.offline-beacons." + beacon.ownerId() + "." + entry.getKey();
            getConfig().set(path + ".world", beacon.worldName());
            getConfig().set(path + ".x", beacon.x());
            getConfig().set(path + ".y", beacon.y());
            getConfig().set(path + ".z", beacon.z());
        }
        saveConfig();
    }

    private List<TrackedTarget> scanActiveTargets() {
        List<TrackedTarget> targets = new ArrayList<>();

        // 1. Check online players carrying a legendary item
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<String> carriedIds = new HashSet<>();
            for (ItemStack stack : player.getInventory().getContents()) {
                String id = getLegendaryId(stack);
                if (id != null) {
                    carriedIds.add(id);
                }
            }
            for (String id : carriedIds) {
                LegendaryItem def = registry.getById(id);
                String name = def != null ? def.displayName() : id;
                targets.add(new TrackedTarget(id, name, player.getLocation(), player.getUniqueId(), player.getName()));
            }
        }

        // 2. Check dropped legendary items on the ground
        for (World world : Bukkit.getWorlds()) {
            for (Item dropped : world.getEntitiesByClass(Item.class)) {
                String id = getLegendaryId(dropped.getItemStack());
                if (id != null) {
                    LegendaryItem def = registry.getById(id);
                    String name = def != null ? def.displayName() : id;
                    targets.add(new TrackedTarget(id, name, dropped.getLocation(), null, "DROPPED"));
                }
            }
        }

        return targets;
    }

    private void updateBeaconsAndProximity() {
        // Apply Glowing + Sky Beacon to holders and dropped items
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<String> carriedIds = getLegendaryIdsInInventory(player);
            if (!carriedIds.isEmpty()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                for (String id : carriedIds) {
                    spawnBeaconBeam(player.getLocation(), id);
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Item dropped : world.getEntitiesByClass(Item.class)) {
                String id = getLegendaryId(dropped.getItemStack());
                if (id != null) {
                    dropped.setInvulnerable(true);
                    if (!dropped.isGlowing()) {
                        dropped.setGlowing(true);
                        dropped.setUnlimitedLifetime(true);
                    }
                    spawnBeaconBeam(dropped.getLocation(), id);
                }
            }
        }
        for (Map.Entry<String, OfflineBeacon> entry : offlineBeacons.entrySet()) {
            OfflineBeacon beacon = entry.getValue();
            World world = Bukkit.getWorld(beacon.worldName());
            if (world != null) {
                spawnBeaconBeam(new Location(world, beacon.x(), beacon.y(), beacon.z()), entry.getKey());
            }
        }
        updateLegendaryGlowTeams();

        // Update Proximity Boss Bar for nearby hunters
        List<TrackedTarget> targets = scanActiveTargets();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            TrackedTarget nearest = null;
            double nearestDist = Double.MAX_VALUE;

            for (TrackedTarget target : targets) {
                // Don't show the proximity alert to the player who is currently holding that item
                if (target.holderUuid() != null && target.holderUuid().equals(viewer.getUniqueId())) {
                    continue;
                }
                if (!target.location().getWorld().equals(viewer.getWorld())) {
                    continue;
                }

                double dist = viewer.getLocation().distance(target.location());
                if (dist <= PROXIMITY_RADIUS && dist < nearestDist) {
                    nearestDist = dist;
                    nearest = target;
                }
            }

            if (nearest != null) {
                // Progress goes from 0.0 (at 250 blocks away) to 1.0 (right on top of it)
                float progress = (float) Math.max(0.0, Math.min(1.0, 1.0 - (nearestDist / PROXIMITY_RADIUS)));
                BossBar.Color barColor = nearestDist <= 75.0 ? BossBar.Color.RED : BossBar.Color.YELLOW;
                String intensity = nearestDist <= 75.0 ? "VERY CLOSE" : "NEARBY";

                Component title = Component.text("⚠ ", NamedTextColor.GOLD)
                        .append(Component.text(nearest.displayName(), NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text(" is " + intensity + "!", NamedTextColor.YELLOW));

                BossBar bar = activeBossBars.computeIfAbsent(viewer.getUniqueId(), uuid -> {
                    BossBar created = BossBar.bossBar(title, progress, barColor, BossBar.Overlay.PROGRESS);
                    viewer.showBossBar(created);
                    return created;
                });

                bar.name(title);
                bar.progress(progress);
                bar.color(barColor);
            } else {
                removeBossBar(viewer);
            }
        }
    }

    private void updateScoreboards() {
        List<LegendaryItem> craftedItems = registry.getAll().stream()
                .filter(item -> isCrafted(item.id()))
                .toList();
        Map<String, TrackedTarget> targetsById = new HashMap<>();
        for (TrackedTarget target : scanActiveTargets()) {
            targetsById.putIfAbsent(target.id(), target);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (craftedItems.isEmpty()) {
                if (player.getScoreboard().getObjective("legendary_track") != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                continue;
            }

            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective(
                    "legendary_track",
                    Criteria.DUMMY,
                    Component.text("⚡ TRACKER ⚡", NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            int scoreLine = craftedItems.size();
            for (LegendaryItem item : craftedItems) {
                TrackedTarget target = targetsById.get(item.id());
                String icon = target == null ? "§8?" : target.holderUuid() == null ? "§b◆" : "§e☻";
                String line = icon + " §6" + item.displayName();
                if (target == null) {
                    line += " §8| §7unlocated";
                } else {
                    line += target.holderUuid() == null
                            ? " §8| §7ground"
                            : " §8| §e" + target.statusLabel();
                    int approxX = roundCoord(target.location().getBlockX());
                    int approxZ = roundCoord(target.location().getBlockZ());
                    line += " §8~§b" + approxX + "," + approxZ;
                }
                obj.getScore(line).setScore(scoreLine--);
            }

            player.setScoreboard(board);
        }
    }

    private int roundCoord(int coord) {
        int step = Math.max(50, Math.min(1000,
                getConfig().getInt("legendary.tracker-coordinate-rounding", COORD_ROUND_STEP)));
        return Math.round((float) coord / step) * step;
    }

    private void spawnBeaconBeam(Location base, String legendaryId) {
        World world = base.getWorld();
        if (world == null) return;

        double x = base.getX();
        double z = base.getZ();
        int startY = base.getBlockY();
        int maxY = Math.min(world.getMaxHeight(), startY + 220);

        Color beamColor = getLegendaryColor(legendaryId);
        Particle.DustOptions beamCore = new Particle.DustOptions(beamColor, 2.5f);

        for (int y = startY; y <= maxY; y += 2) {
            int heightAboveSource = y - startY;
            Location point = new Location(world, x, y, z);

            if (heightAboveSource < 16) {
                if (heightAboveSource % 8 != 0) {
                    continue;
                }
                new ParticleBuilder(Particle.DUST)
                        .location(point)
                        .count(1)
                        .offset(0.04, 0.06, 0.04)
                        .data(new Particle.DustOptions(beamColor, 0.7f))
                        .force(true)
                        .spawn();
                continue;
            }

            new ParticleBuilder(Particle.END_ROD)
                    .location(point)
                    .count(1)
                    .offset(0.05, 0.05, 0.05)
                    .speed(0.0)
                    .force(true)
                    .spawn();

            new ParticleBuilder(Particle.DUST)
                    .location(point)
                    .count(2)
                    .offset(0.12, 0.2, 0.12)
                    .data(beamCore)
                    .force(true)
                    .spawn();
        }
    }

    public Color getLegendaryColor(String legendaryId) {
        return switch (legendaryId) {
            case "astral_sovereign" -> Color.fromRGB(0xFFD700);
            case "mjolnir" -> Color.WHITE;
            case "storm_shuriken" -> Color.fromRGB(0x55FFFF);
            case "astral_excavator" -> Color.fromRGB(0x55FF55);
            case "astral_helmet" -> Color.fromRGB(0x5599FF);
            case "astral_chestplate" -> Color.fromRGB(0x008B8B);
            case "astral_leggings" -> Color.fromRGB(0xCC66FF);
            case "astral_boots" -> Color.fromRGB(0xFF66CC);
            default -> Color.fromRGB(255, 200, 40);
        };
    }

    private NamedTextColor getGlowTeamColor(String legendaryId) {
        return switch (legendaryId) {
            case "astral_sovereign" -> NamedTextColor.GOLD;
            case "mjolnir" -> NamedTextColor.WHITE;
            case "storm_shuriken" -> NamedTextColor.AQUA;
            case "astral_excavator" -> NamedTextColor.DARK_GREEN;
            case "astral_helmet" -> NamedTextColor.BLUE;
            case "astral_chestplate" -> NamedTextColor.DARK_AQUA;
            case "astral_leggings" -> NamedTextColor.LIGHT_PURPLE;
            case "astral_boots" -> NamedTextColor.GREEN;
            default -> NamedTextColor.GOLD;
        };
    }

    private String getGlowTeamName(NamedTextColor color) {
        if (color.equals(NamedTextColor.AQUA)) {
            return "legendary_cyan";
        }
        if (color.equals(NamedTextColor.WHITE)) {
            return "legendary_white";
        }
        if (color.equals(NamedTextColor.DARK_GREEN)) {
            return "legendary_green";
        }
        if (color.equals(NamedTextColor.BLUE)) {
            return "legendary_blue";
        }
        if (color.equals(NamedTextColor.DARK_AQUA)) {
            return "legendary_dark_aqua";
        }
        if (color.equals(NamedTextColor.LIGHT_PURPLE)) {
            return "legendary_purple";
        }
        if (color.equals(NamedTextColor.GREEN)) {
            return "legendary_lime";
        }
        return "legendary_gold";
    }

    private ChatColor getScoreboardColor(NamedTextColor color) {
        if (color.equals(NamedTextColor.AQUA)) {
            return ChatColor.AQUA;
        }
        if (color.equals(NamedTextColor.DARK_GREEN)) {
            return ChatColor.DARK_GREEN;
        }
        if (color.equals(NamedTextColor.WHITE)) {
            return ChatColor.WHITE;
        }
        if (color.equals(NamedTextColor.BLUE)) {
            return ChatColor.BLUE;
        }
        if (color.equals(NamedTextColor.DARK_AQUA)) {
            return ChatColor.DARK_AQUA;
        }
        if (color.equals(NamedTextColor.LIGHT_PURPLE)) {
            return ChatColor.LIGHT_PURPLE;
        }
        if (color.equals(NamedTextColor.GREEN)) {
            return ChatColor.GREEN;
        }
        return ChatColor.GOLD;
    }

    private void updateLegendaryGlowTeams() {
        Map<NamedTextColor, Set<String>> entriesByColor = new LinkedHashMap<>();
        entriesByColor.put(NamedTextColor.GOLD, new HashSet<>());
        entriesByColor.put(NamedTextColor.AQUA, new HashSet<>());
        entriesByColor.put(NamedTextColor.WHITE, new HashSet<>());
        entriesByColor.put(NamedTextColor.DARK_GREEN, new HashSet<>());
        entriesByColor.put(NamedTextColor.BLUE, new HashSet<>());
        entriesByColor.put(NamedTextColor.DARK_AQUA, new HashSet<>());
        entriesByColor.put(NamedTextColor.LIGHT_PURPLE, new HashSet<>());
        entriesByColor.put(NamedTextColor.GREEN, new HashSet<>());

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String id : getLegendaryIdsInInventory(player)) {
                entriesByColor.get(getGlowTeamColor(id)).add(player.getName());
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Item dropped : world.getEntitiesByClass(Item.class)) {
                String id = getLegendaryId(dropped.getItemStack());
                if (id != null) {
                    entriesByColor.get(getGlowTeamColor(id)).add(dropped.getScoreboardEntryName());
                }
            }
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = viewer.getScoreboard();
            Map<NamedTextColor, Team> teams = new LinkedHashMap<>();
            for (NamedTextColor color : entriesByColor.keySet()) {
                String teamName = getGlowTeamName(color);
                Team team = scoreboard.getTeam(teamName);
                if (team == null) {
                    team = scoreboard.registerNewTeam(teamName);
                }
                team.setColor(getScoreboardColor(color));
                for (String oldEntry : trackedGlowEntries) {
                    if (team.hasEntry(oldEntry)) {
                        team.removeEntry(oldEntry);
                    }
                }
                teams.put(color, team);
            }
            entriesByColor.forEach((color, entries) -> entries.forEach(teams.get(color)::addEntry));
        }

        trackedGlowEntries.clear();
        entriesByColor.values().forEach(trackedGlowEntries::addAll);
    }

    public void removeBossBar(Player player) {
        BossBar bar = activeBossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private Set<String> getLegendaryIdsInInventory(Player player) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            String id = getLegendaryId(stack);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private void applyLegendaryOverloadPenalty() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                continue;
            }
            int legendaryCount = 0;
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                legendaryCount += getLegendaryItemCount(stack);
            }
            for (ItemStack stack : player.getInventory().getArmorContents()) {
                legendaryCount += getLegendaryItemCount(stack);
            }
            for (ItemStack stack : player.getInventory().getExtraContents()) {
                legendaryCount += getLegendaryItemCount(stack);
            }

            int allowedCount = 1;
            if ("armor_tool".equalsIgnoreCase(getConfig().getString("legendary.overload-rule", "armor_tool"))) {
                allowedCount = hasLegendaryArmor(player) && hasLegendaryTool(player) ? 2 : 1;
            }

            if (legendaryCount > allowedCount) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, true, true));
                player.damage(2.0);
            }
        }
    }

    private boolean hasLegendaryArmor(Player player) {
        return hasLegendaryCategory(player, true);
    }

    private boolean hasLegendaryTool(Player player) {
        return hasLegendaryCategory(player, false);
    }

    private boolean hasLegendaryCategory(Player player, boolean armor) {
        for (ItemStack[] contents : List.of(
                player.getInventory().getStorageContents(),
                player.getInventory().getArmorContents(),
                player.getInventory().getExtraContents()
        )) {
            for (ItemStack stack : contents) {
                String id = getLegendaryId(stack);
                LegendaryItem item = id == null ? null : registry.getById(id);
                if (item != null && (item.equipmentSlot() != null) == armor) {
                    return true;
                }
            }
        }
        return false;
    }

    public int destroyAllLegendaries() {
            Set<String> destroyedIds = new HashSet<>();
            for (LegendaryItem item : registry.getAll()) {
                if (isCrafted(item.id())) {
                    destroyedIds.add(item.id());
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                clearLegendaryItems(player.getInventory(), destroyedIds);
                clearLegendaryItems(player.getEnderChest(), destroyedIds);
                String cursorId = getLegendaryId(player.getOpenInventory().getCursor());
                if (cursorId != null) {
                    destroyedIds.add(cursorId);
                    player.getOpenInventory().setCursor(null);
                }
            }

            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Item dropped) {
                        String id = getLegendaryId(dropped.getItemStack());
                        if (id != null) {
                            destroyedIds.add(id);
                            dropped.remove();
                        }
                    } else if (entity instanceof Trident trident) {
                        String id = getLegendaryId(trident.getItemStack());
                        if (id != null) {
                            destroyedIds.add(id);
                            trident.remove();
                        }
                    } else if (entity instanceof Snowball projectile && isShurikenProjectile(projectile)) {
                        destroyedIds.add("storm_shuriken");
                        projectile.remove();
                    } else if (entity instanceof ItemFrame itemFrame) {
                        String id = getLegendaryId(itemFrame.getItem());
                        if (id != null) {
                            destroyedIds.add(id);
                            itemFrame.setItem(new ItemStack(org.bukkit.Material.AIR));
                        }
                    }

                    if (entity instanceof LivingEntity living && living.getEquipment() != null) {
                        clearLegendaryEquipment(living, destroyedIds);
                    }
                    if (entity instanceof InventoryHolder holder && !(entity instanceof Player)) {
                        clearLegendaryItems(holder.getInventory(), destroyedIds);
                    }
                }

                for (Chunk chunk : world.getLoadedChunks()) {
                    for (BlockState state : chunk.getTileEntities()) {
                        if (state instanceof Container container) {
                            clearLegendaryItems(container.getInventory(), destroyedIds);
                        }
                    }
                }
            }

            for (String id : destroyedIds) {
                destroyLegendary(id, "destroyed by an administrator");
            }
            return destroyedIds.size();
        }

    private void clearLegendaryEquipment(LivingEntity entity, Set<String> destroyedIds) {
        org.bukkit.inventory.EntityEquipment equipment = entity.getEquipment();
        clearEquipmentSlot(equipment.getHelmet(), equipment::setHelmet, destroyedIds);
        clearEquipmentSlot(equipment.getChestplate(), equipment::setChestplate, destroyedIds);
        clearEquipmentSlot(equipment.getLeggings(), equipment::setLeggings, destroyedIds);
        clearEquipmentSlot(equipment.getBoots(), equipment::setBoots, destroyedIds);
        clearEquipmentSlot(equipment.getItemInMainHand(), equipment::setItemInMainHand, destroyedIds);
        clearEquipmentSlot(equipment.getItemInOffHand(), equipment::setItemInOffHand, destroyedIds);
    }

    private void clearEquipmentSlot(
            ItemStack stack,
            java.util.function.Consumer<ItemStack> setter,
            Set<String> destroyedIds
    ) {
        String id = getLegendaryId(stack);
        if (id != null) {
            destroyedIds.add(id);
            setter.accept(null);
        }
    }

    private void clearLegendaryItems(Inventory inventory, Set<String> destroyedIds) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            String id = getLegendaryId(stack);
            if (id != null) {
                destroyedIds.add(id);
                inventory.setItem(slot, null);
            }
        }
    }

    private int getLegendaryItemCount(ItemStack stack) {
        return getLegendaryId(stack) == null ? 0 : stack.getAmount();
    }

    private void applyHeldAndArmorEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyHeldWeaponEffects(player, player.getInventory().getItemInMainHand());
            applyHeldWeaponEffects(player, player.getInventory().getItemInOffHand());
            applyAstralArmorEffects(player);
        }
    }

    private void applyHeldWeaponEffects(Player player, ItemStack stack) {
        String id = getLegendaryId(stack);
        if (id == null || registry.getById(id) == null) {
            return;
        }

        switch (id) {
            case "astral_sovereign" -> applyConfiguredEffects(player, "effects.astral_sovereign");
            default -> {
            }
        }
    }

    private void applyAstralArmorEffects(Player player) {
        String helmetId = getLegendaryId(player.getInventory().getHelmet());
        String chestplateId = getLegendaryId(player.getInventory().getChestplate());
        String leggingsId = getLegendaryId(player.getInventory().getLeggings());
        String bootsId = getLegendaryId(player.getInventory().getBoots());

        if ("astral_helmet".equals(helmetId)) {
            applyConfiguredEffects(player, "effects.astral_helmet");
        }
        if ("astral_chestplate".equals(chestplateId)) {
            applyConfiguredEffects(player, "effects.astral_chestplate");
        }
        if ("astral_leggings".equals(leggingsId)) {
            applyConfiguredEffects(player, "effects.astral_leggings");
        }
        if ("astral_boots".equals(bootsId)) {
            applyConfiguredEffects(player, "effects.astral_boots.passive");
        }

        if ("astral_helmet".equals(helmetId)
                && "astral_chestplate".equals(chestplateId)
                && "astral_leggings".equals(leggingsId)
                && "astral_boots".equals(bootsId)) {
            applyConfiguredEffects(player, "effects.astral_set");
            if (player.isSprinting()) {
                player.getWorld().spawnParticle(
                        Particle.DUST,
                        player.getLocation().add(0.0, 0.1, 0.0),
                        2,
                        0.15,
                        0.05,
                        0.15,
                        new Particle.DustOptions(getLegendaryColor("astral_helmet"), 1.0f)
                );
            }
        }
    }

    public String getHeldLegendaryId(Player player) {
        String mainHandId = getLegendaryId(player.getInventory().getItemInMainHand());
        if (mainHandId != null) {
            return mainHandId;
        }
        return getLegendaryId(player.getInventory().getItemInOffHand());
    }

    private boolean isShurikenProjectile(Snowball projectile) {
        return projectile.getPersistentDataContainer().has(shurikenProjectileKey, PersistentDataType.BYTE);
    }

    private String formatDimension(World.Environment env) {
        return switch (env) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> "Unknown";
        };
    }

    public String getLegendaryId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public boolean isCrafted(String id) {
        return getConfig().getBoolean("crafted." + id, false);
    }

    public void markCrafted(String id, boolean crafted) {
        getConfig().set("crafted." + id, crafted);
        saveConfig();
    }

    public boolean claimLegendary(String id, Player owner) {
        if (isCrafted(id)) {
            return false;
        }
        getConfig().set("crafted." + id, true);
        getConfig().set("owners." + id, owner.getUniqueId().toString());
        saveConfig();
        return true;
    }

    public void transferLegendaryOwnership(String id, Player newOwner) {
        String oldOwner = getConfig().getString("owners." + id);
        String newOwnerId = newOwner.getUniqueId().toString();
        getConfig().set("owners." + id, newOwnerId);
        saveConfig();
        if (oldOwner == null || oldOwner.equals(newOwnerId) || isAnnouncementCoolingDown(id)) {
            return;
        }

        LegendaryItem item = registry.getById(id);
        String itemName = item == null ? id : item.displayName();
        Bukkit.broadcast(Component.text("⚡ " + newOwner.getName() + " now wields " + itemName + "!", NamedTextColor.AQUA));
        playOwnershipAnnouncementSound();
        ownershipAnnouncementCooldowns.put(id, System.currentTimeMillis());
    }

    public void announceLegendaryDeath(Player player, Collection<String> itemIds) {
        for (String id : itemIds) {
            if (isAnnouncementCoolingDown(id)) {
                continue;
            }
            LegendaryItem item = registry.getById(id);
            String itemName = item == null ? id : item.displayName();
            Bukkit.broadcast(Component.text("⚡ " + player.getName() + " fell while carrying " + itemName + ".", NamedTextColor.YELLOW));
            playOwnershipAnnouncementSound();
            ownershipAnnouncementCooldowns.put(id, System.currentTimeMillis());
        }
    }

    public void destroyLegendary(String id, String cause) {
        if (!isCrafted(id) || hasLegendaryItem(id)) {
            return;
        }
        getConfig().set("crafted." + id, false);
        getConfig().set("owners." + id, null);
        if (offlineBeacons.remove(id) != null) {
            saveOfflineBeacons();
        }
        saveConfig();

        LegendaryItem item = registry.getById(id);
        String itemName = item == null ? id : item.displayName();
        Component title = Component.text("LEGENDARY ITEM DESTROYED", NamedTextColor.RED, TextDecoration.BOLD);
        Component subtitle = Component.text(itemName + " was " + cause + ". It can be forged again.", NamedTextColor.GOLD);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(net.kyori.adventure.title.Title.title(title, subtitle));
        }
        Bukkit.broadcast(Component.text("☄ " + itemName + " has been destroyed (" + cause + ") and may now be forged again!", NamedTextColor.RED, TextDecoration.BOLD));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
        }
    }

    public boolean hasLegendaryItem(String id) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            for (ItemStack stack : onlinePlayer.getInventory().getContents()) {
                if (id.equals(getLegendaryId(stack))) {
                    return true;
                }
            }
            if (id.equals(getLegendaryId(onlinePlayer.getOpenInventory().getCursor()))) {
                return true;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item dropped && dropped.isValid()
                        && id.equals(getLegendaryId(dropped.getItemStack()))) {
                    return true;
                }
                if (entity instanceof Trident trident && trident.isValid()
                        && id.equals(getLegendaryId(trident.getItemStack()))) {
                    return true;
                }
                if (entity instanceof Snowball projectile && projectile.isValid()
                        && "storm_shuriken".equals(id) && isShurikenProjectile(projectile)) {
                    return true;
                }
                if (entity instanceof ItemFrame itemFrame
                        && id.equals(getLegendaryId(itemFrame.getItem()))) {
                    return true;
                }
                if (entity instanceof LivingEntity living && living.getEquipment() != null) {
                    for (ItemStack stack : living.getEquipment().getArmorContents()) {
                        if (id.equals(getLegendaryId(stack))) {
                            return true;
                        }
                    }
                    if (id.equals(getLegendaryId(living.getEquipment().getItemInMainHand()))
                            || id.equals(getLegendaryId(living.getEquipment().getItemInOffHand()))) {
                        return true;
                    }
                }
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) {
                        for (ItemStack stack : container.getInventory().getContents()) {
                            if (id.equals(getLegendaryId(stack))) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isAnnouncementCoolingDown(String id) {
        Long lastAnnounced = ownershipAnnouncementCooldowns.get(id);
        return lastAnnounced != null && System.currentTimeMillis() - lastAnnounced < 30_000L;
    }

    private void playOwnershipAnnouncementSound() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        }
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public NamespacedKey getShurikenProjectileKey() {
        return shurikenProjectileKey;
    }

    public LegendaryRegistry getRegistry() {
        return registry;
    }
}