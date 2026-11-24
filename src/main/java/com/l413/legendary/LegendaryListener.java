package com.l413.legendary;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class LegendaryListener implements Listener {
    private final LegendaryPlugin plugin;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        plugin.removeStormPiercerDisplay(event.getArrow().getUniqueId());
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
            target.damage(4.0);
        }
    }

    private void applyAstralHit(Player attacker, LivingEntity target) {
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0));
        if (ThreadLocalRandom.current().nextDouble() < 0.25) {
            target.damage(4.0);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0.0, 1.0, 0.0), 12, 0.35, 0.5, 0.35, 0.02);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1.0f, 1.0f);
        }
    }

    private void applyVoidCleaverHit(Player attacker, LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
        Vector pull = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() > 0.0) {
            pull.normalize().multiply(0.35).setY(0.15);
            target.setVelocity(target.getVelocity().add(pull));
        }
        double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + 3.0));
        target.getWorld().spawnParticle(Particle.DRAGON_BREATH, target.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.4, 0.3, 0.01);
    }

    private void applyStormPiercerHit(Player attacker, LivingEntity target) {
        if (attacker.equals(target)) {
            return;
        }
        target.getWorld().strikeLightningEffect(target.getLocation());
        target.damage(4.0);
        if (!attacker.isOnGround()) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0));
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

        plugin.markCrafted(id, true);
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
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        if (plugin.getLegendaryId(itemEntity.getItemStack()) != null) {
            itemEntity.setGlowing(true);
            itemEntity.setUnlimitedLifetime(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && "astral_boots".equals(plugin.getLegendaryId(player.getInventory().getBoots()))) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Item itemEntity) {
            if (plugin.getLegendaryId(itemEntity.getItemStack()) != null) {
                event.setCancelled(true);
                if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                    Location safeLoc = itemEntity.getLocation().clone();
                    safeLoc.setY(100);
                    itemEntity.teleport(safeLoc);
                    itemEntity.setVelocity(itemEntity.getVelocity().zero());
                }
            }
        }
    }
}