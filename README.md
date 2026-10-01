# NightBrief

NightBrief is a native Android app that answers a single question before you pack the car: is tonight worth imaging from one of your sites? It scores the coming night from cloud, moon and twilight, transparency, wind, seeing, and sky darkness, then delivers a morning go/no-go digest. You can keep several sites, compare them, and look a few nights ahead.

The app is Kotlin, `minSdk` 26. `:app` is the Compose UI: tonight, a week outlook, a planner for one site and date, and a site list with an OpenStreetMap picker. Saved gear drives the exposure hints on those screens. Forecasts, ephemeris, scoring, and settings live in the libraries below.

## Modules

Dependencies point inward. A module may use the ones it lists, not the other way around.

| Module | Kind | Depends on | Role |
| --- | --- | --- | --- |
| `:core-astro` | JVM | — | Sun, Moon, and galactic-centre positions; night windows |
| `:core-weather` | JVM | — | Open-Meteo and 7Timer clients, forecast cache |
| `:core-sites` | JVM | — | Saved sites and the optional Bortle grid |
| `:core-gear` | JVM | — | Camera bodies, lenses, NPF / 500-rule exposure hints |
| `:core-score` | JVM | astro, weather, sites, gear | Night Score, tonight's plan, digest text, site comparison |
| `:data` | Android | score | `AppState` datastore and the process-wide `AppGraph` |
| `:work` | Android | data | Morning alarm, digest notification, forecast prefetch |
| `:app` | Android | work | Compose UI |

`:app` therefore reaches the core libraries only through `:work` → `:data` → `:core-score`.

## Night Score

`NightScoreEngine` scores each dark hour from 0 to 100, then blends those hours into one night. The six factors and their weights:

| Factor | Weight |
| --- | --- |
| Cloud cover | 35 |
| Moon and twilight | 25 |
| Transparency | 15 |
| Wind | 10 |
| Seeing | 10 |
| Light pollution | 5 |

Raw quality is 0..1. Points for an hour are `weight × quality × gate`.

Two gates keep a pretty secondary factor from rescuing a bad night:

* **Cloud gate**, floor 0.35. `cloudGate = 0.35 + 0.65 × cloudQuality`. It multiplies moon, transparency, wind, seeing, and light pollution. Cloud itself is ungated. A fully overcast hour (`cloudQuality = 0`) can earn at most 35% of the other 65 points.
* **Moon/twilight gate**, floor 0.40. `moonGate = 0.40 + 0.60 × moonQuality`, where `moonQuality` already includes the twilight factor. It multiplies **transparency and light pollution only** (together with the cloud gate), so a dark site does not score as if the Moon were down.

The night score is not just the mean. For each factor, points are a **50/50 blend** of the mean over all dark hours and the mean over the best contiguous window of up to 3 hours (the window that maximizes the sum of hourly points). The published score is the sum of those blended points, rounded. The best window is reported separately. Dark hours are the whole hours inside the darkest usable window: astronomical night if there is one, otherwise nautical, otherwise civil.

Per-factor quality, as implemented:

* **Cloud.** `1 − cloud% / 100`. Missing → 0.5, marked estimated.
* **Moon.** 1 when the Moon is below the horizon. Otherwise `1 − illumination^1.5 × clamp((altitude° + 1) / 21, 0, 1)`.
* **Twilight** (multiplied into the moon factor). Sun ≤ −18° → 1; down to −12° ramps from 1 to 0.4; down to −6° ramps from 0.4 to 0; brighter than −6° → 0.
* **Transparency.** 7Timer index 1..8 (lower is better) maps to `(8 − index) / 7`. With no index, humidity is a proxy: ≤ 50% → 0.8, ≥ 95% → 0.15, linear between, and that hour is marked estimated. Missing both → 0.5, estimated.
* **Wind.** `max(sustained, gust × 0.7)`: 1 at ≤ 10 km/h, 0 at ≥ 40 km/h, linear between. Missing both → 0.7, estimated.
* **Seeing.** Same 1..8 index map as transparency. With no index, 250 hPa wind (jet-stream level) is the proxy: ≤ 70 km/h → 0.8, ≥ 200 km/h → 0.2, linear between, marked estimated. Missing both → 0.5, estimated.
* **Light pollution.** `(9 − bortle) / 8` for a class in 1..9.

7Timer's ASTRO product covers about 72 hours. Past that horizon the index fields are absent, so transparency and seeing fall through to the humidity and 250 hPa proxies above. Open-Meteo still supplies cloud, wind, humidity, and the jet-stream wind. A night factor is flagged estimated if any of its dark hours was estimated.

Bands: Excellent ≥ 85, Good ≥ 70, Fair ≥ 50, Marginal ≥ 30, otherwise Poor. Verdict: Go ≥ 70, Maybe ≥ 50, otherwise No-go.

## Data sources

* **Open-Meteo** forecast API, no key. Hourly cloud (including low/mid/high), humidity, temperature, dew point, 10 m wind and gusts, and wind at 250 hPa. The model is `gem_seamless` (Environment Canada GEM) when latitude ≥ 41.6 and longitude is between −141.1 and −52.5, which covers Canada and the northern US border where GEM's high-resolution domain still applies. Everywhere else the model is `best_match`.
* **7Timer! ASTRO** (`7timer.info`) for seeing and transparency indexes. The nearest 7Timer sample within ±90 minutes is attached to each Open-Meteo hour. If 7Timer fails, the forecast is still used and the score falls back to the proxies above.
* **Ephemeris** is computed on device from the Astronomical Almanac low-precision Sun and Moon formulas (`Bodies`), plus a simple horizontal transform. The Sun is good to about 0.01° and the Moon to about 0.3° in longitude. Rise/set, twilight, illumination, and galactic-centre altitude are checked in `NightEphemerisTest` against Skyfield 1.55 with the DE421 kernel.
* **Bortle class** comes from the site if the user set one. Otherwise scoring uses class 5. If the app ships an asset named `bortle.nblp`, `AppGraph` loads it and the site editor can fill the class from the map. The file is optional; the repo does not include a world grid.

Build a grid from a light-pollution GeoTIFF (for example the 2015 World Atlas of Artificial Night Sky Brightness, artificial brightness in mcd/m²) with `tools/build_bortle_grid.py`. The writer uses the same mcd → SQM → Bortle thresholds as `BortleClass.fromArtificialBrightness`. `--input-units sqm` accepts a mag/arcsec² raster. `--cell-deg` downsamples by pooling linear luminance: **max** (the default) keeps the brightest sample in the cell, the worst sky; **mean** uses the average luminance. Output is the NBLP v1 layout documented in `Bortle.kt`: big-endian magic `NBLP`, version 1, south latitude, west longitude, cell size, row count, column count, then row-major uint8 classes starting at the south-west corner (`0` = no data, `1`..`9` = Bortle).

```bash
python3 -m venv .venv
.venv/bin/pip install -r tools/requirements.txt
.venv/bin/python tools/build_bortle_grid.py atlas.tif bortle.nblp --cell-deg 0.1
.venv/bin/python tools/test_build_bortle_grid.py
```

Place the file at `app/src/main/assets/bortle.nblp` if you want lookup inside the app. The tool needs [rasterio](https://rasterio.readthedocs.io/); without it, it exits and tells you to install `tools/requirements.txt`.

Forecasts are cached as one JSON file per ~1 km cell under the app's files directory. A result younger than 60 minutes is served without a network call unless a refresh is forced. The morning digest always forces a refresh. If that fetch throws and a cached forecast is younger than 48 hours, the digest is built from that stale forecast and the notification says so.

## Digest scheduling

Digest times are wall-clock times in the device zone. The primary site uses the global time (default 08:00) unless it has its own override. Any other site sends a digest only when it has an override. Turning the digest off, or leaving onboarding unfinished, clears the alarm.

`DigestScheduler.reschedule` arms the next time with `AlarmManager`:

* `setExactAndAllowWhileIdle` (`RTC_WAKEUP`) when exact alarms are allowed — always below API 31, and on API 31+ only if `canScheduleExactAlarms()` is true. The manifest declares `SCHEDULE_EXACT_ALARM`.
* otherwise `setAndAllowWhileIdle`, which is inexact but still allowed to fire in Doze.

When the alarm fires, `DigestAlarmReceiver` enqueues an expedited `DigestWorker` (if the app is out of expedited quota it runs as ordinary work) and arms the following day. `RescheduleReceiver` does the same re-arm after boot, app update, clock or timezone changes, and exact-alarm permission changes.

`DigestWorker` scores tonight for each site that is due and posts a notification on the `digest` channel. If a site's forecast was not freshly fetched, it enqueues a **network-constrained** one-time worker (15 minute delay, exponential backoff of 15 minutes) that rebuilds the same notification in place and stays quiet (`setSilent`). A separate `PrefetchWorker` runs every 3 hours, only when the network is connected, so the cache is usually warm even if the morning itself is offline. Prefetch retries only when every site failed.

## Build and test

JDK 21 and an Android SDK are required (`compileSdk` 35).

```bash
export ANDROID_HOME=~/android-sdk
./gradlew :app:assembleDebug
./gradlew :core-astro:test :core-weather:test :core-sites:test :core-gear:test :core-score:test
./gradlew :data:testDebugUnitTest :work:testDebugUnitTest
```

JVM modules (`core-*`) use the `test` task. Android modules use `testDebugUnitTest`.

## Attribution

* **Open-Meteo** forecast data is used under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Credit Open-Meteo (https://open-meteo.com/).
* **OpenStreetMap.** The site map is an osmdroid `MapView` of OSM tiles. The picker shows “Map data © OpenStreetMap contributors”. Keep that credit. OSM data is © OpenStreetMap contributors and available under the [Open Database License](https://www.openstreetmap.org/copyright).
* **7Timer!** seeing and transparency come from the ASTRO product at https://www.7timer.info/.
* A bundled `bortle.nblp` is derived data. If you build it from the World Atlas of Artificial Night Sky Brightness (Falchi et al.), cite that atlas and follow its licence; NightBrief does not ship the grid.
