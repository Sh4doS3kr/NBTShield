package com.nbtshield.utils;

import com.nbtshield.NBTShield;
import com.nbtshield.listeners.BookListener;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class NBTChecker {

    private final NBTShield plugin;
    private final int maxItemNbtBytes;
    private final int maxShulkerNbtBytes;
    private final int maxContainerNbtBytes;
    private final int maxChunkNbtBytes;
    private final int maxBooksPerContainer;

    public NBTChecker(NBTShield plugin) {
        this.plugin = plugin;
        this.maxItemNbtBytes = plugin.getConfig().getInt("max-item-nbt-bytes", 262144);
        this.maxShulkerNbtBytes = plugin.getConfig().getInt("max-shulker-nbt-bytes", 500000);
        this.maxContainerNbtBytes = plugin.getConfig().getInt("max-container-nbt-bytes", 500000);
        this.maxChunkNbtBytes = plugin.getConfig().getInt("max-chunk-nbt-bytes", 1500000);
        this.maxBooksPerContainer = plugin.getConfig().getInt("max-books-per-container", 9);
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
        int bookCount = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (isBook(item.getType())) {
                bookCount += item.getAmount();
                if (bookCount > maxBooksPerContainer) return true;
            }
            int itemSize = getItemByteSize(item);
            if (itemSize == -1) return true;
            totalSize += itemSize;
            if (totalSize > limit) return true;
        }
        return false;
    }

    /**
     * Calculate the total NBT size of all containers and entities in a chunk.
     * Returns the total estimated bytes.
     */
    public int calculateChunkNbtSize(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return 0;

        int totalSize = 0;

        // Sum all container tile entities
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container container) {
                Inventory inv = container.getInventory();
                for (ItemStack item : inv.getContents()) {
                    if (item == null || item.getType().isAir()) continue;
                    int size = getItemByteSize(item);
                    if (size > 0) totalSize += size;
                }
            }
        }

        // Sum all item entities on the ground
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item itemEntity) {
                int size = getItemByteSize(itemEntity.getItemStack());
                if (size > 0) totalSize += size;
            }
            if (entity instanceof ItemFrame itemFrame) {
                int size = getItemByteSize(itemFrame.getItem());
                if (size > 0) totalSize += size;
            }
            if (entity instanceof ArmorStand armorStand) {
                EntityEquipment eq = armorStand.getEquipment();
                if (eq != null) {
                    for (ItemStack item : eq.getArmorContents()) {
                        if (item != null && !item.getType().isAir()) {
                            int size = getItemByteSize(item);
                            if (size > 0) totalSize += size;
                        }
                    }
                    int mh = getItemByteSize(eq.getItemInMainHand());
                    if (mh > 0) totalSize += mh;
                    int oh = getItemByteSize(eq.getItemInOffHand());
                    if (oh > 0) totalSize += oh;
                }
            }
        }

        return totalSize;
    }

    /**
     * Check if a chunk exceeds the maximum allowed total NBT size.
     */
    public boolean isChunkOverLimit(Chunk chunk) {
        if (!plugin.getConfig().getBoolean("chunk-nbt-limit", true)) return false;
        return calculateChunkNbtSize(chunk) > maxChunkNbtBytes;
    }

    /**
     * Enforce chunk NBT limit by removing containers starting from the largest.
     * Returns number of containers removed.
     */
    public int enforceChunkNbtLimit(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return 0;
        if (!plugin.getConfig().getBoolean("chunk-nbt-limit", true)) return 0;

        int totalSize = calculateChunkNbtSize(chunk);
        if (totalSize <= maxChunkNbtBytes) return 0;

        // Build list of containers sorted by size (largest first)
        List<ContainerEntry> containers = new ArrayList<>();
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container container) {
                int containerSize = 0;
                for (ItemStack item : container.getInventory().getContents()) {
                    if (item == null || item.getType().isAir()) continue;
                    int s = getItemByteSize(item);
                    if (s > 0) containerSize += s;
                }
                if (containerSize > 0) {
                    containers.add(new ContainerEntry(state, containerSize));
                }
            }
        }

        // Sort largest first
        containers.sort(Comparator.comparingInt(ContainerEntry::size).reversed());

        int removed = 0;
        for (ContainerEntry entry : containers) {
            if (totalSize <= maxChunkNbtBytes) break;

            entry.state.getBlock().setType(Material.AIR);
            totalSize -= entry.size;
            removed++;

            if (plugin.getConfig().getBoolean("log-removals", true)) {
                plugin.getLogger().warning("[ChunkNBTLimit] Removed container ("
                        + entry.state.getType() + ") at " + entry.state.getLocation()
                        + " (" + entry.size + " bytes) - chunk total was over limit ("
                        + maxChunkNbtBytes + " bytes)");
            }
        }

        if (removed > 0) {
            plugin.notifyAdmins("&c&l[NBTShield] &eRemoved &c" + removed
                    + " &econtainers from chunk [" + chunk.getX() + ", " + chunk.getZ()
                    + "] &e(total NBT exceeded " + (maxChunkNbtBytes / 1000) + "KB limit)");
        }

        return removed;
    }

    private record ContainerEntry(BlockState state, int size) {}

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
