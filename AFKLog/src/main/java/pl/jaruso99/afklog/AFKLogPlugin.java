package pl.jaruso99.afklog;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import pl.jaruso99.afklog.commands.CommandHandler;
import pl.jaruso99.afklog.gui.AFKPanelGUI;
import pl.jaruso99.afklog.gui.PlayerLogsGUI;
import pl.jaruso99.afklog.listeners.GUIListener;
import pl.jaruso99.afklog.listeners.PlayerListener;
import pl.jaruso99.afklog.managers.AFKManager;
import pl.jaruso99.afklog.managers.DatabaseManager;
import pl.jaruso99.afklog.managers.LuckPermsManager;

/**
 * Główna klasa pluginu AFKLog.
 * Plugin działa w tle – śledzeni gracze nie wiedzą o jego istnieniu.
 * Dostęp tylko dla operatorów lub graczy z uprawnieniem afklog.*
 *
 * @author jaruso99
 * @version 1.0.0
 */
public class AFKLogPlugin extends JavaPlugin {

    private static AFKLogPlugin instance;

    private DatabaseManager databaseManager;
    private AFKManager afkManager;
    private LuckPermsManager luckPermsManager;
    private AFKPanelGUI afkPanelGUI;
    private PlayerLogsGUI playerLogsGUI;

    @Override
    public void onEnable() {
        instance = this;
        printBanner();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        hookLuckPerms();

        afkManager = new AFKManager(this);

        afkPanelGUI = new AFKPanelGUI(this);
        playerLogsGUI = new PlayerLogsGUI(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        registerCommands();

        getLogger().info("Plugin uruchomiony. Autor: jaruso99");
    }

    @Override
    public void onDisable() {
        if (afkManager != null) afkManager.shutdown();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("Plugin wyłączony. Wszystkie sesje zapisane.");
    }

    private void hookLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("LuckPerms nie znaleziony – funkcja grup wyłączona.");
            return;
        }
        RegisteredServiceProvider<LuckPerms> provider =
            Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPermsManager = new LuckPermsManager(this, provider.getProvider());
            getLogger().info("Połączono z LuckPerms.");
        } else {
            getLogger().warning("Nie udało się połączyć z LuckPerms!");
        }
    }

    private void registerCommands() {
        CommandHandler handler = new CommandHandler(this);
        String[] cmds = {"dodajafk", "usunafk", "afkpanel", "afkkicker",
                         "dodajgrupeafk", "usungrupeafk", "afkstats"};
        for (String cmd : cmds) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(handler);
                getCommand(cmd).setTabCompleter(handler);
            }
        }
    }

    private void printBanner() {
        getLogger().info("§6╔════════════════════════════╗");
        getLogger().info("§6║  §eAFKLog §6v" + getDescription().getVersion() + "  by §ejaruso99   §6║");
        getLogger().info("§6╚════════════════════════════╝");
    }

    public static AFKLogPlugin getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public AFKManager getAFKManager() { return afkManager; }
    public LuckPermsManager getLuckPermsManager() { return luckPermsManager; }
    public AFKPanelGUI getAfkPanelGUI() { return afkPanelGUI; }
    public PlayerLogsGUI getPlayerLogsGUI() { return playerLogsGUI; }
}
