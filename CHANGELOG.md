# Changelog

## 0.5.0 · Minecraft 1.21.11 / 26.2

### Profiles and controls

- Local named JSON profiles for both `/settings` and `/shop` Quick Buy.
- Automatic creation of the protected `default` preset on first launch.
- Selection without execution via `/setcfg`, with separate `/startcfg` and `/loadcfg` commands.
- `all`, `settings`, and `shop` load modes; remembered name/mode.
- `/cfglist`, name completion, and recoverable deletion through `/del cfg` or `/delcfg`.
- Cancel button on active inventory/sign screens, chat cancellation, and `/stopcfg`.
- Colored help and feedback; help/name clicks suggest commands rather than executing them.

### Menus and preset

- Approved 14 settings and 43 Quick Buy entries.
- Separate Blast Protection and Protection leggings/boots, distinct Fortune/Silk Touch pickaxes, Breach IV mace, empty shulker, and plain flint and steel.
- `/cleanshop` and `/cfg clean` replace the old `/shopclean` command.
- Save stability compares useful profile data rather than live price/lore refreshes.
- Failed/incomplete saves preserve existing profiles.
- Compatibility hint for unavailable or unsupported DonutSMP GUI layouts.
- Recording tools and their commands removed from the mod.

### Minecraft 26.2

- Ported to Java 25 and unobfuscated Mojang APIs, with Fabric Loader 0.19.5+.
- Equivalent commands, profile controls, preset, and cancellation behavior.
- Reads 1.21.11 schema-1 profiles; newly saved 26.2 profiles cannot be loaded by the older reader.
- Added registry/preset regression coverage.

### Verification

Both targets compiled and passed local regression checks. The latest features have not been verified in a live server session. Release tags identify the client target; install one matching JAR only.
