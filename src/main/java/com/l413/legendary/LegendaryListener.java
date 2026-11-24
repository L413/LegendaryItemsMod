package com.l413.legendary;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
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
    private static final long MJOLNIR_LIGHTNING_COOLDOWN_MILLIS = 15_000L;
    private static final long MJOLNIR_SKY_LIGHTNING_COOLDOWN_MILLIS = 90_000L;
    private static final long ASTRAL_WARD_COOLDOWN_MILLIS = 30_000L;
    private static final long SHURIKEN_SPECIAL_COOLDOWN_MILLIS = 8_000L;
    private static final int SHURIKEN_ITEM_COOLDOWN_TICKS = 20;
    private static final int SHURIKEN_BURST_SIZE = 3;
    private static final int SHURIKEN_SPECIAL_SIZE = 12;
    private static final double SHURIKEN_DAMAGE = 10.0;
    private final LegendaryPlugin plugin;
    private final Map<UUID, BlockFace> excavatorMiningFaces = new HashMap<>();
    private final Map<UUID, Long> mjolnirLightningCooldowns = new HashMap<>();
    private final Map<String, Map<UUID, Long>> abilityCooldowns = new HashMap<>();
    private final Set<UUID> areaBreaking = new HashSet<>();

    public LegendaryListener(LegendaryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removeBossBar(event.getPlayer());
        abilityCooldowns.values().forEach(cooldowns -> cooldowns.remove(event.getPlayer().getUniqueId()));
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
        if (!tryUseAbility(player, "mjolnir_sky", MJOLNIR_SKY_LIGHTNING_COOLDOWN_MILLIS)) {
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
        launchPlayerNextTick(player, new Vector(0.0, 2.4, 0.0));
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
        if (!tryUseAbility(player, "astral_ward", ASTRAL_WARD_COOLDOWN_MILLIS)) {
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120, 0, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, true, false, true));
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

    private void activateShurikenBurst(Player player) {
        boolean cooldownsEnabled = player.getGameMode() != org.bukkit.GameMode.CREATIVE;
        if (cooldownsEnabled && player.hasCooldown(Material.SNOWBALL)) {
            return;
        }
        if (cooldownsEnabled) {
            player.setCooldown(Material.SNOWBALL, SHURIKEN_ITEM_COOLDOWN_TICKS);
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
                if (++shots >= SHURIKEN_BURST_SIZE) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void activateShurikenSpecial(Player player) {
        if (!tryUseAbility(player, "shuriken_special", SHURIKEN_SPECIAL_COOLDOWN_MILLIS)) {
            return;
        }

        Location center = player.getEyeLocation();
        for (int i = 0; i < SHURIKEN_SPECIAL_SIZE; i++) {
            if (!damageShuriken(player)) {
                break;
            }
            double angle = 2.0 * Math.PI * i / SHURIKEN_SPECIAL_SIZE;
            Vector direction = new Vector(Math.cos(angle), 0.12, Math.sin(angle)).normalize();
            throwShuriken(player, direction, 1.5);
        }

        for (Entity entity : player.getNearbyEntities(6.0, 4.0, 6.0)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) {
                continue;
            }
            Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (knockback.lengthSquared() < 0.001) {
                knockback = player.getLocation().getDirection().clone();
            }
            knockback.setY(0.75).normalize().multiply(1.6);
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

    private boolean tryUseAbility(Player player, String abilityId, long cooldownMillis) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        Map<UUID, Long> cooldowns = abilityCooldowns.computeIfAbsent(abilityId, key -> new HashMap<>());
        long now = System.currentTimeMillis();
        long nextUse = cooldowns.getOrDefault(playerId, 0L);
        if (now < nextUse) {
            long secondsRemaining = (nextUse - now + 999L) / 1000L;
            player.sendActionBar(Component.text(
                    "Ability ready in " + secondsRemaining + "s",
                    NamedTextColor.YELLOW
            ));
            return false;
        }

        cooldowns.put(playerId, now + cooldownMillis);
        return true;
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
        if (player.isSneaking()
                && !player.isOnGround()
                && player.getVelocity().getY() < -0.08
                && "astral_boots".equals(plugin.getLegendaryId(player.getInventory().getBoots()))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, true, false, true));
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
                announceDestructionNextTick(id, cause);
            }
        } else if (event.getEntity() instanceof Trident trident) {
            String id = plugin.getLegendaryId(trident.getItemStack());
            if (id != null) {
                announceDestructionNextTick(id, cause);
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
            case "mjolnir" -> applyMjolnirHit(attacker, target);
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
            target.damage(SHURIKEN_DAMAGE, attacker);
        } else {
            target.damage(SHURIKEN_DAMAGE);
        }
    }

    private void applyAstralHit(Player attacker, LivingEntity target) {
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0));
        if (ThreadLocalRandom.current().nextDouble() < 0.15) {
            target.damage(2.0);
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

    private void applyMjolnirHit(Player attacker, LivingEntity target) {
        if (attacker.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            strikeMjolnirTarget(target);
            return;
        }

        long now = System.currentTimeMillis();
        long nextAvailable = mjolnirLightningCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        if (now < nextAvailable) {
            return;
        }

        mjolnirLightningCooldowns.put(
                attacker.getUniqueId(),
                now + MJOLNIR_LIGHTNING_COOLDOWN_MILLIS
        );
        strikeMjolnirTarget(target);
    }

    private void strikeMjolnirTarget(LivingEntity target) {
        target.getWorld().strikeLightning(target.getLocation());
        var effectLocation = target.getLocation().add(0.0, 1.0, 0.0);
        for (int strike = 0; strike < 4; strike++) {
            double angle = (Math.PI * 2.0 * strike) / 4.0;
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
            itemEntity.setGlowing(true);
            itemEntity.setUnlimitedLifetime(true);
            itemEntity.setInvulnerable(true);
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

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            String pickedId = plugin.getLegendaryId(event.getItem().getItemStack());
            if (pickedId != null) {
                plugin.transferLegendaryOwnership(pickedId, player);
            }
        }
        String id = plugin.getLegendaryId(event.getItem().getItemStack());
        if (id == null) {
            return;
        }

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
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item itemEntity) {
            String id = plugin.getLegendaryId(itemEntity.getItemStack());
            if (id != null && event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                itemEntity.remove();
                Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary(id, "lost to the void"));
            } else if (id != null) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof Trident trident
                && plugin.getLegendaryId(trident.getItemStack()) != null) {
            String id = plugin.getLegendaryId(trident.getItemStack());
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary(id, "lost to the void"));
            } else {
                event.setCancelled(true);
            }
        }
    }

    private void announceDestructionNextTick(String id, EntityRemoveEvent.Cause cause) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary(
                id,
                cause == EntityRemoveEvent.Cause.OUT_OF_WORLD ? "lost to the void" : "destroyed"
        ));
    }

}