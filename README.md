<h1 align="center">Frosty Weather</h1>

<div align="center">
  <a href="/LICENSE">
    <img src="https://img.shields.io/badge/license-LGPL--3.0-blue?style=for-the-badge" alt="License LGPL-3.0" />
  </a>
</div>

<h4 align="center">Frosty Weather is an unofficial, modified fork of <a href="https://github.com/breezy-weather/breezy-weather">Breezy Weather</a> that brings a new design language to the whole app.</h4>

> [!IMPORTANT]
> **Frosty Weather is not Breezy Weather.** It is a modified version, not affiliated with or endorsed by the Breezy Weather project or its contributors.
> Please do **not** report issues with Frosty Weather to the Breezy Weather issue tracker; use [this repository’s issues](https://github.com/jkubaa/frosty-weather/issues) instead.

# 🧊 What’s different

Frosty Weather keeps the weather data, sources and features of Breezy Weather, and rebuilds how the app looks and feels around a frosted, liquid glass design.

## Home screen

- **Rebuilt in Jetpack Compose**: header, cards, top and bottom bars, pull-to-refresh and location swipes
- **Liquid glass everywhere**: cards, tiles and bars refract, blur and tint the sky behind them, with soft rims of light
- **Collapsing cards**: as you scroll, each card keeps its header pinned while its content slides up underneath, and the next card slides over it
- **Large hero that hands off** to a compact title (temperature · condition · feels like) as you scroll
- **New cards**: a forecast summary, Highlights, and restyled hourly and daily forecasts with temperature range bars
- **Glass bottom bar** for the main actions, with in-app notifications floating above it
- **Frosted weather icons** as the built-in icon set

## Living sky

The background follows the real sun, moon and weather at each location:

- Night skies with the Milky Way, twinkling stars, shooting stars and a cratered moon with its halo
- Light shafts and lens flare from the sun, and a golden hour that warms through sunset and dusk
- Rain, sleet and storms seen through a wet window, with lightning that lights the clouds from inside
- Snow with depth, soft vapour-like clouds and fog, and the aurora on clear nights at high latitudes
- Tilt and scroll parallax, with clouds that drift in as the weather changes

## Details

- **Detail screens as sheets** over the home screen, with a week strip, a glass metric menu and drag-to-dismiss
- **New, faster charts** drawn on the GPU, with scrubbing, H/L labels and a “now” marker
- **Sunrise/sunset screen** with the sun’s path through the day
- **Moon screen** with a NASA lunar map lit by the current phase, a draggable timeline, rise/set times and the next full moon

## Live Updates

- **Rain countdown**: an ongoing notification when rain is about to start (or a short shower is about to end), from the minutely forecast
- **Weather warnings** as ongoing notifications
- On Android 16+, both show as Live Updates in the status bar and on the lock screen

## Widgets

Three new widgets drawn on the location’s sky with the frosted icons: **Conditions** (2×2), **Hourly forecast** (4×2) and **Forecast** (4×4). They show more hours and days as you resize them.

## Settings and dialogs

- Settings, dialogs, sheets and pickers use a consistent Frosty design: large titles, grouped rows with icon tiles, glass toggles, pull-down menus and time wheels
- Locations list and search redesigned in liquid glass
- “Edit this location” and weather source pickers rebuilt as sheets and menus

For the full list of weather features and supported weather sources, see the [upstream Breezy Weather README](https://github.com/breezy-weather/breezy-weather#readme).

# ⬇️ Download

Download the latest APK from the [Releases page](https://github.com/jkubaa/frosty-weather/releases). If you’re not sure which file to pick, use the `universal` one. See [INSTALL.md](INSTALL.md) for details or to build the app from source.

Frosty Weather has its own version numbers. Each release, and the About screen in the app, also says which Breezy Weather version it is based on.

# 🔒 Privacy

See [PRIVACY.md](PRIVACY.md).

# 📜 License

* [GNU Lesser General Public License v3.0](/LICENSE), with the [additional terms](/LICENSE_ADDITIONAL) of the original project:
  * This License does not grant any rights in the trademarks, service marks, or logos of any Contributor.
  * Misrepresentation of the origin of that material is prohibited, and modified versions of such material must be marked in reasonable ways as different from the original version.
* Third-party components are listed in [3RD_PARTY.md](3RD_PARTY.md).

## Credits

Frosty Weather is based on [Breezy Weather](https://github.com/breezy-weather/breezy-weather), © Breezy Weather contributors, licensed under LGPL-3.0. All credit for the original app, its weather sources and its features goes to them. “Breezy Weather” and its logo belong to their respective owners and are not used to brand this fork.

The moon surface in the Moon details screen (`app/src/main/res/drawable-nodpi/moon_surface.jpg`) is the LRO colour map from the [CGI Moon Kit](https://svs.gsfc.nasa.gov/4720/) by NASA's Scientific Visualization Studio, in the public domain.
