# NightBrief Performance Audit Findings (Issue-Ready Backlog)

Reviewed `main` at **`515a3b29f3e10bfef100f2f2bd4b4cbc393c7e80`** (2026-11-21).
Source-only performance audit covering `:core-astro`, `:core-weather`, `:core-sites`, `:core-gear`, `:core-score`, `:data`, `:work`, `:app`, and `:wear`.

This document is structured as **11 self-contained, issue-ready engineering tickets** ranked strictly in order of impact (highest to lowest). Each ticket includes exact file/line references, root cause analysis, investigation steps, concrete implementation plans, `AGENTS.md` invariants to preserve, and exact validation/test criteria so an autonomous or junior agent can pick up any item independently.

---

## Global Guardrails for Implementing Agents

Before starting **any** ticket below, read `AGENTS.md`, `README.md`, and `ROADMAP.md`. Every change must preserve:

1. **Module boundaries (`AGENTS.md`):**
   - Pure JVM modules (`:core-astro`, `:core-weather`, `:core-sites`, `:core-gear`, `:core-score`) must remain free of Android dependencies.
   - `:work` cannot depend on `:app`. `:wear` depends on `:data` only.
   - Do not add a repository on a subproject (`FAIL_ON_PROJECT_REPOS`). Pin versions in `gradle/libs.versions.toml`.
2. **Night Score invariants (`NightScoreEngineTest`):**
   - Do **not** alter weights, gates, bands, or verdicts. Fixtures that must stay green: clear moonless `86..95` (Excellent/Go), overcast `9..21` (Poor/No-go), full-moon clear `49..61` (Maybe).
3. **Bortle grid constraints:**
   - **Never cache uncompressed Bortle grids in RAM** (they are tens of MB uncompressed; `StreamingGridBortleLookup` is intentionally streaming).
   - Keep asset filenames ending in `.gzip` (`noCompress += "gzip"`).
4. **Failure isolation & coroutine cancellation:**
   - Every `catch` in suspending code must re-throw `CancellationException`.
   - Side products (Kp, ISS, comets, air quality, road access, reverse geocoding, Wear publish) must never fail the core night score or widget refresh.
   - Every new `@Serializable` field on persisted models requires a default value so older `app_state.json` files decode cleanly.
5. **Scheduling & UI contracts:**
   - Do not add a third alarm `PendingIntent` slot (1001 = digest, 1002 = window reminder).
   - Keep `forkEvery = 1` in `app/build.gradle.kts`. Keep TalkBack labels (`TalkBackLabelTest`) and Paparazzi goldens (`:app:verifyPaparazziDebug`, `:wear:verifyPaparazziDebug`) passing.
   - Keep live-network tests behind `NIGHTBRIEF_SKIP_LIVE_TESTS=1`.
6. **Build & test baseline commands (JDK 21, bytecode target 17):**
   ```bash
   export ANDROID_HOME=/opt/android-sdk   # or ~/android-sdk locally
   NIGHTBRIEF_SKIP_LIVE_TESTS=1 ./gradlew \
     :core-astro:test :core-weather:test :core-sites:test :core-gear:test :core-score:test \
     :data:testDebugUnitTest :work:testDebugUnitTest :wear:testDebugUnitTest \
     :app:assembleDebug :app:lintDebug :app:verifyPaparazziDebug :wear:verifyPaparazziDebug
   ```

---

## Implementation Status

- **Done in [#138](https://github.com/kchen52/NightBrief/pull/138):** `PERF-A01`, `PERF-A02` (with follow-up glass refinements in `#140` and `#142`), `PERF-A07`.
- **Done in [#145](https://github.com/kchen52/NightBrief/pull/145):** `PERF-A03`, `PERF-A05`, `PERF-A06` Part A, `PERF-A09` Part A.
- **Still open:** `PERF-A04`, `PERF-A06` Part B (`TonightScreen` `LazyColumn`), `PERF-A08`, `PERF-A09` Part B (Open-Meteo multi-coordinate batching), `PERF-A10`, `PERF-A11`.

---

## Ranked Summary Table

| Rank | ID | Status | Priority | Category | Modules | Summary | Relation to `POLISH_FINDINGS.md` |
|---:|---|---|---|---|---|---|---|
| 1 | [PERF-A01](#perf-a01--single-pass-batch-bortle-lookup-and-water-first-filtering-in-darker-sky-search) | Done ([#138](https://github.com/kchen52/NightBrief/pull/138)) | P0 / High | I/O & CPU | `:core-sites`, `:core-score`, `:data` | Eliminate 32 parallel full GZIP inflations in Darker-Sky search; filter open water first | New (extends `PERF-04`) |
| 2 | [PERF-A02](#perf-a02--eliminate-full-screen-compose-recomposition-on-scroll-startstop-and-mainactivity-sideeffect-churn) | Done ([#138](https://github.com/kchen52/NightBrief/pull/138)) | P0 / High | UI Rendering | `:app` | Stop reading `isScrollInProgress` in composition (`ReportGlassScroll`) and narrow `MainActivity` `SideEffect` | New |
| 3 | [PERF-A03](#perf-a03--stop-running-digestschedulerreschedule-on-the-main-thread-for-every-ui-state-mutation) | Done ([#145](https://github.com/kchen52/NightBrief/pull/145)) | P1 / High | Main-Thread IPC / SQLite | `:app`, `:work` | Diff scheduling state before calling `DigestScheduler.reschedule()` and move it off Main | New (extends `PERF-05`) |
| 4 | [PERF-A04](#perf-a04--share-a-single-briefing-snapshot-across-app-workers-widget-and-wear-with-lean-background-scoring) | Open | P1 / High | Architecture / API / Battery | `:core-score`, `:data`, `:work`, `:app` | Share one briefing snapshot across surfaces, add lean background/outlook scoring, skip Wear IPC when unpaired | Extends open `PERF-01` & `PERF-02` step 3 |
| 5 | [PERF-A05](#perf-a05--add-in-memory-cache-layers-and-fix-blocking-disk-io-in-core-weather-caches) | Done ([#145](https://github.com/kchen52/NightBrief/pull/145)) | P1 / Med-High | Disk I/O & Concurrency | `:core-weather`, `:data` | Add in-memory cache in front of `FileForecastCache`, move TLE/Comet disk I/O to `Dispatchers.IO`, fix single-flight cancellation | New |
| 6 | [PERF-A06](#perf-a06--lazify-tonight-vertical-layout-memoize-detailuistateemphasizedtexttimelinecard-and-fix-animation-composition-reads) | Part A done ([#145](https://github.com/kchen52/NightBrief/pull/145)); Part B open | P2 / Med-High | UI Rendering & Memory | `:app` | Lazify `TonightScreen`, `remember` `DetailUiState` / `EmphasizedText` / `TimelineCard` rows, move `AuroraRipple` read to draw phase | Expands `PERF-09` |
| 7 | [PERF-A07](#perf-a07--eliminate-redundant-ephemeris-sweeps-in-nightephemeriscompute-and-targetadvisorevaluate) | Done ([#138](https://github.com/kchen52/NightBrief/pull/138)) | P2 / Medium | CPU / Algorithms | `:core-astro`, `:core-score` | Single-pass 24h solar altitude curve in `NightEphemeris.compute`; precompute Moon steps once per `TargetAdvisor.suggest` | New |
| 8 | [PERF-A08](#perf-a08--convert-bundled-target-sample-jpegs-to-webp-and-decode-thumbnails-off-the-main-thread) | Open | P2 / Medium | APK Size & Image Decoding | `:app` | Convert 40 JPEGs (3.6 MB) to WebP and asynchronously decode/downsample off Main thread | New |
| 9 | [PERF-A09](#perf-a09--batch-open-meteo-forecast-calls-and-bound-overpass-api-concurrency) | Part A done ([#145](https://github.com/kchen52/NightBrief/pull/145)); Part B open | P3 / Low-Med | Network / API Efficiency | `:core-weather`, `:core-score` | Bound/cache Overpass road-access queries (prevent HTTP 429) and evaluate Open-Meteo multi-coordinate batching | New |
| 10 | [PERF-A10](#perf-a10--add-compose-compiler-stability-configuration-for-core--models-and-javatime-types) | Open | P3 / Low-Med | Compose Recomposition | `:app`, `:wear` | Add Compose compiler stability config file so pure-JVM domain models and `java.time.*` types become skippable | New |
| 11 | [PERF-A11](#perf-a11--add-baseline-profiles-and-androidxprofileinstaller-for-cold-start-and-scroll-aot) | Open | P3 / Low-Med | Startup & Runtime AOT | `:app` | Add `androidx.profileinstaller` and an app-owned Baseline Profile (after macrobenchmark harness) | New |

---

## PERF-A01 — Single-pass batch Bortle lookup and water-first filtering in Darker-Sky search

- **Status:** **Done** in [#138](https://github.com/kchen52/NightBrief/pull/138) (`BortleLookup.lookupAll`, single-pass `StreamingGridBortleLookup.lookupAll`, `CompositeBortleLookup.lookupAll`, and water-first filtering in `DarkSkyFinder.search`; covered by `BortleLookupTest` and `DarkSkyFinderTest`).
- **Impact Rank:** #1 (Highest)
- **Priority:** P0 / High
- **Category:** Disk/Asset I/O, CPU, Thermal & Battery
- **Modules:** `:core-sites`, `:core-score`, `:data`
- **Labels:** `performance`, `core-sites`, `core-score`, `io`
- **Relation to `POLISH_FINDINGS.md`:** Extends `PERF-04` (which moved `DarkSkyFinder` to `planLean` and `Dispatchers.Default`, but left the 32× streaming GZIP reads untouched).

### Problem & Impact
Running **Find darker sky** (`DarkSkyFinder.search`) samples 4 rings × 8 bearings = **32 coordinates** around the saved site. Today, for **every** sample:
1. It calls `lookup.lookup(lat, lon)` in parallel via 32 coroutines on `Dispatchers.IO`.
2. Each `StreamingGridBortleLookup.lookup` opens `bortle_na.nblp.gzip` (791 KB compressed, tens of MB uncompressed), initializes a fresh `GZIPInputStream`, parses the 40-byte `NblpHeader`, and sequentially inflates/discards bytes via `skipFully` up to `row * cols + col`. When a cell falls outside NA or returns 0, `CompositeBortleLookup` then inflates `bortle_world.nblp.gzip` (500 KB compressed) too.
3. `water?.isOpenWater(lat, lon)` is checked **after** the Bortle lookup instead of before — even though `GridOpenWater` is already inflated once in RAM (~3 MB bitset) and answers in nanoseconds. For coastal cities (e.g., Vancouver, Seattle, San Francisco), ~30–50% of the 32 samples are ocean and still pay full GZIP inflation cost before being discarded.

Result: 32 concurrent GZIP decompressions saturate CPU cores, cause heavy GC churn from 32 `Inflater` / `BufferedInputStream` allocations, and take seconds of wall time on mid-range phones.

### Affected Code
- `core-score/src/main/kotlin/app/nightbrief/score/DarkSkyFinder.kt:94-116` (`search` loop: calls `lookup.lookup` first, then `water?.isOpenWater`)
- `core-sites/src/main/kotlin/app/nightbrief/sites/Bortle.kt:48-52` (`fun interface BortleLookup`)
- `core-sites/src/main/kotlin/app/nightbrief/sites/Bortle.kt:150-165` (`StreamingGridBortleLookup.lookup`)
- `core-sites/src/main/kotlin/app/nightbrief/sites/Bortle.kt:171-181` (`CompositeBortleLookup.lookup`)
- `core-sites/src/main/kotlin/app/nightbrief/sites/OpenWater.kt:89-100` (`GridOpenWater`)
- `data/src/main/kotlin/app/nightbrief/data/AppGraph.kt:115-139` (wiring of `CompositeBortleLookup` / `StreamingGridBortleLookup`)

### Root Cause Analysis
`GZIPInputStream` cannot seek backward or jump to arbitrary uncompressed offsets; `skipFully` inflates every byte from offset `0` to `index`. Running 32 independent calls inflates the same stream up to 32 times (or up to 64 times if falling through to the world grid). However, because all 32 coordinates are known upfront in `DarkSkyFinder.search`, their payload indices `r * cols + c` can be sorted in ascending order and satisfied in **one single forward pass** through each GZIP stream, retaining zero grid bytes in memory.

### Investigation Steps for Implementing Agent
1. Read `DarkSkyFinder.kt`, `Bortle.kt`, `OpenWater.kt`, `DarkSkyFinderTest.kt`, and `BortleLookupTest.kt`.
2. Measure current `DarkSkyFinder.search` grid-lookup phase duration in a JVM micro-benchmark (or instrumented log in `DarkSkyFinderTest` using real `assets/bortle_na.nblp.gzip` + `bortle_world.nblp.gzip`, see `BortleAtlasAssetTest.kt` for how tests open those assets).
3. Verify how `DarkSkyFinderTest` fakes `BortleLookup` (it uses SAM lambdas `BortleLookup { lat, lon -> ... }`) — preserving SAM compatibility on `BortleLookup` avoids breaking existing callers and tests!

### Recommended Implementation Plan
1. **Filter open water before Bortle lookup (`DarkSkyFinder.kt:97-116`):**
   - Inside `withContext(Dispatchers.IO)`, first filter `samples` with `water?.isOpenWater(sample.latitude, sample.longitude) != true` (isolating non-`CancellationException` exceptions per sample just as before: a throw from `water` skips that sample only, matching the KDoc on lines 76–79).
   - Note: `GridOpenWater.grid` uses Kotlin `by lazy` (synchronized), so calling `isOpenWater` sequentially on 32 samples inflates the water mask once (~3 MB) and checks all 32 points in microseconds without spawning 32 threads fighting over the `lazy` lock.
2. **Add default batch lookup on `BortleLookup` (`Bortle.kt`):**
   - Keep `fun interface BortleLookup` so SAM lambdas continue compiling! Add a default method:
     ```kotlin
     fun interface BortleLookup {
         fun lookup(latitude: Double, longitude: Double): Int?

         /**
          * Looks up multiple coordinates in one pass when supported by the backing grid.
          * Returns a list aligned 1:1 with [points]; null entries mean no data.
          */
         fun lookupAll(points: List<Pair<Double, Double>>): List<Int?> =
             points.map { (lat, lon) -> lookup(lat, lon) }
     }
     ```
3. **Implement single-pass streaming `lookupAll` in `StreamingGridBortleLookup` (`Bortle.kt:150-165`):**
   - If `points.isEmpty()`, return `emptyList()` without opening the stream.
   - Open the stream once (`open().use { input -> ... }`), parse `NblpHeader.read(DataInputStream(input))`.
   - Compute `(originalIndex, payloadIndex)` for every point whose `header.indexOf(lat, lon) != null`. If none fall inside the grid bbox, close the stream immediately and return `List(points.size) { null }`.
   - Sort valid entries by `payloadIndex` ascending.
   - Maintain `var cursor = 0L` (number of payload bytes consumed so far) and `var lastIndex = -1` / `var lastValue: Int? = null` (handles duplicate cells cleanly when two points fall in the same grid cell!).
   - For each `(origIdx, payloadIdx)`:
     - If `payloadIdx == lastIndex`, reuse `lastValue`.
     - Else `val delta = payloadIdx.toLong() - cursor`; `skipFully(input, delta)`; `val b = input.read()` (throw `EOFException` if `< 0`); `cursor = payloadIdx.toLong() + 1L`; `lastIndex = payloadIdx`; `lastValue = bortleOrNull(b)`.
     - Store `result[origIdx] = lastValue`.
   - Check `Thread.currentThread().isInterrupted` or pass a cancellation hook if desired, or let coroutine cancellation surface between grids.
4. **Implement fallback batching in `CompositeBortleLookup` (`Bortle.kt:171-181`):**
   - Start with `result = MutableList<Int?>(points.size) { null }` and `remainingIndices = points.indices.toMutableList()`.
   - For each `lookup` in `lookups`:
     - If `remainingIndices.isEmpty()` break! (For a NA site where all land points hit `bortle_na.nblp.gzip`, `bortle_world.nblp.gzip` is never even opened!)
     - Call `val values = lookup.lookupAll(remainingIndices.map { points[it] })`.
     - Fill non-null entries into `result` and remove resolved indices from `remainingIndices`.
     - Isolate per-lookup failure carefully? Check existing `CompositeBortleLookup` contract: if a single lookup throws, does `DarkSkyFinder` skip only that sample or does a corrupt grid fail? Verify in `BortleLookupTest.kt` and `DarkSkyFinderTest.kt`! If a single corrupt/truncated grid throws, wrap or preserve exact failure semantics (note: for a valid gzip stream, `lookupAll` fails only if the entire asset is corrupt/truncated; if `lookupAll` throws on a custom test fake `BortleLookup` that throws for one coordinate, `DarkSkyFinder` must still skip only that failing sample — see Edge Cases below!).
5. **Wire `lookupAll` into `DarkSkyFinder.search` (`DarkSkyFinder.kt:97-116`):**
   - For default/custom `BortleLookup` implementations (like test fakes that throw for one coordinate), ensure per-sample failure isolation still holds: either catch per-point in `BortleLookup.lookupAll`'s default implementation ONLY inside `DarkSkyFinder`, or handle `try { lookup.lookupAll(coords) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* fallback per-point or skip */ }`. Specifically: if `DarkSkyFinderTest` tests "one sample throws from lookup and the others still succeed", having `DarkSkyFinder` catch non-cancellation exceptions from `lookupAll` and fall back to per-sample `lookup` (or handling per-point exceptions cleanly) guarantees 100% compatibility with test fakes AND real streaming grids!

### Constraints & Pitfalls to Avoid
- **Do NOT cache the uncompressed Bortle grid in memory** (`AGENTS.md`: "Do not cache the grids in memory"). Only the sorted list of <=32 query coordinates and 32 result integers may live in memory.
- Preserve `BortleLookup` as a `fun interface`.
- Preserve `CancellationException` propagation.
- Preserve exact coordinate-to-cell mapping (`floor((lat - south) / cell)`) and duplicate-cell handling (`payloadIndex == lastIndex`).
- Preserve `DarkSkyFinder` KDoc contract: a water-mask throw skips that sample only; a `bortle >= primaryBortle` or `null` is dropped before `take(maxToScore)`.

### Validation & Acceptance Criteria
- **New unit tests in `core-sites/src/test/kotlin/app/nightbrief/sites/BortleLookupTest.kt`:**
  - `StreamingGridBortleLookup.lookupAll` opens the underlying stream **at most once** (assert `openCount == 1`) for N unsorted points, including points in reverse order, duplicate points hitting the same cell, out-of-bounds points, and 0-byte cells.
  - `StreamingGridBortleLookup.lookupAll` produces results bit-for-bit identical to calling `lookup` individually on every point.
  - `StreamingGridBortleLookup.lookupAll` with empty list or all-out-of-bounds points skips zero payload bytes.
  - `CompositeBortleLookup.lookupAll` does **not** open the second grid when the first grid resolves all points, and queries the second grid **only** for unresolved points.
- **New unit tests in `core-score/src/test/kotlin/app/nightbrief/score/DarkSkyFinderTest.kt`:**
  - Open-water samples are filtered **before** `BortleLookup` is called (assert water coordinates never reach `lookup`/`lookupAll`).
  - A fake `BortleLookup` throwing on one sample still returns candidates for the other samples (failure isolation preserved).
  - A fake `OpenWater` throwing `CancellationException` propagates cancellation; throwing `RuntimeException` skips only that sample.
- **Run existing & new suites:**
  ```bash
  ./gradlew :core-sites:test :core-score:test :data:testDebugUnitTest
  ```
- **Before/After measurement:** Log elapsed ms for `DarkSkyFinder.search` against the real NA + World `.gzip` assets (e.g., coastal Vancouver `49.28, -123.12`) before and after; expect >10× speedup on the grid lookup stage.

---

## PERF-A02 — Eliminate full-screen Compose recomposition on scroll start/stop and `MainActivity` `SideEffect` churn

- **Status:** **Done** in [#138](https://github.com/kchen52/NightBrief/pull/138) (`ReportGlassScroll` lambda reader with `snapshotFlow`, narrowed `MainActivity` `nightVision` flow + `LaunchedEffect`, covered by `ReportGlassScrollRecompositionTest`; further glass scroll/tab-switch refinements in `#140` and `#142`).
- **Impact Rank:** #2
- **Priority:** P0 / High
- **Category:** Compose Recomposition & Frame Jank
- **Modules:** `:app`
- **Labels:** `performance`, `compose`, `ui`, `jank`
- **Relation to `POLISH_FINDINGS.md`:** New finding.

### Problem & Impact
Two high-frequency Compose state-read issues cause full-tree recompositions and window IPC during normal interaction:

1. **`ReportGlassScroll(scroll.isScrollInProgress)` reads snapshot state in the caller's composition scope:**
   Every scrollable screen/strip passes the `Boolean` value `state.isScrollInProgress` directly as an argument to `ReportGlassScroll`:
   - `TonightScreen.kt:92`: `ReportGlassScroll(scroll.isScrollInProgress)` (at top of `TonightScreen`!)
   - `WeekScreen.kt:106, 168`: `ReportGlassScroll(gridState.isScrollInProgress)` and `ReportGlassScroll(listState.isScrollInProgress)`
   - `NightDetail.kt:906`: `ReportGlassScroll(hoursScroll.isScrollInProgress)` (top of `TimelineCard`)
   - `Components.kt:261`: `ReportGlassScroll(rowState.isScrollInProgress)` (top of `SiteStrip`)

   Because `isScrollInProgress` is read directly inside `TonightScreen`, `WeekScreen`, `TimelineCard`, and `SiteStrip`, the instant the user's finger begins a drag (`false -> true`) — **frame 1 of the scroll gesture** — Compose invalidates and recomposes the **entire screen** (all cards, top bar, `TimelineCard`'s 10×12 cells, `SiteStrip`, `EmphasizedText` regexes, etc.), and then recomposes the entire screen **again** when the fling settles (`true -> false`). Worse, `ReportGlassScroll` also writes `gate.report(key, scrolling)` directly during composition (`Glass.kt:168`), mutating `GlassScrollGate.moving` (`mutableStateOf`) mid-composition!

2. **`MainActivity` executes `window.attributes = ...` and `enableEdgeToEdge(...)` on every `AppState` change:**
   In `MainActivity.kt:31-45`:
   ```kotlin
   val state by vm.state.collectAsStateWithLifecycle()
   val nightVision = state?.nightVision == true
   SideEffect {
       window.attributes = window.attributes.apply { ... }
       enableEdgeToEdge(...)
   }
   ```
   Because `state` is the full `AppState?` collected at the root of `setContent`, **any** mutation to `AppState` (collapsing a card, dismissing a banner, saving a session, toggling digest, changing a nickname) recomposes the root `setContent` lambda and fires `SideEffect`, issuing WindowManager IPC (`window.attributes = ...`) and re-running `enableEdgeToEdge` even though `nightVision` never changed.

### Affected Code
- `app/src/main/kotlin/app/nightbrief/app/ui/common/Glass.kt:164-172` (`ReportGlassScroll`)
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/TonightScreen.kt:91-92`
- `app/src/main/kotlin/app/nightbrief/app/ui/week/WeekScreen.kt:105-106, 167-168`
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/NightDetail.kt:906`
- `app/src/main/kotlin/app/nightbrief/app/ui/common/Components.kt:261`
- `app/src/main/kotlin/app/nightbrief/app/MainActivity.kt:31-45`

### Root Cause Analysis
- Reading `ScrollState.isScrollInProgress` / `LazyListState.isScrollInProgress` / `LazyGridState.isScrollInProgress` during composition subscribes the enclosing `@Composable` restart scope to every scroll start/stop transition. Deferring that read into `LaunchedEffect` + `snapshotFlow` keeps the subscription completely isolated from composition — only `GlassScrollGate.moving` updates, which only invalidates `Modifier.glassPill` (the floating glass pills), **never** the scrollable screen body.
- Mutating `mutableStateOf` (`gate.report`) synchronously during composition is also a Compose anti-pattern that can trigger extra recomposition passes in the same frame.
- Collecting the entire `AppState` at `MainActivity`'s root `setContent` and using `SideEffect` instead of `LaunchedEffect(nightVision)` couples every setting change to window-level side effects and root recomposition.

### Investigation Steps for Implementing Agent
1. Read `Glass.kt:115-215` and `GlassScrollGateTest.kt` to understand exact timing semantics of `GlassScrollGate.moving`, `settling`, and `ReportGlassScroll`.
2. Check all callers of `ReportGlassScroll` across the codebase (`grep -rn ReportGlassScroll`).
3. Verify `GlassScreenshotTest` and `TabSwipeTest` to ensure Haze glass detachment/re-attachment still behaves identically on real scroll gestures and in Paparazzi/Robolectric tests.

### Recommended Implementation Plan
1. **Refactor `ReportGlassScroll` (`Glass.kt:164-172`) to take a state reader lambda (or `ScrollableState`):**
   ```kotlin
   @Composable
   fun ReportGlassScroll(isScrolling: () -> Boolean) {
       val gate = LocalGlassScrollGate.current
       val key = remember { Any() }
       val currentReader by rememberUpdatedState(isScrolling)
       LaunchedEffect(gate, key) {
           snapshotFlow { currentReader() }
               .distinctUntilChanged()
               .collect { gate.report(key, it) }
       }
       DisposableEffect(gate, key) {
           onDispose { gate.report(key, false) }
       }
   }
   ```
   *(Verify with `GlassScrollGateTest`: if a test asserts synchronous `gate.moving` update on initial composition when `scrolling` starts `true`, check whether `LaunchedEffect` settles on `composeRule.waitForIdle()` — `LaunchedEffect` runs on the frame coroutine before idle completes. If any unit test expects the Boolean overload, keep only the lambda overload and update the test, or check `GlassScrollGateTest.kt` first!)*
2. **Update all 5 call sites** to pass a lambda so the state read happens inside `snapshotFlow`, never in the screen's composition scope:
   - `TonightScreen.kt:92`: `ReportGlassScroll { scroll.isScrollInProgress }`
   - `WeekScreen.kt:106`: `ReportGlassScroll { gridState.isScrollInProgress }`
   - `WeekScreen.kt:168`: `ReportGlassScroll { listState.isScrollInProgress }`
   - `NightDetail.kt:906`: `ReportGlassScroll { hoursScroll.isScrollInProgress }`
   - `Components.kt:261`: `ReportGlassScroll { rowState.isScrollInProgress }`
3. **Narrow `MainActivity.kt:31-45`:**
   - Derive a distinct `Flow<Boolean>` for `nightVision` (e.g., `val nightVision by remember(vm) { vm.state.map { it?.nightVision == true }.distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = false)`) so `MainActivity`'s root `setContent` only recomposes when `nightVision` actually toggles!
   - Replace `SideEffect { ... }` with `LaunchedEffect(nightVision)` (or keep `DisposableEffect`/`LaunchedEffect(nightVision)` on Main) so `window.attributes` and `enableEdgeToEdge` only execute on startup and when `nightVision` changes.

### Constraints & Pitfalls to Avoid
- Do **not** alter `GlassScrollGate`'s settling behavior or `TabDroplet` rest geometry (`GlassScreenshotTest` goldens must remain pixel-identical).
- Ensure `DisposableEffect` still calls `gate.report(key, false)` on dispose so a scrollable leaving composition mid-fling never leaves `gate.moving` stuck `true`.
- In `MainActivity`, verify that initial composition still applies the correct brightness/scrim when the app launches with `nightVision = true` already persisted in `AppState`.

### Validation & Acceptance Criteria
- **Existing tests must pass:** `GlassScrollGateTest`, `TabSwipeTest`, `TonightScreenshotTest`, `GlassScreenshotTest` (via `:app:verifyPaparazziDebug`), `TalkBackLabelTest`.
- **Add regression test in `GlassScrollGateTest.kt`:**
  - Compose a parent with a recomposition counter + `ReportGlassScroll { scrollingState.value }`. Toggle `scrollingState.value` from `false -> true -> false`. Assert `gate.moving` transitions `false -> true -> false` AND the parent composable recomposition count stays at **1** (zero parent recompositions!).
  - Assert disposing while `scrollingState.value == true` resets `gate.moving` to `false`.
- **Run:**
  ```bash
  NIGHTBRIEF_SKIP_LIVE_TESTS=1 ./gradlew :app:testDebugUnitTest --tests "*GlassScrollGateTest*" --tests "*TabSwipeTest*"
  NIGHTBRIEF_SKIP_LIVE_TESTS=1 ./gradlew :app:verifyPaparazziDebug
  ```

---

## PERF-A03 — Stop running `DigestScheduler.reschedule()` on the Main thread for every UI state mutation

- **Status:** **Done** in [#145](https://github.com/kchen52/NightBrief/pull/145) (`DigestScheduler.needsReschedule` scheduling-key projection + `Dispatchers.IO` rescheduling in `AppViewModel.mutate` and `rescheduleDigest`, covered by `DigestTimesTest`).
- **Impact Rank:** #3
- **Priority:** P1 / High
- **Category:** Main-Thread Blocking (AlarmManager IPC + WorkManager SQLite)
- **Modules:** `:app`, `:work`
- **Labels:** `performance`, `work`, `main-thread`, `battery`
- **Relation to `POLISH_FINDINGS.md`:** Extends `PERF-05` (which moved `.good` backup file I/O off Main, leaving `DigestScheduler.reschedule` on Main in `AppViewModel.mutate`).

### Problem & Impact
Every UI setting mutation funnels through `AppViewModel.mutate` (`AppViewModel.kt:607-614`):
```kotlin
private fun mutate(transform: (AppState) -> AppState) {
    viewModelScope.launch {
        val previousSites = graph.settings.current().sites
        val newState = graph.settings.update(transform)
        DigestScheduler.reschedule(getApplication(), newState)
        if (previousSites != newState.sites) WidgetRefresh.request(getApplication())
    }
}
```
`viewModelScope` runs on `Dispatchers.Main.immediate`. On **every** call — collapsing/expanding a card (`toggleCardCollapsed`), toggling Night Vision (`setNightVision`), changing units, adjusting `alternativeThreshold` / `bigNightThreshold` / `auroraKpThreshold`, dismissing a session prompt, marking passport stamps seen, editing gear, or skipping an update — `mutate`:
1. Calls `DigestScheduler.reschedule(context, newState)` **synchronously on the Main thread**.
2. Inside `reschedule()` (`DigestScheduler.kt:49-64`):
   - Calls `WindowAlarmScheduler.reschedule` → `AlarmManager.setAndAllowWhileIdle` or `AlarmManager.cancel` (Binder IPC to `system_server`).
   - Calls `DigestTimes.next(...)` + `armDigestAlarm` → another `AlarmManager` Binder IPC.
   - Calls `schedulePrefetch` → `WorkManager.enqueueUniquePeriodicWork("forecast-prefetch", KEEP, ...)` (WorkManager SQLite write/query + IPC).
   - Calls `AuroraWatch.sync` → `WorkManager.enqueueUniquePeriodicWork` or `cancelUniqueWork("aurora-watch")` (second WorkManager SQLite operation).
3. Also note `AppViewModel.kt:609` calls `graph.settings.current()` before `graph.settings.update(transform)`, doing an extra read when `state.value` or a single atomic comparison of before/after inside `update` (or capturing `previous` in `mutate`) is cheaper and race-free.

### Affected Code
- `app/src/main/kotlin/app/nightbrief/app/AppViewModel.kt:607-615` (`mutate`)
- `app/src/main/kotlin/app/nightbrief/app/AppViewModel.kt:597-599` (`rescheduleDigest`)
- `work/src/main/kotlin/app/nightbrief/work/DigestScheduler.kt:44-64` (`reschedule`)
- `work/src/main/kotlin/app/nightbrief/work/WindowAlarmScheduler.kt:42-56` (`reschedule`)
- `work/src/main/kotlin/app/nightbrief/work/AuroraWatch.kt:37-44` (`sync`)

### Root Cause Analysis
`mutate` was written as a single choke point to guarantee alarms never drift out of sync with settings. However, ~75% of `AppState` fields (collapsed cards, night vision, units, gear, sessions, dismissed prompts, seen passport stamps, thresholds, custom targets, skipped update id, ISS visibility) have **zero effect** on `DigestScheduler`, `WindowAlarmScheduler`, or `AuroraWatch`. Even when a scheduling field *does* change, `AlarmManager` and `WorkManager` IPC should execute on `Dispatchers.IO` (or `Dispatchers.Default`), not on `Dispatchers.Main`.

### Investigation Steps for Implementing Agent
1. Audit **every field** of `AppState` (`data/src/main/kotlin/app/nightbrief/data/AppState.kt`) that is read by:
   - `DigestScheduler.reschedule` & `DigestTimes.next`
   - `WindowAlarmScheduler.reschedule`
   - `AuroraWatch.sync`
2. Confirm the exact set of scheduling-relevant inputs:
   - `onboardingComplete`
   - `digestEnabled`
   - `digestTime`
   - `sites` (specifically site ids, `primaryId`, `zoneId`, `digestTimeOverride`, and emptiness — comparing a lightweight projection or `sites` itself is safe)
   - `windowAlarm`
   - `auroraAlertsEnabled`
   - *(Double check `DigestTimes.kt` and `AuroraWatch.kt` to make sure no other field is read!)*
3. Check whether `NightBriefApp.onCreate` (`NightBriefApp.kt:27`) already runs `DigestScheduler.reschedule` on `graph.appScope` (`Dispatchers.Default`) — yes, it does!

### Recommended Implementation Plan
1. **Define a pure helper in `:work` (e.g., `DigestScheduler.schedulingKey(state: AppState)` or `DigestScheduler.needsReschedule(previous: AppState, next: AppState): Boolean`):**
   - Extracts the exact scheduling-relevant fields (`onboardingComplete`, `digestEnabled`, `digestTime`, `windowAlarm`, `auroraAlertsEnabled`, plus site ids/primaryId/zoneId/digestTimeOverride).
   - Pure JVM-testable function so unit tests verify every relevant field triggers reschedule and irrelevant fields (like `collapsedCards`, `nightVision`, `gear`, `units`, `sessions`, `seenPassportStamps`, `dismissedSessionPrompts`, `alternativeThreshold`, `bigNightThreshold`, `auroraKpThreshold`, `showIssCard`, `skippedUpdateId`, `customTargets`) do **not**.
2. **Update `AppViewModel.mutate` (`AppViewModel.kt:607-615`):**
   - Capture `var previous: AppState? = null` inside `graph.settings.update { current -> previous = current; transform(current) }` (avoids the extra `current()` read and guarantees `previous` is the exact pre-mutation state even under concurrent mutations!).
   - Compare `previous` vs `newState`:
     - Only if `previous == null || DigestScheduler.needsReschedule(previous!!, newState)` → call `withContext(Dispatchers.IO) { DigestScheduler.reschedule(getApplication(), newState) }`.
     - Only if `previous?.sites != newState.sites` → `WidgetRefresh.request(getApplication())`.
3. **Move `rescheduleDigest()` (`AppViewModel.kt:597-599`) off Main too:**
   - `viewModelScope.launch(Dispatchers.IO) { DigestScheduler.reschedule(getApplication()) }`.

### Constraints & Pitfalls to Avoid
- Do **not** skip rescheduling when a site's `digestTimeOverride`, `zoneId`, `primaryId`, or existence changes (for example, deleting the site that holds an armed `windowAlarm` must still cancel alarm slot 1002!).
- Do **not** alter the two-alarm-slot architecture (1001 and 1002).
- Do **not** change `AppViewModel.init`'s briefing refresh triggers (`AppViewModel.kt:144-147`) as part of this ticket unless explicitly scoped — `ROADMAP.md:23` documents the current refresh trigger behavior.

### Validation & Acceptance Criteria
- **New unit tests in `:work` (e.g., `DigestTimesTest.kt` or new `DigestSchedulerRescheduleTest.kt`):**
  - `needsReschedule` returns `false` for `collapsedCards`, `nightVision`, `gear`, `units`, `alternativeThreshold`, `bigNightThreshold`, `auroraKpThreshold`, `sessions`, `seenPassportStamps`, `dismissedSessionPrompts`, `customTargets`, `skippedUpdateId`.
  - `needsReschedule` returns `true` for `onboardingComplete`, `digestEnabled`, `digestTime`, `windowAlarm`, `auroraAlertsEnabled`, site added/removed, site `primaryId` changed, site `zoneId` changed, site `digestTimeOverride` changed.
- **Robolectric tests (`:work:testDebugUnitTest`):** `AuroraWatchTest`, `WindowAlarmSchedulerTest`, `DigestWorkerTest` all pass.
- **StrictMode verification (debug build):** Toggling Night Vision, collapsing/expanding cards, and adjusting thresholds produce zero StrictMode / WorkManager / AlarmManager calls on the Main thread.
- **Run:**
  ```bash
  NIGHTBRIEF_SKIP_LIVE_TESTS=1 ./gradlew :work:testDebugUnitTest :app:testDebugUnitTest
  ```

---

## PERF-A04 — Share a single briefing snapshot across App, Workers, Widget, and Wear (with lean background scoring)

- **Impact Rank:** #4
- **Priority:** P1 / High
- **Category:** Redundant Network/CPU Work, Architecture, Battery
- **Modules:** `:core-score`, `:data`, `:work`, `:app`
- **Labels:** `performance`, `architecture`, `widget`, `wear`, `work`
- **Relation to `POLISH_FINDINGS.md`:** Directly addresses open **`PERF-01`** and open **`PERF-02` step 3**, and adds two newly identified wins: lean background scoring for `PrefetchWorker` and skipping Wear IPC when no watch is connected.

### Problem & Impact
The same night for the same primary site is repeatedly re-fetched, re-scored, and re-enriched across four separate entry points:
1. **Cold app launch (`2×` concurrent work):** `NightBriefApp.onCreate` (`NightBriefApp.kt:28`) calls `WearPublisher.enqueue(this)` which immediately invokes `graph.briefings.brief(sites = listOf(primary), outlookDays = 1)` at the exact same moment `AppViewModel.init` invokes `graph.briefings.brief(sites = allSites, outlookDays = 7)`.
2. **Background digest / prefetch → widget → Wear (`3×` serial work):**
   - `DigestWorker.postDigests` (`Workers.kt:66-72`) and `PrefetchWorker` (`Workers.kt:186-213`) run `graph.briefings.brief(...)`, then call `WidgetRefresh.request(applicationContext)`.
   - `NightBriefWidgetReceiver` (`NightBriefWidgetReceiver.kt:18-29`) runs `NightBriefWidget.updateAll(context)` → `NightBriefWidget.load` (`NightBriefWidget.kt:41-46`) calls `graph.briefings.brief(...)` a **second** time!
   - Immediately after, `NightBriefWidgetReceiver` calls `WearPublisher.enqueue(context)` → `WearPublisher.load` (`WearPublisher.kt:56-61`) calls `graph.briefings.brief(...)` a **third** time!
   - This directly contradicts `AGENTS.md`: *"The widget reads the briefing cache. It does not score on its own."*
3. **Unnecessary side-product fetches & target calculations in background/glance paths:**
   - `PrefetchWorker` (`Workers.kt:186-213`) only needs `scoreValue`, `band`, and `bestWindow` (for Big Night alerts, Collapse alerts, and Window Alarm sync). It **never** reads `suggestions`, `aurora`, `issPasses`, `smoke`, `comet`, or `flavorLine` — yet calls full `brief(...)`, triggering SWPC Kp, Celestrak ISS + SGP4 pass prediction, MPC Comets parsing + orbital propagation, CAMS Air Quality, and `TargetAdvisor.suggest` (~40 targets × 60 steps).
   - `NightBriefWidget` and `WearPublisher` never render `suggestions`, `smoke`, or `comet`. *(Important nuance: both DO render `report.flavorLine`, and `NightFlavor.line` in `NightFlavor.kt:15-40` reads `aurora`, `meteor`, `issPasses`, `cloudReason`, `dew`, `ephemeris`, and `score`! Reading from a shared snapshot published by the app/digest gives them exact `flavorLine` with zero extra work.)*
4. **Unconditional Wear OS DataClient IPC when no watch is paired:**
   - `WearPublisher.push` (`WearPublisher.kt:28-48`) unconditionally builds a briefing and calls `Tasks.await(Wearable.getDataClient(context).putDataItem(...), 5, TimeUnit.SECONDS)` on every launch and every 3-hour prefetch even on phones with zero Wear OS watches paired.
5. **Future Week nights build unused timelines/dew/cloud layers (`PERF-02` step 3):**
   - `BriefingService.brief` (`BriefingService.kt:189-231`) runs full `NightPlanner.plan` for `offset in 1 until outlookDays`, building `TimelineHour` lists, `DewOutlook`, and `CloudReason` objects that `OutlookNight` immediately throws away.

### Affected Code
- `app/src/main/kotlin/app/nightbrief/app/NightBriefApp.kt:25-29`
- `app/src/main/kotlin/app/nightbrief/app/AppViewModel.kt:245-266`
- `app/src/main/kotlin/app/nightbrief/app/widget/NightBriefWidget.kt:36-60`
- `app/src/main/kotlin/app/nightbrief/app/widget/NightBriefWidgetReceiver.kt:18-33`
- `app/src/main/kotlin/app/nightbrief/app/wear/WearPublisher.kt:20-69`
- `work/src/main/kotlin/app/nightbrief/work/Workers.kt:66-113, 166-215`
- `core-score/src/main/kotlin/app/nightbrief/score/BriefingService.kt:149-235`
- `core-score/src/main/kotlin/app/nightbrief/score/NightPlanner.kt:120-238`
- `core-score/src/main/kotlin/app/nightbrief/score/NightFlavor.kt:15-40`
- `data/src/main/kotlin/app/nightbrief/data/AppGraph.kt`

### Root Cause Analysis
`AppGraph` caches raw forecasts on disk (`FileForecastCache`), but has **no shared briefing/glance snapshot** between `AppViewModel`, `DigestWorker`, `PrefetchWorker`, `NightBriefWidget`, and `WearPublisher`. Consequently, every surface calls `graph.briefings.brief(...)` from scratch.

### Investigation Steps for Implementing Agent
1. Read `POLISH_FINDINGS.md` sections `PERF-01` (lines 310–349) and `PERF-02` (lines 350–390).
2. Read `NightBriefWidgetTest.kt`, `WearPublisherTest.kt`, `WidgetRefreshTest.kt`, `PrefetchBigNightTest.kt`, `PrefetchCollapseTest.kt`, `DigestWorkerTest.kt`, and `BriefingEnrichmentTest.kt`.
3. Verify exact fields read by:
   - `NightBriefWidget.load`: `primary.name`, `report.scoreValue`, `WidgetCopy.flavorLine(report)` (= `report.flavorLine`), `report.ephemeris.milkyWay` (`window`, `clearsHorizonAt`), `report.ephemeris.milkyWayBlockedByHorizon`, `report.forecastStatus`, `report.forecastFetchedAt`.
   - `WearPublisher.load`: `primary.name`, `report.scoreValue`, `report.forecastStatus`, `report.forecastFetchedAt`, `report.flavorLine`.
   - `PrefetchWorker`: `BigNightAlerts.select` (`site`, `date`, `scoreValue`, `bestWindow`, `forecastStatus`), `NightCollapse` (`scoreValue`, `bestWindow`), `WindowAlarmScheduler.onBriefingRefreshed` (`report.date`, `report.score?.bestWindow`, `report.scoreValue`).
   - `OutlookNight`: `date`, `scoreValue`, `ephemeris.moonIllumination`, `ephemeris.darkness`, `coverage`, `score.factors.any { it.estimated }`, `NightNames.clearSkies(it)`, `ephemeris.moonPhaseName == MoonPhase.FULL`, `NightNames.showerLabel(it.meteor)`, `skyEventTitle`, `forecastMissing`.

### Recommended Implementation Plan
1. **Add a persistent + in-memory `BriefingSnapshotCache` in `:data` (on `AppGraph`):**
   - Store a small `@Serializable` `PrimaryGlanceSnapshot` (or `BriefingSnapshot` keyed by `siteId`, `siteFingerprint` [lat/lon/zone/bortle/horizon], `nightDate`, `forecastFetchedAtEpochSec`, `generatedAtEpochSec`, `scoreValue`, `flavorLine`, `milkyWayWindowStart/End`, `milkyWayClearsHorizonAt`, `milkyWayBlockedByHorizon`, `forecastStatus`) in memory **and** atomically to `filesDir/cache/glance_snapshot.json` (on `Dispatchers.IO`, temp-file + rename, all new fields defaulted).
   - Producers (`AppViewModel.refresh`, `DigestWorker.postDigests`) publish the primary site's `NightReport` into `graph.glanceSnapshot.save(primary, report)` on every successful briefing!
   - Consumers (`NightBriefWidget.load`, `WearPublisher.load`) check `graph.glanceSnapshot.currentFor(primary, today)` first:
     - **Hit (matches site fingerprint + tonight's date + fresh enough):** Render directly with **zero** scoring or network calls!
     - **Miss (cold process / site edited / next day):** Fall back to computing one primary report (skipping `suggestions`, `comets`, `airQuality` — keeping `kp` and `iss` only because `NightFlavor.line` reads them, or explicitly reusing a single shared fallback so widget + Wear don't both compute it), save it to `glanceSnapshot`, and render.
   - Invalidate `glanceSnapshot` whenever the primary site changes or is deleted (`AppViewModel.mutate` when `sites` changes), so stale site name/score never leaks under a new primary!
2. **Stop duplicate Wear launch on cold app start (`NightBriefApp.kt:28`):**
   - Instead of firing `WearPublisher.enqueue(this)` blindly in `NightBriefApp.onCreate` (which races with `AppViewModel.init`), publish Wear from `AppViewModel.refresh` once the briefing finishes (or only enqueue in `NightBriefApp` if a valid snapshot is already cached and no UI launch is imminent).
3. **Check connected Wear nodes before doing Wear work (`WearPublisher.kt`):**
   - Before calling `load(context)` or `putDataItem`, check `Wearable.getNodeClient(context).connectedNodes` (bounded by short timeout, e.g. 1–2s, or gated by a short-lived cached boolean). If `connectedNodes.isEmpty()`, return early without scoring or calling `putDataItem`! *(Double-check: if a user pairs a watch later, the watch tile itself has standalone fallback and the next app open/prefetch sees the connected node and publishes.)* Keep failure isolation so missing Play Services still swallows cleanly.
4. **Add lean scoring path for `PrefetchWorker` (`BriefingSource` / `Workers.kt`):**
   - Add `briefLean(sites, kit, forceRefresh = false): Briefing` (or a `BriefingOptions` parameter with defaults on `BriefingService`) that uses `NightPlanner.plan(..., includeSuggestions = false)` and skips `kp`, `iss`, `comets`, and `airQuality`.
   - Use `briefLean` in `PrefetchWorker` (`postBigNightAlerts`, `postCollapseAlerts`, and `WindowAlarmScheduler` sync). Keep full `brief` in `DigestWorker` (because morning digest text includes aurora, meteors, ISS, comets, smoke, and target suggestions!).
5. **Implement `PERF-02` step 3 (`NightPlanner.planOutlookNight`):**
   - Extract a shared helper in `NightPlanner` for `offset > 0` nights in `BriefingService.brief` that computes `NightEphemeris`, `NightScoreEngine.score`, `MeteorAdvisor.outlook`, and `ForecastCoverage` without constructing `TimelineHour` lists, `DewOutlook`, or `CloudReason` — reusing the **exact same** scoring code path so scores are guaranteed identical.

### Constraints & Pitfalls to Avoid
- **Never** let a Wear failure skip a widget refresh (`AGENTS.md`).
- **Never** serve a stale site's snapshot after site edit/reorder/delete (key the snapshot by site id + coordinates + zone + Bortle + horizon + night date).
- **Never** drop `flavorLine` parity between Tonight, widget, and watch (keep `NightFlavor.line` consistent).
- Keep `DigestWorker` behavior unchanged (still scores all sites with `forceRefresh = true`, `outlookDays = 1` per `AGENTS.md`).
- Keep `AppGraph.replaceSourcesForTest` working cleanly (clear/bypass snapshot cache in `resetSourcesForTest` or pass test sources cleanly).

### Validation & Acceptance Criteria
- **New unit / Robolectric tests:**
  - Fake `BriefingSource` call-count test: `DigestWorker` → `WidgetRefresh` → `NightBriefWidget` → `WearPublisher` invokes `brief()` **once**, not three times.
  - Cold `NightBriefWidget` with empty snapshot computes fallback once and populates snapshot; immediate subsequent `WearPublisher` reuses snapshot (`0` additional `brief()` calls).
  - Editing or changing the primary site invalidates the snapshot so old site's score/name is never displayed.
  - `PrefetchWorker` test asserts `kp`, `iss`, `comets`, and `airQuality` sources receive **0** calls during prefetch Big Night / Collapse / Window Alarm runs.
  - `BriefingEnrichmentTest`: assert `WeeklyOutlook` produced by the lean outlook path is field-for-field equal to full `NightPlanner.plan` across clear, overcast, partial, stale, and missing-forecast fixtures.
- **Run:**
  ```bash
  NIGHTBRIEF_SKIP_LIVE_TESTS=1 ./gradlew :core-score:test :data:testDebugUnitTest :work:testDebugUnitTest :app:testDebugUnitTest :wear:testDebugUnitTest
  ```

---

## PERF-A05 — Add in-memory cache layers and fix blocking disk I/O in `core-weather` caches

- **Status:** **Done** in [#145](https://github.com/kchen52/NightBrief/pull/145) (`FileForecastCache` 32-entry LRU memory tier, `ForecastRepository` single-flight cancellation isolation + `AppGraph.appScope` `flightScope`, `CachingTleSource` and `CachingCometSource` lock-free fresh memory fast paths + `Dispatchers.IO` + atomic writes, and `BriefingService` parsed-comet reuse across planner dates).
- **Impact Rank:** #5
- **Priority:** P1 / Med-High
- **Category:** Disk I/O, Threading & Coroutine Concurrency
- **Modules:** `:core-weather`, `:data`
- **Labels:** `performance`, `core-weather`, `io`, `concurrency`
- **Relation to `POLISH_FINDINGS.md`:** New (extends the concurrency caution noted in `POLISH_FINDINGS.md` under `PERF-01`).

### Problem & Impact
Three concrete I/O and concurrency issues exist in `:core-weather` cache wrappers:
1. **`FileForecastCache` reads and JSON-parses disk files on every single `get(key)` (`ForecastRepository.kt:41-68`):**
   There is no in-memory cache layer in front of `FileForecastCache`. Every UI refresh, planner date step (`planNight` as the user taps next/prev day), widget update, Wear publish, or darker-sky candidate check opens `fc_XX.XX_YY.YY.json` from flash storage and deserializes ~192 hourly JSON objects from scratch, even if the forecast was just read 100 ms ago.
2. **`CachingTleSource` and `CachingCometSource` do blocking disk I/O on the caller's dispatcher without an in-memory cache (`CelestrakClient.kt:80-175`, `MpcCometClient.kt:91-165`):**
   - `readCache()` calls `cacheFile.readLines()` / `cacheFile.readText()` synchronously inside `mutex.withLock` with **no `withContext(Dispatchers.IO)`** and **no in-memory field** caching the last read `IssTle` / `CometElementsFile`.
   - `writeCache()` calls `cacheFile.writeText(...)` synchronously on the caller's dispatcher too.
   - `Soft00Cmt.txt` (MPC observable comets) is a multi-hundred-KB text file re-read from disk on every `brief()` and every planner `plan()` call (stepping through 7 planner days re-reads and re-parses the full file 7 times!).
   - Furthermore, `CachingCometSource` holds `mutex` across the entire 10-second network timeout (`withTimeout(originTimeout.toMillis())`), blocking readers even if a fresh read could have completed.
3. **Single-flight cancellation leak in `ForecastRepository.kt:126-138`:**
   ```kotlin
   val created = CompletableDeferred<ForecastResult>()
   val inFlight = flights.putIfAbsent(key, created)
   if (inFlight != null) return inFlight.await()
   return try {
       val result = load(latitude, longitude, key, cached)
       created.complete(result)
       result
   } catch (e: Throwable) {
       created.completeExceptionally(e)
       throw e
   } finally {
       flights.remove(key, created)
   }
   ```
   Because `load(...)` runs inside the **first caller's** coroutine, if Caller A (e.g., `AppViewModel.refreshJob` cancelled by a quick site switch or screen leave) is cancelled mid-fetch, `e` (`CancellationException`) completes `created` exceptionally — **aborting Caller B** (e.g., `DigestWorker` or `PrefetchWorker` or a second UI load) that was awaiting the same in-flight `Deferred`!

### Affected Code
- `core-weather/src/main/kotlin/app/nightbrief/weather/ForecastRepository.kt:41-68` (`FileForecastCache`)
- `core-weather/src/main/kotlin/app/nightbrief/weather/ForecastRepository.kt:111-139` (`ForecastRepository.forecast` single-flight)
- `core-weather/src/main/kotlin/app/nightbrief/weather/CelestrakClient.kt:80-175` (`CachingTleSource`)
- `core-weather/src/main/kotlin/app/nightbrief/weather/MpcCometClient.kt:91-165` (`CachingCometSource`)
- `data/src/main/kotlin/app/nightbrief/data/AppGraph.kt:60-64, 80, 98-101`

### Root Cause Analysis
- `FileForecastCache` was implemented directly over `java.io.File` without a bounded memory tier.
- `CachingTleSource` and `CachingCometSource` were written as lightweight file wrappers before `StrictMode` and planner stepping made repeated reads obvious.
- `ForecastRepository` used `CompletableDeferred` inside the caller's structured concurrency scope instead of an independent/reference-counted flight or re-trying on owner cancellation.

### Investigation Steps for Implementing Agent
1. Read `ForecastRepository.kt`, `ForecastRepositoryTest.kt`, `CelestrakClient.kt`, `CelestrakClientTest.kt`, `MpcCometClient.kt`, and `MpcCometClientTest.kt`.
2. Check if any test externally modifies `FileForecastCache` files on disk between two `get()` calls on the **same** `FileForecastCache` instance! (If a test writes directly to disk to simulate an older file or corrupt file, a naive memory cache that never checks `file.lastModified()` or size — or tests that construct a fresh `FileForecastCache` — should be verified!)
3. Check `MpcComets.parse` in `BriefingService.plan` (`BriefingService.kt:262`): stepping dates in the planner calls `plan()` each time and re-parses `cometFile.text` even when `cometFile.fetchedAt` hasn't changed!

### Recommended Implementation Plan
1. **Add bounded in-memory tier to `FileForecastCache` (`ForecastRepository.kt:41-68`):**
   - Keep a bounded `LinkedHashMap<String, CacheEntry>` (e.g., max 32 entries, synchronized or `ConcurrentHashMap` with small cap; each `Forecast` is ~20 KB so 32 entries ≈ 640 KB).
   - Store `data class CacheEntry(val lastModified: Long, val length: Long, val forecast: Forecast)`.
   - On `get(key)`: check in-memory entry first; if present and (either trust memory for writes via `put`, or stat `file.lastModified() == entry.lastModified && file.length() == entry.length()` on `Dispatchers.IO` — note: checking `inMemory[key]` directly without even touching disk is fastest and avoids StrictMode disk reads completely, as long as tests that modify files on disk create a fresh `FileForecastCache` — verify existing tests first!).
   - On `put(key, forecast)`: update in-memory entry immediately after disk write succeeds (or before, and roll back/keep consistent).
2. **Add in-memory cache + `Dispatchers.IO` to `CachingTleSource` and `CachingCometSource`:**
   - Add `@Volatile private var memoryCache: IssTle? = null` (and `CometElementsFile?` for comets).
   - Fast path before acquiring `mutex`: if `val mem = memoryCache; mem != null && ageOf(mem, clock.instant()) <= maxAge` → return `mem` immediately with zero lock contention and zero disk I/O!
   - Wrap `readCache()` and `writeCache()` in `withContext(Dispatchers.IO)` (using temp-file + rename for atomic writes, same pattern as `FileForecastCache`).
   - Optionally cache the last parsed `Pair<Instant, List<CometOrbit>>` inside `BriefingService` so `plan()` date-stepping in the planner never re-parses the unchanged `Soft00Cmt.txt`!
3. **Decouple single-flight cancellation in `ForecastRepository` (`ForecastRepository.kt:126-138`):**
   - If a waiter receives a `CancellationException` from `inFlight.await()` while the waiter's own coroutine is still active (`currentCoroutineContext().isActive`), retry the loop (starting a fresh flight instead of failing!).
   - Even better: accept an optional `flightScope: CoroutineScope? = null` (wired to `graph.appScope` in `AppGraph`) so an in-flight network request runs to completion and populates the cache even if Caller A navigates away, while Caller A's `await()` still cancels immediately!

### Constraints & Pitfalls to Avoid
- Re-throw `CancellationException` whenever the *current* coroutine is cancelled.
- Keep `ForecastRepository`'s `busyCooldown` (HTTP 429) and `STALE` fallback behavior 100% intact.
- Keep `CachingTleSource` / `CachingCometSource` `failureCooldown` and `staleMaxAge` (7 days) contracts intact.
- Verify `CelestrakClientTest` and `MpcCometClientTest` (which may mutate `cacheFile` on disk between calls on the same instance — if they do, either check `cacheFile.lastModified()` or verify how those tests are structured!).

### Validation & Acceptance Criteria
- **New unit tests:**
  - `ForecastRepositoryTest`:
    - Two sequential `FileForecastCache.get(key)` calls parse JSON / read disk only once.
    - Single-flight cancellation test: Caller A starts a slow fetch, Caller B joins it, Caller A is cancelled — assert Caller B still succeeds and receives `FRESH` without throwing `CancellationException`!
  - `CelestrakClientTest` & `MpcCometClientTest`:
    - Second `fetchIss()` / `fetch()` within `maxAge` returns from memory without touching disk (testable by deleting or observing file reads, or verifying timing/behavior).
  - `BriefingEnrichmentTest`:
    - Calling `BriefingService.plan()` for 3 different dates with the same `CometElementsFile` invokes `cometParser` **once**, not 3 times.
- **Run:**
  ```bash
  ./gradlew :core-weather:test :core-score:test :data:testDebugUnitTest
  ```

---

## PERF-A06 — Lazify Tonight vertical layout, memoize `DetailUiState`/`EmphasizedText`/`TimelineCard`, and fix animation composition reads

- **Status:** **Part A done** in [#145](https://github.com/kchen52/NightBrief/pull/145) (`@Stable` + `remember` on `DetailUiState`, `remember(text)` in `EmphasizedText`, memoized `TimelineCard` hours/rows/columns, and draw-phase `anim.value` read in `AuroraRipple` covered by `AuroraRippleRecompositionTest`). **Part B open** (evaluating `LazyColumn` conversion on `TonightScreen` / `NightDetail`).
- **Impact Rank:** #6
- **Priority:** P2 / Med-High
- **Category:** Compose Rendering, Allocations & Scroll Smoothness
- **Modules:** `:app`
- **Labels:** `performance`, `compose`, `ui`, `tonight`
- **Relation to `POLISH_FINDINGS.md`:** Expands `PERF-09` with four newly identified, unconditionally beneficial Compose memoization/draw bugs (`DetailUiState`, `EmphasizedText`, `TimelineCard` row lambdas, `AuroraRipple`).

### Problem & Impact
Even before evaluating whether to convert `TonightScreen`'s vertical container to `LazyColumn`, several concrete Compose bugs cause unnecessary work on every recomposition of `TonightScreen` and `NightDetail`:

1. **`rememberDetailUiState` returns a new unmemoized `DetailUiState` on every recomposition (`NightDetail.kt:255-265`):**
   ```kotlin
   return DetailUiState(whyOpen, expandedTargets, hoursScroll)
   ```
   `DetailUiState` is not a `data class` and is not wrapped in `remember(whyOpen, expandedTargets, hoursScroll)`. Every parent recomposition allocates a fresh `DetailUiState` reference, forcing `TimelineCard`, `SuggestionsCard`, and every other child receiving `ui: DetailUiState` to recompose even when nothing inside them changed!
2. **`EmphasizedText` runs regex matching on every recomposition (`Components.kt:385-412`):**
   `EmphasizedText` calls `emphasize(text)` (which runs `EMPHASIS.findAll(text)` and allocates a `buildAnnotatedString`) directly in composition with no `remember(text)`. `NightDetail` has **~20** `EmphasizedText` call sites (`NightDetail.kt:422, 601, 609, 624, 633, 653, 668, 678, 703, 732, 746, 781, 782, 836, 1025, 1109, 1112, 1120, 1140, 1151`).
3. **`TimelineCard` allocates 10 `TimelineRow` lambdas and filters `report.timeline` on every recomposition (`NightDetail.kt:901, 913-942`):**
   `val hours = report.timeline.filter { it.isDark }.ifEmpty { report.timeline }` and the 10-item `rows` list with 20 closures are recreated every recomposition, and the grid renders ~10 rows × 8–14 hours = ~100–150 `Box`+`Text` nodes eagerly via `Column`/`Row`.
4. **`AuroraRipple` reads `anim.value` during composition (`AliveHeroCard.kt:149`):**
   ```kotlin
   val phase = if (motion) anim.value else 0f
   Canvas(...) { drawRipples(phase, color) }
   ```
   Reading `anim.value` outside the `Canvas` lambda recomposes `AuroraRipple` on **every animation frame** for 1,600 ms (96–192 recompositions at 60–120 Hz), whereas `ShootingStar` right below it (`AliveHeroCard.kt:167`) correctly reads `anim.value` inside the `Canvas` draw block.
5. **Eager vertical `Column` on `TonightScreen` (`TonightScreen.kt:136-185`, `PERF-09`):**
   All ~14 cards (Hero, Summary, Confidence, Sky Windows, Aurora, Meteors, ISS, Comet, Dew, Smoke, Timeline, Targets, Darker Sky, Session Log) are composed and measured on first display even though only the top 2–3 cards fit in the viewport.

### Affected Code
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/NightDetail.kt:254-265` (`rememberDetailUiState`)
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/NightDetail.kt:897-1030` (`TimelineCard`)
- `app/src/main/kotlin/app/nightbrief/app/ui/common/Components.kt:385-412` (`EmphasizedText`)
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/whimsy/AliveHeroCard.kt:141-158` (`AuroraRipple`)
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/TonightScreen.kt:136-185` (`Column.verticalScroll`)

### Investigation Steps for Implementing Agent
1. Read `NightDetail.kt` around `NightDetail(...)`, `rememberDetailUiState`, and `TimelineCard`, plus `TonightScreen.kt` and `AliveHeroCard.kt`.
2. Split this work into two clean commits:
   - **Part A (unconditional low-risk wins):** `remember` `DetailUiState`, `remember(text) { emphasize(text) }`, memoize `TimelineCard` `hours` & `rows`, and move `AuroraRipple` `anim.value` read inside `Canvas`.
   - **Part B (measured layout change — `PERF-09`):** Evaluate converting `TonightScreen` / `NightDetail` single-column vertical list to `LazyColumn` with stable section keys (preserving the foldable two-column layout!). Verify scroll position restoration, TalkBack traversal order, Haze `glassSource` compatibility, `TabSwipeTest`, and Paparazzi full-height scrolling screenshots!

### Recommended Implementation Plan
1. **Part A — Safe memoization & draw-phase fixes:**
   - `NightDetail.kt:264`:
     ```kotlin
     return remember(whyOpen, expandedTargets, hoursScroll) {
         DetailUiState(whyOpen, expandedTargets, hoursScroll)
     }
     ```
     Also annotate `DetailUiState` with `@Stable` (all its properties are Compose `MutableState` / `ScrollState`).
   - `Components.kt:402`:
     ```kotlin
     val annotated = remember(text) { emphasize(text) }
     Text(annotated, modifier = modifier, style = style, color = color, fontWeight = fontWeight)
     ```
   - `NightDetail.kt:901, 913`: Wrap `hours` in `remember(report.timeline)` and `rows` in `remember(colors, units, good, fair, poor, bad)` (or precompute formatted strings per `TimelineHour` in `remember(hours, zone, twentyFourHour, units, colors)` so `TimelineCard` only draws cached strings/colors!).
   - `AliveHeroCard.kt:149-156`: Remove `val phase = ...` from composition; inside `Canvas { drawRipples(if (motion) anim.value else 0f, color) }` so zero recompositions happen during the 1.6s aurora ripple animation.
2. **Part B — Lazify `TonightScreen` (measure first, check Paparazzi carefully!):**
   - **Watch out for Paparazzi!** Full-height Tonight screenshots (`TonightScreenshotTest`, `ReadmeScreenshotTest`) or Robolectric tests using `performScrollTo()` / `onNodeWithText(...)` may expect offscreen nodes to exist or scroll cleanly via `LazyColumn` (`performScrollToNode`). Check all tests referencing `TonightScreen` / `NightDetail` before converting `Column` → `LazyColumn`.
   - If converting to `LazyColumn`, preserve:
     - `ReportGlassScroll { listState.isScrollInProgress }` (from PERF-A02)
     - Foldable two-column layout in `NightDetail`
     - `AnimatedVisibility` collapse behavior in `SectionCard` (collapsed content still leaves composition after exit)
     - Horizontal swipe gesture consumption in `SiteStrip` and `TimelineCard` (`TabSwipeTest`)

### Constraints & Pitfalls to Avoid
- Do **not** break `TonightScreenshotTest`, `ReadmeScreenshotTest`, `TalkBackLabelTest`, `RoadmapUiTest`, or `TabSwipeTest`.
- Do **not** alter `NightDetail` text formatting (`EmphasizeTest` must stay green).
- Remember `AGENTS.md`: *"Collapsed content leaves composition after the exit, so tests that assert it is gone must wait for idle."*

### Validation & Acceptance Criteria
- **New test:** Compose recomposition-count test for `AuroraRipple` (asserts recomposition count stays at 1 while `Animatable` advances) and `EmphasizedText` (asserts regex runs once per unique `text`).
- **Existing tests & goldens:**
  ```bash
  NIGHTBRIEF_SKIP_LIVE_TESTS=1 ./gradlew :app:verifyPaparazziDebug
  ```
  Verify zero golden diffs on Part A (and zero unintended diffs on Part B).

---

## PERF-A07 — Eliminate redundant ephemeris sweeps in `NightEphemeris.compute` and `TargetAdvisor.evaluate`

- **Status:** **Done** in [#138](https://github.com/kchen52/NightBrief/pull/138) (single-pass 24h solar altitude curve in `NightEphemeris.compute` + precomputed Moon table in `TargetAdvisor.suggest`, covered by `NightEphemerisSinglePassTest` and `TargetAdvisorMoonTableTest`).
- **Impact Rank:** #7
- **Priority:** P2 / Medium
- **Category:** CPU & Astronomical Algorithms
- **Modules:** `:core-astro`, `:core-score`
- **Labels:** `performance`, `core-astro`, `core-score`, `cpu`
- **Relation to `POLISH_FINDINGS.md`:** New finding.

### Problem & Impact
Every night in a 7-night briefing (× N sites) runs `NightEphemeris.compute`, and Tonight (plus every planner day) runs `TargetAdvisor.suggest`. Both contain large redundant trig loops:

1. **`NightEphemeris.compute` runs 9 separate 24-hour solar altitude scans (`NightEphemeris.kt:101-175`):**
   - Lines 110–112: `longestBelow(-18°)`, `longestBelow(-12°)`, `longestBelow(-6°)` → 3 full 24h scans (`145` 10-minute grid points each + bisections).
   - Lines 140–141: `firstSetting(-0.833°)` and `lastRising(-0.833°)` → 2 more full 24h scans for the **exact same threshold**!
   - Lines 171–174: `firstSetting(-12°)`, `lastRising(-12°)`, `firstSetting(-18°)`, `lastRising(-18°)` → 4 more full 24h scans duplicating the exact thresholds already scanned on lines 110–111!
   - Total: **9 × 145 ≈ 1,305** `Ephemeris.sunAltitude` calls per night (plus bisections), when **1 × 145 = 145** grid samples + bisections at the crossing intervals produces the **exact same instants** (down to the millisecond)!
   - Across 7 nights × 4 saved sites = 28 nights → ~36,500 solar trig evaluations reduced to ~4,060 (~89% reduction).

2. **`TargetAdvisor.evaluate` recomputes `Ephemeris.moon(t, lat, lon)` inside the inner target loop (`TargetAdvisor.kt:123-152`):**
   - `TargetCatalog.all` has ~40 targets. For every target that passes the Bortle/interest filter, `evaluate` walks `t` from `dark.start` to `dark.end` in 10-minute steps (~48–72 steps per night) and calls `Ephemeris.moon(t, lat, lon)` on line 124 — which computes solar longitude, lunar ecliptic longitude/latitude, nutation/obliquity, topocentric parallax, GMST, altitude, azimuth, and phase illumination.
   - Because `dark.start`, `dark.end`, `lat`, and `lon` are **identical for every target** in a single `TargetAdvisor.suggest()` call, the Moon position is recomputed **~40× redundantly** (~2,400 redundant lunar ephemeris calls per night!).

### Affected Code
- `core-astro/src/main/kotlin/app/nightbrief/astro/NightEphemeris.kt:95-185` (`compute`)
- `core-astro/src/main/kotlin/app/nightbrief/astro/Ephemeris.kt:139-219` (`crossings`, `intervalsBelow`, `intervalsAbove`, `bisect`)
- `core-score/src/main/kotlin/app/nightbrief/score/TargetAdvisor.kt:83-155` (`suggest`, `evaluate`)

### Root Cause Analysis
- `NightEphemeris.compute` composed small declarative helpers (`firstSetting`, `lastRising`, `longestBelow`), each independently calling `Ephemeris.crossings(start, end, ...)`.
- `TargetAdvisor.evaluate` was written per-target without hoisting the time-grid Moon table out to `suggest()`.

### Investigation Steps for Implementing Agent
1. Read `NightEphemeris.kt`, `Ephemeris.kt`, `NightEphemerisTest.kt`, `TargetAdvisor.kt`, `TargetAdvisorTest.kt`, and `TargetAdvisorMoonTest.kt`.
2. Pay close attention to `Ephemeris.intervals(...)` (`Ephemeris.kt:187-200`) and `bisect(...)` (`Ephemeris.kt:202-218`) to guarantee that the single-pass solar scan produces the **exact same** `Instant`s (even at polar latitudes where the sun never rises or never sets, and on DST transitions where `start..end` is 23h or 25h!).

### Recommended Implementation Plan
1. **Single-pass solar sampling in `NightEphemeris.compute` (`NightEphemeris.kt:95-175`):**
   - Sample `sunAlt(t)` once across `[start, end]` at 10-minute steps into two primitive arrays: `times: LongArray` (epoch millis) and `alts: DoubleArray` (size ≤ 151).
   - Write a private helper `crossingsFromSamples(times, alts, threshold, altitude = sunAlt): List<Crossing>` that walks the pre-sampled `alts` array and calls the exact same `bisect(t0, t1, threshold, a0 < 0, sunAlt)` only on brackets where `(a0 < 0) != (a1 < 0)`.
   - Derive all 4 thresholds (`HORIZON_ALTITUDE`, `CIVIL_TWILIGHT`, `NAUTICAL_TWILIGHT`, `ASTRONOMICAL_TWILIGHT`) from that one sampled array, and compute `intervalsBelow` directly from each threshold's `crossings` + `alts[0] < threshold`!
   - Reuse the computed crossing lists directly for `sunset` (`firstOrNull { !it.rising }`), `sunrise` (`lastOrNull { it.rising }`), `nauticalDusk`/`nauticalDawn`, `astronomicalDusk`/`astronomicalDawn`, and `longestBelow` — zero duplicate scans!
2. **Hoist lunar table in `TargetAdvisor.suggest` (`TargetAdvisor.kt:83-155`):**
   - Build a local `val steps: List<Pair<Instant, MoonPosition>>` once for `t = dark.start` while `!t.isAfter(dark.end)` stepping by `STEP` (10 min): `t to Ephemeris.moon(t, eph.latitudeDeg, eph.longitudeDeg)`.
   - Pass `steps` into `evaluate(...)` and iterate `for ((t, moon) in steps)` instead of calling `Ephemeris.moon` per target!
   - *(Optional bonus: if `TargetCatalog.all` is filtered down to 0 candidates before `evaluate`, compute `steps` lazily or only if filtered catalog is non-empty!)*

### Constraints & Pitfalls to Avoid
- **Do NOT alter the astronomical model** (ΔT, precession, refraction, `HORIZON_ALTITUDE`, bisection tolerance of 15 seconds). `NightEphemerisTest` is validated against Skyfield 1.55 + DE421 (`AGENTS.md`).
- Preserve exact edge-case behavior for polar day (`Darkness.NONE`), polar night (sun always below `-18°` — `alts[0] < threshold` with zero crossings must still emit `TimeWindow(start, end)`!), and DST 23h/25h days.

### Validation & Acceptance Criteria
- **All existing astro & score tests must pass with zero tolerance relaxation:**
  `NightEphemerisTest`, `TargetAdvisorTest`, `TargetAdvisorMoonTest`, `TargetInterestTest`, `BrightMoonAndHorizonTest`, `NightScoreEngineTest`, `SkyEventsTest`.
- **Add equivalence test:**
  - Verify single-pass `NightEphemeris.compute` produces **exact** (0 ms diff) sunset, sunrise, nautical/astronomical dusk/dawn, and `darkWindow` across mid-latitude, equatorial, polar summer, polar winter, and DST spring-forward / fall-back dates.
  - Verify `TargetAdvisor.suggest` produces identical `TargetSuggestion` lists across new moon, half moon, full moon, and custom horizon masks.
- **Run:**
  ```bash
  ./gradlew :core-astro:test :core-score:test
  ```

---

## PERF-A08 — Convert bundled target sample JPEGs to WebP and decode thumbnails off the Main thread

- **Impact Rank:** #8
- **Priority:** P2 / Medium
- **Category:** APK Size, Main-Thread Bitmap Decode & Memory
- **Modules:** `:app`
- **Labels:** `performance`, `assets`, `apk-size`, `compose`
- **Relation to `POLISH_FINDINGS.md`:** New finding.

### Problem & Impact
`app/src/main/res/drawable-nodpi/` ships **40 JPEG photographs** (`target_*.jpg`) totaling **3.6 MB** (accounting for >40% of the 8.4 MiB release APK), at resolutions up to **840×840 px** (e.g., `target_m42.jpg` is 840×840, `target_m31.jpg` is 840×553).
In `TargetSamples.kt:104-129`:
- `TargetSampleThumb` renders a **48×48 dp** thumbnail (~144×144 px at xxhdpi) using `painterResource(sample.drawable)`.
- `TargetSampleDetail` renders a `fillMaxWidth() × 156.dp` banner using `painterResource(sample.drawable)`.

`painterResource` for raster drawables decodes the **entire full-resolution JPEG synchronously on the Main thread** during composition via `BitmapFactory` (~2.8 MB RGBA per 840×840 image, ~11 MB if 4 target cards are visible on Tonight), causing measurable frame drops when Tonight first renders targets or when the user expands target cards.

### Affected Code
- `app/src/main/res/drawable-nodpi/target_*.jpg` (40 files, 3.6 MB total)
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/TargetSamples.kt:52-135`
- `app/src/test/kotlin/app/nightbrief/app/ui/TargetSampleUiTest.kt`
- `app/src/test/kotlin/app/nightbrief/app/ui/TargetSampleScreenshotTest.kt`

### Root Cause Analysis
`painterResource` is convenient for small icons/vectors, but for 40 high-resolution photographic JPEGs in `drawable-nodpi`, it neither downsamples (`inSampleSize`) to the 48 dp thumbnail size nor moves `BitmapFactory.decodeResource` off the Main thread. In addition, Android `minSdk 26` natively supports lossy WebP with far better compression than baseline JPEG.

### Investigation Steps for Implementing Agent
1. Read `TargetSamples.kt`, `TargetSampleUiTest.kt`, and `TargetSampleScreenshotTest.kt`.
2. Check dimensions and file sizes of all 40 `app/src/main/res/drawable-nodpi/target_*.jpg` files.
3. Check how Paparazzi (`stillFrame()` in `Motion.kt`) interacts with asynchronous image loading! In Paparazzi screenshot tests (`TargetSampleScreenshotTest`, `TonightScreenshotTest`, `ReadmeScreenshotTest`), `produceState` on `Dispatchers.IO` may not complete before Paparazzi captures frame 1 unless the still-frame / preview path decodes synchronously (or uses `painterResource` when `stillFrame()` is true!).

### Recommended Implementation Plan
1. **Convert all 40 `target_*.jpg` files to lossy WebP (`target_*.webp`):**
   - Use `cwebp -q 80` (or Android Studio / ImageMagick / Pillow), capping the longest edge to `640 px` (plenty sharp for a 156 dp / ~468 px detail card at xxhdpi; keeping exact aspect ratio).
   - Keep identical resource base names (`target_m31.webp` → still `R.drawable.target_m31`!) so `TargetSamples.kt` resource IDs don't change.
   - Verify visual quality (especially dark starfields / nebulae — check for banding on `m42`, `m31`, `mw_core` and bump quality to `82–85` if needed). Expect **~50–65% size reduction** (~1.8–2.3 MB shaved off the APK).
2. **Add a tiny, zero-dependency async downsampling loader in `TargetSamples.kt` (no new Gradle dependencies required):**
   - Create a process-scoped `LruCache<Pair<Int, Int>, ImageBitmap>` keyed by `(drawableResId, targetMaxPx)` capped at e.g. 8–12 MB (in KB).
   - Write `@Composable private fun rememberSampleBitmap(@DrawableRes resId: Int, targetPx: Int): ImageBitmap?`:
     - If `stillFrame()` (Paparazzi/preview): decode synchronously (still downsampled with `inSampleSize`) so screenshots always render the photo immediately!
     - On live devices: check `LruCache` synchronously first (instant hit on re-visits / scroll-back!). On miss, `produceState<ImageBitmap?>(null, resId, targetPx)` with `withContext(Dispatchers.IO)` using `BitmapFactory.Options`: first pass `inJustDecodeBounds = true`, compute power-of-2 `inSampleSize` for `targetPx`, second pass decode with `inPreferredConfig = Bitmap.Config.RGB_565` (photos have no alpha — halves bitmap RAM from 4 B/px to 2 B/px!), wrap `asImageBitmap()`, store in `LruCache`.
   - Show a subtle `surfaceContainerHigh` rounded placeholder box while decoding (~1–2 frames on cold miss).
   - Pass `targetPx = with(LocalDensity.current) { 48.dp.roundToPx() }` in `TargetSampleThumb` (~144 px → `inSampleSize = 4`, **16× less RAM** per thumbnail!) and `~480–640 px` in `TargetSampleDetail`.
   - *(Note: If adding a dependency is explicitly approved later, Coil 3 `coil-compose` could replace this helper, but per `AGENTS.md` dependencies stay on the pinned catalog unless the task is a bump — the ~40-line `LruCache` + `BitmapFactory` helper needs zero new dependencies and adds zero DEX bloat.)*

### Constraints & Pitfalls to Avoid
- Preserve Wikimedia Commons attribution strings and URLs in `TargetSamples.kt` unchanged.
- Preserve `samplePhotoFilter()` (red-channel Night Vision `ColorMatrix` filter!) on both thumb and detail `Image`.
- Preserve `contentDescription = stringResource(R.string.target_sample_photo, name)` on `TargetSampleThumb` and `null` on `TargetSampleDetail`.
- Update Paparazzi goldens (`:app:recordPaparazziDebug`) and `docs/screenshots` **only if** WebP re-encoding causes a pixel diff in `TargetSampleScreenshotTest` / `TonightScreenshotTest` / `ReadmeScreenshotTest` — run on Linux renderer per `AGENTS.md`.

### Validation & Acceptance Criteria
- Record before/after total size of `drawable-nodpi/target_*` and release APK size (`./gradlew :app:assembleRelease`).
- Verify `TargetSampleUiTest`, `TargetSampleScreenshotTest`, `TonightScreenshotTest`, and `ReadmeScreenshotTest` pass.
- Verify StrictMode (`detectDiskReads`) shows zero disk reads on Main when opening Targets on a debug build.
- **Run:**
  ```bash
  NIGHTBRIEF_SKIP_LIVE_TESTS=1 ./gradlew :app:verifyPaparazziDebug :app:assembleRelease
  ```

---

## PERF-A09 — Batch Open-Meteo forecast calls and bound Overpass API concurrency

- **Status:** **Part A done** in [#145](https://github.com/kchen52/NightBrief/pull/145) (`OverpassRoadAccessClient` bounded `Semaphore(maxConcurrent = 2)` + `CachingRoadAccessSource` ~500 m cell LRU cache wired in `AppGraph`, covered by `RoadAccessTest`). **Part B open** (evaluating Open-Meteo multi-coordinate batching).
- **Impact Rank:** #9
- **Priority:** P3 / Low-Medium
- **Category:** Network / API Rate-Limit Resilience & Latency
- **Modules:** `:core-weather`, `:core-score`, `:data`
- **Labels:** `performance`, `network`, `core-weather`
- **Relation to `POLISH_FINDINGS.md`:** New finding.

### Problem & Impact
1. **Overpass API burst concurrency (`DarkSkyFinder.kt:120-139`, `RoadAccess.kt:44-55`):**
   `DarkSkyFinder.search` launches **5 concurrent** `OverpassRoadAccessClient.accessFor` HTTP requests to the public `https://overpass-api.de/api/interpreter` server at the exact same instant, with **no caching** across repeated searches. Public Overpass enforces strict per-IP slot limits (typically 2 concurrent queries) and returns HTTP `429 Too Many Requests` or `504 Gateway Timeout` on bursts — degrading candidate cards to `RoadAccess.UNKNOWN` and burning up to 10-second timeouts.
2. **Multi-coordinate Open-Meteo fan-out (`OpenMeteoClient.kt:22-41`, `ForecastRepository.kt:168-187`, `BriefingService.kt:162-168`):**
   - For a user with `N` saved sites (or 5 darker-sky candidates), `ForecastRepository` issues `2 × N` separate Open-Meteo HTTP calls (`N` primary model + `N` secondary cloud-only model) plus `N` `7Timer` calls = `3 × N` HTTP requests (e.g., 15 HTTP calls for 5 darker-sky candidates or 4 saved sites).
   - Open-Meteo's `/v1/forecast` endpoint natively accepts comma-separated coordinate lists (`latitude=lat1,lat2,...&longitude=lon1,lon2,...`), returning a JSON array of location objects when >1 coordinate is queried (and a single object when 1 coordinate is queried). Grouping coordinates that share the same `WeatherModel` into a single HTTP request cuts TLS/HTTP round-trips and Open-Meteo request quota usage proportionally.

### Affected Code
- `core-weather/src/main/kotlin/app/nightbrief/weather/RoadAccess.kt:44-124` (`OverpassRoadAccessClient`)
- `core-score/src/main/kotlin/app/nightbrief/score/DarkSkyFinder.kt:120-139`
- `core-weather/src/main/kotlin/app/nightbrief/weather/OpenMeteoClient.kt:22-92`
- `core-weather/src/main/kotlin/app/nightbrief/weather/ForecastRepository.kt:168-187`
- `core-score/src/main/kotlin/app/nightbrief/score/BriefingService.kt:162-168`
- `data/src/main/kotlin/app/nightbrief/data/AppGraph.kt:78`

### Root Cause Analysis
Each client was designed around a single `(latitude, longitude)` call without concurrency throttling, cell caching (for Overpass), or multi-location batching (for Open-Meteo).

### Investigation Steps for Implementing Agent
1. Read `RoadAccess.kt`, `RoadAccessTest.kt`, `DarkSkyFinderRoadAccessTest.kt`, `OpenMeteoClient.kt`, and `ForecastRepositoryTest.kt`.
2. Check `core-weather/src/main/kotlin/app/nightbrief/weather/Http.kt` (recently updated in `515a3b2` with response size bounds!) so any batched response stays comfortably inside `Http.kt`'s max response byte cap.
3. Confirm Overpass caching key precision: rounding to `0.005°` (~500 m, matching `DEFAULT_RADIUS_M = 500`) in an in-memory `CachingRoadAccessSource` avoids redundant Overpass hits when re-running darker-sky search in the same session.

### Recommended Implementation Plan
1. **Part A — Overpass concurrency limit + in-memory cache (high reliability win, low risk):**
   - Add a `Semaphore(permits = 2)` inside `OverpassRoadAccessClient` (configurable via constructor default `maxConcurrent = 2`) so at most 2 Overpass queries fly concurrently, preventing `429` bursts.
   - Wrap `OverpassRoadAccessClient` in a small `CachingRoadAccessSource` in `AppGraph.kt:78` (in-memory cache keyed by ~500 m cell, e.g., `%.3f,%.3f`, caching `DRIVE_UP` / `HIKE_IN` for the process lifetime — never caching `UNKNOWN`/failures!).
2. **Part B — Optional Open-Meteo multi-location batching (measure/evaluate carefully):**
   - Add `OpenMeteoClient.fetchBatch(coords: List<Pair<Double, Double>>, days, model, hourly): List<Forecast>` that falls back to single-item `fetch` when `coords.size == 1`, and parses a top-level `JsonArray` when `coords.size > 1`.
   - Expose a default `forecastBatch(...)` on `ForecastSource` (falling back to parallel `forecast(...)`), implemented in `ForecastRepository` to group uncached coordinates by `WeatherModel.forLocation(...)` and batch primary + secondary Open-Meteo calls while still storing individual per-cell `FileForecastCache` entries and isolating per-site failures (e.g., if a batch fails, fall back cleanly to stale cache per coordinate).

### Constraints & Pitfalls to Avoid
- **Failure isolation:** A single malformed coordinate or batch error must never break stale-cache fallback for other coordinates in the batch.
- Keep `User-Agent: NightBrief/0.1` (`Http.kt`) and response size bounds untouched.
- Never add live-network tests without `NIGHTBRIEF_SKIP_LIVE_TESTS=1`; use `MockWebServer` in `:core-weather` tests.

### Validation & Acceptance Criteria
- **New `MockWebServer` tests in `RoadAccessTest.kt`:**
  - Verify `OverpassRoadAccessClient` never exceeds `maxConcurrent` in-flight requests when 5 coroutines call `accessFor` simultaneously.
  - Verify `CachingRoadAccessSource` serves a second lookup in the same cell with 0 network calls, and does NOT cache failures.
- **New `MockWebServer` tests in `ForecastRepositoryTest.kt` / `OpenMeteoClientTest` (if Part B is implemented):**
  - Verify 5 coordinates in the same `WeatherModel` box trigger 1 batched primary Open-Meteo call and 1 batched secondary call, populate 5 individual cache cells, and fall back to per-cell stale cache on HTTP 500/429.
- **Run:**
  ```bash
  ./gradlew :core-weather:test :core-score:test
  ```

---

## PERF-A10 — Add Compose Compiler stability configuration for `core-*` models and `java.time` types

- **Impact Rank:** #10
- **Priority:** P3 / Low-Medium
- **Category:** Compose Compiler Skippability
- **Modules:** `:app`, `:wear`
- **Labels:** `performance`, `compose`, `compiler`
- **Relation to `POLISH_FINDINGS.md`:** Mentioned as a diagnostic in `POLISH_FINDINGS.md:684`; this ticket turns it into a concrete, zero-dependency compiler optimization.

### Problem & Impact
NightBrief's UI composables (`TonightScreen`, `NightDetail`, `WeekScreen`, `HeroCard`, `TimelineCard`, `NightRow`, `WearGlanceScreen`) take rich immutable domain models defined in pure JVM modules (`:core-score`, `:core-sites`, `:core-gear`, `:core-astro`, `:core-weather`) such as `NightReport`, `WeeklyOutlook`, `OutlookNight`, `Site`, `GearKit`, `NightScore`, `TargetSuggestion`, and `java.time.LocalDate` / `Instant` / `ZoneId` / `Duration`.

Because those pure JVM modules do **not** apply `kotlin.plugin.compose`, and because those data classes contain Kotlin `List<T>` and `java.time.*` properties, the Compose compiler treats them as **unstable**. As a result, composables accepting them are marked **non-skippable** (in standard mode, or fall back to structural/equals comparison if Strong Skipping is enabled — and even with Strong Skipping on Kotlin 2.x, `List` and `java.time` fields still force reference-equality checks on newly recreated collections or defeat stability inference in downstream lambdas).

### Affected Code
- `app/build.gradle.kts` (add `composeCompiler { stabilityConfigurationFiles.add(...) }`)
- `wear/build.gradle.kts` (same)
- New file: `compose-stability.conf` at repo root (or `app/compose-stability.conf`)

### Investigation Steps for Implementing Agent
1. Inspect the Kotlin version in `gradle/libs.versions.toml` (`kotlin = "2.4.21"`, `kotlin-compose` plugin). Note that Kotlin 2.0.20+ enables Strong Skipping Mode by default, so **measure first**!
2. Run Compose compiler reports on `:app:compileReleaseKotlin` by temporarily enabling `reportsDestination` and `metricsDestination` in `composeCompiler { ... }` (or via a Gradle property `-Pnightbrief.composeMetrics=true`):
   - Inspect `*-composables.txt` and `*-classes.txt` to see which composables in `app.nightbrief.app.ui.*` are currently marked `skippable` vs `unskippable` and which classes are marked `unstable`.
3. Only add types to `compose-stability.conf` that are **genuinely deeply immutable** (`java.time.Instant`, `java.time.LocalDate`, `java.time.LocalTime`, `java.time.ZoneId`, `java.time.Duration`, plus verified immutable `val`-only `core-*` data classes that never expose `MutableList`/`MutableMap`!).

### Recommended Implementation Plan
1. Add an opt-in Gradle property (`-Pnightbrief.composeMetrics=true`) in `app/build.gradle.kts` to dump Compose compiler metrics/reports to `build/compose_compiler` without affecting normal builds.
2. Audit `*-classes.txt` and `*-composables.txt` to identify every genuinely unstable parameter causing unskippable composables or unstable lambda captures.
3. Add `compose-stability.conf` declaring verified immutable external types:
   ```text
   // java.time immutable value types
   java.time.Instant
   java.time.LocalDate
   java.time.LocalTime
   java.time.ZoneId
   java.time.Duration
   // Verified immutable core-* models (only include classes audited to hold no mutable state!)
   ```
   Wire it in `app/build.gradle.kts` and `wear/build.gradle.kts` via `composeCompiler { stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf")) }`.
4. Re-run Compose compiler metrics and record the before/after counts of stable classes and skippable composables in the PR description.

### Constraints & Pitfalls to Avoid
- **NEVER declare a class with `var` properties or backed by a `MutableList`/`MutableSet` as stable** in `compose-stability.conf` — doing so causes stale UI bugs where Compose skips recomposing when mutated state changes!
- Do **not** apply `kotlin.plugin.compose` to `:core-*` modules (they must remain pure JVM per `AGENTS.md`).
- Keep zero new runtime dependencies.

### Validation & Acceptance Criteria
- Document before/after Compose compiler report metrics (`skippable` %, `stable` classes) in the PR.
- All unit, Robolectric, and Paparazzi tests pass cleanly:
  ```bash
  NIGHTBRIEF_SKIP_LIVE_TESTS=1 ./gradlew :app:assembleRelease :wear:assembleRelease :app:verifyPaparazziDebug :wear:verifyPaparazziDebug
  ```

---

## PERF-A11 — Add Baseline Profiles and `androidx.profileinstaller` for cold start and scroll AOT

- **Impact Rank:** #11
- **Priority:** P3 / Low-Medium (measure first; requires catalog dependency addition)
- **Category:** Cold Startup (TTID/TTFD) & First-Scroll AOT Compilation
- **Modules:** `:app` (and optional `:baselineprofile` benchmark module)
- **Labels:** `performance`, `startup`, `baseline-profile`, `build`
- **Relation to `POLISH_FINDINGS.md`:** Aligns with `POLISH_FINDINGS.md:674-694` ("An app/library baseline profile is present in the built artifacts, but there is no app-owned macrobenchmark suite. Generating or expanding baseline profiles should follow journey measurements, not substitute for them.").

### Problem & Impact
`:app` currently does not declare `androidx.profileinstaller` or an app-owned `app/src/main/baseline-prof.txt`. On cold install or after an APK update (including NightBrief's GitHub sideload updater), ART must JIT-compile app-specific hot paths — `AppGraph` initialization, `AppState` kotlinx.serialization decode, `NightEphemeris` + `NightScoreEngine`, `TonightScreen` + `Haze` glass + `NightDetail` composition — during the user's first sessions, causing higher cold TTID and first-scroll frame spikes until cloud profiles or idle dexopt run.

### Affected Code
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- New (when generated): `app/src/main/baseline-prof.txt` (and optional `:baselineprofile` module)

### Investigation Steps for Implementing Agent
1. **Confirm approval for dependency catalog addition** (`AGENTS.md`: *"Dependencies stay on the pinned late-2024 catalog unless the task is a bump"*). Adding `androidx.profileinstaller` (and `androidx.benchmark:benchmark-macro-junit4` + `androidx.baselineprofile` plugin if generating automatically) modifies `gradle/libs.versions.toml` and `settings.gradle.kts`.
2. Inspect the current release APK's `assets/dexopt/baseline.prof` (`unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep baseline`) to confirm only library profiles (Compose/Lifecycle) are packaged today and zero `app/nightbrief/*` classes are profiled.
3. Capture baseline cold-start TTID and Tonight-scroll `FrameTimingMetric` numbers on a real physical Android device (API 31+, release/non-debuggable build) before adding profiles — emulator software rendering is **not** valid for frame/AOT benchmarking.

### Recommended Implementation Plan
1. Add `androidx.profileinstaller` to `gradle/libs.versions.toml` and `implementation(libs.androidx.profileinstaller)` to `app/build.gradle.kts`.
2. Create a `:baselineprofile` test module (using `androidx.baselineprofile` plugin) covering the critical user journeys with deterministic local state/cache:
   - Cold startup to populated Tonight screen
   - Scrolling Tonight (including Haze glass detach/reattach and expanded Timeline)
   - Switching tabs Tonight → Week → Sites → Gear → Tonight
   - Opening Week → Night Planner and stepping 2 nights
3. Generate `app/src/main/baseline-prof.txt` on a rooted/userdebug API 33+ device or Gradle Managed Device, commit the generated `baseline-prof.txt`, and verify `assets/dexopt/baseline.prof` in the release APK includes `Lapp/nightbrief/` rules.
4. Benchmark before/after (`CompilationMode.None()` vs `CompilationMode.Partial(BaselineProfileMode.Require)`) over 5+ iterations on a physical device and record median + spread.

### Constraints & Pitfalls to Avoid
- Do **not** commit hand-guessed `baseline-prof.txt` rules without running `profgen` / Macrobenchmark verification.
- Keep `ci.yml` fast — baseline profile generation runs manually/on-demand, not inside standard PR CI.
- Verify release APK size delta (usually <30 KB) and ensure `app/proguard-rules.pro` + `PlayReleaseManifestTest` remain green.

### Validation & Acceptance Criteria
- `unzip -l app-release.apk` shows `assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm`.
- Macrobenchmark startup + frame-timing comparison shows measurable median TTID / P90 frame-time improvement with `BaselineProfileMode.Require` vs `CompilationMode.None()`.
- Full CI test suite remains green.

---

## Appendix: Open Performance Items Already Tracked in `POLISH_FINDINGS.md`

For completeness, three additional lower-priority or watch-specific performance items remain open in `POLISH_FINDINGS.md` and are **not** duplicated above:

- **`POLISH_FINDINGS.md` → `PERF-06` (P2):** `app/src/main/kotlin/app/nightbrief/app/ui/tonight/whimsy/Motion.kt:20-58` walks `Throwable().stackTrace` on production devices to detect Paparazzi and reflectively calls hidden `ValueAnimator.getDurationScale` (`DiscouragedPrivateApi` lint warning).
- **`POLISH_FINDINGS.md` → `PERF-07` (P2):** `wear/src/main/kotlin/app/nightbrief/wear/WearGlanceLoader.kt:14-41` and `NightBriefTileService.kt:14-37` block the single tile executor on a full network-backed briefing via `runBlocking` after a 3-second phone-data miss.
- **`POLISH_FINDINGS.md` → `PERF-08` (P3, measure before changing):** `app/src/main/kotlin/app/nightbrief/app/ui/NightBriefNavHost.kt:361-455` keeps visited hidden tabs composed and measured for instant tab switching. Do **not** remove retained tabs without macrobenchmark evidence.
