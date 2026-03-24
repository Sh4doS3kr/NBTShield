package com.nbtshield.utils;

import com.nbtshield.NBTShield;
import com.nbtshield.listeners.BookListener;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class NBTChecker {

    private final NBTShield plugin;
    private final int maxItemNbtBytes;
    private final int maxShulkerNbtBytes;
    private final int maxContainerNbtBytes;

    public NBTChecker(NBTShield plugin) {
        this.plugin = plugin;
        this.maxItemNbtBytes = plugin.getConfig().getInt("max-item-nbt-bytes", 262144);
        this.maxShulkerNbtBytes = plugin.getConfig().getInt("max-shulker-nbt-bytes", 500000);
        this.maxContainerNbtBytes = plugin.getConfig().getInt("max-container-nbt-bytes", 500000);
    }

    /**
     * Check if a Material is any type of shulker box.
     */
    public static boolean isShulkerBox(Material material) {
        if (material == null) return false;
        return switch (material) {
            case SHULKER_BOX,
                 WHITE_SHULKER_BOX,
                 ORANGE_SHULKER_BOX,
                 MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX,
                 LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX,
                 LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX,
                 PURPLE_SHULKER_BOX,
                 BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX,
                 GREEN_SHULKER_BOX,
                 RED_SHULKER_BOX,
                 BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    /**
     * Check if a Material is a container block (chest, barrel, hopper, dispenser, etc.).
     */
    public static boolean isContainer(Material material) {
        if (material == null) return false;
        if (isShulkerBox(material)) return true;
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, HOPPER, DROPPER, DISPENSER -> true;
            default -> false;
        };
    }

    /**
     * Check if a Material is a book type.
     */
    public static boolean isBook(Material material) {
        if (material == null) return false;
        return material == Material.WRITTEN_BOOK || material == Material.WRITABLE_BOOK;
    }

    /**
     * Estimate the serialized byte size of an ItemStack.
     * Returns -1 if serialization fails.
     */
    public int getItemByteSize(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
            boos.flush();
            return baos.size();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to serialize item for size check: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Check if an item exceeds the allowed NBT size limits.
     * Returns true if the item is oversized (illegal).
     * Covers: shulker boxes, books, containers, and any other item.
     */
    public boolean isOversized(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        // Check books using specialized book checker
        if (isBook(item.getType())) {
            return BookListener.isBookOversized(plugin, this, item);
        }

        int size = getItemByteSize(item);
        if (size == -1) return true; // Can't serialize = suspicious

        // Shulker boxes have their own limit
        if (isShulkerBox(item.getType())) {
            return size > maxShulkerNbtBytes;
        }

        // Other containers
        if (isContainer(item.getType())) {
            return size > maxContainerNbtBytes;
        }

        // General item check
        return size > maxItemNbtBytes;
    }

    /**
     * Check if a shulker box block entity has oversized contents.
     */
    public boolean isShulkerBoxOversized(ShulkerBox shulkerBox) {
        if (shulkerBox == null) return false;

        Inventory inv = shulkerBox.getInventory();
        int totalSize = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            int itemSize = getItemByteSize(item);
            if (itemSize == -1) return true;
            totalSize += itemSize;
            if (totalSize > maxShulkerNbtBytes) return true;
        }
        return false;
    }

    /**
     * Check if any Container block entity has oversized contents.
     */
    public boolean isContainerOversized(Container container) {
        if (container == null) return false;

        int limit = (container instanceof ShulkerBox) ? maxShulkerNbtBytes : maxContainerNbtBytes;
        Inventory inv = container.getInventory();
        int totalSize = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            int itemSize = getItemByteSize(item);
            if (itemSize == -1) return true;
            totalSize += itemSize;
            if (totalSize > limit) return true;
        }
        return false;
    }

    /**
     * Scan and clean a player's inventory. Returns number of items removed.
     */
    public int scanAndCleanInventory(Player player) {
        if (player == null) return 0;

        int removed = 0;
        Inventory inv = player.getInventory();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            if (isOversized(item)) {
                int size = getItemByteSize(item);
                inv.setItem(i, null);
                removed++;

                if (plugin.getConfig().getBoolean("log-removals", true)) {
                    plugin.getLogger().warning("Removed oversized item from " + player.getName()
                            + " at slot " + i + " (Type: " + item.getType() + ", Size: " + size + " bytes)");
                }

                String adminMsg = plugin.getConfig().getString("admin-message",
                                "&c&l[NBTShield] &eRemoved oversized item from &6{player} &e(Size: &c{size}&e bytes)")
                        .replace("{player}", player.getName())
                        .replace("{size}", String.valueOf(size));
                plugin.notifyAdmins(adminMsg);
            }
        }

        // Also check cursor item
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir() && isOversized(cursor)) {
            player.setItemOnCursor(null);
            removed++;
        }

        // Check ender chest
        Inventory enderChest = player.getEnderChest();
        for (int i = 0; i < enderChest.getSize(); i++) {
            ItemStack item = enderChest.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            if (isOversized(item)) {
                int size = getItemByteSize(item);
                enderChest.setItem(i, null);
                removed++;

                if (plugin.getConfig().getBoolean("log-removals", true)) {
                    plugin.getLogger().warning("Removed oversized item from " + player.getName()
                            + " ender chest slot " + i + " (Type: " + item.getType() + ", Size: " + size + " bytes)");
                }
            }
        }

        if (removed > 0) {
            String msg = plugin.getConfig().getString("player-message",
                    "&c&l[NBTShield] &eAn illegal oversized item was removed from your inventory.");
            player.sendMessage(NBTShield.colorize(msg));

            // Record strike
            boolean exceeded = plugin.recordStrike(player.getUniqueId());
            if (exceeded) {
                removeAllDangerousItems(player);
            }
        }

        return removed;
    }

    /**
     * Remove ALL shulker boxes AND other dangerous items from a player's inventory.
     * Called when a player exceeds the strike threshold.
     */
    public void removeAllDangerousItems(Player player) {
        Inventory inv = player.getInventory();
        int removed = 0;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            // Remove all shulkers, containers with items, and oversized books
            if (isShulkerBox(item.getType()) || isOversized(item)) {
                inv.setItem(i, null);
                removed++;
            }
        }

        // Check cursor
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && (isShulkerBox(cursor.getType()) || isOversized(cursor))) {
            player.setItemOnCursor(null);
            removed++;
        }

        // Clean ender chest too
        Inventory enderChest = player.getEnderChest();
        for (int i = 0; i < enderChest.getSize(); i++) {
            ItemStack item = enderChest.getItem(i);
            if (item != null && (isShulkerBox(item.getType()) || isOversized(item))) {
                enderChest.setItem(i, null);
                removed++;
            }
        }

        if (removed > 0) {
            String msg = plugin.getConfig().getString("strike-action-message",
                    "&c&l[NBTShield] &eAll shulker boxes and illegal items have been removed from your inventory due to repeated violations.");
            player.sendMessage(NBTShield.colorize(msg));

            plugin.getLogger().warning("Removed ALL dangerous items (" + removed + ") from " + player.getName()
                    + " due to exceeding strike threshold.");
            plugin.notifyAdmins("&c&l[NBTShield] &eRemoved ALL dangerous items from &6" + player.getName()
                    + " &e(strike threshold exceeded)");
        }
    }
}
