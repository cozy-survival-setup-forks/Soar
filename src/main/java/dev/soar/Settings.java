package dev.soar;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The values of config.yml. */
public record Settings(long defaultTime, Set<String> blockedWorlds, Set<Long> warnAt, Key warnSound,
                       boolean actionBar, String flightTag, String originsTag,
                       boolean speedEnabled, double speedStep, int speedMaxLevel, int speedDefaultLevel) {

    private static final Key DEFAULT_SOUND = Key.key("block.note_block.pling");

    /** Reads a yaml file and fails with the reason, where the Bukkit loaders only log it and carry on with nothing. */
    public static YamlConfiguration strict(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        return config;
    }

    public static Settings from(FileConfiguration config) {
        Set<String> worlds = new HashSet<>();
        for (String world : config.getStringList("blocked-worlds")) worlds.add(world.toLowerCase(Locale.ROOT));

        Set<Long> marks = new HashSet<>();
        for (long mark : config.getLongList("warn-at")) marks.add(mark);

        Key sound;
        try {
            sound = Key.key(config.getString("warn-sound", DEFAULT_SOUND.asString()));
        } catch (InvalidKeyException e) {
            sound = DEFAULT_SOUND;
        }

        double step = Math.max(0.01, Math.min(1.0, config.getDouble("speed.step", 0.1)));
        // The fastest level may not go past what Minecraft allows
        int top = Math.max(1, Math.min(Math.min(Speeds.LIMIT, (int) Math.floor(1.0 / step + 1e-9)), config.getInt("speed.max-level", 10)));
        int fallback = Math.max(1, Math.min(top, config.getInt("speed.default-level", 1)));

        return new Settings(
                Math.max(0, config.getLong("default-time", 60)),
                worlds,
                marks,
                sound,
                config.getBoolean("actionbar", true),
                config.getString("flight-tag", "tempfly_active"),
                config.getString("origins-tag", "origins_flight"),
                config.getBoolean("speed.enabled", true),
                step,
                top,
                fallback);
    }
}
