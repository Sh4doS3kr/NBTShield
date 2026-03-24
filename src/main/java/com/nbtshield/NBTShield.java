package com.nbtshield;

import com.nbtshield.listeners.BookListener;
import com.nbtshield.listeners.ChunkListener;
import com.nbtshield.listeners.EntityListener;
import com.nbtshield.listeners.ItemListener;
import com.nbtshield.network.PacketProtection;
import com.nbtshield.utils.NBTChecker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NBTShield extends JavaPlugin {

    private static NBTShield instance;
    private NBTChecker nbtChecker;

    // Track player strikes: playerUUID -> list of timestamps
    private final Map<UUID, List<Long>> playerStrikes = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        nbtChecker = new NBTChecker(this);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new ItemListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChunkListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BookListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EntityListener(this), this);

        // Inject packet protection for already-online players (in case of reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            PacketProtection.inject(this, p);
        }

        // Periodic cleanup of old strike data (every 5 minutes)
        Bukkit.getScheduler().runTaskTimer(this, this::cleanupStrikes, 6000L, 6000L);

        getLogger().info("==============================================");
        getLogger().info("NBTShield v2.0 enabled");
        getLogger().info("Protections: NBT, Books, Signs, Entities, Packets");
        getLogger().info("==============================================");
    }

    @Override
    public void onDisable() {
        // Remove Netty handlers from all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            PacketProtection.uninject(p);
        }
        playerStrikes.clear();
        getLogger().info("NBTShield disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("nbtshield")) return false;

        if (!sender.hasPermission("nbtshield.admin")) {
            sender.sendMessage(colorize("&cNo permission."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(colorize("&6[NBTShield] &eCommands:"));
            sender.sendMessage(colorize("&6/nbs reload &7- Reload configuration"));
            sender.sendMessage(colorize("&6/nbs scan &7- Scan all online players' inventories"));
            sender.sendMessage(colorize("&6/nbs scanplayer <name> &7- Scan a specific player"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                nbtChecker = new NBTChecker(this);
                sender.sendMessage(colorize("&a[NBTShield] Configuration reloaded."));
            }
            case "scan" -> {
                int removed = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    removed += nbtChecker.scanAndCleanInventory(p);
                }
                sender.sendMessage(colorize("&a[NBTShield] Scan complete. Removed &c" + removed + " &aillegal items."));
            }
            case "scanplayer" -> {
                if (args.length < 2) {
                    sender.sendMessage(colorize("&cUsage: /nbs scanplayer <name>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(colorize("&cPlayer not found."));
                    return true;
                }
                int removed = nbtChecker.scanAndCleanInventory(target);
                sender.sendMessage(colorize("&a[NBTShield] Removed &c" + removed + " &aillegal items from &6" + target.getName()));
            }
            default -> sender.sendMessage(colorize("&cUnknown subcommand. Use /nbs for help."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("nbtshield")) return null;
        if (!sender.hasPermission("nbtshield.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("reload", "scan", "scanplayer").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("scanplayer")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Record a strike for a player and check if they exceed the threshold.
     * Returns true if the player exceeded the strike threshold.
     */
    public boolean recordStrike(UUID playerUUID) {
        int threshold = getConfig().getInt("strike-threshold", 3);
        int windowSeconds = getConfig().getInt("strike-window-seconds", 60);
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        List<Long> strikes = playerStrikes.computeIfAbsent(playerUUID, k -> Collections.synchronizedList(new ArrayList<>()));
        strikes.add(now);

        // Remove old strikes outside the window
        strikes.removeIf(t -> (now - t) > windowMs);

        return strikes.size() >= threshold;
    }

    private void cleanupStrikes() {
        long now = System.currentTimeMillis();
        int windowSeconds = getConfig().getInt("strike-window-seconds", 60);
        long windowMs = windowSeconds * 1000L;

        playerStrikes.forEach((uuid, strikes) -> {
            strikes.removeIf(t -> (now - t) > windowMs);
        });
        playerStrikes.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public NBTChecker getNbtChecker() {
        return nbtChecker;
    }

    public void notifyAdmins(String message) {
        if (!getConfig().getBoolean("notify-admins", true)) return;
        String colored = colorize(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("nbtshield.admin")) {
                p.sendMessage(colored);
            }
        }
        getLogger().warning(ChatColor.stripColor(colored));
    }

    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static NBTShield getInstance() {
        return instance;
    }
}
