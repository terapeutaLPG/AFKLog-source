package pl.jaruso99.afklog.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.jaruso99.afklog.AFKLogPlugin;
import pl.jaruso99.afklog.managers.AFKManager;
import pl.jaruso99.afklog.models.TrackedPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Główny panel AFK wyświetlający głowy śledzonych graczy.
 * Sloty 0-44: głowy; slot 45/53: strzałki; slot 49: info. Brak szkła.
 */
public class AFKPanelGUI {

    public static final String TITLE_PREFIX = "§0§l» §6§lAFK Panel §0§l«";
    private static final int PLAYERS_PER_PAGE = 45;

    private final AFKLogPlugin plugin;

    public AFKPanelGUI(AFKLogPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, int page) {
        List<TrackedPlayer> all = plugin.getDatabaseManager().getAllTrackedPlayers();
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PLAYERS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = TITLE_PREFIX + " §8[§7" + (page + 1) + "§8/§7" + totalPages + "§8]";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Głowy graczy – sloty 0–44
        int start = page * PLAYERS_PER_PAGE;
        int end = Math.min(start + PLAYERS_PER_PAGE, all.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildSkull(all.get(i)));
        }

        // Nawigacja – tylko gdy strona istnieje (brak szkła zastępczego)
        if (page > 0) {
            inv.setItem(45, navItem(Material.ARROW, "§6§l← §ePoprzednia strona",
                "§7Strona §e" + page + " §7z §e" + totalPages));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, navItem(Material.ARROW, "§eNastępna strona §6§l→",
                "§7Strona §e" + (page + 2) + " §7z §e" + totalPages));
        }

        // Info (środek dolnego rzędu)
        List<String> trackedGroups = plugin.getDatabaseManager().getAllTrackedGroups();
        inv.setItem(49, makeItem(Material.BOOK, "§6§lAFK Panel §8– §7Informacje",
            Arrays.asList(
                "§8▸ §7Śledzeni gracze: §e" + all.size(),
                "§8▸ §7Śledzone grupy LP: §e" + trackedGroups.size(),
                trackedGroups.isEmpty() ? "§8  (brak)" : "§8  " + String.join("§8, §7", trackedGroups),
                "",
                "§7Kliknij w §egłowę gracza §7aby",
                "§7zobaczyć jego logi AFK.",
                "",
                "§8Autor pluginu: §6jaruso99"
            )
        ));

        viewer.openInventory(inv);
    }

    private ItemStack buildSkull(TrackedPlayer tp) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        OfflinePlayer op = Bukkit.getOfflinePlayer(tp.getUuid());
        meta.setOwningPlayer(op);

        AFKManager afkMgr = plugin.getAFKManager();
        boolean online = op.isOnline();
        boolean afk = afkMgr.isAFK(tp.getUuid());

        String status;
        if (!online)    status = "§7◉ Offline";
        else if (afk)   status = "§c◉ AFK";
        else            status = "§a◉ Online";

        meta.setDisplayName("§e§l" + tp.getName() + "  " + status);

        List<String> lore = new ArrayList<>();
        lore.add("§8UUID: §7" + tp.getUuid());
        lore.add("");
        int km = tp.getKickMinutes();
        lore.add(km > 0 ? "§c⚠ §7Auto-kick po: §e" + km + " min AFK" : "§7Auto-kick: §8wyłączony");
        if (afk) lore.add("§cJest teraz AFK!");
        lore.add("");
        lore.add("§7► Kliknij aby zobaczyć logi");

        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack navItem(Material mat, String name, String... lore) {
        return makeItem(mat, name, Arrays.asList(lore));
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
