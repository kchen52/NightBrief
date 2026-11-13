# NightBrief context

A short orientation for a new session. The details live in `README.md` (spec), `AGENTS.md` (working rules), `ROADMAP.md` (open work and debt), and `CHANGELOG.md` (history).

## What it is

NightBrief is a native Android go/no-go digest for astrophotography. It scores the coming night from the weather and sky, explains the score, plans targets and sessions, and sends a morning digest. A home-screen widget and an optional Wear OS tile mirror the primary site.

- Package: `app.nightbrief`
- Version: `0.1.1` (`versionCode` 2 in `gradle.properties`)
- Android: `minSdk` 26, `compileSdk` 37, phone `targetSdk` 36; watch `minSdk` 30, `targetSdk` 34
- Stack: Kotlin 2.1.21, Jetpack Compose, WorkManager, DataStore, osmdroid, kotlinx.serialization
- Build: Gradle 8.11.1, JDK 21, bytecode target 17

## Modules

| Module | Kind | Role |
| --- | --- | --- |
| `:core-astro` | JVM | Sun, Moon, twilight, meteor showers, comets, SGP4, ISS passes, planets, sky events |
| `:core-weather` | JVM | Open-Meteo, 7Timer, SWPC, Celestrak, MPC, air quality, forecast/TLE/comet caches |
| `:core-sites` | JVM | Sites, Bortle class, atlas lookup |
| `:core-gear` | JVM | Bodies, lenses, NPF exposure, trackers, filters |
| `:core-score` | JVM | Night Score, planner, digest copy, imaging modes, widget and Wear copy |
| `:data` | Android | `AppState`, settings persistence, process-wide `AppGraph` |
| `:work` | Android | Alarms, `DigestWorker`, `PrefetchWorker`, notifications, widget refresh |
| `:app` | Android | Compose UI, widget, Wear data-layer publish |
| `:wear` | Android watch | Tile and watch screen |

Dependencies point inward: `:app` sees the engines through `:work` → `:data` → `:core-score`. `:wear` depends on `:data` only.

## Things to know before changing behavior

- **The published score is `NightScoreEngine`.** Weights: cloud 35, moon and twilight 25, transparency 15, wind 10, seeing 10, light pollution 5. The score blends all dark hours with the best contiguous 3-hour window, 50/50. Bands: Excellent ≥ 85, Good ≥ 70, Fair ≥ 50, Marginal ≥ 30, else Poor. Verdict: Go ≥ 70, Maybe ≥ 50, else No-go.
- **Do not retune the engine without updating `README.md` and the three fixtures** in `NightScoreEngineTest`: clear moonless 86..95, overcast 9..21, full moon 49..61.
- **`ImagingModes` is display-only.** Tonight and the planner add a planetary weighting when it crosses a band; the digest, widget, Wear, Sites, and alerts keep the primary verdict.
- **Side products never take down the core.** A failed aurora, ISS, comet, air-quality, second-model, or zone fetch leaves that field empty. Re-throw `CancellationException`; isolate other failures at the boundary.
- **Saved data must keep decoding.** Every new `@Serializable` field needs a default. Corrupt settings restore from `app_state.json.good`.
- **Forecasts are cached per ~1 km cell.** Younger than 60 minutes is used without a network call; after that the network is tried, and a failure serves the last file at any age with a saved-at label.
- **The Bortle atlas is CC BY-NC 4.0.** A paid build, ads, or IAP cannot ship the grids until the source is replaced.
- **There are two alarm slots:** digest 1001 (re-arms) and window reminder 1002 (one-shot). Do not add a third without changing the scheduler.
- **Test style:** JUnit 4, one behavior per test, MockWebServer for HTTP. Robolectric uses `@Config(sdk = [34])` on phone/work and `[30]` on wear. Keep `forkEvery = 1` on `:app`.

## Current state

- No open roadmap work. The Nov 2026 field review closed with imaging modes; all committed scope through v1.0 is shipped.
- CI runs `assembleDebug`, `lintDebug`, `test`, and `verifyPaparazziDebug` with `NIGHTBRIEF_SKIP_LIVE_TESTS=1`. The live-network digest test runs only locally.
- Known debt and product decisions are in `ROADMAP.md`; do not fix them as a drive-by.
- Goldens in `docs/screenshots` come from Paparazzi tests. Record only when the picture should change, and update the README image with the golden.

## Quick commands

```bash
export ANDROID_HOME=~/android-sdk
./gradlew :core-astro:test :core-weather:test :core-sites:test :core-gear:test :core-score:test
./gradlew :data:testDebugUnitTest :work:testDebugUnitTest :wear:testDebugUnitTest
./gradlew assembleDebug :app:lintDebug test
./gradlew :app:verifyPaparazziDebug :wear:verifyPaparazziDebug
```
