# Publication privacy review

Review date: **2026-09-30**. Scope: the source/build files selected for this repository, and the two Donutcfg 0.5.0 release JARs. This is a bounded review, not a guarantee or an independent security certification.

## Findings

- No hardcoded access tokens, passwords, private keys, email addresses, IP addresses, hardware identifiers, or absolute user-folder paths were found in the selected mod sources/resources.
- The two release archives were inspected, including metadata and compiled-class strings. No matching credentials/private paths/HWID patterns, recording classes, test classes, or log files were found.
- No separate HTTP client, webhook, socket-based telemetry, or hardware-ID collection was found in the mod implementation. Normal in-game server commands and GUI interactions remain necessary for its features.
- The public author handle and Java package attribution are intentional, not private login credentials.
- Build repositories point to standard Fabric/Gradle/Maven services. Build tools and third-party dependencies were **not** comprehensively security-audited.

## What was excluded

Personal JSON profiles, Minecraft account/session data, local settings, logs, wiki/chat history, recording output, caches, local build folders, and third-party Minecraft/dependency JARs were not selected for publication. The only binary files in source control are the standard Gradle wrappers. Release mod JARs are distributed separately as release assets.

## Local data handling

The mod writes profiles into the active instance's `config/donutcfg-configs/` folder. These files contain the saved server-settings choices, Quick Buy layout, item variants, quantities, and enchantments. `.selection.json` stores the selected profile and mode. Deleted personal profiles are moved to `.deleted/` rather than permanently erased.

If `/savecfg` has no name while `default` is selected, the mod uses the active Minecraft username as a **local filename**. Use `/savecfg mysetup` to choose a different name. Profile names and error paths may appear in local feedback/logs; redact them before sharing diagnostics.

Do not publish your whole Minecraft instance or config folder. If you share a profile, inspect its contents and filename first. The `.gitignore` exclusions are a convenience, not a substitute for reviewing staged files.

## Release checksums

| Asset | SHA-256 |
| --- | --- |
| `donutcfg-0.5.0.jar` | `fe91922ee952a48b27da0fa4ebe7c999b97e9ae57d8de4599c62f5d55e647694` |
| `donutcfg-0.5.0+26.2.jar` | `3571c342471d4a430c2b6e2b5601aec9e2e5425ff6ec6daf1e96571c558b9f9d` |

Compiled archives can differ after repackaging because ZIP metadata is included in the checksum. These hashes identify the assets prepared for this publication, not every future build from the same source.
