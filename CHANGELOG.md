# Changelog

All notable changes to Orba. Download the latest version from the
[Releases](https://github.com/btalal/OrbaLauncher/releases/latest) page; install `orba.apk` over the previous one,
your tiles and settings are kept.

## [Unreleased]

## [3.11.1] - 2026-11-23
### Changed
- The **screensaver** follows the chosen theme: deep black with a soft amber glow in OLED (no waves), unchanged in Orba and Glossy.
- The repository is now named **OrbaLauncher** (old links keep working).

## [3.11] - 2026-11-23
### Added
- **Themes** (Orba settings → *Theme*, applied at once): **Orba** (the plain original look), **Glossy** (the OrbaTV
  render: translucent glass, glossy highlights, a gradient cyan → blue → violet glow that gently breathes, gradient
  section titles) and **OLED** (deep black `#0A0A0C`, amber accent, Inter type, no waves, sober glow, as in OrbaTV).
  Glossy is the default; the choice is kept.
### Changed
- Focus on the header buttons, menus and tiles follows the chosen theme; long app names scroll instead of being cut.

## [3.10] - 2026-11-23
### Added
- **Uninstall…** in the tile menu (long-press OK), for apps you installed yourself; Android's own confirmation
  window appears before anything is removed. System apps don't show the option.
### Fixed
- **Smooth screensaver**: the logo, time and date no longer stutter or shimmer. They are drawn once into
  graphics layers (the text once a minute) and only moved, rotated and faded by the GPU: 60 frames per second
  measured on a Philips OLED.

## [3.9] - 2026-11-19
### Added
- **App on start-up** (Orba settings → *On start-up*, off by default): after a full start of the TV, Orba opens
  the chosen app once (e.g. a TV box or live TV app); Home brings Orba back. No permission needed. It does not
  run when the TV only wakes from a light standby.
- **Signal colours** on the Wi-Fi icon: green when strong, yellow when medium, red when weak or offline (soft tones
  that suit the midnight-blue background). Ethernet keeps its normal colour.
### Fixed
- After a full restart of the TV, the stock home stayed on screen for about a minute before Orba replaced it (the
  Home service starts after the stock home is already shown). Orba now appears a second or two after start-up.

## [3.8] - 2026-11-19
### Added
- **Favourites dock**: up to 4 apps in large tiles above the grid, with the selected app's name in large type.
  Long-press OK on a tile → *Add to favourites* / *Remove from favourites*; *Move* reorders the dock. On first
  start, the dock is filled with the big streaming apps already installed (YouTube, Netflix, Prime Video,
  Disney+, Canal+, Apple TV, in that order).
- Repository: TV cleanup tools (`tools/`) with the list tested on the Philips 65OLED908, automatic APK build on
  GitHub Actions, contributing guide, issue and pull request templates, this changelog.

## [3.7] - 2026-11-19
### Added
- English and French: Orba follows the TV's language for menus, settings, dates and the screensaver.
- Long-press OK on a tile → **App info…** (force stop, clear cache, uninstall).
- Sideloaded Android apps (`LAUNCHER` category) appear in the grid.
- The remote's **Source / Input** key opens the Sources panel; active HDMI ports show a `●` in Orba's own list.
- Orba declares `CATEGORY_HOME`: it can be the native Home app once the stock launcher is disabled.
### Changed
- The Home shortcut service recognises `launcherx`, `tvlauncher`, `leanbacklauncher` and Xiaomi's `com.mitv.tvhome`.
- Minimum Android version lowered to 8.0 (API 26).

## [3.6] - 2026-11-19
### Added
- Animated screensaver: the logo ring spins and pulses, the time and date drift slowly across the screen (OLED
  friendly). Also registered as an Android screensaver (DreamService).
- **Orba settings** on the TV (hold OK on the gear): tiles per row (4 to 7), background (animated waves, still
  waves, pure black), selection glow (app colour or Orba cyan), screensaver delay.
### Changed
- MIT licence, English README with badges and a demo GIF.

## [3.5] - 2026-11-19
### Added
- **Sources** button in the header: opens the Philips Sources panel, or a list of HDMI inputs on other TVs.
### Fixed
- Philips: the Sources panel crashed while the Help app (`org.droidtv.eum`) was disabled; it must stay enabled.

## [3.4] - 2026-11-18
### Added
- First public version: the Orba home screen (logo, time, date, app grid with a glow in each app's colour,
  animated waves), Wi-Fi and Settings icons, move and hide tiles, Home key shortcut through an accessibility
  service.

[Unreleased]: https://github.com/btalal/OrbaLauncher/compare/v3.11.1...HEAD
[3.11.1]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.11.1
[3.11]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.11
[3.10]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.10
[3.9]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.9
[3.8]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.8
[3.7]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.7
[3.6]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.6
[3.5]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.5
[3.4]: https://github.com/btalal/OrbaLauncher/releases/tag/v3.4
