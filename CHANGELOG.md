# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.0.0

First release. Needs Minecraft 26.2, Fabric Loader 0.19.5+, Fabric API, Java 25 and
[Create Fly](https://github.com/ZurrTum/Create-Fly) 26.2-6.0.9-1 or a later 6.0.x (tested with 26.2-6.0.9-2).

### Added

- In-game config screens for Create Fly's client, common and server settings, in the style of Create's
  original menu (spinning cog behind the screens, breadcrumbs, units, search with Ctrl + F).
- Settings of addons that use Create Fly's config system (tested with Create: Dragons Plus).
- Changes are staged until saved: save, discard, set the whole file to the defaults, reset one option; leaving
  with unsaved changes asks first. Options that need a restart or a rejoin are marked.
- The goggles button on the title screen and the pause menu, placed with Create Fly's own
  `mainMenuConfigButtonRow/OffsetX` and `ingameMenuConfigButtonRow/OffsetX` client settings, and the
  Create menu behind it (Configure..., Ponder Index, links to Create Fly).
- Mod Menu integration (optional).
- On a multiplayer server the server settings are read-only.
