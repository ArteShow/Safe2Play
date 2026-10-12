package dev.arteshow.safe2play.plugin;

import dev.arteshow.safe2play.plugin.auth.KeyValidator;
import dev.arteshow.safe2play.plugin.database.PlayerDatabase;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Locale;

public final class Safe2PlayPlugin extends JavaPlugin {
    private PlayerDatabase database;

    @Override
    public void onEnable() {
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IllegalStateException(
                        "Could not create plugin data directory."
                );
            }

            database = new PlayerDatabase(
                    getDataFolder().toPath().resolve("safe2play.db")
            );
        } catch (Exception exception) {
            getLogger().severe("Could not initialize Safe2Play database.");
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Safe2Play enabled; SQLite database initialized.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
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
                                "Usage: /safe2play register <player> <public-key>"
                        );
                        return true;
                    }

                    // Reject malformed keys before writing to the database.
                    KeyValidator.decodeEd25519(args[2]);
                    database.register(args[1], args[2]);

                    sender.sendMessage(
                            "Registered public key for " + args[1] + "."
                    );
                }

                case "list" -> {
                    if (args.length != 1) {
                        sender.sendMessage("Usage: /safe2play list");
                        return true;
                    }

                    var players = database.listPlayers();
                    sender.sendMessage(
                            "Safe2Play registrations: " + players.size()
                    );

                    for (String player : players) {
                        sender.sendMessage("- " + player);
                    }
                }

                case "revoke" -> {
                    if (args.length != 2 || !validUsername(args[1])) {
                        sender.sendMessage(
                                "Usage: /safe2play revoke <player>"
                        );
                        return true;
                    }

                    boolean revoked = database.revoke(args[1]);
                    sender.sendMessage(revoked
                            ? "Revoked registration for " + args[1] + "."
                            : "No enabled registration found for " + args[1] + ".");
                }

                default -> sender.sendMessage(
                        "Unknown subcommand. Use /safe2play"
                );
            }
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("Invalid Ed25519 public key (Base64 X.509 format required).");
        } catch (Exception exception) {
            getLogger().warning("Safe2Play command failed: " + exception.getMessage());
            sender.sendMessage("Safe2Play operation failed. Check the server log.");
        }

        return true;
    }

    private boolean validUsername(String username) {
        return username.matches("[A-Za-z0-9_]{1,16}");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            try {
                database.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close Safe2Play database.");
            }
        }
    }
}
