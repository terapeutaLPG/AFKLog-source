package pl.jaruso99.afklog.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pl.jaruso99.afklog.AFKLogPlugin;
import pl.jaruso99.afklog.gui.AFKPanelGUI;
import pl.jaruso99.afklog.gui.PlayerLogsGUI;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Obsługuje kliknięcia we wszystkich GUI pluginu AFKLog.
 */
public class GUIListener implements Listener {

    private static final Pattern PAGE_PATTERN = Pattern.compile("\\[(\\d+)/(\\d+)\\]");

    private final AFKLogPlugin plugin;
    private final AFKPanelGUI afkPanelGUI;
    private final PlayerLogsGUI playerLogsGUI;

    public GUIListener(AFKLogPlugin plugin) {
        this.plugin = plugin;
        this.afkPanelGUI = plugin.getAfkPanelGUI();
        this.playerLogsGUI = plugin.getPlayerLogsGUI();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Porównuj po usuniętych kodach kolorów z OBU stron
        String cleanTitle = stripColors(event.getView().getTitle());

        if (cleanTitle.contains(stripColors(AFKPanelGUI.TITLE_PREFIX))) {
            event.setCancelled(true);
            handleAFKPanel((Player) event.getWhoClicked(), event);

        } else if (cleanTitle.contains(stripColors(PlayerLogsGUI.TITLE_PREFIX))) {
            event.setCancelled(true);
            handlePlayerLogs((Player) event.getWhoClicked(), event);
        }
    }

    // ─────────────────────────────────────────────
    //  AFK PANEL
    // ─────────────────────────────────────────────

    private void handleAFKPanel(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getSlot();
        int currentPage = extractPage(event.getView().getTitle());

        switch (slot) {
            case 45: // poprzednia strona
                if (currentPage > 0) afkPanelGUI.open(player, currentPage - 1);
                return;
            case 53: // następna strona
                afkPanelGUI.open(player, currentPage + 1);
                return;
            case 49: // info – nic nie rób
                return;
        }

        // Kliknięcie w głowę gracza (sloty 0-35)
        if (slot < 36 && clicked.getType() == Material.PLAYER_HEAD) {
            if (!(clicked.getItemMeta() instanceof SkullMeta)) return;
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta.getOwningPlayer() == null) return;

            UUID targetUUID = meta.getOwningPlayer().getUniqueId();
            String targetName = meta.getOwningPlayer().getName();
            if (targetName == null) targetName = "Nieznany";

            playerLogsGUI.open(player, targetUUID, targetName, 0);
        }
    }

    // ─────────────────────────────────────────────
    //  PLAYER LOGS
    // ─────────────────────────────────────────────

    private void handlePlayerLogs(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getSlot();
        int currentPage = extractPage(event.getView().getTitle());

        // Pobierz UUID celu ze slotu 4 (głowa gracza)
        UUID targetUUID = getTargetUUID(event.getView().getTopInventory());
        String targetName = getTargetName(event.getView().getTitle());

        switch (slot) {
            case 45: // powrót do panelu
                afkPanelGUI.open(player, 0);
                return;
            case 48: // poprzednia strona
                if (targetUUID != null && currentPage > 0) {
                    playerLogsGUI.open(player, targetUUID, targetName, currentPage - 1);
                }
                return;
            case 50: // następna strona
                if (targetUUID != null) {
                    playerLogsGUI.open(player, targetUUID, targetName, currentPage + 1);
                }
                return;
        }
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    /** Wyciąga numer bieżącej strony (0-based) z tytułu inventory. */
    private int extractPage(String title) {
        String clean = stripColors(title);
        Matcher m = PAGE_PATTERN.matcher(clean);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)) - 1; }
            catch (NumberFormatException ignore) { }
        }
        return 0;
    }

    /** Pobiera UUID z głowy w slocie 4 głównego inventory. */
    private UUID getTargetUUID(Inventory inv) {
        ItemStack item = inv.getItem(4);
        if (item == null || item.getType() != Material.PLAYER_HEAD) return null;
        if (!(item.getItemMeta() instanceof SkullMeta)) return null;
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        return meta.getOwningPlayer() != null ? meta.getOwningPlayer().getUniqueId() : null;
    }

    /** Wyciąga nick gracza z tytułu okna logów. */
    private String getTargetName(String title) {
        // Format po stripColors: "Logi AFK: {name} [X/Y]"
        String clean = stripColors(title);
        String cleanPrefix = stripColors(PlayerLogsGUI.TITLE_PREFIX);
        if (!clean.startsWith(cleanPrefix)) return "Nieznany";
        String rest = clean.substring(cleanPrefix.length()).trim();
        // Usuń " [X/Y]" z końca
        int idx = rest.lastIndexOf(" [");
        if (idx >= 0) return rest.substring(0, idx).trim();
        return rest.trim();
    }

    /** Usuwa kody kolorów z tekstu. */
    private String stripColors(String s) {
        return s.replaceAll("§[0-9a-fklmnorA-FKLMNOR]", "");
    }
}
