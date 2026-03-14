package pl.jaruso99.afklog.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.jaruso99.afklog.AFKLogPlugin;
import pl.jaruso99.afklog.managers.AFKManager;
import pl.jaruso99.afklog.models.TrackedPlayer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Obsługuje wszystkie komendy pluginu AFKLog.
 * Komendy są widoczne/działają TYLKO dla graczy z odpowiednim uprawnieniem (lub OP).
 */
public class CommandHandler implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§8[§6AFK§8] ";

    private final AFKLogPlugin plugin;

    public CommandHandler(AFKLogPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "dodajafk":       return cmdDodajAFK(sender, args);
            case "usunafk":        return cmdUsunAFK(sender, args);
            case "afkpanel":       return cmdAFKPanel(sender);
            case "afkkicker":      return cmdAFKKicker(sender, args);
            case "dodajgrupeafk":  return cmdDodajGrupeAFK(sender, args);
            case "usungrupeafk":   return cmdUsunGrupeAFK(sender, args);
            case "afkstats":       return cmdAFKStats(sender, args);
            default:               return false;
        }
    }

    // ─────────────────────────────────────────────
    //  /dodajafk <nick>
    // ─────────────────────────────────────────────

    private boolean cmdDodajAFK(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afklog.dodaj")) { noPerms(sender); return true; }
        if (args.length < 1) { sender.sendMessage(PREFIX + "§7Użycie: §e/dodajafk §6<nick>"); return true; }

        String nick = args[0];
        UUID uuid = resolveUUID(nick);
        String name = resolveName(nick, uuid);

        if (uuid == null) { sender.sendMessage(PREFIX + "§cGracz §e" + nick + " §cnie istnieje lub nigdy nie grał."); return true; }
        if (plugin.getAFKManager().isTracked(uuid)) { sender.sendMessage(PREFIX + "§eGracz §6" + name + " §ejuż jest śledzony."); return true; }

        boolean added = plugin.getAFKManager().addTrackedPlayer(uuid, name);
        if (added) sender.sendMessage(PREFIX + "§aGracz §e" + name + " §azostał dodany do śledzenia AFK.");
        else sender.sendMessage(PREFIX + "§cNie udało się dodać gracza. Sprawdź logi serwera.");
        // Gracz NIE dostaje żadnej wiadomości
        return true;
    }

    // ─────────────────────────────────────────────
    //  /usunafk <nick> [--zachowajlogi]
    // ─────────────────────────────────────────────

    private boolean cmdUsunAFK(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afklog.usun")) { noPerms(sender); return true; }
        if (args.length < 1) {
            sender.sendMessage(PREFIX + "§7Użycie: §e/usunafk §6<nick> §8[--zachowajlogi]");
            sender.sendMessage("§8  §7Domyślnie logi gracza zostają §cusunięte§7.");
            sender.sendMessage("§8  §7Dodaj §e--zachowajlogi §7aby je zachować.");
            return true;
        }

        String nick = args[0];
        boolean keepLogs = args.length >= 2 && args[1].equalsIgnoreCase("--zachowajlogi");

        UUID uuid = resolveUUID(nick);
        String name = resolveName(nick, uuid);

        if (uuid == null) { sender.sendMessage(PREFIX + "§cGracz §e" + nick + " §cnie znaleziony."); return true; }
        if (!plugin.getAFKManager().isTracked(uuid)) { sender.sendMessage(PREFIX + "§eGracz §6" + name + " §enie jest śledzony."); return true; }

        boolean removed = plugin.getAFKManager().removeTrackedPlayer(uuid, !keepLogs);
        if (removed) {
            sender.sendMessage(PREFIX + "§aGracz §e" + name + " §azostał usunięty ze śledzenia AFK.");
            if (keepLogs) sender.sendMessage("§8  §7Logi zostały §azachowane§7.");
            else sender.sendMessage("§8  §7Logi zostały §cusunięte§7.");
        } else {
            sender.sendMessage(PREFIX + "§cNie udało się usunąć gracza. Sprawdź logi serwera.");
        }
        return true;
    }

    // ─────────────────────────────────────────────
    //  /afkpanel
    // ─────────────────────────────────────────────

    private boolean cmdAFKPanel(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(PREFIX + "§cTylko dla graczy."); return true; }
        if (!sender.hasPermission("afklog.panel")) { noPerms(sender); return true; }
        plugin.getAfkPanelGUI().open((Player) sender, 0);
        return true;
    }

    // ─────────────────────────────────────────────
    //  /afkkicker <nick> <minuty>
    // ─────────────────────────────────────────────

    private boolean cmdAFKKicker(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afklog.kicker")) { noPerms(sender); return true; }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "§7Użycie: §e/afkkicker §6<nick> <minuty>");
            sender.sendMessage("§8  §7Podaj §e0 §7aby wyłączyć auto-kick.");
            return true;
        }

        String nick = args[0];
        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
            if (minutes < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + "§cPodaj prawidłową liczbę minut (całkowita ≥ 0)."); return true;
        }

        UUID uuid = resolveUUID(nick);
        String name = resolveName(nick, uuid);

        if (uuid == null) { sender.sendMessage(PREFIX + "§cGracz §e" + nick + " §cnie istnieje."); return true; }
        if (!plugin.getAFKManager().isTracked(uuid)) {
            sender.sendMessage(PREFIX + "§cGracz §e" + name + " §cnie jest śledzony. Użyj §e/dodajafk " + name + " §cpierwej.");
            return true;
        }

        plugin.getAFKManager().setKickMinutes(uuid, minutes);
        if (minutes == 0) sender.sendMessage(PREFIX + "§7Wyłączono auto-kick dla §e" + name + "§7.");
        else sender.sendMessage(PREFIX + "§7Gracz §e" + name + " §7zostanie wykopany po §c" + minutes + " min §7AFK.");
        // Gracz NIE dostaje wiadomości
        return true;
    }

    // ─────────────────────────────────────────────
    //  /dodajgrupeafk <grupa>
    // ─────────────────────────────────────────────

    private boolean cmdDodajGrupeAFK(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afklog.grupa")) { noPerms(sender); return true; }
        if (args.length < 1) { sender.sendMessage(PREFIX + "§7Użycie: §e/dodajgrupeafk §6<nazwa_grupy>"); return true; }
        if (plugin.getLuckPermsManager() == null) { sender.sendMessage(PREFIX + "§cLuckPerms nie jest aktywny."); return true; }

        String groupName = args[0].toLowerCase();
        if (!plugin.getLuckPermsManager().groupExists(groupName)) {
            sender.sendMessage(PREFIX + "§cGrupa §e" + groupName + " §cnie istnieje w LuckPerms."); return true;
        }
        if (plugin.getDatabaseManager().isGroupTracked(groupName)) {
            sender.sendMessage(PREFIX + "§eGrupa §6" + groupName + " §ejuż jest śledzona."); return true;
        }

        plugin.getDatabaseManager().addTrackedGroup(groupName);
        sender.sendMessage(PREFIX + "§aGrupa §e" + groupName + " §adodana do śledzenia AFK.");

        // Dodaj online graczy z tej grupy (cicho)
        int added = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.getAFKManager().isTracked(p.getUniqueId())
                && plugin.getLuckPermsManager().playerInGroup(p, groupName)) {
                plugin.getAFKManager().addTrackedPlayer(p.getUniqueId(), p.getName());
                added++;
            }
        }
        if (added > 0) sender.sendMessage("§8  §7Automatycznie dodano §e" + added + " §7graczy online z tej grupy.");
        sender.sendMessage("§8  §7Nowi gracze z tej grupy będą dodawani automatycznie przy wejściu.");
        return true;
    }

    // ─────────────────────────────────────────────
    //  /usungrupeafk <grupa>
    // ─────────────────────────────────────────────

    private boolean cmdUsunGrupeAFK(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afklog.usungrupe")) { noPerms(sender); return true; }
        if (args.length < 1) { sender.sendMessage(PREFIX + "§7Użycie: §e/usungrupeafk §6<nazwa_grupy>"); return true; }
        if (plugin.getLuckPermsManager() == null) { sender.sendMessage(PREFIX + "§cLuckPerms nie jest aktywny."); return true; }

        String groupName = args[0].toLowerCase();
        if (!plugin.getDatabaseManager().isGroupTracked(groupName)) {
            sender.sendMessage(PREFIX + "§eGrupa §6" + groupName + " §enie jest śledzona."); return true;
        }

        boolean removed = plugin.getDatabaseManager().removeTrackedGroup(groupName);
        if (removed) {
            sender.sendMessage(PREFIX + "§aGrupa §e" + groupName + " §ausunięta ze śledzenia.");
            sender.sendMessage("§8  §7Uwaga: gracze już dodani indywidualnie nadal są śledzeni.");
            sender.sendMessage("§8  §7Użyj §e/usunafk <nick> §7aby usunąć konkretnych graczy.");
        } else {
            sender.sendMessage(PREFIX + "§cNie udało się usunąć grupy. Sprawdź logi serwera.");
        }
        return true;
    }

    // ─────────────────────────────────────────────
    //  /afkstats <nick> [dni]
    // ─────────────────────────────────────────────

    private boolean cmdAFKStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afklog.stats")) { noPerms(sender); return true; }
        if (args.length < 1) { sender.sendMessage(PREFIX + "§7Użycie: §e/afkstats §6<nick> §7[dni]"); return true; }

        String nick = args[0];
        int days = 7;
        if (args.length >= 2) {
            try {
                days = Integer.parseInt(args[1]);
                if (days < 1 || days > 365) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "§cPodaj prawidłową liczbę dni (1–365)."); return true;
            }
        }

        UUID uuid = resolveUUID(nick);
        String name = resolveName(nick, uuid);
        if (uuid == null) { sender.sendMessage(PREFIX + "§cGracz §e" + nick + " §cnie istnieje."); return true; }

        final UUID finalUUID = uuid;
        final String finalName = name;
        final int finalDays = days;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Long> daily = plugin.getDatabaseManager().getDailyAFKStats(finalUUID, finalDays);
            Bukkit.getScheduler().runTask(plugin, () -> {
                String bar = "§8§m" + repeat("═", 42);
                sender.sendMessage(bar);
                sender.sendMessage("§6§lStatystyki AFK §8─ §e" + finalName + "  §8(ostatnie §7" + finalDays + "§8 dni)");
                sender.sendMessage(bar);

                if (daily.isEmpty()) {
                    sender.sendMessage("§8  §7Brak sesji AFK w tym okresie.");
                } else {
                    long totalMs = 0;
                    long maxMs = daily.values().stream().mapToLong(Long::longValue).max().orElse(1);

                    for (Map.Entry<String, Long> entry : daily.entrySet()) {
                        long ms = entry.getValue();
                        totalMs += ms;
                        String progressBar = buildBar(ms, maxMs, 12);
                        sender.sendMessage("§8  §e" + entry.getKey() + " §8│ " + progressBar + " §6" + AFKManager.formatDuration(ms));
                    }

                    sender.sendMessage(bar);
                    long avgMs = totalMs / daily.size();
                    sender.sendMessage("§8  §7Łącznie: §6§l" + AFKManager.formatDuration(totalMs)
                        + "  §8│  §7Średnia/dzień: §e" + AFKManager.formatDuration(avgMs));

                    daily.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e ->
                        sender.sendMessage("§8  §7Rekordowy dzień: §e" + e.getKey() + " §8(§6" + AFKManager.formatDuration(e.getValue()) + "§8)")
                    );
                }
                sender.sendMessage(bar);
            });
        });

        sender.sendMessage(PREFIX + "§7Pobieranie statystyk dla §e" + name + "§7...");
        return true;
    }

    // ─────────────────────────────────────────────
    //  TAB COMPLETE
    // ─────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            switch (cmd.getName().toLowerCase()) {
                case "dodajafk":
                case "afkkicker":
                case "afkstats":
                    Bukkit.getOnlinePlayers().stream()
                        .filter(p -> sender.hasPermission("afklog.admin") || sender.isOp())
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(partial))
                        .forEach(completions::add);
                    break;
                case "usunafk":
                    // Tylko śledzeni gracze
                    plugin.getDatabaseManager().getAllTrackedPlayers().stream()
                        .map(TrackedPlayer::getName)
                        .filter(n -> n.toLowerCase().startsWith(partial))
                        .forEach(completions::add);
                    break;
                case "dodajgrupeafk":
                case "usungrupeafk":
                    if (plugin.getLuckPermsManager() != null) {
                        plugin.getLuckPermsManager().getAllGroups().stream()
                            .filter(g -> g.toLowerCase().startsWith(partial))
                            .forEach(completions::add);
                    }
                    break;
            }
        } else if (args.length == 2) {
            switch (cmd.getName().toLowerCase()) {
                case "afkkicker":
                    completions.addAll(Arrays.asList("0", "5", "10", "15", "30", "60"));
                    break;
                case "afkstats":
                    completions.addAll(Arrays.asList("1", "3", "7", "14", "30", "90"));
                    break;
                case "usunafk":
                    completions.add("--zachowajlogi");
                    break;
            }
        }
        return completions;
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    private void noPerms(CommandSender sender) {
        sender.sendMessage(PREFIX + "§cBrak uprawnień.");
    }

    private UUID resolveUUID(String nick) {
        Player online = Bukkit.getPlayer(nick);
        if (online != null) return online.getUniqueId();
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(nick);
        return op.hasPlayedBefore() ? op.getUniqueId() : null;
    }

    private String resolveName(String nick, UUID uuid) {
        if (uuid == null) return nick;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String n = op.getName();
        return n != null ? n : nick;
    }

    private String buildBar(long value, long max, int length) {
        int filled = (int) Math.min(length, (value * length) / Math.max(1, max));
        return "§a" + repeat("█", filled) + "§8" + repeat("█", length - filled);
    }

    private String repeat(String s, int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
