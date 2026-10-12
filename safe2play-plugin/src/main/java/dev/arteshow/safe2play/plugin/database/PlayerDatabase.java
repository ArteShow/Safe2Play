package dev.arteshow.safe2play.plugin.database;

import java.nio.file.Path;
import java.sql.*;
import java.util.Optional;

public final class PlayerDatabase implements AutoCloseable {
    private final Connection connection;

    public PlayerDatabase(Path path) throws SQLException {
        this.connection = DriverManager.getConnection(
                "jdbc:sqlite:" + path.toAbsolutePath()
        );

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS registered_players (
                    username TEXT PRIMARY KEY COLLATE NOCASE,
                    public_key TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    registered_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
    }

    public synchronized void register(
            String username, String publicKey
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO registered_players (username, public_key, enabled)
                VALUES (?, ?, 1)
                ON CONFLICT(username) DO UPDATE SET
                    public_key = excluded.public_key,
                    enabled = 1
                """)) {
            statement.setString(1, username);
            statement.setString(2, publicKey);
            statement.executeUpdate();
        }
    }

    public synchronized Optional<String> getPublicKey(
            String username
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT public_key
                FROM registered_players
                WHERE username = ? COLLATE NOCASE AND enabled = 1
                """)) {
            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(result.getString("public_key"))
                        : Optional.empty();
            }
        }
    }

    public synchronized boolean revoke(String username)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE registered_players SET enabled = 0
                WHERE username = ? COLLATE NOCASE AND enabled = 1
                """)) {
            statement.setString(1, username);
            return statement.executeUpdate() > 0;
        }
    }

    public synchronized java.util.List<String> listPlayers()
            throws SQLException {
        var players = new java.util.ArrayList<String>();

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT username, enabled FROM registered_players
                ORDER BY username COLLATE NOCASE
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String status = result.getInt("enabled") == 1
                        ? "enabled" : "revoked";
                players.add(result.getString("username") + " (" + status + ")");
            }
        }

        return players;
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }
}
