# NightBrief roadmap

Native Android go/no-go digest (`app.nightbrief`, `versionName` 1.0.0, `minSdk` 26, `compileSdk` 37 on `:app` and `:wear`). Kotlin + Compose; module boundaries are in `AGENTS.md`.

This file tracks open work, known debt, and decisions. Shipped history lives in `CHANGELOG.md`; `README.md` is the spec for the score, the Bortle grids, and scheduling.

## Status

All committed scope is shipped. The P0 device checks, the v0.2 features, the v1.0 widget and Wear glance, and the Nov 2026 astrophotographer field review are done. The app was last walked on a SM-F731W (Android 16) and released as an R8-minified APK.

CI (`.github/workflows/ci.yml`) runs `assembleDebug`, `lintDebug`, `test`, and `verifyPaparazziDebug` on JDK 21 with `NIGHTBRIEF_SKIP_LIVE_TESTS=1`. The live-network `DigestWorkerTest.onboardedSitePostsTonightDigest` runs locally only. JVM modules use `test`; `:data`, `:work`, and `:wear` use `testDebugUnitTest`.

## Open work

None. The only open item from the Nov 2026 field review shipped:

- [x] **Imaging-mode second verdict.** `ImagingModes` in `:core-score` recomputes a night from the same per-hour qualities and gates with a planetary table: cloud 25, moon and twilight 5, transparency 10, wind 20, seeing 35, light pollution 5. Deep sky repeats the published weights. Tonight and the planner show `62 overall · 78 for planets` only when the alternate crosses a band; a difference inside one band stays hidden. `NightScoreEngine`, its gates, and the three fixtures are untouched, and the primary verdict still drives the digest, widget, Wear tile, Sites list, and alerts. The table is documented in `README.md`; tests are `ImagingModesTest` and `ImagingModeUiTest`.

## Known gaps & tech debt

- **Onboarding drafts survive process death only partly.** `AppViewModel` writes the site draft and gear kit into `SavedStateHandle` as JSON. The step index and digest time still use `rememberSaveable`. `OnboardingProcessDeathTest` covers the draft restore; the site editor still uses `SiteDraftSaver`.
- **One `AppViewModel` for every screen**, created in `MainActivity` and passed through `NightBriefNavHost`. `selectedSiteId` is memory-only, so process death returns Tonight and Week to the primary site unless the notification intent sets `EXTRA_SITE_ID`.
- **Briefing refresh is tied to sites and gear, not every setting.** `AppViewModel` init collects `onboardingComplete` + `sites` + `gear` and calls `refresh(force = false)`. A rename, reorder, primary change, or exposure-only gear edit refetches forecasts. Digest time, digest enabled, night vision, units, and `alternativeThreshold` do not; the threshold and units are read live.
- **Ephemeris is planning-grade.** `Bodies` uses the Astronomical Almanac low-precision Sun (~0.01°) and Moon (~0.3° longitude). `AstroTime.daysSinceJ2000` ignores the ~70 s TT−UT offset. `Ephemeris.position` applies J2000 RA/Dec with no precession or nutation. Refraction is only the `HORIZON_ALTITUDE = -0.833` rise/set constant; the Moon does get topocentric parallax.
- **7Timer covers ~72 h.** Later hours have null seeing/transparency, so `NightScoreEngine` estimates them from humidity and 250 hPa wind and marks the factor estimated. Week rows with any estimated factor show "est.". Open-Meteo still supplies cloud, wind, humidity, and jet-stream wind for about 8 days.
- **GEM is a lat/lon box, not a country polygon.** `WeatherModel.forLocation` picks `gem_seamless` when latitude ≥ 41.6 and longitude is in −141.1..−52.5; otherwise `best_match`. That includes the northern US border and drops most of Alaska.
- **Bortle grids are CC BY-NC 4.0** (Falchi et al. 2016, doi:10.5880/GFZ.1.4.2016.001). Non-commercial only: a paid release cannot ship `bortle_na.nblp.gzip` or `bortle_world.nblp.gzip`. World grid is 0.05°; cells outside the atlas lookup as no data. See `Bortle.kt`, `README.md`, and `LightPollutionAttribution.TEXT`.
- **Leaving the atlas keeps the previous class.** `SiteForm` writes a looked-up Bortle only when the grid returns non-null, so a MAP class can stick after a move into a 0-byte or out-of-bbox cell. The slider also displays `draft.bortle ?: 5`, so a null class looks like Bortle 5 before it is stored (`Site.effectiveBortle` is 5).
- **Bortle I/O is blocking and uncached.** `StreamingGridBortleLookup` reads the gzip header and seeks on every call; the North America grid can take about a second. `AppViewModel.lookupBortle` uses `Dispatchers.IO`; the grids are deliberately not cached in memory.
- **Lint warnings are not gated.** No `lint.xml`, baseline, or `warningsAsErrors` in `app/build.gradle.kts`. `:app:lintDebug` can be green with warnings left over. Existing suppressions are `@Suppress("MissingPermission")` in `DigestNotifier.post` and `@SuppressLint("MissingPermission")` in `LocationHelper`.
- **Dependencies stay on the late-2024 catalog** in `gradle/libs.versions.toml`, except AGP 8.10.1, Kotlin 2.1.21, and Paparazzi 2.0.0-alpha02, which compile and snapshot SDK 36. Wrapper is Gradle 8.11.1; build with JDK 21, bytecode target 17. Phone version comes from `nightbrief.versionCode` / `nightbrief.versionName` in `gradle.properties`; the watch keeps its own version.
- **Two alarm slots, not a general scheduler.** The digest uses request code 1001 and re-arms after each fire; the one-shot window reminder uses 1002 and clears itself. Do not add another slot without changing the scheduler.
- **`DigestWorker` scores every site** (`outlookDays = 1`, `forceRefresh = true`) even when only the primary is due.

## Decisions needed first

- **Business model vs the Bortle licence.** The grids are CC BY-NC 4.0, so any paid tier, ads, or in-app purchase means replacing the light-pollution data first. Light pollution is only 5 points of the score, but Bortle also gates `TargetAdvisor` (`maxBortle`) and sets ISO in `ExposureCalculator.suggestIso`. If paid, evaluate a VIIRS-derived source with a commercial-compatible licence and rebuild with `tools/build_bortle_grid.py`.
- **How do we know the score is right?** The engine is still validated by three fixtures plus the session log's display-only score-bias readout. Turning real nights into a feedback loop that can tune weights is a product decision, not a display change.

## Review guidance — opinions, not debt

- **Passport and gamification** (`Passport`, `ConstellationStreaks`, stamp notices, the `ForecastSurprise` sentence): the field review's least value-to-effort layer. Do not extend it. If a later pass trims it, `AppState.seenPassportStamps` keeps its default so older files decode, and the goldens and README frames follow.
- **Whimsy volume.** Keep `NightFlavor`; treat the whimsy budget as spent. New conditions ship plain copy first.
- **Example kit pre-seed.** Lowest priority: onboarding checks and `GearCatalog.exampleKit` assume the Canon R7 + Sigma start, so changing it means updating those tests, and the review only mildly disliked it.
- The review keeps, explicitly: night vision, the postcard's 0.1° location rounding, ISS off by default, and the forecast-confidence card.

## Conventions for agents

Follow `AGENTS.md`: module boundaries, failure isolation, score fixtures, grids, attribution, tests, and release/versioning rules.
