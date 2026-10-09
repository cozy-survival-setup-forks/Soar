# Soar

Flight that is paid for in time, for Paper 1.21.11. Players start with a few seconds of TempFly, use them up while they fly, and can buy more or pay each other. It replaces a Kite script and is made to share flight with [Origins-Reborn](https://github.com/cozy-survival-setup-forks/Origins-Reborn) without the two fighting over the double tap.

## What it does

- `/tempfly` switches TempFly on and off. Double tap jump to fly, as with creative flight. Time is only used while the player is really flying, and only in survival and adventure.
- A title and a sound at the seconds you choose (`warn-at`), and the time left above the hotbar while flying.
- Time is saved on the player. New players get `default-time` seconds. `tempfly.infinite` flies without using time.
- No fall damage while TempFly is on. If it ends in the air the player gets one fall without damage, so running out of time over a cliff does not kill.
- TempFly switches off in the worlds of `blocked-worlds` and comes back after a relog, a death or a game mode change.
- Placeholders: `%tempfly_duration_raw%`, `%tempfly_duration_formatted%`, `%tempfly_active%`.

## Commands

| Command | What it does | Permission |
| --- | --- | --- |
| `/tempfly` | Switch TempFly on or off | `tempfly.use` (everyone) |
| `/tempfly balance` | Your time left | `tempfly.use` |
| `/tempfly pay <player> <time>` | Send some of your time | `tempfly.pay` (everyone) |
| `/tempfly give\|take\|set <player> <time>` | Change someone's time, `0` works for `set` | `tempfly.admin` (op) |
| `/tempfly reset <player>` | Back to `default-time` | `tempfly.admin` |
| `/tempfly balance <player>` | See someone's time | `tempfly.admin` |
| `/tempfly reload` | Reload config.yml and messages.yml | `tempfly.admin` |

Aliases are `/tf`, `/tfly` and `/soar`. `/tempfly admin give ...` also works, the way older setups call it. Times are written `90`, `45s`, `10m`, `2h`, `1d` or together, `1h30m`.

## Working with Origins-Reborn

Both plugins want the double tap: Origins uses it for elytra gliding, TempFly for flying. They settle it with scoreboard tags, which the Origins-Reborn fork supports from `2.10.10-cozy.3`.

- While TempFly is on, Soar puts the tag `tempfly_active` on the player. Origins sees it, leaves flight, fly speed and gliding alone, and the double tap flies.
- When TempFly ends (off, out of time, blocked world) the tag is removed first, so a player with the elytra ability glides again with the same double tap.
- Origins puts `origins_flight` on players with the elytra ability. Soar never takes away flight that belongs to Origins, and it stops gliding when TempFly starts in the air and keeps the player flying instead of dropping them.

The two tag names are `flight-tag` and `origins-tag` in Soar's config.yml. They have to match `misc-settings.external-flight-tag` and `misc-settings.origins-flight-tag` in Origins-Reborn's config.yml, and they do by default. If both plugins write in the action bar, set `actionbar: false`.

## Files

`config.yml` for the numbers and tags, `messages.yml` for every text (MiniMessage and `&` codes, an empty text is skipped).

## Telemetry

On startup Soar sends a small anonymous beacon (plugin name/version, server software/version, online/max player
counts, and a random ID with no player data) so we know which versions are in use. Turn it off with
`metrics.enabled: false` in `config.yml`.

## License

See `LICENSE`: free to run on your own servers, not for redistribution or resale.
