# Create: Gravis's Flying Config Menu

The in-game config menu for [Create Fly](https://github.com/ZurrTum/Create-Fly) (the Fabric port of Create) on
Minecraft 26.2, made to look and work like Create's own. Client-only.

The mod's page text (for CurseForge and Modrinth) is in [docs/description.md](docs/description.md), the history is
in [CHANGELOG.md](CHANGELOG.md), and the credits for the design of the screens are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## How it works

- **Finding the configs.** A mixin on Create Fly's `catnip.config.Builder.create` records every config file that is
  built (Create Fly's and its addons'), with its `ConfigBase` root and its `Builder` (`config/ConfigRegistry`).
- **Reading and writing.** `config/ConfigTrees` builds a tree of groups and options from the file's JSON and the
  live `ConfigBase` values; an edit validates the range and calls Create Fly's own `CValue.set`, which saves the
  file. Nothing is written until the player saves.
- **Screens.** `ui/` reimplements the screens of Create's Catnip config menu on top of the GUI toolkit that Create
  Fly already ships (boxes, icons, the spinning cog). `ui/menu/` has the goggles button and the Create menu.
- **Remote servers.** `config/WorldState` makes the server file read-only while connected to a multiplayer server.

## Build and test

Java 25, Gradle wrapper, from PowerShell:

```
.\gradlew.bat build --offline
```

The jar is `build\libs\gravis-flying-config-menu-26.2-<version>.jar`.

`.\gradlew.bat runClientGameTest` (or `herramientas\probar-cliente.ps1`) starts the real client with Create Fly,
opens every screen, edits values, opens a single-player world to check that saved values change the game (maximum
rotation speed, windmill sails per RPM, windmill advancements) and, if `eula=true` is in
`build\run\clientGameTest\eula.txt`, connects to a local dedicated server to check that the server file is
read-only there. Screenshots go to `build\run\clientGameTest\screenshots`. `-SinModMenu` runs it without Mod Menu,
which is optional.

## License

MIT, see [LICENSE](LICENSE).
