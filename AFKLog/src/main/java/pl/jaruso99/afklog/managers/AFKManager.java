package pl.jaruso99.afklog.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.jaruso99.afklog.AFKLogPlugin;
import pl.jaruso99.afklog.models.TrackedPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zarządza systemem wykrywania i logowania AFK.
 * Gracze NIE są informowani o tym, że są śledzeni – plugin działa w tle.
 *
 * Dwa niezależne timery:
 *  - AFK_THRESHOLD_MS  (7 min) → zapis do logów
 *  - kickMinutesMap    (ustaw. per gracz) → cichy kick, bazuje na surowym bezruchu
 */
public class AFKManager {

    private static final long AFK_THRESHOLD_MS = 7L * 60L * 1000L;
    // Sprawdzamy co 20 sekund – wystarczająca granularność, minimalne obciążenie
    private static final long CHECK_INTERVAL_TICKS = 400L;

    private final AFKLogPlugin plugin;

    // UUID -> czas ostatniej aktywności (ms)
    private final ConcurrentHashMap<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    // UUID -> czas startu bieżącej sesji AFK (ms); brak wpisu = gracz aktywny
    private final ConcurrentHashMap<UUID, Long> afkSessions = new ConcurrentHashMap<>();
    // Cache śledzonych UUID (z bazy)
    private final Set<UUID> trackedUUIDs = ConcurrentHashMap.newKeySet();
    // UUID -> minuty bezruchu do cichego kicka (0 = wyłączony)
    private final ConcurrentHashMap<UUID, Integer> kickMinutesMap = new ConcurrentHashMap<>();
    // UUID -> czy już wysłano zadanie kicka (blokuje podwójny kick)
    private final Set<UUID> pendingKick = ConcurrentHashMap.newKeySet();

    private BukkitTask checkTask;

    public AFKManager(AFKLogPlugin plugin) {
        this.plugin = plugin;
        loadFromDatabase();
        startChecker();
    }

    private void loadFromDatabase() {
        for (TrackedPlayer tp : plugin.getDatabaseManager().getAllTrackedPlayers()) {
            trackedUUIDs.add(tp.getUuid());
            if (tp.getKickMinutes() > 0) kickMinutesMap.put(tp.getUuid(), tp.getKickMinutes());
        }
        plugin.getLogger().info("Załadowano " + trackedUUIDs.size() + " śledzonych graczy.");
    }

    private void startChecker() {
        checkTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, this::runAFKCheck, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    // ─────────────────────────────────────────────
    //  GŁÓWNY SPRAWDZACZ (ASYNC, co 20 sek.)
    // ─────────────────────────────────────────────

    private void runAFKCheck() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!trackedUUIDs.contains(uuid)) continue;

            long last  = lastActivity.getOrDefault(uuid, now);
            long idleMs = now - last;

            // ── 1. Cichy kick (własny timer, niezależny od AFK threshold) ──
            checkKick(player, uuid, idleMs);

            // ── 2. Logowanie AFK (7 min) ──
            if (idleMs >= AFK_THRESHOLD_MS) {
                if (!afkSessions.containsKey(uuid)) {
                    long afkStart = last + AFK_THRESHOLD_MS;
                    afkSessions.put(uuid, afkStart);
                    plugin.getDatabaseManager().startAFKSession(uuid, afkStart);
                }
            } else {
                if (afkSessions.containsKey(uuid)) {
                    closeAfkSession(uuid, now);
                }
            }
        }
    }

    /**
     * Sprawdza czy gracz przekroczył limit bezruchu ustawiony przez /afkkicker.
     * Bazuje na surowym idleMs – niezależnie od progu AFK 7 min.
     * Kick jest CAŁKOWICIE CICHY – gracz widzi pusty ekran rozłączenia.
     */
    private void checkKick(Player player, UUID uuid, long idleMs) {
        int kickMins = kickMinutesMap.getOrDefault(uuid, 0);
        if (kickMins <= 0) return;

        long kickThresholdMs = kickMins * 60_000L;
        if (idleMs < kickThresholdMs) return;
        if (pendingKick.contains(uuid)) return; // już w kolejce

        pendingKick.add(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingKick.remove(uuid);
            if (!player.isOnline()) return;

            // Zamknij sesję AFK jeśli była otwarta
            if (afkSessions.containsKey(uuid)) closeAfkSession(uuid, System.currentTimeMillis());

            // Cichy kick – pustę ciąg znaków; gracz nie widzi żadnego powodu
            // Końcowe "-s" (silent flag) ukrywa komunikat w niektórych konfiguracjach proxy
            player.kickPlayer(" -s");
        });
    }

    // ─────────────────────────────────────────────
    //  API PUBLICZNE
    // ─────────────────────────────────────────────

    /** Aktualizuje czas ostatniej aktywności. Wywoływane przez listenery. */
    public void updateActivity(UUID uuid) {
        if (!trackedUUIDs.contains(uuid)) return;
        lastActivity.put(uuid, System.currentTimeMillis());
        if (afkSessions.containsKey(uuid)) closeAfkSession(uuid, System.currentTimeMillis());
    }

    /** Dodaje gracza do śledzenia. Zwraca false jeśli już śledzony. */
    public boolean addTrackedPlayer(UUID uuid, String name) {
        if (trackedUUIDs.contains(uuid)) return false;
        boolean added = plugin.getDatabaseManager().addTrackedPlayer(uuid, name);
        if (added) {
            trackedUUIDs.add(uuid);
            lastActivity.put(uuid, System.currentTimeMillis());
        }
        return added;
    }

    /** Usuwa gracza ze śledzenia. Czyści pamięć i opcjonalnie logi. */
    public boolean removeTrackedPlayer(UUID uuid, boolean deleteLogs) {
        if (!trackedUUIDs.contains(uuid)) return false;
        if (afkSessions.containsKey(uuid)) {
            plugin.getDatabaseManager().endAFKSession(uuid, System.currentTimeMillis());
            afkSessions.remove(uuid);
        }
        trackedUUIDs.remove(uuid);
        lastActivity.remove(uuid);
        kickMinutesMap.remove(uuid);
        pendingKick.remove(uuid);
        return plugin.getDatabaseManager().removeTrackedPlayer(uuid, deleteLogs);
    }

    public boolean isTracked(UUID uuid) { return trackedUUIDs.contains(uuid); }
    public boolean isAFK(UUID uuid)     { return afkSessions.containsKey(uuid); }

    public void setKickMinutes(UUID uuid, int minutes) {
        if (minutes <= 0) kickMinutesMap.remove(uuid);
        else kickMinutesMap.put(uuid, minutes);
        plugin.getDatabaseManager().updateKickMinutes(uuid, minutes);
    }

    public int getKickMinutes(UUID uuid) {
        return kickMinutesMap.getOrDefault(uuid, 0);
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        if (trackedUUIDs.contains(uuid)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().updatePlayerName(uuid, player.getName()));
        }
    }

    public void onPlayerQuit(UUID uuid) {
        if (afkSessions.containsKey(uuid)) closeAfkSession(uuid, System.currentTimeMillis());
        lastActivity.remove(uuid);
        pendingKick.remove(uuid);
    }

    public void shutdown() {
        if (checkTask != null) checkTask.cancel();
        long now = System.currentTimeMillis();
        for (UUID uuid : afkSessions.keySet()) plugin.getDatabaseManager().endAFKSession(uuid, now);
        afkSessions.clear();
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    private void closeAfkSession(UUID uuid, long endTime) {
        afkSessions.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().endAFKSession(uuid, endTime));
    }

    public static String formatDuration(long ms) {
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h " + (m % 60) + "m";
        if (h > 0) return h + "h " + (m % 60) + "m " + (s % 60) + "s";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }
}
