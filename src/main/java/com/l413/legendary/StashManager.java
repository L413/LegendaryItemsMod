package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StashManager implements Listener, CommandExecutor, TabCompleter {
    private final LegendaryPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Stash> stashes = new HashMap<>();
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private final Map<String, Long> itemCooldowns = new HashMap<>();
    private final Map<String, HoldState> holdStates = new HashMap<>();
    private final Map<String, Location> groundLocations = new HashMap<>();
    private final Map<String, String> ticketedChunks = new HashMap<>();
    private final Map<String, Chunk> chunksByKey = new HashMap<>();
    private final Map<String, Set<String>> chunkTicketOwners = new HashMap<>();
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final Set<String> missingWorldWarnings = new HashSet<>();
    private boolean dirty;
    private BukkitTask saveTask;

    private record Stash(
            UUID playerId,
            String playerName,
            String legendaryId,
            String itemBytes,
            StoredLocation stashLocation,
            StoredLocation logoutLocation,
            long stashedAt,
            long expiresAt
    ) {}

    private record StoredLocation(String worldName, double x, double y, double z) {}

    private static final class HoldState {
        private UUID holderId;
        private long heldMillis;
        private long startedAt;

        private HoldState(UUID holderId, long heldMillis, long startedAt) {
            this.holderId = holderId;
            this.heldMillis = heldMillis;
            this.startedAt = startedAt;
        }
    }

    private record Warmup(Location start, BukkitTask task) {}

    public StashManager(LegendaryPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stashes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        loadData();
        restoreGroundTickets();
        Bukkit.getScheduler().runTask(plugin, this::expireStashes);
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanHolders, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireStashes, 20L, 100L);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, 100L, 100L);
    }

    public void shutdown() {
        for (UUID playerId : new HashSet<>(warmups.keySet())) {
            cancelWarmup(playerId, "Stash warmup cancelled because the server is stopping.");
        }
        pauseAllHolds();
        if (saveTask != null) {
            saveTask.cancel();
        }
        saveData();
        for (Chunk chunk : new HashSet<>(chunksByKey.values())) {
            chunk.removePluginChunkTicket(plugin);
        }
        ticketedChunks.clear();
        chunksByKey.clear();
        chunkTicketOwners.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (command.getName().equalsIgnoreCase("unstash")
                || (args.length > 0 && args[0].equalsIgnoreCase("claim"))) {
            claimStash(player);
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Component.text("Usage: /stash [claim]", NamedTextColor.YELLOW));
            return true;
        }
        beginStash(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return command.getName().equalsIgnoreCase("stash") && args.length == 1
                ? List.of("claim")
                : List.of();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::scanHolders);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Stash stash = stashes.get(playerId);
        if (stash != null) {
            stashes.put(playerId, new Stash(
                    stash.playerId(),
                    stash.playerName(),
                    stash.legendaryId(),
                    stash.itemBytes(),
                    stash.stashLocation(),
                    storeLocation(event.getPlayer().getLocation()),
                    stash.stashedAt(),
                    stash.expiresAt()
            ));
            markDirty();
        }
        cancelWarmup(playerId, "Stash warmup cancelled because you disconnected.");
        pausePlayerHolds(playerId);
        saveData();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Bukkit.getScheduler().runTask(plugin, this::scanHolders);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Bukkit.getScheduler().runTask(plugin, this::scanHolders);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Warmup warmup = warmups.get(event.getPlayer().getUniqueId());
        if (warmup == null || event.getTo() == null) {
            return;
        }
        Location start = warmup.start();
        Location to = event.getTo();
        if (start.getWorld() != to.getWorld()
                || start.getBlockX() != to.getBlockX()
                || start.getBlockY() != to.getBlockY()
                || start.getBlockZ() != to.getBlockZ()) {
            cancelWarmup(event.getPlayer().getUniqueId(), "Stash warmup cancelled because you moved.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && warmups.containsKey(player.getUniqueId())) {
            cancelWarmup(player.getUniqueId(), "Stash warmup cancelled because you took damage.");
        }
    }

    public boolean hasActiveStash(String legendaryId) {
        return stashes.values().stream().anyMatch(stash -> stash.legendaryId().equals(legendaryId));
    }

    public Set<String> getPersistedLegendaryIds() {
        Set<String> ids = new HashSet<>(groundLocations.keySet());
        stashes.values().forEach(stash -> ids.add(stash.legendaryId()));
        return ids;
    }

    public Map<String, String> getActiveStashDisplays() {
        Map<String, String> displays = new HashMap<>();
        for (Stash stash : stashes.values()) {
            long remaining = Math.max(0L, stash.expiresAt() - System.currentTimeMillis());
            long minutes = (remaining + 59_999L) / 60_000L;
            String ownerName = getStashOwnerName(stash);
            displays.put(stash.legendaryId(), "Stashed - " + ownerName + " ("
                    + (minutes / 60L) + "h " + (minutes % 60L) + "m left)");
        }
        return displays;
    }

    public Location getGroundLocation(String legendaryId) {
        Location location = groundLocations.get(legendaryId);
        return location == null ? null : location.clone();
    }

    public void updateGroundLocation(String legendaryId, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        Location previous = groundLocations.get(legendaryId);
        boolean firstLocation = previous == null;
        if (previous == null
                || previous.getWorld() != location.getWorld()
                || Double.compare(previous.getX(), location.getX()) != 0
                || Double.compare(previous.getY(), location.getY()) != 0
                || Double.compare(previous.getZ(), location.getZ()) != 0) {
            groundLocations.put(legendaryId, location.clone());
            dirty = true;
        }
        Chunk currentChunk = location.getChunk();
        String currentKey = chunkKey(currentChunk);
        boolean movedChunk = !currentKey.equals(ticketedChunks.get(legendaryId));
        if (movedChunk) {
            releaseTicket(legendaryId);
            Set<String> owners = chunkTicketOwners.computeIfAbsent(currentKey, ignored -> new HashSet<>());
            if (owners.isEmpty()) {
                if (!currentChunk.addPluginChunkTicket(plugin)) {
                    plugin.getLogger().warning("Could not add a plugin chunk ticket for Legendary " + legendaryId + ".");
                }
                chunksByKey.put(currentKey, currentChunk);
            }
            owners.add(legendaryId);
            ticketedChunks.put(legendaryId, currentKey);
        }
        if (firstLocation || movedChunk) {
            saveData();
        }
    }

    public void clearGroundLocation(String legendaryId) {
        groundLocations.remove(legendaryId);
        releaseTicket(legendaryId);
        markDirty();
        saveData();
    }

    public void discardLegendary(String legendaryId) {
        stashes.entrySet().removeIf(entry -> entry.getValue().legendaryId().equals(legendaryId));
        holdStates.remove(legendaryId);
        itemCooldowns.remove(legendaryId);
        clearGroundLocation(legendaryId);
        markDirty();
    }

    public void observeHolder(String legendaryId, UUID holderId) {
        HoldState state = holdStates.get(legendaryId);
        long now = System.currentTimeMillis();
        if (state == null || !holderId.equals(state.holderId)) {
            holdStates.put(legendaryId, new HoldState(holderId, 0L, now));
            markDirty();
            saveData();
        } else if (state.startedAt == 0L) {
            state.startedAt = now;
            markDirty();
            saveData();
        }
    }

    public void resetHold(String legendaryId, UUID holderId) {
        holdStates.put(legendaryId, new HoldState(holderId, 0L, System.currentTimeMillis()));
        markDirty();
    }

    public void noteCrafted(String legendaryId, Player holder) {
        observeHolder(legendaryId, holder.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, this::scanHolders);
    }

    public void pauseHold(String legendaryId) {
        HoldState state = holdStates.get(legendaryId);
        if (state != null) {
            pause(state);
            markDirty();
        }
    }

    private void beginStash(Player player) {
        ItemStack legendary = findLegendary(player);
        String id = plugin.getLegendaryId(legendary);
        if (id == null) {
            player.sendMessage(Component.text("You must be carrying a Legendary item to stash it.", NamedTextColor.RED));
            return;
        }
        if (stashes.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You already have an active stash.", NamedTextColor.RED));
            return;
        }
        if (isCombatTagged(player)) {
            long millis = combatTagMillis() - (System.currentTimeMillis() - lastCombatDamage(player));
            player.sendMessage(Component.text("You cannot stash while combat-tagged ("
                    + formatRemaining(millis) + " remaining).", NamedTextColor.RED));
            return;
        }
        long now = System.currentTimeMillis();
        long playerUntil = playerCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (playerUntil > now) {
            player.sendMessage(Component.text("Your stash cooldown has " + formatRemaining(playerUntil - now) + " remaining.", NamedTextColor.RED));
            return;
        }
        long itemUntil = itemCooldowns.getOrDefault(id, 0L);
        if (itemUntil > now) {
            player.sendMessage(Component.text("This Legendary's stash cooldown has "
                    + formatRemaining(itemUntil - now) + " remaining.", NamedTextColor.RED));
            return;
        }
        observeHolder(id, player.getUniqueId());
        long heldMillis = getHeldMillis(id);
        long minimumMillis = Math.max(0L, plugin.getConfig().getLong("legendary.stash-min-hold-minutes", 15L)) * 60_000L;
        if (heldMillis < minimumMillis) {
            player.sendMessage(Component.text("Hold this Legendary for "
                    + formatRemaining(minimumMillis - heldMillis) + " more before stashing it.", NamedTextColor.RED));
            return;
        }
        if (warmups.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("A stash warmup is already in progress.", NamedTextColor.RED));
            return;
        }

        int ticks = Math.max(0, plugin.getConfig().getInt("legendary.stash-warmup-seconds", 5)) * 20;
        if (ticks == 0) {
            completeStash(player, id);
            return;
        }
        Location start = player.getLocation().clone();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            warmups.remove(player.getUniqueId());
            completeStash(player, id);
        }, ticks);
        warmups.put(player.getUniqueId(), new Warmup(start, task));
        player.sendMessage(Component.text("Stand still for " + (ticks / 20) + " seconds to stash your Legendary.", NamedTextColor.GOLD));
    }

    private void completeStash(Player player, String id) {
        if (!player.isOnline() || stashes.containsKey(player.getUniqueId())) {
            return;
        }
        if (isCombatTagged(player)) {
            player.sendMessage(Component.text("Stash cancelled because you are combat-tagged.", NamedTextColor.RED));
            return;
        }
        ItemStack item = findLegendary(player);
        if (!id.equals(plugin.getLegendaryId(item))) {
            player.sendMessage(Component.text("Stash cancelled because you are no longer carrying that Legendary.", NamedTextColor.RED));
            return;
        }
        long minimumMillis = Math.max(0L, plugin.getConfig().getLong("legendary.stash-min-hold-minutes", 15L)) * 60_000L;
        long heldMillis = getHeldMillis(id);
        if (heldMillis < minimumMillis) {
            player.sendMessage(Component.text("Stash cancelled; hold time remaining: "
                    + formatRemaining(minimumMillis - heldMillis) + ".", NamedTextColor.RED));
            return;
        }
        StoredLocation stashLocation = storeLocation(player.getLocation());
        removeLegendary(player, item);
        long stashedAt = System.currentTimeMillis();
        long duration = stashDurationMillis();
        Stash stash = new Stash(
                player.getUniqueId(),
                player.getName(),
                id,
                Base64.getEncoder().encodeToString(item.serializeAsBytes()),
                stashLocation,
                stashLocation,
                stashedAt,
                stashedAt + duration
        );
        stashes.put(player.getUniqueId(), stash);
        plugin.clearOfflineBeacon(id);
        pauseHold(id);
        markDirty();
        saveData();
        player.sendMessage(Component.text("Your Legendary is stashed.", NamedTextColor.GREEN));
    }

    private void claimStash(Player player) {
        Stash stash = stashes.get(player.getUniqueId());
        if (stash == null) {
            player.sendMessage(Component.text("You do not have an active stash.", NamedTextColor.RED));
            return;
        }
        ItemStack item = deserialize(stash);
        if (item == null) {
            player.sendMessage(Component.text("Your stash could not be restored; check the server log.", NamedTextColor.RED));
            return;
        }
        stashes.remove(player.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldown = calculateCooldown(stash, now);
        long cooldownUntil = now + cooldown;
        playerCooldowns.put(player.getUniqueId(), cooldownUntil);
        itemCooldowns.put(stash.legendaryId(), cooldownUntil);
        resetHold(stash.legendaryId(), player.getUniqueId());
        markDirty();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            plugin.trackDroppedLegendary(dropped);
        }
        saveData();
        player.sendMessage(Component.text("Your Legendary has been unstashed. Stash cooldown: "
                + formatExactDuration(cooldown) + ".", NamedTextColor.GREEN));
    }

    private void expireStashes() {
        long now = System.currentTimeMillis();
        for (Stash stash : new ArrayList<>(stashes.values())) {
            if (now < stash.expiresAt()) {
                continue;
            }
            ItemStack item = deserialize(stash);
            if (item == null) {
                plugin.getLogger().severe("Could not deserialize expired stash for " + stash.playerId() + " (" + stash.legendaryId() + "); preserving it.");
                continue;
            }
            Player owner = Bukkit.getPlayer(stash.playerId());
            Location dropLocation = owner == null ? resolveLocation(stash.logoutLocation()) : owner.getLocation().clone();
            if (owner != null && (dropLocation == null || dropLocation.getWorld() == null)) {
                plugin.getLogger().warning("Could not locate expired stash for " + stash.playerId() + "; preserving it.");
                continue;
            }
            if (dropLocation == null || dropLocation.getWorld() == null) {
                continue;
            }
            dropLocation.getChunk();
            stashes.remove(stash.playerId());
            long cooldown = calculateCooldown(stash, now);
            long cooldownUntil = now + cooldown;
            playerCooldowns.put(stash.playerId(), cooldownUntil);
            itemCooldowns.put(stash.legendaryId(), cooldownUntil);
            markDirty();
            Item dropped = dropLocation.getWorld().dropItemNaturally(dropLocation, item);
            plugin.trackDroppedLegendary(dropped);
            saveData();
            var definition = plugin.getRegistry().getById(stash.legendaryId());
            String name = definition == null ? stash.legendaryId() : definition.displayName();
            Bukkit.broadcast(Component.text(name + " stashed by "
                    + getStashOwnerName(stash)
                    + " has expired and dropped. Stash cooldown: "
                    + formatExactDuration(cooldown) + ".", NamedTextColor.GOLD));
        }
    }

    private Location resolveLocation(StoredLocation stored) {
        if (stored == null) {
            return null;
        }
        World world = Bukkit.getWorld(stored.worldName());
        if (world == null) {
            if (missingWorldWarnings.add(stored.worldName())) {
                plugin.getLogger().warning("Stash drop is waiting for world '" + stored.worldName() + "' to load.");
            }
            return null;
        }
        missingWorldWarnings.remove(stored.worldName());
        Location location = new Location(world, stored.x(), stored.y(), stored.z());
        location.getChunk();
        return location;
    }

    private StoredLocation storeLocation(Location location) {
        return new StoredLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    private String getStashOwnerName(Stash stash) {
        if (stash.playerName() != null && !stash.playerName().isBlank()) {
            return stash.playerName();
        }
        String cachedName = Bukkit.getOfflinePlayer(stash.playerId()).getName();
        return cachedName == null ? stash.playerId().toString().substring(0, 8) : cachedName;
    }

    private ItemStack deserialize(Stash stash) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(stash.itemBytes()));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().severe("Invalid Base64 item data in stash for " + stash.playerId() + ": " + exception.getMessage());
            return null;
        }
    }

    private ItemStack findLegendary(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (plugin.getLegendaryId(stack) != null) {
                return stack;
            }
        }
        return plugin.getLegendaryId(player.getOpenInventory().getCursor()) == null
                ? null
                : player.getOpenInventory().getCursor();
    }

    private void removeLegendary(Player player, ItemStack item) {
        String id = plugin.getLegendaryId(item);
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (id.equals(plugin.getLegendaryId(stack))) {
                player.getInventory().setItem(slot, null);
                return;
            }
        }
        if (id.equals(plugin.getLegendaryId(player.getOpenInventory().getCursor()))) {
            player.getOpenInventory().setCursor(null);
        }
    }

    private void scanHolders() {
        Map<String, UUID> current = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack stack : player.getInventory().getContents()) {
                String id = plugin.getLegendaryId(stack);
                if (id != null) {
                    current.putIfAbsent(id, player.getUniqueId());
                }
            }
            String cursorId = plugin.getLegendaryId(player.getOpenInventory().getCursor());
            if (cursorId != null) {
                current.putIfAbsent(cursorId, player.getUniqueId());
            }
        }
        for (Map.Entry<String, UUID> entry : current.entrySet()) {
            observeHolder(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, HoldState> entry : holdStates.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                if (pause(entry.getValue())) {
                    markDirty();
                }
            } else if (entry.getValue().startedAt != 0L) {
                markDirty();
            }
        }
    }

    private void pausePlayerHolds(UUID playerId) {
        for (HoldState state : holdStates.values()) {
            if (playerId.equals(state.holderId)) {
                pause(state);
            }
        }
        markDirty();
    }

    private void pauseAllHolds() {
        for (HoldState state : holdStates.values()) {
            pause(state);
        }
        markDirty();
    }

    private boolean pause(HoldState state) {
        if (state.startedAt != 0L) {
            state.heldMillis += Math.max(0L, System.currentTimeMillis() - state.startedAt);
            state.startedAt = 0L;
            return true;
        }
        return false;
    }

    private long getHeldMillis(String legendaryId) {
        HoldState state = holdStates.get(legendaryId);
        if (state == null) {
            return 0L;
        }
        return state.heldMillis + (state.startedAt == 0L ? 0L : Math.max(0L, System.currentTimeMillis() - state.startedAt));
    }

    private boolean isCombatTagged(Player player) {
        return System.currentTimeMillis() - lastCombatDamage(player) < combatTagMillis();
    }

    private long lastCombatDamage(Player player) {
        return player.getPersistentDataContainer().getOrDefault(
                plugin.getCombatTagKey(),
                PersistentDataType.LONG,
                0L
        );
    }

    private long combatTagMillis() {
        return Math.max(0L, plugin.getConfig().getLong("legendary.stash-combat-tag-seconds", 20L)) * 1000L;
    }

    private String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private String formatExactDuration(long millis) {
        long remaining = Math.max(0L, millis);
        long hours = remaining / 3_600_000L;
        remaining %= 3_600_000L;
        long minutes = remaining / 60_000L;
        remaining %= 60_000L;
        long seconds = remaining / 1000L;
        long milliseconds = remaining % 1000L;
        return hours + "h " + minutes + "m " + seconds + "s " + milliseconds + "ms";
    }

    private long stashDurationMillis() {
        return Math.max(0L, plugin.getConfig().getLong("legendary.stash-duration-hours", 16L)) * 3_600_000L;
    }

    private long calculateCooldown(Stash stash, long now) {
        long elapsedMillis = Math.max(0L, now - stash.stashedAt());
        long maxDurationMillis = stashDurationMillis();
        double fractionUsed = maxDurationMillis == 0L
                ? 1.0
                : Math.min(1.0, (double) elapsedMillis / maxDurationMillis);
        long maxCooldownMillis = Math.max(0L,
                plugin.getConfig().getLong("legendary.stash-cooldown-hours", 10L)) * 3_600_000L;
        long minCooldownMillis = Math.max(0L,
                plugin.getConfig().getLong("legendary.stash-min-cooldown-minutes", 15L)) * 60_000L;
        return Math.max(minCooldownMillis, Math.round(fractionUsed * maxCooldownMillis));
    }

    private void cancelWarmup(UUID playerId, String message) {
        Warmup warmup = warmups.remove(playerId);
        if (warmup != null) {
            warmup.task().cancel();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(Component.text(message, NamedTextColor.RED));
            }
        }
    }

    private void loadData() {
        ConfigurationSection stashSection = data.getConfigurationSection("active-stashes");
        if (stashSection != null) {
            for (String key : stashSection.getKeys(false)) {
                ConfigurationSection section = stashSection.getConfigurationSection(key);
                try {
                    if (section == null) {
                        continue;
                    }
                    UUID playerId = UUID.fromString(section.getString("player-uuid", key));
                    String playerName = section.getString("player-name");
                    String id = section.getString("legendary-id");
                    String itemBytes = section.getString("item-bytes");
                    StoredLocation stashLocation = readStoredLocation(section.getConfigurationSection("stash-location"));
                    StoredLocation logoutLocation = readStoredLocation(section.getConfigurationSection("logout-location"));
                    if (id == null || itemBytes == null || stashLocation == null || logoutLocation == null) {
                        plugin.getLogger().warning("Ignoring incomplete stash record " + key + ".");
                        continue;
                    }
                    stashes.put(playerId, new Stash(
                            playerId,
                            playerName,
                            id,
                            itemBytes,
                            stashLocation,
                            logoutLocation,
                            section.getLong(
                                    "stashed-at",
                                    section.getLong("expires-at") - stashDurationMillis()
                            ),
                            section.getLong("expires-at")
                    ));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Ignoring invalid stash record " + key + ": " + exception.getMessage());
                }
            }
        }
        ConfigurationSection playerSection = data.getConfigurationSection("player-cooldowns");
        if (playerSection != null) {
            for (String key : playerSection.getKeys(false)) {
                UUID playerId = parseUuid(key, "player cooldown");
                if (playerId != null) {
                    playerCooldowns.put(playerId, playerSection.getLong(key + ".cooldown-until"));
                }
            }
        }
        ConfigurationSection itemSection = data.getConfigurationSection("item-cooldowns");
        if (itemSection != null) {
            for (String id : itemSection.getKeys(false)) {
                itemCooldowns.put(id, itemSection.getLong(id + ".cooldown-until"));
            }
        }
        ConfigurationSection holds = data.getConfigurationSection("holds");
        if (holds != null) {
            for (String id : holds.getKeys(false)) {
                ConfigurationSection section = holds.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                String holder = section.getString("holder-uuid");
                try {
                    holdStates.put(id, new HoldState(
                            holder == null ? null : UUID.fromString(holder),
                            section.getLong("held-millis"),
                            0L
                    ));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Ignoring invalid hold record for " + id + ".");
                }
            }
        }
        ConfigurationSection ground = data.getConfigurationSection("ground-locations");
        if (ground != null) {
            for (String id : ground.getKeys(false)) {
                Location location = readLocation(ground.getConfigurationSection(id));
                if (location != null) {
                    groundLocations.put(id, location);
                }
            }
        }
    }

    private UUID parseUuid(String key, String description) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Ignoring invalid " + description + " UUID: " + key);
            return null;
        }
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
    }

    private StoredLocation readStoredLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return new StoredLocation(
                worldName,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z")
        );
    }

    private void restoreGroundTickets() {
        for (Map.Entry<String, Location> entry : groundLocations.entrySet()) {
            Location location = entry.getValue();
            if (location.getWorld() == null) {
                continue;
            }
            Chunk chunk = location.getChunk();
            String key = chunkKey(chunk);
            Set<String> owners = chunkTicketOwners.computeIfAbsent(key, ignored -> new HashSet<>());
            if (owners.isEmpty()) {
                if (!chunk.addPluginChunkTicket(plugin)) {
                    plugin.getLogger().warning("Could not restore a plugin chunk ticket for Legendary " + entry.getKey() + ".");
                }
                chunksByKey.put(key, chunk);
            }
            owners.add(entry.getKey());
            ticketedChunks.put(entry.getKey(), key);
        }
    }

    private String chunkKey(Chunk chunk) {
        return chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private void releaseTicket(String legendaryId) {
        String key = ticketedChunks.remove(legendaryId);
        if (key == null) {
            return;
        }
        Set<String> owners = chunkTicketOwners.get(key);
        if (owners != null) {
            owners.remove(legendaryId);
            if (owners.isEmpty()) {
                chunkTicketOwners.remove(key);
                Chunk chunk = chunksByKey.remove(key);
                if (chunk != null) {
                    chunk.removePluginChunkTicket(plugin);
                }
            }
        }
    }

    private void markDirty() {
        dirty = true;
    }

    private void saveIfDirty() {
        if (dirty) {
            saveData();
        }
    }

    private void saveData() {
        long now = System.currentTimeMillis();
        for (HoldState state : holdStates.values()) {
            if (state.startedAt != 0L) {
                state.heldMillis += Math.max(0L, now - state.startedAt);
                state.startedAt = now;
            }
        }
        data.set("active-stashes", null);
        for (Map.Entry<UUID, Stash> entry : stashes.entrySet()) {
            Stash stash = entry.getValue();
            String root = "active-stashes." + entry.getKey();
            data.set(root + ".player-uuid", stash.playerId().toString());
            data.set(root + ".player-name", stash.playerName());
            data.set(root + ".legendary-id", stash.legendaryId());
            data.set(root + ".item-bytes", stash.itemBytes());
            writeLocation(root + ".stash-location", stash.stashLocation());
            writeLocation(root + ".logout-location", stash.logoutLocation());
            data.set(root + ".stashed-at", stash.stashedAt());
            data.set(root + ".expires-at", stash.expiresAt());
        }
        data.set("player-cooldowns", null);
        for (Map.Entry<UUID, Long> entry : playerCooldowns.entrySet()) {
            data.set("player-cooldowns." + entry.getKey() + ".cooldown-until", entry.getValue());
        }
        data.set("item-cooldowns", null);
        for (Map.Entry<String, Long> entry : itemCooldowns.entrySet()) {
            data.set("item-cooldowns." + entry.getKey() + ".cooldown-until", entry.getValue());
        }
        data.set("holds", null);
        for (Map.Entry<String, HoldState> entry : holdStates.entrySet()) {
            HoldState state = entry.getValue();
            String root = "holds." + entry.getKey();
            data.set(root + ".holder-uuid", state.holderId == null ? null : state.holderId.toString());
            data.set(root + ".held-millis", state.heldMillis);
            data.set(root + ".started-at", state.startedAt);
        }
        data.set("ground-locations", null);
        for (Map.Entry<String, Location> entry : groundLocations.entrySet()) {
            writeLocation("ground-locations." + entry.getKey(), entry.getValue());
        }
        try {
            data.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save stashes.yml: " + exception.getMessage());
        }
    }

    private void writeLocation(String path, Location location) {
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
    }

    private void writeLocation(String path, StoredLocation location) {
        data.set(path + ".world", location.worldName());
        data.set(path + ".x", location.x());
        data.set(path + ".y", location.y());
        data.set(path + ".z", location.z());
    }
}
