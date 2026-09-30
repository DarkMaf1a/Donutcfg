# Contributing

Both Minecraft versions live in this repository, in separate Gradle projects. Keep user-facing commands, profile behavior, and the default preset consistent across them; their Minecraft APIs and Java versions differ.

## Build

Install Git and the development JDK for the target client. Gradle is included through the wrapper; do not commit downloaded dependencies or Minecraft JARs.

```text
git clone https://github.com/DarkMaf1a/Donutcfg.git
cd Donutcfg/minecraft-1.21.11
```

With **JDK 21**, on Windows:

```powershell
.\gradlew.bat build regressionTest
```

On Linux/macOS:

```sh
./gradlew build regressionTest
```

For the port, use `minecraft-26.2` and **JDK 25**, with the same commands. Output is in the selected project's `build/libs/`. Install the normal mod JAR, not the `-sources` or development JAR.

| Project | Build setup |
| --- | --- |
| `minecraft-1.21.11` | Loom 1.14.10, Gradle 9.2.1, Yarn 1.21.11+build.4, Fabric API 0.141.2+1.21.11 |
| `minecraft-26.2` | Loom 1.18.2, Gradle 9.7.0, unobfuscated Mojang names, Fabric API 0.161.0+26.2 |

The 26.2 project does not use Yarn or a manual remapping step; it uses plain `implementation` dependencies with the non-remapping Loom plugin. Both wrappers verify the distribution checksum.

## Regression checks

`regressionTest` runs the standalone Java test entrypoints:

- `SaveSnapshotStabilityTest`: snapshot stability ignores irrelevant GUI updates.
- `CommandsAndProfilesTest`: command parsing, select/start separation, filename validation, list/delete behavior.
- `MessageFormattingTest`: colors and command-suggestion behavior.
- `PresetPortTest` (26.2 only): real vanilla registry initialization and the 14-setting/43-item preset.

These are standalone checks, not JUnit-discovered tests and **not** an end-to-end DonutSMP session. The Gradle `check` task requires all JavaExec regression tasks; an assertion failure fails the build. Empty JUnit discovery is allowed because no JUnit tests are defined. GitHub Actions builds both targets and runs these checks. Its artifacts are build outputs, not automatically published releases.

## Workflow

1. Create a focused branch from `main`, such as `fix/shop-search`.
2. Make the change in both targets when it affects shared behavior.
3. Build and run regression checks for the affected targets.
4. Test against the current supported server GUI, if available. Record whether this was actually done.
5. Open a pull request with the problem, change, and verification results.

Do not commit profiles, account files, tokens, logs, local paths, caches, or recording output. Review `git diff --cached` before pushing. Use a GitHub noreply email if you do not want your real email in commit history.

There is no project-wide redistribution license selected yet. Publication of source alone is not a grant of a permissive license; ask the maintainer before redistributing a fork.
