package pl.jaruso99.afklog.models;

import java.util.UUID;

/**
 * Reprezentuje gracza śledzony przez system AFK.
 */
public class TrackedPlayer {

    private final UUID uuid;
    private String name;
    private int kickMinutes; // 0 = brak automatycznego kicka

    public TrackedPlayer(UUID uuid, String name, int kickMinutes) {
        this.uuid = uuid;
        this.name = name;
        this.kickMinutes = kickMinutes;
    }

    public UUID getUuid() { return uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getKickMinutes() { return kickMinutes; }
    public void setKickMinutes(int kickMinutes) { this.kickMinutes = kickMinutes; }
}
