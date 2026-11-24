package com.l413.legendary;

import com.destroystokyo.paper.ParticleBuilder;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class LegendaryPlugin extends JavaPlugin {
    // Tune these two constants to adjust search area size and boss bar trigger range
    private static final int COORD_ROUND_STEP = 250;
    private static final double PROXIMITY_RADIUS = 250.0;

    private NamespacedKey itemKey;
    private NamespacedKey thrownTridentKey;
    private LegendaryRegistry registry;
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private final Map<UUID, TrackedStormPiercer> stormPiercerDisplays = new HashMap<>();

    private record TrackedTarget(String id, String displayName, Location location, UUID holderUuid, String statusLabel) {}

    private static final class TrackedStormPiercer {
        private final ItemDisplay display;
        private BukkitTask task;

        private TrackedStormPiercer(ItemDisplay display) {
            this.display = display;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.itemKey = new NamespacedKey(this, "legendary_id");
        this.thrownTridentKey = new NamespacedKey(this, "thrown_trident");
        this.registry = new LegendaryRegistry(this);

        Bukkit.getPluginManager().registerEvents(new LegendaryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LegendaryStorageListener(this), this);

        this.registerCommand("givelegendary", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (!source.getSender().isOp()) {
                    source.getSender().sendMessage(Component.text("You must be an operator to use this command.", NamedTextColor.RED));
                    return;
                }
                if (!(source.getSender() instanceof Player player)) {
                    source.getSender().sendMessage(Component.text("Only players can receive legendary items.", NamedTextColor.RED));
                    return;
                }
                if (args.length != 1) {
                    sendAvailableIds(source);
                    return;
                }

                LegendaryItem item = registry.getById(args[0].toLowerCase());
                if (item == null) {
                    sendAvailableIds(source);
                    return;
                }

                player.getInventory().addItem(item.createItemStack(LegendaryPlugin.this));
                player.sendMessage(Component.text("Gave " + item.displayName() + " (" + item.id() + ").", NamedTextColor.GREEN));
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                return registry.getAll().stream()
                        .map(LegendaryItem::id)
                        .collect(Collectors.toList());
            }

            private void sendAvailableIds(CommandSourceStack source) {
                String available = registry.getAll().stream()
                        .map(LegendaryItem::id)
                        .collect(Collectors.joining(", "));
                source.getSender().sendMessage(Component.text(
                        "Unknown or missing item ID. Available IDs: " + (available.isEmpty() ? "(none)" : available),
                        NamedTextColor.RED
                ));
            }
        });

        // Fast visual loop (every 10 ticks / 0.5s): sky beacons, glowing, and proximity boss bar
        Bukkit.getScheduler().runTaskTimer(this, this::updateBeaconsAndProximity, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, this::applyLegendaryOverloadPenalty, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::applyHeldAndArmorEffects, 20L, 20L);

        // Scoreboard refresh loop (every 40 ticks / 2s): updates rounded coordinates sidebar
        Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 20L, 40L);

        getLogger().info("LegendaryWeapons (com.l413) enabled with " + registry.getAll().size() + " legendary item(s).");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeBossBar(player);
            for (World world : Bukkit.getWorlds()) {
                for (Trident trident : world.getEntitiesByClass(Trident.class)) {
                    if (isStormPiercer(trident)) {
                        player.showEntity(this, trident);
                    }
                }
            }
        }
        activeBossBars.clear();
        for (Map.Entry<UUID, TrackedStormPiercer> entry : stormPiercerDisplays.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Trident trident && trident.isValid()) {
                trident.setInvisible(false);
            }
            TrackedStormPiercer tracked = entry.getValue();
            if (tracked.task != null) {
                tracked.task.cancel();
            }
            if (tracked.display.isValid()) {
                tracked.display.remove();
            }
        }
        stormPiercerDisplays.clear();
    }

    public void onStormPiercerLaunched(Trident trident) {
        if (!isStormPiercer(trident)) {
            return;
        }

        ItemDisplay display = trident.getWorld().spawn(trident.getLocation(), ItemDisplay.class);
        display.setItemStack(trident.getItemStack().clone());
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setGlowing(true);
        display.setGlowColorOverride(Color.AQUA);
        display.setTeleportDuration(1);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.getPersistentDataContainer().set(thrownTridentKey, PersistentDataType.STRING, trident.getUniqueId().toString());
        hideStormPiercer(trident);
        trident.setInvisible(true);
        trident.setGlowing(true);
        UUID tridentId = trident.getUniqueId();
        TrackedStormPiercer tracked = new TrackedStormPiercer(display);
        stormPiercerDisplays.put(tridentId, tracked);
        updateStormPiercerDisplay(tridentId);
        tracked.task = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> updateStormPiercerDisplay(tridentId),
                0L,
                1L
        );
    }

    public void hideStormPiercer(Trident trident) {
        if (!isStormPiercer(trident)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideEntity(this, trident);
        }
    }

    public void hideStormPiercersFor(Player player) {
        for (World world : Bukkit.getWorlds()) {
            for (Trident trident : world.getEntitiesByClass(Trident.class)) {
                if (isStormPiercer(trident)) {
                    player.hideEntity(this, trident);
                }
            }
        }
    }

    public void removeStormPiercerDisplay(UUID tridentId) {
        TrackedStormPiercer tracked = stormPiercerDisplays.remove(tridentId);
        removeStormPiercerDisplay(tracked);
    }

    private void updateStormPiercerDisplay(UUID tridentId) {
        TrackedStormPiercer tracked = stormPiercerDisplays.get(tridentId);
        if (tracked == null) {
            return;
        }

        Entity entity = Bukkit.getEntity(tridentId);
        if (!(entity instanceof Trident trident) || !trident.isValid() || trident.isDead()) {
            removeStormPiercerDisplay(tridentId);
            return;
        }

        ItemDisplay display = tracked.display;
        if (!display.isValid()) {
            trident.setInvisible(false);
            removeStormPiercerDisplay(tridentId);
            return;
        }

        if (!trident.isInBlock()) {
            Vector velocity = trident.getVelocity();
            Vector direction = velocity.lengthSquared() > 0.001
                    ? velocity.clone().normalize()
                    : trident.getLocation().getDirection().normalize();
            Vector3f dir = new Vector3f(
                    (float) direction.getX(),
                    (float) direction.getY(),
                    (float) direction.getZ()
            ).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    rotation,
                    new Vector3f(1, 1, 1),
                    new Quaternionf()
            ));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
        }

        Location displayLocation = trident.getLocation();
        displayLocation.setYaw(0.0f);
        displayLocation.setPitch(0.0f);
        display.setTeleportDuration(1);
        display.teleport(displayLocation);
    }

    private void removeStormPiercerDisplay(TrackedStormPiercer tracked) {
        if (tracked == null) {
            return;
        }
        if (tracked.task != null) {
            tracked.task.cancel();
        }
        if (tracked.display.isValid()) {
            tracked.display.remove();
        }
    }

    private List<TrackedTarget> scanActiveTargets() {
        List<TrackedTarget> targets = new ArrayList<>();

        // 1. Check online players carrying a legendary item
        for (Player player : Bukkit.getOnlinePlayers()) {
            String id = findLegendaryInInventory(player);
            if (id != null) {
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
            for (Trident trident : world.getEntitiesByClass(Trident.class)) {
                String id = getLegendaryId(trident.getItemStack());
                LegendaryItem def = id == null ? null : registry.getById(id);
                if (def != null && def.id().equals("storm_piercer")) {
                    targets.add(new TrackedTarget(id, def.displayName(), trident.getLocation(), null, "THROWN"));
                }
            }
        }

        return targets;
    }

    private void updateBeaconsAndProximity() {
        // Apply Glowing + Sky Beacon to holders and dropped items
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (findLegendaryInInventory(player) != null) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                spawnBeaconBeam(player.getLocation());
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Item dropped : world.getEntitiesByClass(Item.class)) {
                if (getLegendaryId(dropped.getItemStack()) != null) {
                    if (!dropped.isGlowing()) {
                        dropped.setGlowing(true);
                        dropped.setUnlimitedLifetime(true);
                    }
                    spawnBeaconBeam(dropped.getLocation());
                }
            }
            for (Trident trident : world.getEntitiesByClass(Trident.class)) {
                if (isStormPiercer(trident)) {
                    trident.setGlowing(true);
                    spawnBeaconBeam(trident.getLocation());
                }
            }
        }

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
        List<TrackedTarget> targets = scanActiveTargets();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (targets.isEmpty()) {
                // Clear tracker scoreboard when no legendary items are currently active/online
                if (player.getScoreboard().getObjective("legendary_track") != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                continue;
            }

            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective(
                    "legendary_track",
                    Criteria.DUMMY,
                    Component.text("⚡ LEGENDARY TRACKER ⚡", NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            int scoreLine = targets.size() * 4;
            for (TrackedTarget target : targets) {
                int approxX = roundCoord(target.location().getBlockX());
                int approxZ = roundCoord(target.location().getBlockZ());
                String dim = formatDimension(target.location().getWorld().getEnvironment());

                obj.getScore("§6§l" + target.displayName()).setScore(scoreLine--);
                obj.getScore("§7Holder: §e" + target.statusLabel()).setScore(scoreLine--);
                obj.getScore("§7Zone: §b~" + approxX + ", ~" + approxZ + " §8(" + dim + ")").setScore(scoreLine--);
                obj.getScore("§r" + " ".repeat(Math.max(1, scoreLine))).setScore(scoreLine--);
            }

            player.setScoreboard(board);
        }
    }

    private int roundCoord(int coord) {
        return Math.round((float) coord / COORD_ROUND_STEP) * COORD_ROUND_STEP;
    }

    private void spawnBeaconBeam(Location base) {
        World world = base.getWorld();
        if (world == null) return;

        double x = base.getX();
        double z = base.getZ();
        int startY = base.getBlockY();
        int maxY = Math.min(world.getMaxHeight(), startY + 220);

        Particle.DustOptions beamCore = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 2.5f);

        for (int y = startY; y <= maxY; y += 2) {
            Location point = new Location(world, x, y, z);

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

    public void removeBossBar(Player player) {
        BossBar bar = activeBossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private String findLegendaryInInventory(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            String id = getLegendaryId(stack);
            if (id != null) return id;
        }
        return null;
    }

    private void applyLegendaryOverloadPenalty() {
        for (Player player : Bukkit.getOnlinePlayers()) {
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

            if (legendaryCount > 1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, true, true));
                player.damage(2.0);
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
            case "astral_sovereign" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 1, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 45, 1, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, true));
            }
            case "void_cleaver" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 45, 0, true, false, true));
            }
            case "storm_piercer" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 45, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 45, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 0, true, false, true));
            }
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 45, 0, true, false, true));
        }
        if ("astral_chestplate".equals(chestplateId)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, true, false, true));
        }
        if ("astral_leggings".equals(leggingsId)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 0, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 45, 0, true, false, true));
        }
        if ("astral_boots".equals(bootsId)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 45, 0, true, false, true));
        }

        if ("astral_helmet".equals(helmetId)
                && "astral_chestplate".equals(chestplateId)
                && "astral_leggings".equals(leggingsId)
                && "astral_boots".equals(bootsId)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 45, 0, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 45, 0, true, false, true));
            if (player.isSprinting()) {
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0, 0.1, 0.0), 2, 0.15, 0.05, 0.15, 0.0);
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

    private boolean isStormPiercer(Trident trident) {
        String id = getLegendaryId(trident.getItemStack());
        LegendaryItem item = id == null ? null : registry.getById(id);
        return item != null && item.id().equals("storm_piercer");
    }

    public boolean isStormPiercerTrident(Trident trident) {
        return isStormPiercer(trident);
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

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public LegendaryRegistry getRegistry() {
        return registry;
    }
}