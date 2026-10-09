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

        Metrics.start(this);
        Banner.print(this, "Thanks for flying with us.");
        boolean placeholders = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        getLogger().info("Ready: " + settings.defaultTime() + "s of TempFly for new players, " + settings.blockedWorlds().size()
                + " blocked world(s), flight speed " + (settings.speedEnabled() ? "levels 1-" + settings.speedMaxLevel() : "off")
                + ", PlaceholderAPI " + (placeholders ? "hooked" : "not installed") + ".");
        checkOrigins();

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

    /** Both plugins have to use the same tag names, or the double tap goes to the wrong one. */
    private void checkOrigins() {
        org.bukkit.plugin.Plugin origins = Bukkit.getPluginManager().getPlugin("Origins-Reborn");
        if (origins == null) return;
        getLogger().info("Origins-Reborn found, sharing flight through the tag '" + settings.flightTag() + "'.");
        String theirs = origins.getConfig().getString("misc-settings.external-flight-tag");
        if (theirs != null && !theirs.equals(settings.flightTag())) {
            getLogger().warning("flight-tag is '" + settings.flightTag() + "' here but misc-settings.external-flight-tag is '" + theirs
                    + "' in Origins-Reborn. Make them the same, or the double tap will glide instead of fly.");
        }
        String elytra = origins.getConfig().getString("misc-settings.origins-flight-tag");
        if (elytra != null && !elytra.equals(settings.originsTag())) {
            getLogger().warning("origins-tag is '" + settings.originsTag() + "' here but misc-settings.origins-flight-tag is '" + elytra
                    + "' in Origins-Reborn. Make them the same.");
        }
    }

    /** @return false if a file is broken, in which case what was loaded before stays in use */
    public boolean reload() {
        Settings fresh = readSettings();
        if (fresh == null || !messages.load()) return false;
        settings = fresh;
        checkOrigins();
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

    /** %tempfly_duration_raw%, %tempfly_duration_formatted%, %tempfly_active% and %tempfly_speed%. */
    private final class Expansion extends PlaceholderExpansion {

        @Override
        public @NotNull String getIdentifier() {
            return "tempfly";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Groovified";
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
                case "speed" -> String.valueOf(flight.level(player));
                default -> null;
            };
        }
    }
}
