package com.l413.legendary;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
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
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LegendaryListener implements Listener {
    private final LegendaryPlugin plugin;
    private final Map<UUID, BlockFace> titanMiningFaces = new HashMap<>();
    private final Set<UUID> areaBreaking = new HashSet<>();

    public LegendaryListener(LegendaryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removeBossBar(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.hideStormPiercersFor(event.getPlayer());
    }

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Trident trident) {
            plugin.hideStormPiercer(trident);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident) {
            plugin.onStormPiercerLaunched(trident);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        String id = plugin.getLegendaryId(event.getItemInHand());
        if ("astral_excavator".equals(id)
                && event.getPlayer().getGameMode() == GameMode.SURVIVAL
                && event.getBlock().getType().getHardness() >= 0.0f) {
            event.setInstaBreak(true);
        } else if ("titan_pickaxe".equals(id)) {
            titanMiningFaces.put(event.getPlayer().getUniqueId(), event.getBlockFace());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (areaBreaking.contains(playerId)
                || !"titan_pickaxe".equals(plugin.getLegendaryId(player.getInventory().getItemInMainHand()))) {
            return;
        }

        BlockFace face = titanMiningFaces.remove(playerId);
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
        plugin.removeStormPiercerDisplay(event.getArrow().getUniqueId());
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
        if (cause != EntityRemoveEvent.Cause.OUT_OF_WORLD
                && cause != EntityRemoveEvent.Cause.DEATH
                && cause != EntityRemoveEvent.Cause.DESPAWN
                && cause != EntityRemoveEvent.Cause.EXPLODE
                && cause != EntityRemoveEvent.Cause.DISCARD) {
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
                plugin.removeStormPiercerDisplay(trident.getUniqueId());
                announceDestructionNextTick(id, cause);
            }
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
            case "void_cleaver" -> applyVoidCleaverHit(attacker, target);
            case "storm_piercer" -> applyStormPiercerHit(attacker, target);
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)
                || !(event.getHitEntity() instanceof LivingEntity target)
                || !plugin.isStormPiercerTrident(trident)) {
            return;
        }

        if (trident.getShooter() instanceof Entity thrower && thrower.equals(target)) {
            return;
        }
        if (trident.getShooter() instanceof Player attacker) {
            applyStormPiercerHit(attacker, target);
        } else {
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.damage(2.0);
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

    private void applyVoidCleaverHit(Player attacker, LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
        Vector pull = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() > 0.0) {
            pull.normalize().multiply(0.25).setY(0.1);
            target.setVelocity(target.getVelocity().add(pull));
        }
        double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + 1.5));
        target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().add(0.0, 1.0, 0.0),
                16,
                0.3,
                0.4,
                0.3,
                new Particle.DustOptions(plugin.getLegendaryColor("void_cleaver"), 1.4f)
        );
    }

    private void applyStormPiercerHit(Player attacker, LivingEntity target) {
        if (attacker.equals(target)) {
            return;
        }
        target.getWorld().strikeLightningEffect(target.getLocation());
        target.damage(2.0);
        target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().add(0.0, 1.0, 0.0),
                20,
                0.45,
                0.6,
                0.45,
                new Particle.DustOptions(plugin.getLegendaryColor("storm_piercer"), 1.4f)
        );
        if (!attacker.isOnGround()) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0));
        }
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
            if (id != null && isDestroyingItemDamage(event.getCause())) {
                EntityDamageEvent.DamageCause cause = event.getCause();
                itemEntity.remove();
                Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary(
                        id,
                        cause == EntityDamageEvent.DamageCause.VOID ? "lost to the void" : "destroyed"
                ));
            } else if (id != null) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof Trident trident
                && plugin.isStormPiercerTrident(trident)
                && isDestroyingItemDamage(event.getCause())) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            plugin.removeStormPiercerDisplay(trident.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary(
                    "storm_piercer",
                    cause == EntityDamageEvent.DamageCause.VOID ? "lost to the void" : "destroyed"
            ));
        }
    }

    private void announceDestructionNextTick(String id, EntityRemoveEvent.Cause cause) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.destroyLegendary(
                id,
                cause == EntityRemoveEvent.Cause.OUT_OF_WORLD ? "lost to the void" : "destroyed"
        ));
    }

    private boolean isDestroyingItemDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }
}