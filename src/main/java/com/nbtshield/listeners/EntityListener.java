package com.nbtshield.listeners;

import com.nbtshield.NBTShield;
import com.nbtshield.utils.NBTChecker;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Protects against entity-based NBT exploits:
 * - Item frames with oversized NBT items
 * - Armor stands with oversized NBT items
 * - Any entity carrying oversized items
 */
public class EntityListener implements Listener {

    private final NBTShield plugin;
    private final NBTChecker checker;

    public EntityListener(NBTShield plugin) {
        this.plugin = plugin;
        this.checker = plugin.getNbtChecker();
    }

    /**
     * Prevent placing item frames with oversized items.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("entity-protection", true)) return;
        if (event.getPlayer() == null) return;
        if (event.getPlayer().hasPermission("nbtshield.bypass")) return;

        // Check the item used to place
        ItemStack item = event.getItemStack();
        if (item != null && checker.isOversized(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(NBTShield.colorize(
                    "&c&l[NBTShield] &eCannot place entity with oversized NBT data."));
            logEntityViolation(event.getPlayer(), "hanging entity placement", item);
        }
    }

    /**
     * Prevent placing oversized items on armor stands.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!plugin.getConfig().getBoolean("entity-protection", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        // Check the item being placed on the armor stand
        ItemStack playerItem = event.getPlayerItem();
        if (playerItem != null && checker.isOversized(playerItem)) {
            event.setCancelled(true);
            player.sendMessage(NBTShield.colorize(
                    "&c&l[NBTShield] &eCannot place oversized item on armor stand."));
            player.getInventory().remove(playerItem);
            logEntityViolation(player, "armor stand item placement", playerItem);
            return;
        }

        // Check the item already on the armor stand
        ItemStack armorStandItem = event.getArmorStandItem();
        if (armorStandItem != null && checker.isOversized(armorStandItem)) {
            event.setCancelled(true);
            // Clear the problematic item from the armor stand
            event.getRightClicked().getEquipment().clear();
            player.sendMessage(NBTShield.colorize(
                    "&c&l[NBTShield] &eRemoved oversized item from armor stand."));
            logEntityViolation(player, "armor stand had oversized item", armorStandItem);
        }
    }

    /**
     * Prevent interacting with entities that have oversized items (item frames).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("entity-protection", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        Entity entity = event.getRightClicked();

        // Check item frames
        if (entity instanceof ItemFrame itemFrame) {
            ItemStack frameItem = itemFrame.getItem();
            if (frameItem != null && checker.isOversized(frameItem)) {
                event.setCancelled(true);
                itemFrame.setItem(null);
                player.sendMessage(NBTShield.colorize(
                        "&c&l[NBTShield] &eRemoved oversized item from item frame."));
                logEntityViolation(player, "item frame interaction", frameItem);
                return;
            }

            // Check the item the player is trying to put in the frame
            ItemStack handItem = player.getInventory().getItem(event.getHand());
            if (handItem != null && checker.isOversized(handItem)) {
                event.setCancelled(true);
                player.getInventory().setItem(event.getHand(), null);
                player.sendMessage(NBTShield.colorize(
                        "&c&l[NBTShield] &eCannot place oversized item in item frame."));
                logEntityViolation(player, "item frame item placement", handItem);
            }
        }
    }

    /**
     * Scan all entities with items in a chunk.
     * Called from ChunkListener on chunk load.
     */
    public static void scanChunkEntities(NBTShield plugin, NBTChecker checker, Entity[] entities) {
        if (!plugin.getConfig().getBoolean("scan-entity-nbt-on-chunk-load", true)) return;

        for (Entity entity : entities) {
            // Check item frames
            if (entity instanceof ItemFrame itemFrame) {
                ItemStack item = itemFrame.getItem();
                if (item != null && checker.isOversized(item)) {
                    itemFrame.setItem(null);
                    logEntityRemoval(plugin, entity, item);
                }
            }

            // Check armor stands
            if (entity instanceof ArmorStand armorStand) {
                var equipment = armorStand.getEquipment();
                if (equipment != null) {
                    checkAndClearSlot(plugin, checker, entity, equipment.getItemInMainHand(),
                            () -> equipment.setItemInMainHand(null));
                    checkAndClearSlot(plugin, checker, entity, equipment.getItemInOffHand(),
                            () -> equipment.setItemInOffHand(null));
                    checkAndClearSlot(plugin, checker, entity, equipment.getHelmet(),
                            () -> equipment.setHelmet(null));
                    checkAndClearSlot(plugin, checker, entity, equipment.getChestplate(),
                            () -> equipment.setChestplate(null));
                    checkAndClearSlot(plugin, checker, entity, equipment.getLeggings(),
                            () -> equipment.setLeggings(null));
                    checkAndClearSlot(plugin, checker, entity, equipment.getBoots(),
                            () -> equipment.setBoots(null));
                }
            }
        }
    }

    private static void checkAndClearSlot(NBTShield plugin, NBTChecker checker,
                                          Entity entity, ItemStack item, Runnable clearAction) {
        if (item != null && !item.getType().isAir() && checker.isOversized(item)) {
            clearAction.run();
            logEntityRemoval(plugin, entity, item);
        }
    }

    private static void logEntityRemoval(NBTShield plugin, Entity entity, ItemStack item) {
        if (plugin.getConfig().getBoolean("log-removals", true)) {
            plugin.getLogger().warning("[EntityProtection] Removed oversized item from "
                    + entity.getType() + " at " + entity.getLocation()
                    + " (Item: " + item.getType() + ")");
        }
    }

    private void logEntityViolation(Player player, String action, ItemStack item) {
        if (plugin.getConfig().getBoolean("log-removals", true)) {
            plugin.getLogger().warning("[EntityProtection] Blocked " + action + " by " + player.getName()
                    + " (Item: " + item.getType() + ", Size: " + checker.getItemByteSize(item) + " bytes)");
        }
        plugin.notifyAdmins("&c&l[NBTShield] &eBlocked entity exploit from &6" + player.getName()
                + " &e(" + action + ")");
        plugin.recordStrike(player.getUniqueId());
    }
}
