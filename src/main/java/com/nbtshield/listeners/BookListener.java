package com.nbtshield.listeners;

import com.nbtshield.NBTShield;
import com.nbtshield.utils.NBTChecker;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Protects against book-based exploits:
 * - "Tried to read NBT tag that was too big" (max allowed: 2097152)
 * - "Payload may not be larger than 32767 bytes"
 * - "Payload may not be larger than 1048576 bytes"
 * - Books with excessive pages/content used for chunk banning
 */
public class BookListener implements Listener {

    private final NBTShield plugin;
    private final NBTChecker checker;

    public BookListener(NBTShield plugin) {
        this.plugin = plugin;
        this.checker = plugin.getNbtChecker();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!plugin.getConfig().getBoolean("book-protection", true)) return;

        Player player = event.getPlayer();
        if (player.hasPermission("nbtshield.bypass")) return;

        BookMeta newMeta = event.getNewBookMeta();
        int maxPages = plugin.getConfig().getInt("max-book-pages", 100);
        int maxPageLength = plugin.getConfig().getInt("max-book-page-length", 320);
        int maxBookBytes = plugin.getConfig().getInt("max-book-bytes", 32000);

        // Check page count
        if (newMeta.getPageCount() > maxPages) {
            event.setCancelled(true);
            player.sendMessage(NBTShield.colorize(
                    "&c&l[NBTShield] &eBook has too many pages (" + newMeta.getPageCount()
                            + "/" + maxPages + "). Edit cancelled."));
            logBookViolation(player, "too many pages: " + newMeta.getPageCount());
            return;
        }

        // Check individual page length
        for (int i = 1; i <= newMeta.getPageCount(); i++) {
            String page = newMeta.getPage(i);
            if (page != null && page.length() > maxPageLength) {
                event.setCancelled(true);
                player.sendMessage(NBTShield.colorize(
                        "&c&l[NBTShield] &eBook page " + i + " is too long ("
                                + page.length() + "/" + maxPageLength + " chars). Edit cancelled."));
                logBookViolation(player, "page " + i + " too long: " + page.length() + " chars");
                return;
            }
        }

        // Check total serialized size
        ItemStack bookItem = new ItemStack(Material.WRITTEN_BOOK);
        bookItem.setItemMeta(newMeta);
        int size = checker.getItemByteSize(bookItem);
        if (size > maxBookBytes || size == -1) {
            event.setCancelled(true);
            player.sendMessage(NBTShield.colorize(
                    "&c&l[NBTShield] &eBook data is too large ("
                            + size + "/" + maxBookBytes + " bytes). Edit cancelled."));
            logBookViolation(player, "total size: " + size + " bytes");
        }
    }

    /**
     * Check if an ItemStack is an oversized book.
     * Called from NBTChecker for general item scanning.
     */
    public static boolean isBookOversized(NBTShield plugin, NBTChecker checker, ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        if (type != Material.WRITTEN_BOOK && type != Material.WRITABLE_BOOK) return false;
        if (!plugin.getConfig().getBoolean("book-protection", true)) return false;

        if (!(item.getItemMeta() instanceof BookMeta bookMeta)) return false;

        int maxPages = plugin.getConfig().getInt("max-book-pages", 100);
        int maxPageLength = plugin.getConfig().getInt("max-book-page-length", 320);
        int maxBookBytes = plugin.getConfig().getInt("max-book-bytes", 32000);

        // Check page count
        if (bookMeta.getPageCount() > maxPages) return true;

        // Check individual page length
        for (int i = 1; i <= bookMeta.getPageCount(); i++) {
            String page = bookMeta.getPage(i);
            if (page != null && page.length() > maxPageLength) return true;
        }

        // Check total serialized size
        int size = checker.getItemByteSize(item);
        return size > maxBookBytes || size == -1;
    }

    private void logBookViolation(Player player, String reason) {
        if (plugin.getConfig().getBoolean("log-removals", true)) {
            plugin.getLogger().warning("[BookProtection] Blocked book exploit from " + player.getName()
                    + ": " + reason);
        }
        plugin.notifyAdmins("&c&l[NBTShield] &eBlocked book exploit from &6" + player.getName()
                + " &e(" + reason + ")");
        plugin.recordStrike(player.getUniqueId());
    }
}
