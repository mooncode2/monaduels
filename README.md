# MonaDuels

Decompiled and reconstructed Gradle source for the **MonaDuels** Paper plugin (MC 1.21.11).

## Build

```bash
./gradlew build       # Linux/macOS
gradlew.bat build     # Windows
```

The compiled plugin jar is written to `build/libs/MonaDuels-1.0-SNAPSHOT.jar`.

## Requirements

- JDK 21
- Paper API `1.21.11-R0.1-SNAPSHOT` (fetched automatically from the PaperMC Maven repo)

## Layout

- `src/main/java` — plugin sources (package `org.Mona.monaDuels`)
- `src/main/resources` — `plugin.yml`, config/message YAMLs, kit and menu definitions

## Notes

- Sources were recovered by decompilation (Vineflower). A handful of decompiler
  artifacts were hand-fixed to compile cleanly: raw-generic restoration in
  `ArenaManager`, a switch-case variable rename in `MduelCommand`, and raw-type
  casts for reflective `Enum.valueOf` calls in `MultiverseInventoriesHook`.
- The Multiverse-Core / Multiverse-Inventories integration is soft-dependent and
  accessed entirely by reflection, so no Multiverse artifact is needed to build.
- `plugin.yml`'s `version` is populated from the Gradle project version at build time.
