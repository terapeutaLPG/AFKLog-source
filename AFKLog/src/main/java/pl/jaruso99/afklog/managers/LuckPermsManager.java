package pl.jaruso99.afklog.managers;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import pl.jaruso99.afklog.AFKLogPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper dla API LuckPerms.
 */
public class LuckPermsManager {

    private final AFKLogPlugin plugin;
    private final LuckPerms luckPerms;

    public LuckPermsManager(AFKLogPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    /** Sprawdza czy dana grupa istnieje w LuckPerms. */
    public boolean groupExists(String groupName) {
        return luckPerms.getGroupManager().getGroup(groupName.toLowerCase()) != null;
    }

    /** Sprawdza czy gracz (online) należy do danej grupy (z dziedziczeniem). */
    public boolean playerInGroup(Player player, String groupName) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;

        return user.getInheritedGroups(user.getQueryOptions())
            .stream()
            .anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
    }

    /** Zwraca wszystkie grupy gracza (z dziedziczeniem). */
    public List<String> getPlayerGroups(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return new ArrayList<>();

        return user.getInheritedGroups(user.getQueryOptions())
            .stream()
            .map(Group::getName)
            .collect(Collectors.toList());
    }

    /**
     * Sprawdza czy gracz należy do którejkolwiek ze śledzonych grup.
     * Wywołanie kosztowne – nie używaj w gorącej pętli.
     */
    public boolean playerInAnyTrackedGroup(Player player) {
        List<String> trackedGroups = plugin.getDatabaseManager().getAllTrackedGroups();
        if (trackedGroups.isEmpty()) return false;

        List<String> playerGroups = getPlayerGroups(player);
        return playerGroups.stream()
            .anyMatch(pg -> trackedGroups.stream().anyMatch(tg -> tg.equalsIgnoreCase(pg)));
    }

    /** Zwraca listę wszystkich załadowanych grup LuckPerms. */
    public List<String> getAllGroups() {
        return luckPerms.getGroupManager().getLoadedGroups()
            .stream()
            .map(Group::getName)
            .sorted()
            .collect(Collectors.toList());
    }
}
