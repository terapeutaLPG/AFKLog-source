package pl.jaruso99.afklog.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.jaruso99.afklog.AFKLogPlugin;
import pl.jaruso99.afklog.managers.AFKManager;
import pl.jaruso99.afklog.models.AFKSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Panel logów AFK konkretnego gracza.
 *
 * Układ (54 sloty):
 *   Slot 4      → głowa gracza + info (środek górnego rzędu)
 *   Sloty 9-44  → sesje AFK (do 36 na stronę)
 *   Slot 45     → powrót do panelu
 *   Slot 48     → poprzednia strona
 *   Slot 50     → następna strona
 *   Brak szkła
 */
public class PlayerLogsGUI {

    public static final String TITLE_PREFIX = "§0§lLogi AFK: §e";
    private static final int SESSIONS_PER_PAGE = 36; // sloty 9–44

    private final AFKLogPlugin plugin;

    public PlayerLogsGUI(AFKLogPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, UUID targetUUID, String targetName, int page) {
        List<AFKSession> sessions = plugin.getDatabaseManager().getPlayerSessions(targetUUID);

        int totalPages = Math.max(1, (int) Math.ceil(sessions.size() / (double) SESSIONS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = TITLE_PREFIX + targetName + " §8[§7" + (page + 1) + "§8/§7" + totalPages + "§8]";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // ── Głowa gracza w centrum górnego rzędu (slot 4) ──
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUUID));

        boolean isAfk = plugin.getAFKManager().isAFK(targetUUID);
        int kickMins = plugin.getAFKManager().getKickMinutes(targetUUID);

        skullMeta.setDisplayName("§e§l" + targetName + (isAfk ? " §c§l[AFK]" : ""));
        skullMeta.setLore(Arrays.asList(
            "§8UUID: §7" + targetUUID,
            "",
            "§8▸ §7Wszystkich sesji: §e" + sessions.size(),
            kickMins > 0 ? "§8▸ §7Auto-kick: §e" + kickMins + " min" : "§8▸ §7Auto-kick: §8wyłączony",
            "",
            isAfk ? "§c⚠ Gracz jest teraz AFK!" : "§7Status: §anormalny"
        ));
        skull.setItemMeta(skullMeta);
        inv.setItem(4, skull);

        // ── Sesje AFK – sloty 9–44 ──
        int start = page * SESSIONS_PER_PAGE;
        int end = Math.min(start + SESSIONS_PER_PAGE, sessions.size());
        int slot = 9;
        for (int i = start; i < end; i++) {
            if (slot >= 45) break;
            inv.setItem(slot++, buildSessionItem(sessions.get(i), i + 1));
        }

        if (sessions.isEmpty()) {
            inv.setItem(31, makeItem(Material.PAPER, "§7Brak sesji AFK",
                Arrays.asList("§8Ten gracz nie ma jeszcze", "§8zarejestrowanych sesji AFK.")));
        }

        // ── Przyciski dolnego rzędu ──
        inv.setItem(45, makeItem(Material.BARRIER, "§c§l← §eWróć do panelu",
            Arrays.asList("§7Powrót do głównego panelu AFK")));

        if (page > 0) {
            inv.setItem(48, makeItem(Material.ARROW, "§6§l← §ePoprzednia strona",
                Arrays.asList("§7Strona §e" + page + " §7z §e" + totalPages)));
        }
        if (page < totalPages - 1) {
            inv.setItem(50, makeItem(Material.ARROW, "§eNastępna strona §6§l→",
                Arrays.asList("§7Strona §e" + (page + 2) + " §7z §e" + totalPages)));
        }

        viewer.openInventory(inv);
    }

    private ItemStack buildSessionItem(AFKSession session, int index) {
        Material mat = session.isActive() ? Material.LIME_DYE : Material.GRAY_DYE;
        String label = session.isActive()
            ? "§a§l✦ §6Sesja #" + index + " §a[AKTYWNA]"
            : "§7§l✦ §6Sesja #" + index;

        return makeItem(mat, label, Arrays.asList(
            "§8▸ §7Start:    §e" + session.getFormattedStart(),
            "§8▸ §7Koniec:   §e" + session.getFormattedEnd(),
            "§8▸ §7Czas AFK: §6" + AFKManager.formatDuration(session.getDurationMs())
        ));
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        if (lore != null) m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }
}
