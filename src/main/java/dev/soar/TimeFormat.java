package dev.soar;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Seconds as short text ("1h 5m") and short text as seconds ("1h30m", "90"). */
public final class TimeFormat {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms]?)", Pattern.CASE_INSENSITIVE);

    private TimeFormat() {
    }

    public static String format(long totalSeconds) {
        long total = Math.max(0, totalSeconds);
        long days = total / 86400;
        long hours = (total % 86400) / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        StringBuilder text = new StringBuilder();
        if (days > 0) text.append(days).append("d ");
        if (hours > 0) text.append(hours).append("h ");
        if (minutes > 0) text.append(minutes).append("m ");
        if (seconds > 0 || text.isEmpty()) text.append(seconds).append("s");
        return text.toString().trim();
    }

    /**
     * Understands "90" (seconds), "45s", "10m", "2h", "1d" and parts put together such as "1h30m" or "1d 2h".
     *
     * @return the seconds, or -1 if the text is not a time
     */
    public static long parse(String input) {
        String text = input.trim();
        if (text.isEmpty()) return -1;

        Matcher matcher = PART.matcher(text);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            // Anything between the parts other than spaces means the text is not a time.
            if (!text.substring(end, matcher.start()).isBlank()) return -1;
            end = matcher.end();
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
            long unit = switch (matcher.group(2).toLowerCase()) {
                case "d" -> 86400;
                case "h" -> 3600;
                case "m" -> 60;
                default -> 1;
            };
            if (amount > Long.MAX_VALUE / unit || total > Long.MAX_VALUE - amount * unit) return -1;
            total += amount * unit;
        }
        return end == text.length() && total > 0 ? total : -1;
    }
}
