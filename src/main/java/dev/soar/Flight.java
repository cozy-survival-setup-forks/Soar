package dev.soar;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything about TempFly flight. The time and the on/off state of a player are kept on the player, the rest is
 * only remembered while they are online.
 *
 * <p>While TempFly is on, the player carries the scoreboard tag {@code flight-tag}. Origins-Reborn looks for that tag
 * and leaves flight, fly speed and gliding alone until it is gone, so the double tap starts TempFly flight and not
 * elytra gliding. When TempFly ends the tag is removed first and a player with the elytra ability glides again.
 */
public final class Flight {

    private static final long LANDING_MILLIS = 20_000;

    private final SoarPlugin plugin;
    private final NamespacedKey timeKey;
    private final NamespacedKey enabledKey;

    private final Set<UUID> active = new HashSet<>();
    // players whose flight TempFly switched on, so only TempFly switches it off again
    private final Set<UUID> granted = new HashSet<>();
    private final Map<UUID, Set<Long>> warned = new HashMap<>();
    // players who ended TempFly in the air and get one fall without damage
    private final Map<UUID, Long> landing = new HashMap<>();

    public Flight(SoarPlugin plugin) {
        this.plugin = plugin;
        this.timeKey = new NamespacedKey(plugin, "duration");
        this.enabledKey = new NamespacedKey(plugin, "enabled");
    }

    // ---- time ----

    public boolean hasStoredTime(Player player) {
        return player.getPersistentDataContainer().has(timeKey, PersistentDataType.LONG);
    }

    public long time(Player player) {
        Long stored = player.getPersistentDataContainer().get(timeKey, PersistentDataType.LONG);
        return stored != null ? stored : plugin.settings().defaultTime();
    }

    public void setTime(Player player, long seconds) {
        player.getPersistentDataContainer().set(timeKey, PersistentDataType.LONG, Math.max(0, seconds));
    }

    public void addTime(Player player, long seconds) {
        setTime(player, TimeFormat.add(time(player), seconds));
    }

    public void takeTime(Player player, long seconds) {
        setTime(player, time(player) - seconds);
    }

    public boolean infinite(Player player) {
        return player.hasPermission("tempfly.infinite");
    }

    public boolean hasTime(Player player) {
        return infinite(player) || time(player) > 0;
    }

    public boolean blocked(Player player) {
        return plugin.settings().blockedWorlds().contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    // ---- state ----

    public boolean active(Player player) {
        return active.contains(player.getUniqueId());
    }

    private boolean enabled(Player player) {
        Byte stored = player.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE);
        return stored != null && stored == 1;
    }

    private void setEnabled(Player player, boolean value) {
        player.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }

    private static boolean survival(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    @SuppressWarnings("deprecation")
    private static boolean airborne(Player player) {
        return !player.isOnGround() && !player.isInWater();
    }

    /** Puts the tag on right away, so Origins does not take the first double tap while the join is still being checked. */
    public void markOnJoin(Player player) {
        if (enabled(player)) player.addScoreboardTag(plugin.settings().flightTag());
    }

    /** Brings back the state of a player after a join or a reload. */
    public void restore(Player player) {
        if (!enabled(player)) return;
        if (blocked(player) || !hasTime(player) || !player.hasPermission("tempfly.use")) {
            disable(player, null);
            return;
        }
        enable(player, false);
    }

    public void enable(Player player, boolean notify) {
        UUID id = player.getUniqueId();
        boolean airborne = airborne(player);

        // If something else (the elytra of Origins) already allows flight, it stays theirs when TempFly ends.
        if (!player.getAllowFlight()) granted.add(id);

        active.add(id);
        setEnabled(player, true);
        player.addScoreboardTag(plugin.settings().flightTag());
        player.setAllowFlight(true);
        player.setFlySpeed(0.1f);
        player.setFallDistance(0f);
        landing.remove(id);
        if (player.isGliding()) player.setGliding(false);
        // Switched on in the air: keep the player up instead of dropping them
        if (airborne && survival(player)) player.setFlying(true);

        if (notify) plugin.messages().send(player, "enabled");
    }

    /**
     * @param messageKey what to tell the player, or null to say nothing
     */
    public void disable(Player player, String messageKey) {
        UUID id = player.getUniqueId();
        boolean wasActive = active.remove(id);
        boolean ours = granted.remove(id);
        warned.remove(id);
        setEnabled(player, false);

        // The tag goes first, so Origins already owns the flight again when it looks at the player
        player.removeScoreboardTag(plugin.settings().flightTag());

        if (wasActive) {
            boolean originsFlight = player.getScoreboardTags().contains(plugin.settings().originsTag());
            if (ours && !originsFlight && survival(player)) {
                player.setAllowFlight(false);
            } else if (player.isFlying() && survival(player)) {
                player.setFlying(false);
            }
            if (airborne(player) && survival(player)) landing.put(id, System.currentTimeMillis() + LANDING_MILLIS);
        }

        if (messageKey != null) plugin.messages().send(player, messageKey);
    }

    public void expire(Player player) {
        disable(player, "expired");
    }

    /** Called when a player leaves: only what was remembered for this session goes, the saved state stays. */
    public void forget(Player player) {
        UUID id = player.getUniqueId();
        active.remove(id);
        granted.remove(id);
        warned.remove(id);
        landing.remove(id);
        player.removeScoreboardTag(plugin.settings().flightTag());
    }

    /** Called when the plugin stops: nobody keeps flight that TempFly gave them. */
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (active.contains(id) && granted.contains(id) && survival(player)) {
                player.setAllowFlight(false);
            }
            player.removeScoreboardTag(plugin.settings().flightTag());
        }
        active.clear();
        granted.clear();
        warned.clear();
        landing.clear();
    }

    /** True once for a player who ended TempFly in the air, so their fall does not hurt. */
    public boolean consumeLanding(Player player) {
        Long until = landing.remove(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    // ---- timers ----

    /** Once a second: time is only used up while the player is really flying. */
    public void tick() {
        for (UUID id : List.copyOf(active)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                active.remove(id);
                continue;
            }
            if (blocked(player)) {
                disable(player, "blocked-world");
                continue;
            }
            if (!player.hasPermission("tempfly.use")) {
                disable(player, "no-permission");
                continue;
            }
            if (!survival(player)) continue;
            if (!hasTime(player)) {
                expire(player);
                continue;
            }
            if (!player.isFlying()) continue;

            if (infinite(player)) {
                actionBar(player, plugin.messages().raw("infinite"));
                continue;
            }

            long left = time(player) - 1;
            setTime(player, left);
            actionBar(player, TimeFormat.format(left));

            if (left <= 0) {
                expire(player);
            } else if (plugin.settings().warnAt().contains(left) && warned.computeIfAbsent(id, k -> new HashSet<>()).add(left)) {
                warn(player, left);
            }
        }
    }

    /** A few times a second: flight is put back if another plugin, a respawn or a game mode change took it away. */
    public void repair() {
        long now = System.currentTimeMillis();
        landing.values().removeIf(until -> until < now);

        for (UUID id : List.copyOf(active)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                active.remove(id);
                continue;
            }
            if (!blocked(player) && survival(player) && hasTime(player) && !player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        }
    }

    private void actionBar(Player player, String time) {
        if (!plugin.settings().actionBar()) return;
        player.sendActionBar(plugin.messages().component("actionbar", "time", time));
    }

    private void warn(Player player, long left) {
        Messages messages = plugin.messages();
        Component title = messages.component("warn-title", "time", TimeFormat.format(left));
        Component subtitle = messages.component("warn-subtitle", "time", TimeFormat.format(left));
        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(1), Duration.ofMillis(250))));
        player.playSound(Sound.sound(plugin.settings().warnSound(), Sound.Source.MASTER, 1f, 1.2f));
    }
}
