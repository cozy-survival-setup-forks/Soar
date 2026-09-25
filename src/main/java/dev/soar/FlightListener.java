package dev.soar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class FlightListener implements Listener {

    private final SoarPlugin plugin;
    private final Flight flight;

    public FlightListener(SoarPlugin plugin) {
        this.plugin = plugin;
        this.flight = plugin.flight();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!flight.hasStoredTime(player)) flight.setTime(player, plugin.settings().defaultTime());
        flight.markOnJoin(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) flight.restore(player);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flight.forget(event.getPlayer());
    }

    /**
     * Runs first. A player who may not fly any more does not get the toggle. When TempFly ends here the tag is
     * already gone, so Origins can use the same double tap for gliding.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!flight.active(player)) return;

        if (flight.blocked(player)) {
            event.setCancelled(true);
            flight.disable(player, "blocked-world");
        } else if (!flight.hasTime(player)) {
            event.setCancelled(true);
            flight.expire(player);
        } else {
            player.setAllowFlight(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) return;
        if (flight.active(player) || flight.consumeLanding(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getEntity().setFlying(false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean wasFlying = player.isFlying();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !flight.active(player)) return;
            if (flight.blocked(player)) {
                flight.disable(player, "blocked-world");
            } else {
                player.setAllowFlight(true);
                player.setFlying(wasFlying);
                player.setFallDistance(0f);
            }
        }, 2L);
    }
}
