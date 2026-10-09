package dev.soar;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Soar: flight that is paid for in time. Players spend seconds of TempFly while they fly, and can pay each other
 * time. It shares flight with Origins-Reborn through a scoreboard tag, see {@link Flight}.
 */
public final class SoarPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private Flight flight;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        if (!messages.load() || (settings = readSettings()) == null) {
            getLogger().severe("Soar cannot start with a broken file, so it is switching itself off. Fix the file named above, "
                    + "or delete it to get the default one, then restart.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        flight = new Flight(this);

        TempFlyCommand command = new TempFlyCommand(this);
        PluginCommand tempfly = getCommand("tempfly");
        if (tempfly != null) {
            tempfly.setExecutor(command);
            tempfly.setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new FlightListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, flight::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, flight::repair, 5L, 5L);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) new Expansion().register();

        // After a reload the players are already online
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!flight.hasStoredTime(player)) flight.setTime(player, settings.defaultTime());
            flight.restore(player);
        }
    }

    @Override
    public void onDisable() {
        if (flight != null) flight.shutdown();
    }

    /** @return false if a file is broken, in which case what was loaded before stays in use */
    public boolean reload() {
        Settings fresh = readSettings();
        if (fresh == null || !messages.load()) return false;
        settings = fresh;
        return true;
    }

    private Settings readSettings() {
        try {
            return Settings.from(Settings.strict(new java.io.File(getDataFolder(), "config.yml")));
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            getLogger().severe("config.yml could not be read: " + e.getMessage());
            return null;
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Flight flight() {
        return flight;
    }

    /** %tempfly_duration_raw%, %tempfly_duration_formatted% and %tempfly_active%. */
    private final class Expansion extends PlaceholderExpansion {

        @Override
        public @NotNull String getIdentifier() {
            return "tempfly";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Groovyy Roy";
        }

        @Override
        public @NotNull String getVersion() {
            return getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
            if (player == null) return "";
            return switch (params.toLowerCase(java.util.Locale.ROOT)) {
                case "duration_raw" -> flight.infinite(player) ? "∞" : String.valueOf(flight.time(player));
                case "duration_formatted" -> flight.infinite(player) ? "∞" : TimeFormat.format(flight.time(player));
                case "active" -> String.valueOf(flight.active(player));
                default -> null;
            };
        }
    }
}
