package pl.jaruso99.afklog.models;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Reprezentuje jedną sesję AFK gracza.
 */
public class AFKSession {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private final int id;
    private final String uuid;
    private final long startTime;
    private final long endTime; // 0 = sesja wciąż aktywna

    public AFKSession(int id, String uuid, long startTime, long endTime) {
        this.id = id;
        this.uuid = uuid;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getId() { return id; }
    public String getUuid() { return uuid; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public boolean isActive() { return endTime == 0; }

    /** Zwraca czas trwania sesji w milisekundach. */
    public long getDurationMs() {
        long end = isActive() ? System.currentTimeMillis() : endTime;
        return end - startTime;
    }

    public String getFormattedStart() {
        return DATE_FORMAT.format(new Date(startTime));
    }

    public String getFormattedEnd() {
        if (isActive()) return "§aTrwa teraz";
        return DATE_FORMAT.format(new Date(endTime));
    }
}
