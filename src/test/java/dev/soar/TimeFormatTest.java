package dev.soar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatTest {

    @Test
    void timeIsShort() {
        assertEquals("0s", TimeFormat.format(0));
        assertEquals("0s", TimeFormat.format(-5));
        assertEquals("30s", TimeFormat.format(30));
        assertEquals("1m 5s", TimeFormat.format(65));
        assertEquals("1h 1m", TimeFormat.format(3660));
        assertEquals("1d 2h", TimeFormat.format(93_600));
    }

    @Test
    void timeIsParsed() {
        assertEquals(90, TimeFormat.parse("90"));
        assertEquals(45, TimeFormat.parse("45s"));
        assertEquals(600, TimeFormat.parse("10m"));
        assertEquals(5400, TimeFormat.parse("1h30m"));
        assertEquals(93_600, TimeFormat.parse("1d 2h"));
        assertEquals(93_600, TimeFormat.parse("1D2H"));
    }

    @Test
    void notATimeIsRefused() {
        assertEquals(-1, TimeFormat.parse(""));
        assertEquals(-1, TimeFormat.parse("abc"));
        assertEquals(-1, TimeFormat.parse("10x"));
        assertEquals(-1, TimeFormat.parse("5m banana"));
        assertEquals(-1, TimeFormat.parse("0"));
        assertEquals(-1, TimeFormat.parse("-5"));
        assertEquals(-1, TimeFormat.parse("99999999999999999999d"));
    }

    @Test
    void oldColourCodesBecomeTags() {
        assertEquals("<#ff8800>Hi", Messages.convertLegacy("&#ff8800Hi"));
        assertEquals("<gray>a<red>b<bold>c<reset>", Messages.convertLegacy("&7a&cb&lc&r"));
    }
}
