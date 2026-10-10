package dev.soar;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /tempfly [toggle|balance|pay] for players and give, take, set, reset and reload for admins. The admin ones also
 * work as /tempfly admin give ..., the way older setups call them.
 */
public final class TempFlyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("toggle", "balance", "pay", "speed");
    private static final List<String> ADMIN_SUBS = List.of("give", "take", "set", "reset", "reload", "doctor", "backup");

    private final SoarPlugin plugin;

    public TempFlyCommand(SoarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        String[] rest = args;
        if (rest.length > 0 && rest[0].equalsIgnoreCase("admin")) rest = Arrays.copyOfRange(rest, 1, rest.length);

        String sub = rest.length == 0 ? "toggle" : rest[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> toggle(sender);
            case "balance", "bal", "time" -> balance(sender, rest);
            case "pay" -> pay(sender, rest);
            case "speed" -> speed(sender, rest);
            case "give", "take", "set", "reset" -> {
                if (admin(sender)) change(sender, sub, rest);
            }
            case "reload" -> {
                if (admin(sender)) {
                    messages.send(sender, plugin.reload() ? "reloaded" : "reload-failed");
                }
            }
            case "doctor" -> {
                if (admin(sender)) plugin.doctor().forEach(sender::sendPlainMessage);
            }
            case "backup" -> {
                if (admin(sender)) {
                    if (rest.length < 2 || !rest[1].equalsIgnoreCase("now"))
                        sender.sendPlainMessage("Use /tempfly backup now");
                    else
                        sender.sendPlainMessage(plugin.backupNow() ? "Backup made and checked." : "The backup FAILED, see the console.");
                }
            }
            default -> help(sender);
        }
        return true;
    }

    // ---- player commands ----

    private void toggle(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        Flight flight = plugin.flight();
        Messages messages = plugin.messages();

        if (!player.hasPermission("tempfly.use")) {
            messages.send(player, "no-permission");
        } else if (flight.active(player)) {
            flight.disable(player, "disabled");
        } else if (flight.blocked(player)) {
            messages.send(player, "blocked-world");
        } else if (!flight.hasTime(player)) {
            messages.send(player, "no-time");
        } else {
            flight.enable(player, true);
        }
    }

    private void balance(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        Flight flight = plugin.flight();

        if (args.length > 1) {
            if (!admin(sender)) return;
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.send(sender, "player-not-found", "player", args[1]);
                return;
            }
            messages.send(sender, "balance-other", "player", target.getName(), "time", shown(target));
            return;
        }

        Player player = player(sender);
        if (player == null) return;
        if (!player.hasPermission("tempfly.use")) {
            messages.send(player, "no-permission");
            return;
        }
        if (flight.infinite(player)) {
            messages.send(player, "balance-infinite", "time", TimeFormat.format(flight.time(player)));
        } else {
            messages.send(player, "balance", "time", TimeFormat.format(flight.time(player)));
        }
    }

    private void speed(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) return;
        Messages messages = plugin.messages();
        Flight flight = plugin.flight();

        if (!plugin.settings().speedEnabled()) {
            messages.send(player, "speed-disabled");
            return;
        }
        if (!player.hasPermission("tempfly.speed")) {
            messages.send(player, "no-permission");
            return;
        }
        int max = flight.maxLevel(player);
        if (args.length < 2) {
            messages.send(player, "speed-current", "level", String.valueOf(flight.level(player)), "max", String.valueOf(max));
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            level = 0;
        }
        if (level < 1 || level > plugin.settings().speedMaxLevel()) {
            messages.send(player, "speed-invalid", "max", String.valueOf(max));
        } else if (level > max) {
            messages.send(player, "speed-denied", "level", String.valueOf(level), "max", String.valueOf(max));
        } else {
            flight.setLevel(player, level);
            messages.send(player, "speed-set", "level", String.valueOf(level));
        }
    }

    private String shown(Player player) {
        Flight flight = plugin.flight();
        String time = TimeFormat.format(flight.time(player));
        return flight.infinite(player) ? plugin.messages().raw("infinite") + " (" + time + ")" : time;
    }

    private void pay(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) return;
        Messages messages = plugin.messages();
        Flight flight = plugin.flight();

        if (!player.hasPermission("tempfly.pay")) {
            messages.send(player, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(player, "usage-pay");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "player-not-found", "player", args[1]);
            return;
        }
        if (target.equals(player)) {
            messages.send(player, "pay-self");
            return;
        }
        long seconds = TimeFormat.parse(String.join("", Arrays.copyOfRange(args, 2, args.length)));
        if (seconds <= 0) {
            messages.send(player, "invalid-time");
            return;
        }
        if (flight.time(player) < seconds) {
            messages.send(player, "not-enough");
            return;
        }

        flight.takeTime(player, seconds);
        flight.addTime(target, seconds);
        messages.send(player, "pay-sent", "time", TimeFormat.format(seconds), "player", target.getName());
        messages.send(target, "pay-received", "time", TimeFormat.format(seconds), "player", player.getName());
    }

    // ---- admin commands ----

    private void change(CommandSender sender, String action, String[] args) {
        Messages messages = plugin.messages();
        Flight flight = plugin.flight();

        if (args.length < 2) {
            messages.send(sender, "usage-admin");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return;
        }

        long seconds = 0;
        if (!action.equals("reset")) {
            String text = args.length > 2 ? String.join("", Arrays.copyOfRange(args, 2, args.length)) : "";
            seconds = text.equals("0") && action.equals("set") ? 0 : TimeFormat.parse(text);
            if (seconds < 0) {
                messages.send(sender, "invalid-time");
                return;
            }
        }

        switch (action) {
            case "give" -> {
                flight.addTime(target, seconds);
                messages.send(target, "received", "time", TimeFormat.format(seconds));
            }
            case "take" -> flight.takeTime(target, seconds);
            case "set" -> flight.setTime(target, seconds);
            default -> flight.setTime(target, plugin.settings().defaultTime());
        }
        messages.send(sender, "updated", "player", target.getName(), "time", TimeFormat.format(flight.time(target)));
    }

    // ---- helpers ----

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        plugin.messages().send(sender, "players-only");
        return null;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("tempfly.admin")) return true;
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private void help(CommandSender sender) {
        for (String line : plugin.messages().lines("help")) sender.sendMessage(plugin.messages().parse(line));
        if (sender.hasPermission("tempfly.admin")) {
            for (String line : plugin.messages().lines("help-admin")) sender.sendMessage(plugin.messages().parse(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("tempfly.admin");
        String[] rest = args;
        if (admin && rest.length > 1 && rest[0].equalsIgnoreCase("admin")) rest = Arrays.copyOfRange(rest, 1, rest.length);

        List<String> options = new ArrayList<>();
        if (rest.length == 1) {
            options.addAll(PLAYER_SUBS);
            if (admin) options.addAll(ADMIN_SUBS);
        } else if (rest.length == 2) {
            switch (rest[0].toLowerCase(Locale.ROOT)) {
                case "pay" -> Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(sender)).forEach(p -> options.add(p.getName()));
                case "speed" -> {
                    if (sender instanceof Player player && plugin.settings().speedEnabled()) {
                        for (int level = 1; level <= plugin.flight().maxLevel(player); level++) options.add(String.valueOf(level));
                    }
                }
                case "give", "take", "set", "reset", "balance" -> {
                    if (admin) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                }
                default -> { }
            }
        } else if (rest.length == 3 && admin && List.of("give", "take", "set").contains(rest[0].toLowerCase(Locale.ROOT))
                || rest.length == 3 && rest[0].equalsIgnoreCase("pay")) {
            options.addAll(List.of("30s", "5m", "1h"));
        }

        String typed = rest.length == 0 ? "" : rest[rest.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
