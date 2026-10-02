# Practices for agents

NightBrief is a native Android app (`app.nightbrief`, Kotlin, `minSdk` 26, `compileSdk` 35) that scores a night for astrophotography. Read this before changing code. `README.md` is the spec for the score, the Bortle grids, and digest scheduling. `ROADMAP.md` is status, known debt, and ideas that are not committed scope.

Match the code that is already here. A change that "cleans up" an intentional constraint is a bug.

## Read the matching spec first

- Score weights, gates, bands, or verdicts: `README.md` (Night Score) and `NightScoreEngine`.
- Bortle grids or lookup: `README.md` (Data sources) and `Bortle.kt`.
- Digest alarms, prefetch, Big Night, or the widget refresh: `README.md` (Digest scheduling) and `work/`.
- Anything under "Proposed" in `ROADMAP.md`: it is not committed work. Do not start it unless the task says so.

## Module boundaries

Dependencies point inward. A module may use the ones it lists, and nothing else.

| Module | Kind | May depend on | Put here |
| --- | --- | --- | --- |
| `:core-astro` | JVM | — | Sun, Moon, galactic centre, night windows, meteor table, SGP4, ISS passes |
| `:core-weather` | JVM | — | Open-Meteo, 7Timer, SWPC, Celestrak, forecast and TLE caches |
| `:core-sites` | JVM | — | Sites, Bortle class, grid lookup |
| `:core-gear` | JVM | — | Bodies, lenses, NPF / 500-rule exposure |
| `:core-score` | JVM | the four cores above | Night Score, tonight's plan, meteors, ISS and aurora outlooks, digest text, site comparison, widget and Wear copy |
| `:data` | Android | `:core-score` | `AppState`, `SettingsRepository`, process-wide `AppGraph` |
| `:work` | Android | `:data` | Alarms, `DigestWorker`, `PrefetchWorker`, notifications, the widget-refresh broadcast |
| `:app` | Android | `:work` only | Compose UI, home-screen widget, Wear data-layer publish |
| `:wear` | Android (watch) | `:data` | Tile and watch screen |

`:app` reaches the engines only through `:work` → `:data` → `:core-score`. `:wear` does not depend on `:app` or `:work`, so the phone widget and the digest alarms do not depend on the watch.

`:core-score`, `:data`, and `:work` expose their project dependency with `api`, so `:app` can see the engines through `:work` without depending on them directly. `:app` and `:wear` use `implementation`. Pin versions in `gradle/libs.versions.toml`. Do not add a repository on a subproject (`FAIL_ON_PROJECT_REPOS`).

Pure JVM modules stay free of Android. New scoring, ephemeris, and forecast parsing go in `core-*` with unit tests. Compose, WorkManager, notifications, and the map stay in `:app` or `:work`.

`AppGraph.get(context)` is the one process-wide graph, shared by the UI and the workers. Tests swap sources with `replaceSourcesForTest` / `resetSourcesForTest`. Do not open a second settings file to fake a forecast.

`:work` cannot depend on `:app`. Workers refresh the home widget with `WidgetRefresh.request` (`app.nightbrief.action.REFRESH_WIDGET`). The widget reads the briefing cache. It does not score on its own.

## Where a new behavior goes

- A number the user sees on Tonight, in the digest, on the widget, and on the watch is computed in `:core-score` (or a core it already depends on) and rendered in each surface. Do not recompute it in the composable, the worker, and the tile.
- Imaging targets stay in `TargetAdvisor` / `suggestions`. A shower, an ISS pass, or aurora is its own card and digest line, not a row in "Try:".
- `ExposureCalculator` and `TargetAdvisor` already produce the exposure hint. Do not rebuild that path.
- Kp, meteors, ISS, dew, and cloud layers are not `NightScoreEngine` inputs. Show them. Leave the score fixtures alone.
- Optional network products are constructor arguments that may be null (`KpSource`, `TleSource`). A failed fetch leaves that field empty and does not fail the briefing.

## Failure isolation

A missing side product must not take down the night score, the digest, or the widget.

- Re-throw `CancellationException`. Swallowing it breaks coroutine cancellation. Every existing `catch` does this.
- Catch other failures at the boundary: one site's forecast, SWPC, Celestrak, a zone lookup, a Wear publish. The rest of the briefing still returns.
- `DigestWorker` and `PrefetchWorker` return `Result.success()` immediately when onboarding is incomplete or there are no sites. They do not fetch.
- Prefetch retries only when every site's forecast failed. Scoring or notifying after a saved prefetch must not turn that success into `Result.retry()` (see the comment in `PrefetchWorker`).
- A Wear publish failure is swallowed in `WearPublisher` and must not skip the widget update.
- Corrupt `app_state.json` is replaced wholesale with `AppState()` (onboarding incomplete, example gear, no sites). That wipe is known debt. Do not make it more likely: every new `@Serializable` field needs a default so an older file still decodes.

## Night Score invariants

`NightScoreEngine` is the product. Do not retune weights, gates, or bands without updating `README.md` and the fixtures in `NightScoreEngineTest`.

Weights: cloud 35, moon and twilight 25, transparency 15, wind 10, seeing 10, light pollution 5. Cloud gate floor 0.35 multiplies every factor except cloud. Moon/twilight gate floor 0.40 multiplies transparency and light pollution only. The night score is a 50/50 blend of all dark hours and the best contiguous window of up to 3 hours.

Bands: Excellent ≥ 85, Good ≥ 70, Fair ≥ 50, Marginal ≥ 30, otherwise Poor. Verdict: Go ≥ 70, Maybe ≥ 50, otherwise No-go. `WearGlance` uses that same verdict. It is not an engine input.

Fixtures that must keep passing:

- Clear moonless night in 86..95 (Excellent, Go).
- Overcast in 9..21 (Poor, No-go).
- Full-moon clear night in 49..61 (Maybe).

Missing inputs use the documented proxy and set `estimated`. A night factor is estimated if any dark hour was. Do not treat a proxy as a measurement.

Unknown Bortle scores as class 5 (`Site.effectiveBortle`). `SiteForm` writes a looked-up class only when the grid returns non-null, so a class can stick after a move into a cell with no data. That is current behavior.

`WeatherModel.forLocation` uses `gem_seamless` when latitude ≥ 41.6 and longitude is in −141.1..−52.5. That is a box, not a country. High latitude for aurora is geographic |latitude| ≥ 55°. It is not the GEM box.

Ephemeris is planning-grade (`Bodies`, Astronomical Almanac low precision). `core-astro/src/test/kotlin/app/nightbrief/astro/NightEphemerisTest.kt` is checked against Skyfield 1.55 + DE421 (tolerances 4 min, 6 min for lunar rise/set). Do not "correct" ΔT, precession, or refraction unless the task is to change that model. To refresh values, use a Python venv with `skyfield`, load `de421.bsp`, and update the expected instants in that test. There is no generator script in the repo. `tools/requirements.txt` is numpy and rasterio only, not Skyfield.

## Time and persistence

- Inject `java.time.Clock` (default `Clock.systemUTC()`) into anything that reads "now". Tests use `Clock.fixed` or a small settable clock. Do not call `Instant.now()` inside scoring or cache decisions.
- Digest times are wall-clock `HH:mm` strings in the device zone. The primary site uses the global time (default 08:00) unless it has `digestTimeOverride`. Any other site sends a digest only when it has an override.
- `@Serializable` models store instants as epoch seconds and clock times as strings. Expose `java.time` types as computed properties (`Forecast.fetchedAt`, `Site.zone`). `java.time` types do not belong on a serialized field.
- JSON is `Json { ignoreUnknownKeys = true; encodeDefaults = true }` for `AppState` and the onboarding drafts. Forecast files use `ignoreUnknownKeys = true`.
- Domain checks use `require` in `init` (latitude, longitude, Bortle 1..9).
- Forecast cache: one JSON file per ~1 km cell, written via a temp file. Younger than 60 minutes is served without a network call unless refresh is forced. The morning digest always forces a refresh. If that fetch throws and the cache is younger than 48 hours, the digest is built from it and the copy says so.
- ISS elements cache for 12 hours, and up to 7 days when a later fetch fails.
- Bortle lookup does blocking I/O and can take about a second. Call it on `Dispatchers.IO`. Do not cache the grids in memory. The site form waits about 400 ms after coordinates settle before looking up.
- There is one digest `PendingIntent` (request code 1001). The receiver re-arms the next instant after it fires. Do not add a second alarm slot unless the task is to fix that.

## Bortle grids and licences

The atlas is CC BY-NC 4.0. A paid tier, ads, or an in-app purchase cannot ship `bortle_na.nblp.gzip` and `bortle_world.nblp.gzip`. Do not add a purchase flow on top of these files.

Asset names end in `.gzip`, not `.gz`. AGP's asset merger gunzips `*.gz` and drops the suffix. `:data` and `:app` both set `noCompress += "gzip"`. Leave the committed files gzip-compressed. Do not commit uncompressed `.nblp`.

Rebuild only with `tools/build_bortle_grid.py`, the bbox and `--aggregate mean` from `README.md`, then `tools/test_build_bortle_grid.py`. The source GeoTIFF is not in the repo.

## Attribution stays visible

- Open-Meteo, CC BY 4.0, credited in Settings.
- 7Timer.
- The map shows “Map data © OpenStreetMap contributors” (`MapPicker.kt`). Keep that credit on the picker and in Settings.
- `LightPollutionAttribution.TEXT` in Settings, unchanged.
- NOAA SWPC and Celestrak credits in Settings.
- Ephemeris credit points at the Astronomical Almanac low-precision formulas.
- HTTP calls send `User-Agent: NightBrief/0.1` (`Http.kt`).

## UI

- One `AppViewModel`, created in `MainActivity` and passed through `NightBriefNavHost`. `selectedSiteId` is memory-only. Process death returns Tonight and Week to the primary site unless the notification intent sets `EXTRA_SITE_ID`.
- Onboarding site and gear drafts live in `SavedStateHandle` as JSON. The step index and digest time use `rememberSaveable`.
- Routes: Tonight, Week, Sites, Gear are tabs. Settings is pushed from Tonight. `night/{siteId}/{date}` is the planner. The route date is the source of truth. Replacing the destination keeps the back stack from growing by one entry per day. The stepper stops at the forecast horizon (`OpenMeteoClient.DEFAULT_FORECAST_DAYS`).
- Briefing refresh follows onboarding, sites, and gear. Digest time, digest enabled, and `alternativeThreshold` do not refetch. The threshold is read live.
- UI copy is hardcoded in composables. `strings.xml` is only `app_name`. Match that until the task is localisation. New controls that TalkBack must name (planner arrows, the Daily digest and Big Night switches) need the same content descriptions `ConnectedAccessibilityTest` and `TalkBackLabelTest` already look up.
- Screenshots in `docs/screenshots` come from `ReadmeScreenshotTest` and `WearScreenshotTest`. If you change a golden, update the README image too.
- Phone `applicationId` is `app.nightbrief`. The watch uses the same id, `minSdk` 30, and depends on `:data` only. The tile prefers the phone's data item and otherwise reads the watch's own `AppGraph`. No complication.

## Tests

JDK 21 to run Gradle. Module bytecode target is 17.

```bash
export ANDROID_HOME=~/android-sdk
./gradlew :core-astro:test :core-weather:test :core-sites:test :core-gear:test :core-score:test
./gradlew :data:testDebugUnitTest :work:testDebugUnitTest :wear:testDebugUnitTest
./gradlew assembleDebug :app:lintDebug test
```

JVM modules use `test`. Android modules use `testDebugUnitTest`. `testReleaseUnitTest` is disabled on `:app` and `:wear` because Compose tests need the debug `ui-test-manifest`. Leave it disabled.

JUnit 4 (`org.junit.Test`, `org.junit.Assert`), not JUnit 5. Name a test for the behavior (`clearMoonlessNightScoresAround90`). Put the expected value and the actual value in the assertion message when the assertion is not `assertEquals`.

- Engine and parsing tests are plain JVM tests. HTTP clients use MockWebServer. Do not add another test that calls the live network. `DigestWorkerTest.onboardedSitePostsTonightDigest` already does, with a 180s timeout, and it fails offline.
- Robolectric: `@Config(sdk = [34])` on phone and `:work` tests, `@Config(sdk = [30])` on `:wear`. Pass `application = Application::class` when the test must not start `NightBriefApp`.
- Workers: `TestListenableWorkerBuilder` and `WorkManagerTestInitHelper`.
- Compose behavior: `createComposeRule` under Robolectric (`RoadmapUiTest`, `TalkBackLabelTest`, `OnboardingProcessDeathTest`, `WearGlanceScreenTest`).
- Goldens: `./gradlew :app:verifyPaparazziDebug` and `:wear:verifyPaparazziDebug`. Record only when the picture is supposed to change.
- `app/src/androidTest` runs only with a device. `assembleDebugAndroidTest` compiles it without one. The orchestrator clears app data and disables animations.

A behavior change in `core-*` needs a unit test next to the existing ones. A new notification, worker branch, or Wear publish needs a Robolectric test that forces the failure path, not only the success path.

## Style

`kotlin.code.style=official`. KDoc states an invariant a caller can break (what null means, what a failure does, units, a licence). Do not restate the function name.

Prefer a small interface (`ForecastSource`, `BriefingSource`, `KpSource`, `TleSource`, `ForecastCache`) over a framework fake. Keep related types in the file that owns the behavior (`Factor` and `Band` live with the engine). Package names follow the module: `app.nightbrief.score`, `app.nightbrief.work`, and so on.

Release minify is on for `:app`. `app/proguard-rules.pro` keeps kotlinx.serialization companions and serializers and `-dontwarn`s OkHttp platform providers. New serializable models ride those rules. There are no osmdroid keep rules. Do not add them unless a release-APK map failure says so.

## Leave these alone unless the task is that debt

`ROADMAP.md` already lists them. A drive-by "fix" fights the rest of the app.

- Notification id `1000 + (siteId.hashCode() and 0x0FFF)` can collide.
- `DigestWorker` scores every site even when only one is due.
- Lint warnings are not errors. There is no `lint.xml`.
- No CI. Do not add a live-network test to a future workflow without excluding `onboardedSitePostsTonightDigest`.
- Dependencies stay on the pinned late-2024 catalog unless the task is a bump.
