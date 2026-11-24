# Installing Frosty Weather

Frosty Weather is an unofficial fork of [Breezy Weather](https://github.com/breezy-weather/breezy-weather). Official Breezy Weather releases (GitHub, F-Droid, IzzyOnDroid…) do **not** contain Frosty Weather.

## Releases

Releases are published on the [Releases page](https://github.com/jkubaa/frosty-weather/releases). Each release has:
- `frosty-weather-<version>_universal.apk`: works on every device; pick this one if unsure
- `frosty-weather-<version>_arm64-v8a.apk`, `_armeabi-v7a.apk`, `_x86.apk`, `_x86_64.apk`: smaller, per-architecture APKs (most phones are `arm64-v8a`)
- `frosty-weather-<version>_freenet.apk`: only weather sources that work over free networks

SHA-256 checksums are listed in each release’s notes.

## Building from source

Requirements: a JDK (see [`.github/.java-version`](.github/.java-version)) and the Android SDK.

```bash
git clone https://github.com/jkubaa/frosty-weather.git
cd frosty-weather
./gradlew assembleBasicDebug
```

Two flavors are available, as in the upstream project:
- `basic`: the standard version
- `freenet`: only includes weather sources that work over free networks

Do **not** pass the `-Pbreezy` flag: it brands the app as Breezy Weather, which is not allowed for modified versions.

Frosty Weather uses its own `applicationId` (`io.github.jkubaa.frostyweather`), so it can be installed alongside Breezy Weather.
