package com.nbtshield.listeners;

import com.nbtshield.NBTShield;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Paper-specific chat listener using AsyncChatEvent.
 * Separate class so if AsyncChatEvent doesn't exist, main listener still works.
 * Uses WHITELIST detection - only Latin + symbols + emoji allowed.
 */
public class PaperChatListener implements Listener {

    private final NBTShield plugin;

    public PaperChatListener(NBTShield plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("[UnicodeProtection] Paper AsyncChatEvent listener registered");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPaperChat(AsyncChatEvent event) {
        try {
            if (!plugin.getConfig().getBoolean("unicode-protection", true)) return;
            Player player = event.getPlayer();
            if (player.hasPermission("nbtshield.bypass")) return;

            // Check both original and current message via plain text
            String plainOrig = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
            String plainMsg = PlainTextComponentSerializer.plainText().serialize(event.message());

            boolean detected = UnicodeExploitListener.containsPuaCharacters(plainOrig)
                    || UnicodeExploitListener.containsPuaCharacters(plainMsg);

            // Log for debugging
            StringBuilder hex = new StringBuilder();
            String toLog = plainOrig.isEmpty() ? plainMsg : plainOrig;
            for (int i = 0; i < Math.min(toLog.length(), 30); ) {
                int cp = toLog.codePointAt(i);
                hex.append(String.format("U+%04X ", cp));
                i += Character.charCount(cp);
            }
            plugin.getLogger().info("[UnicodeProtection] PaperChat from " + player.getName()
                    + " codes=[" + hex.toString().trim() + "] detected=" + detected);

            if (detected) {
                event.setCancelled(true);
                event.viewers().clear();
                plugin.getLogger().warning("[UnicodeProtection] BLOCKED in PaperChat from " + player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        String kickMsg = plugin.getConfig().getString("unicode-kick-message",
                                "&c&l[NBTShield] &eYou have been kicked for using resource pack characters.");
                        plugin.notifyAdmins("&c&l[NBTShield] &4KICKED &6" + player.getName()
                                + " &efor illegal characters in chat");
                        player.kick(Component.text(NBTShield.colorize(kickMsg)));
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[UnicodeProtection] ERROR in PaperChat: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
