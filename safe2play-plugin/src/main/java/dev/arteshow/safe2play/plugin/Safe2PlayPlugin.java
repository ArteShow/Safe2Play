
package dev.arteshow.safe2play.plugin;

import dev.arteshow.safe2play.plugin.auth.KeyValidator;
import dev.arteshow.safe2play.plugin.database.PlayerDatabase;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Signature;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Safe2PlayPlugin extends JavaPlugin
        implements Listener, PluginMessageListener {

    private static final String CHALLENGE_CHANNEL = "safe2play:challenge";
    private static final String RESPONSE_CHANNEL = "safe2play:response";
    private static final long AUTH_TIMEOUT_TICKS = 100L;

    private final SecureRandom secureRandom = new SecureRandom();

    private final Map<UUID, byte[]> challenges = new ConcurrentHashMap<>();
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    private PlayerDatabase database;

    @Override
    public void onEnable() {
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IllegalStateException(
                        "Could not create plugin data directory.");
            }

            database = new PlayerDatabase(
                    getDataFolder().toPath().resolve("safe2play.db"));
        } catch (Exception exception) {
            getLogger().severe("Could not initialize Safe2Play database.");
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, CHALLENGE_CHANNEL);
        messenger.registerIncomingPluginChannel(
                this, RESPONSE_CHANNEL, this);

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("Safe2Play enabled. Authentication enforcement is active.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);

        if (database != null) {
            try {
                database.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close Safe2Play database.");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        authenticated.remove(id);

        byte[] nonce = new byte[32];
        secureRandom.nextBytes(nonce);
        challenges.put(id, nonce);

        // Give the connection a moment to finish joining before sending.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || !challenges.containsKey(id)) {
                return;
            }

            try {
                String challenge = Base64.getEncoder().encodeToString(nonce);
                player.sendPluginMessage(
                        this, CHALLENGE_CHANNEL, encodeString(challenge));
            } catch (Exception exception) {
                getLogger().warning(
                        "Could not send authentication challenge to "
                                + player.getName());
                player.kickPlayer("Safe2Play could not start authentication.");
            }
        }, 2L);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !authenticated.contains(id)) {
                challenges.remove(id);
                player.kickPlayer("Safe2Play authentication timed out.");
            }
        }, AUTH_TIMEOUT_TICKS);
    }

    @Override
    public void onPluginMessageReceived(
            String channel, Player player, byte[] message) {
        if (!RESPONSE_CHANNEL.equals(channel)) {
            return;
        }

        UUID id = player.getUniqueId();
        byte[] nonce = challenges.get(id);

        if (nonce == null || authenticated.contains(id)) {
            reject(player, "Unexpected authentication response.");
            return;
        }

        try {
            String response = decodeString(message);
            String[] parts = response.split("\\.", 2);

            if (parts.length != 2) {
                reject(player, "Malformed authentication response.");
                return;
            }

            String suppliedPublicKey = parts[0];
            byte[] signatureBytes = Base64.getDecoder().decode(parts[1]);

            var storedKey = database.getPublicKey(player.getName());

            if (storedKey.isEmpty()) {
                challenges.remove(id);
                reject(
                        player,
                        "This player is not registered with Safe2Play. "
                                + "Ask the server owner to enroll you.");
                return;
            }

            if (!storedKey.get().equals(suppliedPublicKey)) {
                challenges.remove(id);
                reject(player, "This client key does not match the registration.");
                return;
            }

            var publicKey = KeyValidator.decodeEd25519(storedKey.get());

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(nonce);

            if (!verifier.verify(signatureBytes)) {
                challenges.remove(id);
                reject(player, "Safe2Play signature verification failed.");
                return;
            }

            challenges.remove(id);
            authenticated.add(id);

            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "Safe2Play authentication successful."));

            getLogger().info(
                    "Authenticated player " + player.getName() + ".");
        } catch (Exception exception) {
            challenges.remove(id);
            getLogger().warning(
                    "Authentication failed for " + player.getName()
                            + ": " + exception.getMessage());
            reject(player, "Safe2Play authentication failed.");
        }
    }

    private void reject(Player player, String reason) {
        player.kickPlayer(reason);
    }

    private boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        challenges.remove(id);
        authenticated.remove(id);
    }

    // Freeze unauthenticated players until they pass the challenge.
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null && (from.getX() != to.getX()
                    || from.getY() != to.getY()
                    || from.getZ() != to.getZ())) {
                event.setTo(from);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player
                && !isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && !isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && !isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && !isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && !isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!isAuthenticated(player)
                && !player.hasPermission("safe2play.admin")) {
            event.setCancelled(true);
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "Authenticate with Safe2Play before using commands."));
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {
        if (!sender.hasPermission("safe2play.admin")) {
            sender.sendMessage("You do not have permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Safe2Play commands:");
            sender.sendMessage("/safe2play register <player> <public-key>");
            sender.sendMessage("/safe2play list");
            sender.sendMessage("/safe2play revoke <player>");
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "register" -> {
                    if (args.length != 3 || !validUsername(args[1])) {
                        sender.sendMessage(
                                "Usage: /safe2play register <player> <public-key>");
                        return true;
                    }

                    KeyValidator.decodeEd25519(args[2]);
                    database.register(args[1], args[2]);
                    sender.sendMessage(
                            "Registered public key for " + args[1] + ".");
                }

                case "list" -> {
                    if (args.length != 1) {
                        sender.sendMessage("Usage: /safe2play list");
                        return true;
                    }

                    var players = database.listPlayers();
                    sender.sendMessage(
                            "Safe2Play registrations: " + players.size());

                    for (String player : players) {
                        sender.sendMessage("- " + player);
                    }
                }

                case "revoke" -> {
                    if (args.length != 2 || !validUsername(args[1])) {
                        sender.sendMessage(
                                "Usage: /safe2play revoke <player>");
                        return true;
                    }

                    boolean revoked = database.revoke(args[1]);
                    sender.sendMessage(revoked
                            ? "Revoked registration for " + args[1] + "."
                            : "No enabled registration found for "
                                    + args[1] + ".");
                }

                default -> sender.sendMessage(
                        "Unknown subcommand. Use /safe2play");
            }
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(
                    "Invalid Ed25519 public key (Base64 X.509 format required).");
        } catch (Exception exception) {
            getLogger().warning(
                    "Safe2Play command failed: " + exception.getMessage());
            sender.sendMessage(
                    "Safe2Play operation failed. Check the server log.");
        }

        return true;
    }

    private boolean validUsername(String username) {
        return username.matches("[A-Za-z0-9_]{1,16}");
    }

    private static byte[] encodeString(String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeVarInt(output, data.length);
        output.writeBytes(data);

        return output.toByteArray();
    }

    private static String decodeString(byte[] packet) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(packet);
        int length = readVarInt(input);

        if (length < 0 || length > 8192 || length != input.available()) {
            throw new IllegalArgumentException("Invalid message length.");
        }

        byte[] data = input.readNBytes(length);

        if (data.length != length) {
            throw new IllegalArgumentException("Incomplete message.");
        }

        return new String(data, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        output.write(value);
    }

    private static int readVarInt(ByteArrayInputStream input) {
        int result = 0;
        int position = 0;
        int currentByte;

        do {
            currentByte = input.read();

            if (currentByte == -1 || position >= 35) {
                throw new IllegalArgumentException("Invalid VarInt.");
            }

            result |= (currentByte & 0x7F) << position;
            position += 7;
        } while ((currentByte & 0x80) != 0);

        return result;
    }
}
