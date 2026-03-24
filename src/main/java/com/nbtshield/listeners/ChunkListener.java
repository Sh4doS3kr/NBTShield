package com.nbtshield.listeners;

import com.nbtshield.NBTShield;
import com.nbtshield.utils.NBTChecker;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Scans chunks on load for:
 * - Oversized container tile entities (shulker boxes, chests, barrels, etc.)
 * - Oversized item entities on the ground
 * - Entities with oversized NBT (item frames, armor stands)
 *
 * Prevents: "Chunk Packet trying to allocate too much memory on read"
 *           and all chunk-ban related crashes.
 */
public class ChunkListener implements Listener {

    private final NBTShield plugin;
    private final NBTChecker checker;

    public ChunkListener(NBTShield plugin) {
        this.plugin = plugin;
        this.checker = plugin.getNbtChecker();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfig().getBoolean("scan-chunks-on-load", true)) return;

        Chunk chunk = event.getChunk();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!chunk.isLoaded()) return;

            // Scan ALL container tile entities (not just shulker boxes)
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Container container) {
                    if (checker.isContainerOversized(container)) {
                        state.getBlock().setType(Material.AIR);

                        if (plugin.getConfig().getBoolean("log-removals", true)) {
                            plugin.getLogger().warning("[ChunkScan] Removed oversized container ("
                                    + state.getType() + ") at " + state.getLocation()
                                    + " in chunk [" + chunk.getX() + ", " + chunk.getZ()
                                    + "] in world " + chunk.getWorld().getName());
                        }
                    }
                }
            }

            // Scan item entities in the chunk
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Item itemEntity) {
                    ItemStack itemStack = itemEntity.getItemStack();
                    if (checker.isOversized(itemStack)) {
                        itemEntity.remove();

                        if (plugin.getConfig().getBoolean("log-removals", true)) {
                            plugin.getLogger().warning("[ChunkScan] Removed oversized item entity at "
                                    + entity.getLocation() + " in chunk ["
                                    + chunk.getX() + ", " + chunk.getZ() + "] (Type: "
                                    + itemStack.getType() + ", Size: "
                                    + checker.getItemByteSize(itemStack) + " bytes)");
                        }
                    }
                }
            }

            // Scan entities with items (item frames, armor stands)
            if (plugin.getConfig().getBoolean("scan-entity-nbt-on-chunk-load", true)) {
                EntityListener.scanChunkEntities(plugin, checker, chunk.getEntities());
            }

            // Enforce total chunk NBT limit (prevents accumulation of many small items)
            checker.enforceChunkNbtLimit(chunk);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!plugin.getConfig().getBoolean("scan-chunks-on-load", true)) return;

        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item itemEntity) {
                ItemStack itemStack = itemEntity.getItemStack();
                if (checker.isOversized(itemStack)) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (!itemEntity.isDead()) {
                            itemEntity.remove();
                            if (plugin.getConfig().getBoolean("log-removals", true)) {
                                plugin.getLogger().warning("[EntityLoad] Removed oversized item entity at "
                                        + entity.getLocation() + " (Type: " + itemStack.getType()
                                        + ", Size: " + checker.getItemByteSize(itemStack) + " bytes)");
                            }
                        }
                    }, 1L);
                }
            }
        }

        // Scan item frames, armor stands on entity load
        if (plugin.getConfig().getBoolean("scan-entity-nbt-on-chunk-load", true)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                EntityListener.scanChunkEntities(plugin, checker,
                        event.getEntities().toArray(new Entity[0]));
            }, 1L);
        }
    }
}
