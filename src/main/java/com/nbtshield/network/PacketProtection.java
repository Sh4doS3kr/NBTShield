package com.nbtshield.network;

import com.nbtshield.NBTShield;
import com.nbtshield.listeners.UnicodeExploitListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Netty pipeline handler that intercepts packet-level exploits:
 * - PacketTooLarge (outbound packets exceeding 8388608 bytes)
 * - VarInt too big / VarLong too big
 * - Badly compressed packets
 * - Length wider than 21-bit
 * - Unable to fit X into 3
 * - Connection timeouts
 * - Payload too large (1048576 / 32767)
 */
public class PacketProtection extends ChannelDuplexHandler {

    private final NBTShield plugin;
    private final UUID playerUUID;
    private final String playerName;

    public PacketProtection(NBTShield plugin, UUID playerUUID, String playerName) {
        this.plugin = plugin;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
    }

    /**
     * Intercept INCOMING packets - checks chat messages for PUA characters
     * at the lowest possible level (before any Bukkit event fires).
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (plugin.getConfig().getBoolean("unicode-protection", true)) {
            String packetName = msg.getClass().getSimpleName();

            // Intercept chat packets: ServerboundChatPacket, ServerboundChatCommandPacket, etc.
            if (packetName.toLowerCase().contains("chat")) {
                // Extract ALL string fields from the packet via reflection
                String chatContent = extractStringFields(msg);

                // Log EVERY chat packet for debugging
                if (chatContent != null) {
                    StringBuilder hexDebug = new StringBuilder();
                    for (int i = 0; i < Math.min(chatContent.length(), 50); i++) {
                        hexDebug.append(String.format("U+%04X ", (int) chatContent.charAt(i)));
                    }
                    plugin.getLogger().info("[PacketProtection] Chat packet from " + playerName
                            + " type=" + packetName + " chars=[" + hexDebug.toString().trim() + "]");
                }

                if (chatContent != null && UnicodeExploitListener.containsPuaCharacters(chatContent)) {
                    plugin.getLogger().warning("[PacketProtection] BLOCKED PUA in packet "
                            + packetName + " from " + playerName);

                    // Kick the player on the main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player player = Bukkit.getPlayer(playerUUID);
                        if (player != null && player.isOnline()) {
                            plugin.notifyAdmins("&c&l[NBTShield] &4KICKED &6" + playerName
                                    + " &efor PUA characters in chat packet");
                            String kickMsg = plugin.getConfig().getString("unicode-kick-message",
                                    "&c&l[NBTShield] &eYou have been kicked for using resource pack characters.");
                            player.kick(Component.text(NBTShield.colorize(kickMsg)));
                        }
                    });

                    // DROP the packet - do NOT pass it through
                    return;
                }
            }
        }

        // Pass through normally
        super.channelRead(ctx, msg);
    }

    /**
     * Extract the chat message string from a packet object.
     * Tries multiple methods in order of reliability:
     * 1. Record component accessors (cleanest, gets just the message field)
     * 2. Public getter methods (message(), command(), etc.)
     * 3. Direct field access (may fail on Java 17+ modules)
     * 4. toString() as last resort (contains all packet data)
     */
    private String extractStringFields(Object packet) {
        StringBuilder sb = new StringBuilder();

        // Method 1: Record component accessors (Java 16+ records - Paper uses records for packets)
        try {
            var components = packet.getClass().getRecordComponents();
            if (components != null) {
                for (var component : components) {
                    if (component.getType() == String.class) {
                        try {
                            String value = (String) component.getAccessor().invoke(packet);
                            if (value != null && !value.isEmpty()) {
                                sb.append(value);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        if (sb.length() > 0) return sb.toString();

        // Method 2: Try common getter method names
        String[] methodNames = {"message", "command", "getMessage", "getCommand"};
        for (String name : methodNames) {
            try {
                var method = packet.getClass().getMethod(name);
                if (method.getReturnType() == String.class) {
                    String value = (String) method.invoke(packet);
                    if (value != null && !value.isEmpty()) {
                        sb.append(value);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (sb.length() > 0) return sb.toString();

        // Method 3: Direct field access
        try {
            for (Field field : packet.getClass().getDeclaredFields()) {
                if (field.getType() == String.class) {
                    try {
                        field.setAccessible(true);
                        String value = (String) field.get(packet);
                        if (value != null && !value.isEmpty()) {
                            sb.append(value);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        if (sb.length() > 0) return sb.toString();

        // Method 4: Last resort - toString() (contains packet metadata too)
        try {
            String str = packet.toString();
            if (str != null && !str.isEmpty()) return str;
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String message = cause.getMessage();
        if (message == null) message = cause.getClass().getSimpleName();

        String lowerMsg = message.toLowerCase();

        // Check for all known packet/protocol exploit exceptions
        if (lowerMsg.contains("packettoolarge")
                || lowerMsg.contains("packet too large")
                || lowerMsg.contains("packet too big")
                || lowerMsg.contains("varint too big")
                || lowerMsg.contains("varlong too big")
                || lowerMsg.contains("badly compressed")
                || lowerMsg.contains("wider than 21-bit")
                || lowerMsg.contains("unable to fit")
                || lowerMsg.contains("tried to read nbt tag that was too big")
                || lowerMsg.contains("too big; tried to allocate")
                || lowerMsg.contains("larger than protocol maximum")
                || lowerMsg.contains("allocate too much memory")
                || lowerMsg.contains("payload may not be larger")
                || lowerMsg.contains("over maximum protocol size")
                || lowerMsg.contains("connection timed out")) {

            String logMsg = message;
            plugin.getLogger().warning("[PacketProtection] Caught exploit packet from/to " + playerName
                    + " (" + playerUUID + "): " + logMsg);

            // Schedule cleanup on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                var player = plugin.getServer().getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    plugin.getNbtChecker().scanAndCleanInventory(player);
                    plugin.notifyAdmins("&c&l[NBTShield] &eCaught packet exploit for &6" + playerName
                            + "&e: " + logMsg);
                }
            });

            // Close connection gracefully instead of crashing
            ctx.close();
            return;
        }

        // For unknown exceptions, let them pass through normally
        super.exceptionCaught(ctx, cause);
    }

    /**
     * Inject PacketProtection handler into a player's Netty channel.
     * Uses reflection to access the internal channel - compatible with Paper 1.21.x.
     */
    public static void inject(NBTShield plugin, Player player) {
        if (!plugin.getConfig().getBoolean("packet-protection", true)) return;

        try {
            Channel channel = getChannel(player);
            if (channel != null) {
                // Remove existing handler if present (e.g. from previous injection)
                if (channel.pipeline().get("nbtshield_protection") != null) {
                    channel.pipeline().remove("nbtshield_protection");
                }

                channel.pipeline().addBefore("packet_handler", "nbtshield_protection",
                        new PacketProtection(plugin, player.getUniqueId(), player.getName()));
                plugin.getLogger().info("[PacketProtection] Injected for " + player.getName());
            } else {
                plugin.getLogger().warning("[PacketProtection] Could not get channel for " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PacketProtection] FAILED to inject for " + player.getName()
                    + ": " + e.getMessage());
        }
    }

    /**
     * Remove PacketProtection handler from a player's Netty channel.
     */
    public static void uninject(Player player) {
        try {
            Channel channel = getChannel(player);
            if (channel != null && channel.pipeline().get("nbtshield_protection") != null) {
                channel.pipeline().remove("nbtshield_protection");
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Get the Netty Channel for a player using reflection.
     * Works with Paper 1.21.x (Mojang mappings, no versioned CraftBukkit).
     */
    private static Channel getChannel(Player player) throws Exception {
        // CraftPlayer.getHandle() -> ServerPlayer
        Object serverPlayer = player.getClass().getMethod("getHandle").invoke(player);

        // ServerPlayer.connection -> ServerGamePacketListenerImpl
        Object connection = findFieldValue(serverPlayer, "ServerGamePacketListenerImpl",
                "ServerCommonPacketListenerImpl");

        if (connection == null) {
            // Fallback: search for field named "connection"
            connection = getFieldByName(serverPlayer, "connection");
        }

        if (connection == null) return null;

        // ServerCommonPacketListenerImpl.connection -> Connection (net.minecraft.network.Connection)
        // Look for the Connection object in the class hierarchy
        Object networkConnection = findNetworkConnection(connection);

        if (networkConnection == null) return null;

        // Connection.channel -> Channel
        return findChannelField(networkConnection);
    }

    private static Object findFieldValue(Object obj, String... typeNameContains) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                String typeName = field.getType().getSimpleName();
                for (String contains : typeNameContains) {
                    if (typeName.contains(contains)) {
                        field.setAccessible(true);
                        return field.get(obj);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Object findNetworkConnection(Object packetListener) throws Exception {
        Class<?> clazz = packetListener.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                String typeName = field.getType().getSimpleName();
                if (typeName.equals("Connection") || typeName.contains("NetworkManager")) {
                    field.setAccessible(true);
                    return field.get(packetListener);
                }
            }
            clazz = clazz.getSuperclass();
        }

        // Fallback: look for field named "connection" in superclass
        clazz = packetListener.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField("connection");
                f.setAccessible(true);
                Object val = f.get(packetListener);
                if (val != null && !val.getClass().getSimpleName().contains("PacketListener")) {
                    return val;
                }
            } catch (NoSuchFieldException ignored) {
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Channel findChannelField(Object networkConnection) throws Exception {
        Class<?> clazz = networkConnection.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (Channel) field.get(networkConnection);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Object getFieldByName(Object obj, String fieldName) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException ignored) {
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
