package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LegendaryListener implements Listener {
    private final LegendaryPlugin plugin;
    private final NamespacedKey combatTagKey;
    private final NamespacedKey safeDropWorldKey;
    private final NamespacedKey safeDropXKey;
    private final NamespacedKey safeDropYKey;
    private final NamespacedKey safeDropZKey;
    private final Map<UUID, BlockFace> excavatorMiningFaces = new HashMap<>();
    private final Set<UUID> areaBreaking = new HashSet<>();
    private final Set<UUID> astralBootSlowFalling = new HashSet<>();

    public LegendaryListener(LegendaryPlugin plugin) {
        this.plugin = plugin;
        this.combatTagKey = new NamespacedKey(plugin, "last_player_combat_damage");
        this.safeDropWorldKey = new NamespacedKey(plugin, "safe_drop_world");
        this.safeDropXKey = new NamespacedKey(plugin, "safe_drop_x");
        this.safeDropYKey = new NamespacedKey(plugin, "safe_drop_y");
        this.safeDropZKey = new NamespacedKey(plugin, "safe_drop_z");
        Bukkit.getScheduler().runTaskTimer(plugin, this::protectDroppedLegendaries, 1L, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removeBossBar(event.getPlayer());
        astralBootSlowFalling.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLegendaryItemBreak(PlayerItemBreakEvent event) {
        String id = plugin.getLegendaryId(event.getBrokenItem());
        if (id != null) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary(id, "broken"));
        }
    }

    @EventHandler
    public void onLegendaryRightClick(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        boolean rightClick = event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) {
            return;
        }

        Player player = event.getPlayer();
        String itemId = plugin.getLegendaryId(player.getInventory().getItemInMainHand());
        if (itemId == null) {
            return;
        }

        if ("mjolnir".equals(itemId)
                && player.isSneaking()
                && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && player.getEyeLocation().getDirection().getY() > 0.0) {
            event.setCancelled(true);
            activateMjolnirSkyLightning(player);
        } else if ("astral_sovereign".equals(itemId) && player.isSneaking()) {
            event.setCancelled(true);
            activateAstralWard(player);
        } else if ("astral_excavator".equals(itemId) && player.isSneaking()) {
            event.setCancelled(true);
            activateExcavatorHaste(player);
        } else if ("storm_shuriken".equals(itemId)) {
            event.setCancelled(true);
            if (player.isSneaking()
                    && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                    && event.getClickedBlock() != null
                    && event.getBlockFace() == BlockFace.UP) {
                activateShurikenSpecial(player);
            } else {
                activateShurikenBurst(player);
            }
        }
    }

    private void activateMjolnirSkyLightning(Player player) {
        long combatTagMillis = configSecondsMillis("abilities.mjolnir.sky-launch.combat-tag-seconds");
        long lastCombatDamage = player.getPersistentDataContainer().getOrDefault(
                combatTagKey,
                PersistentDataType.LONG,
                0L
        );
        if (System.currentTimeMillis() - lastCombatDamage < combatTagMillis) {
            long secondsRemaining = (combatTagMillis - (System.currentTimeMillis() - lastCombatDamage) + 999L) / 1000L;
            player.sendActionBar(Component.text(
                    "Cannot launch while combat-tagged (" + secondsRemaining + "s)",
                    NamedTextColor.RED
            ));
            return;
        }
        if (!tryUseAbility(
                player,
                player.getInventory().getItemInMainHand(),
                "mjolnir_sky",
                configSecondsMillis("abilities.mjolnir.sky-launch.cooldown-seconds")
        )) {
            return;
        }

        Location center = player.getLocation();
        player.getWorld().spawnParticle(
                Particle.FLASH,
                center.clone().add(0.0, 1.0, 0.0),
                2,
                0.6,
                0.5,
                0.6,
                0.0,
                Color.WHITE
        );
        player.getWorld().spawnParticle(
                Particle.GUST,
                center.clone().add(0.0, 1.0, 0.0),
                5,
                1.0,
                0.7,
                1.0,
                0.0
        );
        launchPlayerNextTick(player, new Vector(
                0.0,
                plugin.getConfig().getDouble("abilities.mjolnir.sky-launch.vertical-velocity", 2.4),
                0.0
        ));
        UUID playerId = player.getUniqueId();
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                Player ascendingPlayer = Bukkit.getPlayer(playerId);
                if (ascendingPlayer == null || !ascendingPlayer.isOnline() || tick >= 30) {
                    cancel();
                    return;
                }

                Location position = ascendingPlayer.getLocation();
                double angle = tick * Math.PI / 5.0;
                for (int strike = 0; strike < 3; strike++) {
                    double strikeAngle = angle + strike * (Math.PI * 2.0 / 3.0);
                    double radius = strike == 1 ? 3.0 : 2.0;
                    Location boltLocation = position.clone().add(
                            Math.cos(strikeAngle) * radius,
                            3.0 + (tick % 4) * 1.5,
                            Math.sin(strikeAngle) * radius
                    );
                    ascendingPlayer.getWorld().strikeLightningEffect(boltLocation);
                }

                for (int point = 0; point < 4; point++) {
                    double spiralAngle = angle + point * (Math.PI / 2.0);
                    double radius = 1.0 + (point % 2) * 0.35;
                    Location particleLocation = position.clone().add(
                            Math.cos(spiralAngle) * radius,
                            0.4 + (point * 0.55),
                            Math.sin(spiralAngle) * radius
                    );
                    ascendingPlayer.getWorld().spawnParticle(
                            Particle.ELECTRIC_SPARK,
                            particleLocation,
                            8,
                            0.12,
                            0.18,
                            0.12,
                            0.04
                    );
                    ascendingPlayer.getWorld().spawnParticle(
                            Particle.END_ROD,
                            particleLocation,
                            2,
                            0.08,
                            0.12,
                            0.08,
                            0.01
                    );
                }
                ascendingPlayer.setFallDistance(0.0f);
                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
        player.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.65f);
        player.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 0.8f);
    }

    private void activateAstralWard(Player player) {
        if (!tryUseAbility(
                player,
                player.getInventory().getItemInMainHand(),
                "astral_ward",
                configSecondsMillis("abilities.astral_sovereign.ward.cooldown-seconds")
        )) {
            return;
        }

        plugin.applyConfiguredEffects(player, "abilities.astral_sovereign.ward.effects");
        Location effectLocation = player.getLocation().add(0.0, 1.0, 0.0);
        player.getWorld().spawnParticle(
                Particle.DUST,
                effectLocation,
                40,
                0.65,
                0.9,
                0.65,
                new Particle.DustOptions(plugin.getLegendaryColor("astral_sovereign"), 1.5f)
        );
        player.getWorld().spawnParticle(Particle.END_ROD, effectLocation, 16, 0.5, 0.8, 0.5, 0.03);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.35f);
    }

    private void activateExcavatorHaste(Player player) {
        if (!tryUseAbility(
                player,
                player.getInventory().getItemInMainHand(),
                "excavator_haste",
                configSecondsMillis("abilities.astral_excavator.haste.cooldown-seconds")
        )) {
            return;
        }
        int amplifier = Math.max(0, plugin.getConfig().getInt("abilities.astral_excavator.haste.amplifier", 100) - 1);
        int durationTicks = Math.max(1, (int) Math.round(
                plugin.getConfig().getDouble("abilities.astral_excavator.haste.duration-seconds", 10.0) * 20.0
        ));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationTicks, amplifier, true, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);
    }

    private void activateShurikenBurst(Player player) {
        if (!tryUseAbility(
                player,
                player.getInventory().getItemInMainHand(),
                "shuriken_burst",
                configSecondsMillis("abilities.storm_shuriken.burst.cooldown-seconds")
        )) {
            return;
        }

        new BukkitRunnable() {
            private int shots;

            @Override
            public void run() {
                if (!player.isOnline()
                        || !"storm_shuriken".equals(plugin.getLegendaryId(player.getInventory().getItemInMainHand()))) {
                    cancel();
                    return;
                }
                if (!damageShuriken(player)) {
                    cancel();
                    return;
                }
                Vector direction = player.getEyeLocation().getDirection().normalize();
                double spread = 0.045;
                direction.add(new Vector(
                        ThreadLocalRandom.current().nextDouble(-spread, spread),
                        ThreadLocalRandom.current().nextDouble(-spread, spread),
                        ThreadLocalRandom.current().nextDouble(-spread, spread)
                )).normalize();
                throwShuriken(player, direction, 1.8);
                if (++shots >= Math.max(1, plugin.getConfig().getInt(
                        "abilities.storm_shuriken.burst.projectile-count",
                        3
                ))) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void activateShurikenSpecial(Player player) {
        if (!tryUseAbility(
                player,
                player.getInventory().getItemInMainHand(),
                "shuriken_special",
                configSecondsMillis("abilities.storm_shuriken.radial.cooldown-seconds")
        )) {
            return;
        }

        Location center = player.getEyeLocation();
        int projectileCount = Math.max(1, plugin.getConfig().getInt("abilities.storm_shuriken.radial.projectile-count", 12));
        for (int i = 0; i < projectileCount; i++) {
            if (!damageShuriken(player)) {
                break;
            }
            double angle = 2.0 * Math.PI * i / projectileCount;
            Vector direction = new Vector(Math.cos(angle), 0.12, Math.sin(angle)).normalize();
            throwShuriken(player, direction, 1.5);
        }

        double radius = Math.max(0.0, plugin.getConfig().getDouble("abilities.storm_shuriken.radial.radius", 6.0));
        double horizontalKnockback = plugin.getConfig().getDouble(
                "abilities.storm_shuriken.radial.horizontal-knockback",
                1.6
        );
        double verticalKnockback = plugin.getConfig().getDouble(
                "abilities.storm_shuriken.radial.vertical-knockback",
                0.75
        );
        double radialDamage = Math.max(0.0, plugin.getConfig().getDouble("abilities.storm_shuriken.radial.damage", 0.0));
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) {
                continue;
            }
            if (radialDamage > 0.0) {
                target.damage(radialDamage, player);
            }
            Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (knockback.lengthSquared() < 0.001) {
                knockback = player.getLocation().getDirection().clone();
            }
            knockback.setY(verticalKnockback).normalize().multiply(horizontalKnockback);
            target.setVelocity(knockback);
        }

        player.getWorld().spawnParticle(Particle.GUST, center, 4, 0.5, 0.4, 0.5, 0.0);
        player.getWorld().spawnParticle(Particle.CLOUD, center, 24, 0.7, 0.4, 0.7, 0.12);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.4f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.2f, 0.7f);
    }

    private boolean damageShuriken(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!"storm_shuriken".equals(plugin.getLegendaryId(weapon))) {
            return false;
        }
        int damage = weapon.getDataOrDefault(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE, 0) + 1;
        int maxDamage = weapon.getDataOrDefault(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE, 0);
        if (maxDamage <= 0 || damage >= maxDamage) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary("storm_shuriken", "broken"));
            return false;
        }
        weapon.setData(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE, damage);
        player.getInventory().setItemInMainHand(weapon);
        return true;
    }

    private void throwShuriken(Player player, Vector direction, double speed) {
        Snowball projectile = player.getWorld().spawn(player.getEyeLocation(), Snowball.class);
        projectile.setShooter(player);
        ItemStack thrownItem = player.getInventory().getItemInMainHand().clone();
        thrownItem.setAmount(1);
        projectile.setItem(thrownItem);
        projectile.getPersistentDataContainer().set(
                plugin.getShurikenProjectileKey(),
                PersistentDataType.BYTE,
                (byte) 1
        );
        projectile.setVelocity(direction.clone().normalize().multiply(speed));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.65f, 1.6f);
    }

    private void launchPlayerNextTick(Player player, Vector velocity) {
        UUID playerId = player.getUniqueId();
        Vector launchVelocity = velocity.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                return;
            }
            onlinePlayer.setVelocity(launchVelocity);
            onlinePlayer.setFallDistance(0.0f);
        });
    }

    private boolean tryUseAbility(Player player, ItemStack item, String abilityId, long cooldownMillis) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return true;
        }

        if (item == null || item.getType().isAir()) {
            return false;
        }

        NamespacedKey cooldownKey = new NamespacedKey(plugin, "cooldown_" + abilityId);
        var meta = item.getItemMeta();
        var cooldownData = meta.getPersistentDataContainer();
        long now = System.currentTimeMillis();
        long nextUse = cooldownData.getOrDefault(cooldownKey, PersistentDataType.LONG, 0L);
        if (now < nextUse) {
            long secondsRemaining = (nextUse - now + 999L) / 1000L;
            player.sendActionBar(Component.text(
                    "Ability ready in " + secondsRemaining + "s",
                    NamedTextColor.YELLOW
            ));
            return false;
        }

        cooldownData.set(cooldownKey, PersistentDataType.LONG, now + cooldownMillis);
        item.setItemMeta(meta);
        return true;
    }

    private long configSecondsMillis(String path) {
        double seconds = plugin.getConfig().getDouble(path, 0.0);
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            plugin.getLogger().warning("Invalid cooldown duration at '" + path + "'; treating it as zero.");
            return 0L;
        }
        return Math.round(seconds * 1000.0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamagedByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0.0) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker != null && !attacker.equals(victim)) {
            victim.getPersistentDataContainer().set(combatTagKey, PersistentDataType.LONG, System.currentTimeMillis());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        String id = plugin.getLegendaryId(event.getItemInHand());
        if ("astral_excavator".equals(id)) {
            excavatorMiningFaces.put(event.getPlayer().getUniqueId(), event.getBlockFace());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (areaBreaking.contains(playerId)
                || !plugin.getConfig().getBoolean("abilities.astral_excavator.area-mining-enabled", true)
                || !"astral_excavator".equals(plugin.getLegendaryId(player.getInventory().getItemInMainHand()))) {
            return;
        }

        BlockFace face = excavatorMiningFaces.remove(playerId);
        if (face == null) {
            return;
        }

        Set<Block> plane = new HashSet<>();
        for (int first = -1; first <= 1; first++) {
            for (int second = -1; second <= 1; second++) {
                if (first == 0 && second == 0) {
                    continue;
                }
                Block adjacent = switch (face) {
                    case UP, DOWN -> event.getBlock().getRelative(first, 0, second);
                    case EAST, WEST -> event.getBlock().getRelative(0, first, second);
                    case NORTH, SOUTH -> event.getBlock().getRelative(first, second, 0);
                    default -> null;
                };
                if (adjacent != null) {
                    plane.add(adjacent);
                }
            }
        }

        areaBreaking.add(playerId);
        try {
            for (Block block : plane) {
                if (block.getType().isAir()
                        || block.getType().getHardness() < 0.0f
                        || block.getState() instanceof Container) {
                    continue;
                }
                player.breakBlock(block);
            }
        } finally {
            areaBreaking.remove(playerId);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        boolean shouldHaveSlowFalling = player.isSneaking()
                && !player.isOnGround()
                && player.getVelocity().getY() < -0.08
                && "astral_boots".equals(plugin.getLegendaryId(player.getInventory().getBoots()));
        if (shouldHaveSlowFalling) {
            plugin.applyConfiguredEffect(player, "effects.astral_boots.slow-falling");
            astralBootSlowFalling.add(playerId);
        } else if (astralBootSlowFalling.remove(playerId)) {
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking() && astralBootSlowFalling.remove(event.getPlayer().getUniqueId())) {
            event.getPlayer().removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        if (event.getArrow() instanceof Trident trident) {
            String id = plugin.getLegendaryId(trident.getItemStack());
            if (id != null) {
                plugin.transferLegendaryOwnership(id, event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreativeInventoryChange(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Set<String> candidateIds = new HashSet<>();
        String currentId = plugin.getLegendaryId(event.getCurrentItem());
        String cursorId = plugin.getLegendaryId(event.getCursor());
        if (currentId != null) {
            candidateIds.add(currentId);
        }
        if (cursorId != null) {
            candidateIds.add(cursorId);
        }
        if (candidateIds.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String id : candidateIds) {
                if (!plugin.hasLegendaryItem(id)) {
                    plugin.destroyLegendary(id, "deleted in Creative mode");
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemoved(EntityRemoveEvent event) {
        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause != EntityRemoveEvent.Cause.OUT_OF_WORLD) {
            return;
        }

        if (event.getEntity() instanceof Item item) {
            String id = plugin.getLegendaryId(item.getItemStack());
            if (id != null) {
                Location safe = readSafeDropLocation(item);
                rescueLegendaryEntity(
                        item.getItemStack(),
                        item.getWorld(),
                        safe == null ? findSafeFallback(item.getWorld(), item.getLocation()) : safe
                );
            }
        } else if (event.getEntity() instanceof Trident trident) {
            if (plugin.getLegendaryId(trident.getItemStack()) != null) {
                rescueLegendaryEntity(
                        trident.getItemStack(),
                        trident.getWorld(),
                        findSafeFallback(trident.getWorld(), trident.getLocation())
                );
            }
        } else if (event.getEntity() instanceof Snowball projectile
                && projectile.getPersistentDataContainer().has(plugin.getShurikenProjectileKey(), PersistentDataType.BYTE)) {
            announceDestructionNextTick("storm_shuriken", cause);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Set<String> itemIds = new HashSet<>();
        for (ItemStack stack : event.getDrops()) {
            String id = plugin.getLegendaryId(stack);
            if (id != null) {
                itemIds.add(id);
            }
        }
        for (ItemStack stack : event.getEntity().getInventory().getContents()) {
            String id = plugin.getLegendaryId(stack);
            if (id != null) {
                itemIds.add(id);
            }
        }
        if (!itemIds.isEmpty()) {
            plugin.announceLegendaryDeath(event.getEntity(), itemIds);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)
                || attacker.equals(target)) {
            return;
        }

        String weaponId = plugin.getHeldLegendaryId(attacker);
        if (weaponId == null) {
            return;
        }

        switch (weaponId) {
            case "astral_sovereign" -> applyAstralHit(attacker, target);
            case "mjolnir" -> applyMjolnirHit(attacker, target, getHeldLegendaryItem(attacker, weaponId));
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball projectile)
                || !projectile.getPersistentDataContainer().has(plugin.getShurikenProjectileKey(), PersistentDataType.BYTE)) {
            return;
        }

        Location impact = projectile.getLocation();
        projectile.getWorld().spawnParticle(
                Particle.DUST,
                impact,
                14,
                0.22,
                0.22,
                0.22,
                new Particle.DustOptions(Color.fromRGB(0xFFD700), 1.2f)
        );
        projectile.getWorld().playSound(impact, Sound.ITEM_TRIDENT_HIT, 0.8f, 1.5f);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary("storm_shuriken", "broken"));
        if (!(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        if (projectile.getShooter() instanceof Entity thrower && thrower.equals(target)) {
            return;
        }
        if (projectile.getShooter() instanceof Player attacker) {
            target.damage(
                    Math.max(0.0, plugin.getConfig().getDouble("abilities.storm_shuriken.projectile-damage", 6.0)),
                    attacker
            );
        } else {
            target.damage(Math.max(
                    0.0,
                    plugin.getConfig().getDouble("abilities.storm_shuriken.projectile-damage", 6.0)
            ));
        }
    }

    private void applyAstralHit(Player attacker, LivingEntity target) {
        plugin.applyConfiguredEffects(attacker, "abilities.astral_sovereign.on-hit.effects");
        double chance = plugin.getConfig().getDouble(
                "abilities.astral_sovereign.on-hit.astral-burst-chance",
                0.15
        );
        if (ThreadLocalRandom.current().nextDouble() < Math.max(0.0, Math.min(1.0, chance))) {
            double bonusDamage = Math.max(0.0, plugin.getConfig().getDouble(
                    "abilities.astral_sovereign.on-hit.bonus-damage",
                    2.0
            ));
            target.damage(bonusDamage);
            target.getWorld().spawnParticle(
                    Particle.DUST,
                    target.getLocation().add(0.0, 1.0, 0.0),
                    18,
                    0.35,
                    0.5,
                    0.35,
                    new Particle.DustOptions(plugin.getLegendaryColor("astral_sovereign"), 1.2f)
            );
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0.0, 1.0, 0.0), 8, 0.35, 0.5, 0.35, 0.02);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1.0f, 1.0f);
        }
    }

    private ItemStack getHeldLegendaryItem(Player player, String itemId) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (itemId.equals(plugin.getLegendaryId(mainHand))) {
            return mainHand;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return itemId.equals(plugin.getLegendaryId(offHand)) ? offHand : null;
    }

    private void applyMjolnirHit(Player attacker, LivingEntity target, ItemStack mjolnir) {
        if (attacker.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            strikeMjolnirTarget(target);
            return;
        }

        if (tryUseAbility(
                attacker,
                mjolnir,
                "mjolnir_lightning",
                configSecondsMillis("abilities.mjolnir.on-hit-lightning.cooldown-seconds")
        )) {
            strikeMjolnirTarget(target);
        }
    }

    private void strikeMjolnirTarget(LivingEntity target) {
        target.getWorld().strikeLightning(target.getLocation());
        var effectLocation = target.getLocation().add(0.0, 1.0, 0.0);
        int visualStrikeCount = Math.max(0, plugin.getConfig().getInt(
                "abilities.mjolnir.on-hit-lightning.visual-strike-count",
                4
        ));
        for (int strike = 0; strike < visualStrikeCount; strike++) {
            double angle = visualStrikeCount == 0 ? 0.0 : (Math.PI * 2.0 * strike) / visualStrikeCount;
            Location arcLocation = target.getLocation().clone().add(
                    Math.cos(angle) * 1.8,
                    0.0,
                    Math.sin(angle) * 1.8
            );
            target.getWorld().strikeLightningEffect(arcLocation);
        }
        target.getWorld().spawnParticle(
                Particle.FLASH,
                effectLocation,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                Color.WHITE
        );
        target.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                effectLocation,
                48,
                0.8,
                1.0,
                0.8,
                0.18
        );
        target.getWorld().spawnParticle(
                Particle.CLOUD,
                target.getLocation(),
                30,
                0.7,
                0.15,
                0.7,
                0.12
        );
        target.getWorld().spawnParticle(
                Particle.GUST,
                effectLocation,
                2,
                0.35,
                0.4,
                0.35,
                0.0
        );
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.7f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.8f, 0.8f);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.6f, 0.75f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.7f);
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

        if (!(event.getWhoClicked() instanceof Player crafter)
                || !plugin.claimLegendary(id, crafter)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(Component.text("That legendary item is already claimed.", NamedTextColor.RED));
            return;
        }
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
        Location location = crafter.getLocation().add(0.0, 1.0, 0.0);
        crafter.getWorld().spawnParticle(
                Particle.DUST,
                location,
                48,
                0.6,
                0.8,
                0.6,
                new Particle.DustOptions(plugin.getLegendaryColor(id), 1.8f)
        );
        crafter.getWorld().spawnParticle(Particle.END_ROD, location, 24, 0.45, 0.7, 0.45, 0.03);
        crafter.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        if (plugin.getLegendaryId(itemEntity.getItemStack()) != null) {
            ItemStack stack = itemEntity.getItemStack();
            plugin.refreshLegendaryItemStack(stack);
            itemEntity.setItemStack(stack);
            itemEntity.setGlowing(true);
            itemEntity.setUnlimitedLifetime(true);
            itemEntity.setInvulnerable(true);
            if (isSafeDropLocation(itemEntity.getLocation())) {
                storeSafeDropLocation(itemEntity, itemEntity.getLocation());
            } else {
                storeSafeDropLocation(itemEntity, findSafeFallback(itemEntity.getWorld(), itemEntity.getLocation()));
            }
            String id = plugin.getLegendaryId(itemEntity.getItemStack());
            Location location = itemEntity.getLocation();
            itemEntity.getWorld().spawnParticle(
                    Particle.DUST,
                    location,
                    12,
                    0.2,
                    0.25,
                    0.2,
                    new Particle.DustOptions(plugin.getLegendaryColor(id), 1.2f)
            );
            itemEntity.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        String id = plugin.getLegendaryId(event.getItem().getItemStack());
        if (id == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        plugin.transferLegendaryOwnership(id, player);

        Location location = event.getEntity().getLocation().add(0.0, 1.0, 0.0);
        event.getEntity().getWorld().spawnParticle(
                Particle.DUST,
                location,
                18,
                0.35,
                0.45,
                0.35,
                new Particle.DustOptions(plugin.getLegendaryColor(id), 1.4f)
        );
        event.getEntity().getWorld().spawnParticle(Particle.END_ROD, location, 8, 0.25, 0.4, 0.25, 0.02);
        event.getEntity().getWorld().playSound(location, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLegendaryItemDespawn(ItemDespawnEvent event) {
        if (plugin.getLegendaryId(event.getEntity().getItemStack()) != null) {
            event.setCancelled(true);
            event.getEntity().setUnlimitedLifetime(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item itemEntity) {
            if (plugin.getLegendaryId(itemEntity.getItemStack()) != null) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof Trident trident
                && plugin.getLegendaryId(trident.getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    private void protectDroppedLegendaries() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (plugin.getLegendaryId(item.getItemStack()) == null) {
                    continue;
                }
                item.setInvulnerable(true);
                item.setUnlimitedLifetime(true);
                Location location = item.getLocation();
                if (location.getY() < world.getMinHeight() + 2.0 || isHazardousLocation(location)) {
                    Location safe = readSafeDropLocation(item);
                    if (safe == null) {
                        safe = findSafeFallback(world, location);
                    }
                    item.teleport(safe);
                    item.setVelocity(new Vector());
                } else if (isSafeDropLocation(location)) {
                    storeSafeDropLocation(item, location);
                }
            }
        }
    }

    private boolean isSafeDropLocation(Location location) {
        if (location.getWorld() == null || location.getY() < location.getWorld().getMinHeight() + 2.0) {
            return false;
        }
        Material current = location.getBlock().getType();
        Material below = location.clone().subtract(0.0, 0.1, 0.0).getBlock().getType();
        if (isHazardousDropMaterial(current) || isHazardousDropMaterial(below)) {
            return false;
        }
        return below.isSolid() || location.getWorld().getPlayers().stream()
                .anyMatch(player -> player.getLocation().distanceSquared(location) <= 64.0);
    }

    private boolean isHazardousLocation(Location location) {
        return isHazardousDropMaterial(location.getBlock().getType())
                || isHazardousDropMaterial(location.clone().subtract(0.0, 0.1, 0.0).getBlock().getType());
    }

    private boolean isHazardousDropMaterial(Material material) {
        return material == Material.LAVA
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.CACTUS
                || material == Material.MAGMA_BLOCK;
    }

    private void storeSafeDropLocation(Item item, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        var data = item.getPersistentDataContainer();
        data.set(safeDropWorldKey, PersistentDataType.STRING, location.getWorld().getName());
        data.set(safeDropXKey, PersistentDataType.DOUBLE, location.getX());
        data.set(safeDropYKey, PersistentDataType.DOUBLE, location.getY());
        data.set(safeDropZKey, PersistentDataType.DOUBLE, location.getZ());
    }

    private Location readSafeDropLocation(Item item) {
        var data = item.getPersistentDataContainer();
        String worldName = data.get(safeDropWorldKey, PersistentDataType.STRING);
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null
                || !data.has(safeDropXKey, PersistentDataType.DOUBLE)
                || !data.has(safeDropYKey, PersistentDataType.DOUBLE)
                || !data.has(safeDropZKey, PersistentDataType.DOUBLE)) {
            return null;
        }
        return new Location(
                world,
                data.get(safeDropXKey, PersistentDataType.DOUBLE),
                data.get(safeDropYKey, PersistentDataType.DOUBLE),
                data.get(safeDropZKey, PersistentDataType.DOUBLE)
        );
    }

    private Location findSafeFallback(World world, Location near) {
        int highestY = world.getHighestBlockYAt(near);
        if (highestY > world.getMinHeight() + 1) {
            Location candidate = new Location(world, near.getBlockX() + 0.5, highestY + 1.1, near.getBlockZ() + 0.5);
            if (!isHazardousDropMaterial(candidate.getBlock().getType())) {
                return candidate;
            }
        }
        Location spawn = world.getSpawnLocation();
        int spawnY = world.getHighestBlockYAt(spawn);
        if (spawnY > world.getMinHeight() + 1) {
            spawn.setY(spawnY + 1.1);
        }
        return spawn;
    }

    private void rescueLegendaryEntity(ItemStack stack, World world, Location safeLocation) {
        ItemStack restoredStack = stack.clone();
        Location destination = safeLocation == null ? findSafeFallback(world, world.getSpawnLocation()) : safeLocation.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Item restored = world.dropItem(destination, restoredStack);
            restored.setInvulnerable(true);
            restored.setUnlimitedLifetime(true);
            restored.setGlowing(true);
            storeSafeDropLocation(restored, destination);
        });
    }

    private void announceDestructionNextTick(String id, EntityRemoveEvent.Cause cause) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary(
                id,
                cause == EntityRemoveEvent.Cause.OUT_OF_WORLD ? "lost to the void" : "destroyed"
        ));
    }

}