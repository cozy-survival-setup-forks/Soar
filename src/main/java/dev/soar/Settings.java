package dev.soar;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The values of config.yml. */
public record Settings(long defaultTime, Set<String> blockedWorlds, Set<Long> warnAt, Key warnSound,
                       boolean actionBar, String flightTag, String originsTag) {

    private static final Key DEFAULT_SOUND = Key.key("block.note_block.pling");

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

        return new Settings(
                Math.max(0, config.getLong("default-time", 60)),
                worlds,
                marks,
                sound,
                config.getBoolean("actionbar", true),
                config.getString("flight-tag", "tempfly_active"),
                config.getString("origins-tag", "origins_flight"));
    }
}
