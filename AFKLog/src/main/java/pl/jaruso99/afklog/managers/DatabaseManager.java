package pl.jaruso99.afklog.managers;

import pl.jaruso99.afklog.AFKLogPlugin;
import pl.jaruso99.afklog.models.AFKSession;
import pl.jaruso99.afklog.models.TrackedPlayer;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;

/**
 * Zarządza bazą danych SQLite dla pluginu AFKLog.
 * Plik: plugins/AFKLog/afklog.db – przeżywa restarty serwera.
 */
public class DatabaseManager {

    private final AFKLogPlugin plugin;
    private Connection connection;

    public DatabaseManager(AFKLogPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            String url = "jdbc:sqlite:" + new File(dataFolder, "afklog.db").getAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA cache_size=1000;");
            }

            createTables();
            plugin.getLogger().info("Baza danych SQLite zainicjalizowana pomyślnie.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd inicjalizacji bazy danych!", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS tracked_players (" +
                "  uuid TEXT PRIMARY KEY," +
                "  name TEXT NOT NULL," +
                "  kick_minutes INTEGER DEFAULT 0" +
                ");"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS tracked_groups (" +
                "  group_name TEXT PRIMARY KEY" +
                ");"
            );
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS afk_sessions (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  uuid TEXT NOT NULL," +
                "  start_time INTEGER NOT NULL," +
                "  end_time INTEGER DEFAULT 0" +
                ");"
            );
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_uuid ON afk_sessions(uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_time ON afk_sessions(start_time);");
        }
    }

    // ─────────────────────────────────────────────
    //  TRACKED PLAYERS
    // ─────────────────────────────────────────────

    public synchronized boolean addTrackedPlayer(UUID uuid, String name) {
        String sql = "INSERT OR IGNORE INTO tracked_players (uuid, name, kick_minutes) VALUES (?, ?, 0);";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd dodawania gracza: " + name, e);
            return false;
        }
    }

    /** Usuwa gracza ze śledzenia. Opcjonalnie usuwa też jego logi. */
    public synchronized boolean removeTrackedPlayer(UUID uuid, boolean deleteLogs) {
        try {
            if (deleteLogs) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM afk_sessions WHERE uuid = ?;")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM tracked_players WHERE uuid = ?;")) {
                stmt.setString(1, uuid.toString());
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd usuwania gracza: " + uuid, e);
            return false;
        }
    }

    public synchronized boolean isTracked(UUID uuid) {
        String sql = "SELECT 1 FROM tracked_players WHERE uuid = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized List<TrackedPlayer> getAllTrackedPlayers() {
        List<TrackedPlayer> players = new ArrayList<>();
        String sql = "SELECT uuid, name, kick_minutes FROM tracked_players ORDER BY name ASC;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                players.add(new TrackedPlayer(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("name"),
                    rs.getInt("kick_minutes")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd pobierania listy graczy.", e);
        }
        return players;
    }

    public synchronized void updateKickMinutes(UUID uuid, int minutes) {
        String sql = "UPDATE tracked_players SET kick_minutes = ? WHERE uuid = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, minutes);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd aktualizacji kick_minutes.", e);
        }
    }

    public synchronized int getKickMinutes(UUID uuid) {
        String sql = "SELECT kick_minutes FROM tracked_players WHERE uuid = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("kick_minutes");
        } catch (SQLException e) {
            // silent
        }
        return 0;
    }

    public synchronized void updatePlayerName(UUID uuid, String name) {
        String sql = "UPDATE tracked_players SET name = ? WHERE uuid = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            // silent
        }
    }

    // ─────────────────────────────────────────────
    //  TRACKED GROUPS
    // ─────────────────────────────────────────────

    public synchronized boolean addTrackedGroup(String groupName) {
        String sql = "INSERT OR IGNORE INTO tracked_groups (group_name) VALUES (?);";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupName.toLowerCase());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd dodawania grupy: " + groupName, e);
            return false;
        }
    }

    public synchronized boolean removeTrackedGroup(String groupName) {
        String sql = "DELETE FROM tracked_groups WHERE group_name = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupName.toLowerCase());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd usuwania grupy: " + groupName, e);
            return false;
        }
    }

    public synchronized boolean isGroupTracked(String groupName) {
        String sql = "SELECT 1 FROM tracked_groups WHERE group_name = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupName.toLowerCase());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized List<String> getAllTrackedGroups() {
        List<String> groups = new ArrayList<>();
        String sql = "SELECT group_name FROM tracked_groups ORDER BY group_name ASC;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) groups.add(rs.getString("group_name"));
        } catch (SQLException e) {
            // silent
        }
        return groups;
    }

    // ─────────────────────────────────────────────
    //  AFK SESSIONS
    // ─────────────────────────────────────────────

    public synchronized void startAFKSession(UUID uuid, long startTime) {
        endAllOpenSessions(uuid, startTime);
        String sql = "INSERT INTO afk_sessions (uuid, start_time, end_time) VALUES (?, ?, 0);";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, startTime);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd zapisu sesji AFK dla: " + uuid, e);
        }
    }

    public synchronized void endAFKSession(UUID uuid, long endTime) {
        String sql = "UPDATE afk_sessions SET end_time = ? WHERE uuid = ? AND end_time = 0;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, endTime);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd zamykania sesji AFK dla: " + uuid, e);
        }
    }

    private synchronized void endAllOpenSessions(UUID uuid, long endTime) {
        String sql = "UPDATE afk_sessions SET end_time = ? WHERE uuid = ? AND end_time = 0;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, endTime);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            // silent
        }
    }

    public synchronized List<AFKSession> getPlayerSessions(UUID uuid) {
        List<AFKSession> sessions = new ArrayList<>();
        String sql = "SELECT id, uuid, start_time, end_time FROM afk_sessions " +
                     "WHERE uuid = ? ORDER BY start_time DESC LIMIT 200;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                sessions.add(new AFKSession(
                    rs.getInt("id"),
                    rs.getString("uuid"),
                    rs.getLong("start_time"),
                    rs.getLong("end_time")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd pobierania sesji gracza: " + uuid, e);
        }
        return sessions;
    }

    public synchronized Map<String, Long> getDailyAFKStats(UUID uuid, int days) {
        Map<String, Long> dailyStats = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        long cutoff = now - (long) days * 24L * 60L * 60L * 1000L;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

        String sql = "SELECT start_time, end_time FROM afk_sessions " +
                     "WHERE uuid = ? AND start_time >= ? ORDER BY start_time ASC;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, cutoff);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long start = rs.getLong("start_time");
                long end = rs.getLong("end_time");
                if (end == 0) end = now;
                String day = sdf.format(new Date(start));
                dailyStats.merge(day, end - start, Long::sum);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Błąd pobierania statystyk AFK dla: " + uuid, e);
        }
        return dailyStats;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            // silent
        }
    }
}
