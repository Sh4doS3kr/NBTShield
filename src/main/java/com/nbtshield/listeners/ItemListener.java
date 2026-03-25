package com.nbtshield.listeners;

import com.nbtshield.NBTShield;
import com.nbtshield.utils.NBTChecker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import com.nbtshield.network.PacketProtection;

public class ItemListener implements Listener {

    private final NBTShield plugin;
    private final NBTChecker checker;

    // Cooldown to avoid checking the same player/container every click
    private final java.util.Map<java.util.UUID, Long> interactCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long INTERACT_COOLDOWN_MS = 2000; // 2 seconds

    public ItemListener(NBTShield plugin) {
        this.plugin = plugin;
        this.checker = plugin.getNbtChecker();
    }

    /**
     * Scan player inventory on join and inject packet protection.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Inject Netty packet protection handler
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                PacketProtection.inject(plugin, player);
            }
        }, 2L);

        if (!plugin.getConfig().getBoolean("scan-on-join", true)) return;
        if (player.hasPermission("nbtshield.bypass")) return;

        // Run 1 tick later so inventory is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                checker.scanAndCleanInventory(player);
            }
        }, 1L);
    }

    /**
     * Remove packet protection handler on quit.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PacketProtection.uninject(event.getPlayer());
    }

    /**
     * Check inventory clicks to prevent moving oversized items.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("scan-on-inventory-click", true)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.hasPermission("nbtshield.bypass")) return;

        // Check the item being clicked
        ItemStack current = event.getCurrentItem();
        if (current != null && checker.isOversized(current)) {
            event.setCurrentItem(null);
            event.setCancelled(true);
            logAndNotify(player, current);
            return;
        }

        // Check the cursor item
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && checker.isOversized(cursor)) {
            player.setItemOnCursor(null);
            event.setCancelled(true);
            logAndNotify(player, cursor);
        }
    }

    /**
     * Intercept creative mode inventory actions (players can create items with arbitrary NBT).
     * Prevents: "Tried to read NBT tag that was too big" via creative mode.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (!plugin.getConfig().getBoolean("scan-creative-actions", true)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.hasPermission("nbtshield.bypass")) return;

        ItemStack item = event.getCursor();
        if (item != null && !item.getType().isAir() && checker.isOversized(item)) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            logAndNotify(player, item);
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null && !current.getType().isAir() && checker.isOversized(current)) {
            event.setCurrentItem(null);
            event.setCancelled(true);
            logAndNotify(player, current);
        }
    }

    /**
     * Scan inventory when a player opens any inventory (e.g., opening a shulker box).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.hasPermission("nbtshield.bypass")) return;

        // Scan the opened inventory for oversized items
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                var inv = event.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && checker.isOversized(item)) {
                        inv.setItem(i, null);
                        logAndNotify(player, item);
                    }
                }
            }
        }, 1L);
    }

    /**
     * Scan inventory when closed — catches items imported from external sources.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.hasPermission("nbtshield.bypass")) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                checker.scanAndCleanInventory(player);
            }
        }, 1L);
    }

    /**
     * Check item when swapping hands (offhand swap).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        if (mainHand != null && checker.isOversized(mainHand)) {
            event.setCancelled(true);
            player.getInventory().setItemInMainHand(null);
            logAndNotify(player, mainHand);
            return;
        }
        if (offHand != null && checker.isOversized(offHand)) {
            event.setCancelled(true);
            player.getInventory().setItemInOffHand(null);
            logAndNotify(player, offHand);
        }
    }

    /**
     * Check item when player switches hotbar slot — catches items that appeared externally.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem != null && checker.isOversized(newItem)) {
            player.getInventory().setItem(event.getNewSlot(), null);
            logAndNotify(player, newItem);
        }
    }

    /**
     * Prevent placing oversized containers/shulker boxes as blocks.
     * Also prevents placing containers that would push the chunk over the NBT limit.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        ItemStack item = event.getItemInHand();
        if (checker.isOversized(item)) {
            event.setCancelled(true);
            player.getInventory().setItem(event.getHand(), null);
            logAndNotify(player, item);
            return;
        }

        // Check if placing this container would push the chunk over the NBT limit
        if (plugin.getConfig().getBoolean("chunk-nbt-limit", true)
                && NBTChecker.isContainer(item.getType())) {
            Chunk chunk = event.getBlock().getChunk();
            int currentChunkSize = checker.calculateChunkNbtSize(chunk);
            int itemSize = checker.getItemByteSize(item);
            int maxChunkBytes = plugin.getConfig().getInt("max-chunk-nbt-bytes", 2500000);

            if (itemSize > 0 && (currentChunkSize + itemSize) > maxChunkBytes) {
                event.setCancelled(true);
                player.sendMessage(NBTShield.colorize(
                        "&c&l[NBTShield] &eCannot place this container here. Chunk NBT limit would be exceeded ("
                                + (currentChunkSize / 1000) + "KB/" + (maxChunkBytes / 1000) + "KB)."));
                if (plugin.getConfig().getBoolean("log-removals", true)) {
                    plugin.getLogger().warning("[ChunkNBTLimit] Blocked " + player.getName()
                            + " from placing " + item.getType() + " at " + event.getBlock().getLocation()
                            + " (chunk NBT: " + currentChunkSize + "/" + maxChunkBytes + " bytes)");
                }
                plugin.notifyAdmins("&c&l[NBTShield] &eBlocked &6" + player.getName()
                        + " &efrom placing container (chunk NBT limit)");
                plugin.recordStrike(player.getUniqueId());
            }
        }
    }

    /**
     * Sign protection: prevents oversized sign text data.
     * Covers: "Payload may not be larger than 32767 bytes"
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!plugin.getConfig().getBoolean("sign-protection", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        int maxLength = plugin.getConfig().getInt("max-sign-line-length", 384);
        for (int i = 0; i < event.lines().size(); i++) {
            String line = event.line(i) != null ? event.line(i).toString() : "";
            if (line.length() > maxLength) {
                event.setCancelled(true);
                player.sendMessage(NBTShield.colorize(
                        "&c&l[NBTShield] &eSign text too long. Placement cancelled."));
                if (plugin.getConfig().getBoolean("log-removals", true)) {
                    plugin.getLogger().warning("[SignProtection] Blocked oversized sign from " + player.getName()
                            + " (line " + i + " length: " + line.length() + "/" + maxLength + ")");
                }
                plugin.notifyAdmins("&c&l[NBTShield] &eBlocked oversized sign from &6" + player.getName());
                plugin.recordStrike(player.getUniqueId());
                return;
            }
        }
    }

    /**
     * Prevent picking up oversized items from the ground.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!plugin.getConfig().getBoolean("scan-dropped-items", true)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.hasPermission("nbtshield.bypass")) return;

        ItemStack item = event.getItem().getItemStack();
        if (checker.isOversized(item)) {
            event.setCancelled(true);
            event.getItem().remove();
            logAndNotify(player, item);
        }
    }

    /**
     * Check items when they spawn as entities in the world.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("scan-dropped-items", true)) return;

        ItemStack item = event.getEntity().getItemStack();
        if (checker.isOversized(item)) {
            event.setCancelled(true);
            if (plugin.getConfig().getBoolean("log-removals", true)) {
                plugin.getLogger().warning("Prevented oversized item from spawning at "
                        + event.getEntity().getLocation() + " (Type: " + item.getType()
                        + ", Size: " + checker.getItemByteSize(item) + " bytes)");
            }
        }
    }

    /**
     * Check dropped items.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        ItemStack item = event.getItemDrop().getItemStack();
        if (checker.isOversized(item)) {
            event.getItemDrop().remove();
            logAndNotify(player, item);
        }
    }

    /**
     * Check interactions with blocks and items.
     * Covers: shulker boxes, containers, and any held item with oversized NBT.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        // Rate-limit: skip if this player was checked recently
        long now = System.currentTimeMillis();
        Long lastCheck = interactCooldown.get(player.getUniqueId());
        if (lastCheck != null && (now - lastCheck) < INTERACT_COOLDOWN_MS) return;
        interactCooldown.put(player.getUniqueId(), now);

        // Evict old cooldown entries
        if (interactCooldown.size() > 200) {
            interactCooldown.entrySet().removeIf(e -> (now - e.getValue()) > INTERACT_COOLDOWN_MS * 2);
        }

        // Check if interacting with a container block (shulker, chest, barrel, etc.)
        if (event.getClickedBlock() != null) {
            var state = event.getClickedBlock().getState();
            if (state instanceof Container container) {
                if (checker.isContainerOversized(container)) {
                    event.setCancelled(true);
                    event.getClickedBlock().setType(Material.AIR);
                    player.sendMessage(NBTShield.colorize(
                            "&c&l[NBTShield] &eAn illegal oversized container block was removed."));
                    if (plugin.getConfig().getBoolean("log-removals", true)) {
                        plugin.getLogger().warning("Removed oversized container at "
                                + event.getClickedBlock().getLocation()
                                + " (Type: " + event.getClickedBlock().getType()
                                + ", interacted by " + player.getName() + ")");
                    }
                    plugin.notifyAdmins("&c&l[NBTShield] &eRemoved oversized container at "
                            + event.getClickedBlock().getLocation() + " &e(by &6" + player.getName() + "&e)");
                }
            }
        }

        // Check held item
        ItemStack heldItem = event.getItem();
        if (heldItem != null && checker.isOversized(heldItem)) {
            event.setCancelled(true);
            player.getInventory().setItem(event.getHand(), null);
            logAndNotify(player, heldItem);
        }
    }

    private void logAndNotify(Player player, ItemStack item) {
        int size = checker.getItemByteSize(item);

        if (plugin.getConfig().getBoolean("log-removals", true)) {
            plugin.getLogger().warning("Removed oversized item from " + player.getName()
                    + " (Type: " + item.getType() + ", Size: " + size + " bytes)");
        }

        String playerMsg = plugin.getConfig().getString("player-message",
                "&c&l[NBTShield] &eAn illegal oversized item was removed from your inventory.");
        player.sendMessage(NBTShield.colorize(playerMsg));

        String adminMsg = plugin.getConfig().getString("admin-message",
                        "&c&l[NBTShield] &eRemoved oversized item from &6{player} &e(Size: &c{size}&e bytes)")
                .replace("{player}", player.getName())
                .replace("{size}", String.valueOf(size));
        plugin.notifyAdmins(adminMsg);

        // Record strike
        boolean exceeded = plugin.recordStrike(player.getUniqueId());
        if (exceeded) {
            checker.removeAllDangerousItems(player);
        }
    }
}
