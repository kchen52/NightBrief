# NightBrief Red-Team Findings

Review performed from `main` at `42c23726ce20ef05646b5f4326df16771c8f995d`, equal to the local `origin/main` tracking ref at review time. Remote freshness was not checked during the audit. During PR preparation, `origin/main` was fetched and still pointed to that same commit. The only worktree item at review time was the supplied, untracked `RED_TEAM_SPEC.md`.

This was a source-only review. No builds or tests were run, no live providers were contacted, and no runtime reproductions were performed. These results do not establish that the project is secure or that review coverage was exhaustive.

## Confirmed findings

### [LOW] Malformed forecast data can bypass an eligible stale-cache fallback

**Confidence:** High

**Area:** `core-weather` / forecast parsing and cache fallback

**Affected code:** `core-weather/src/main/kotlin/app/nightbrief/weather/OpenMeteoClient.kt:69`; `core-weather/src/main/kotlin/app/nightbrief/weather/ForecastRepository.kt:85`

**Summary:** A structurally malformed but valid JSON response can throw a general parsing exception. The repository's stale-cache fallback handles only `WeatherApiException`, so it can skip a usable cached forecast for that site.

**Preconditions:** A response contains a non-primitive value in `hourly.time`, and a cached forecast for that location is less than 48 hours old.

**Evidence:** Root JSON parsing is wrapped at `OpenMeteoClient.kt:43-47`, but each `hourly.time` element is accessed with `t.jsonPrimitive` at line 70 outside that catch. `ForecastRepository.forecast` catches only `WeatherApiException` at lines 89-94. The README documents use of an eligible cache when a forced fetch fails.

**Reproduction:**
1. Seed a cache entry for synthetic coordinates with a forecast less than 48 hours old.
2. Supply valid JSON with an object inside `hourly.time`, such as `[{}]`.
3. Call `ForecastRepository.forecast` for those coordinates.
4. The parsing exception escapes instead of returning the cached forecast as stale. This was reasoned from source and was not run.

**Expected:** A failed fetch with an eligible cached forecast returns it with `ForecastStatus.STALE`.

**Actual:** This response shape can throw outside the handled exception type, so the site may be briefed without forecast data.

**Impact:** A malformed response can remove forecast-based scoring for one site despite a recent fallback being available.

**Fix direction:** Ensure malformed response shapes enter the stale-cache failure path.

**Regression check:** With a recent cached forecast, supply a non-primitive `hourly.time` element and verify that the repository returns the cached forecast as `STALE`.

### [LOW] Prefetch can swallow cancellation during forecast fetching

**Confidence:** High

**Area:** `:work` / `PrefetchWorker`

**Affected code:** `work/src/main/kotlin/app/nightbrief/work/Workers.kt:102-106`

**Summary:** `runCatching` surrounds a suspending forecast call and catches `CancellationException`. A cancelled prefetch can continue counting failures and reach the retry branch instead of propagating cancellation promptly.

**Preconditions:** WorkManager or the OS cancels a prefetch while it is fetching forecasts.

**Evidence:** The loop wraps `graph.forecasts.forecast(...)` in `runCatching` and counts every failure at line 104; if all sites fail, it returns `Result.retry()` at line 106. `AGENTS.md` requires rethrowing `CancellationException` because swallowing it breaks coroutine cancellation.

**Reproduction:**
1. Configure a local synthetic `ForecastSource` to throw `CancellationException` during prefetch.
2. Run `PrefetchWorker.doWork()`.
3. Source behavior predicts the exception is counted as a forecast failure and the loop may reach the retry branch. This was not run.

**Expected:** Cancellation propagates and the worker stops promptly.

**Actual:** Cancellation is caught as a fetch failure; the worker continues through the loop and may return retry.

**Impact:** Cancelled work may perform more processing and schedule a retry, consuming resources and delaying shutdown.

**Fix direction:** Preserve cancellation when handling forecast failures.

**Regression check:** Make a synthetic forecast source throw `CancellationException` and verify that prefetch propagates it rather than treating it as a failed forecast.

## Potential issues that need validation

- **NaN input to `NightScoreEngine`:** The engine can throw when a caller supplies `Double.NaN` for moon altitude: the value can flow into factor points and then `roundToInt()`. The input type does not validate finiteness. Whether any app-reachable astronomy or weather path can produce such an input was not established, so this is unconfirmed. Relevant code: `core-score/src/main/kotlin/app/nightbrief/score/NightScoreEngine.kt:110-131` and `:167-170`.
- **Unbounded settings import read:** The import flow reads the selected document fully into memory and decodes it synchronously, with no visible size limit. The practical size or nesting threshold for a user-visible failure was not verified. Relevant code: `app/src/main/kotlin/app/nightbrief/app/ui/settings/SettingsScreen.kt:85` and `data/src/main/kotlin/app/nightbrief/data/LibraryTransfer.kt:30`.
- **Coordinate privacy disclosures:** Source review found coordinates sent to forecast and time-zone providers, but did not verify whether external distribution disclosures explain this. No confirmed issue was established from the repository alone.

## Scopes reviewed with no findings identified

- Android entry points, manifests, receivers, notification intents, and site selection from intents.
- Local settings, persistence, import/export, recovery, and onboarding drafts.
- Location permissions and coordinate flows, including notification, widget, and Wear surfaces.
- Build and release configuration: dependency versions are pinned in the catalog, repository mode is centrally enforced, and the app release build enables minification. No confirmed issue identified in this scope.

## Coverage gaps and limitations

- All reviews were source-only; no builds or tests were run, and no live providers were contacted.
- No device or emulator checks were performed for intent handling, backup behavior, approximate-only location, or lock-screen privacy.
- The existing digest worker test was not run because it may contact live providers.
- External privacy disclosures were not checked.
