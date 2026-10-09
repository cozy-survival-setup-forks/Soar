package dev.soar;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedsTest {

    @Test
    void theHighestPermittedLevelWins() {
        assertEquals(1, Speeds.allowed(10, level -> false));
        assertEquals(4, Speeds.allowed(10, level -> level == 4 || level == 2));
        assertEquals(10, Speeds.allowed(10, level -> true));
    }

    @Test
    void aLevelAboveTheConfiguredMaximumIsNeverGranted() {
        assertEquals(5, Speeds.allowed(5, level -> true));
        assertEquals(Speeds.LIMIT, Speeds.allowed(500, level -> true));
    }

    @Test
    void levelOneIsTheNormalSpeedAndTheTopStaysInsideWhatMinecraftAllows() {
        assertEquals(Speeds.NORMAL, Speeds.speed(1, 0.1), 1e-6);
        assertEquals(1.0f, Speeds.speed(10, 0.1), 1e-6);
        assertEquals(1.0f, Speeds.speed(50, 0.1), 1e-6);
        assertTrue(Speeds.speed(1, 0) > 0);
    }

    @Test
    void speedSettingsAreKeptInRange() {
        Settings normal = Settings.from(new YamlConfiguration());
        assertTrue(normal.speedEnabled());
        assertEquals(10, normal.speedMaxLevel());
        assertEquals(1, normal.speedDefaultLevel());

        YamlConfiguration wild = new YamlConfiguration();
        wild.set("speed.step", 0.25);
        wild.set("speed.max-level", 40);
        wild.set("speed.default-level", 9);
        wild.set("speed.enabled", false);
        Settings settings = Settings.from(wild);
        assertFalse(settings.speedEnabled());
        assertEquals(4, settings.speedMaxLevel(), "4 levels of 0.25 already reach the top speed");
        assertEquals(4, settings.speedDefaultLevel());
    }
}
