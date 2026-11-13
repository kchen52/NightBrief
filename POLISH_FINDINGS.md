# NightBrief UI and performance polish findings

Reviewed `main` at **`54e465d0906fb102f97d2be5370f3a874c80f061`**, after pulling `origin/main`.
The review covers the phone Compose UI, navigation and responsive layouts, map/forms/settings, share and print,
briefing construction, workers, the Glance widget, and the Wear screen/tile.

This is an implementation handoff, not a request to redesign the product. **No production source files were
changed during the review.**

## Confidence and priorities

- **Confirmed** means the code path or build output establishes the finding. It does not mean a frame-time or
  battery impact was measured on a device.
- **Measured build result** means a local build supplied the stated number.
- **Validate** means the implementation must be checked with a device trace, accessibility test, or a stated
  regression test before claiming a win.

Priorities:

- **P1 — do first:** clear correctness/accessibility problems or duplicated work with broad reach.
- **P2 — next:** bounded UX and efficiency improvements with lower reach or impact.
- **P3 — measure before structural work:** plausible costs where a rewrite would otherwise be speculative.

User-visible correctness fixes do not need to wait for the profiling harness. Performance work should retain
before/after numbers rather than claim that source inspection alone proves jank or energy savings.

## Recommended implementation order

1. **Small correctness batch:** UI-01, UI-02, UI-04, UI-07 — addressed in
   [#130](https://github.com/kchen52/NightBrief/pull/130).
2. **Profiling baseline plus bounded performance fixes:** PERF-01 through PERF-04, with call counts and traces.
3. **Interaction and adaptive-UI batch:** UI-03, UI-05, UI-06, UI-08, UI-09.
4. **I/O and packaging batch:** PERF-05 and PKG-01.
5. **Only after measurements:** PERF-08 and PERF-09. Do not start with a Compose/navigation rewrite.

## Implementation status

The first batch is implemented in
[#130](https://github.com/kchen52/NightBrief/pull/130): UI-01, UI-02, UI-04, and UI-07. Each section
below records its resolution. The remaining findings are unchanged.

## Highest-value findings at a glance

| ID | Priority | Finding | Status |
| --- | --- | --- | --- |
| UI-01 | P1 | Hero verdict looks and acts clickable but its callback is empty | Confirmed · addressed in #130 |
| UI-02 | P1 | Events can open a different site from the one used to compute them | Confirmed · addressed in #130 |
| UI-03 | P1 | Several custom controls have undersized targets or incomplete semantics | Confirmed code; run TalkBack |
| PERF-01 | P1 | Widget/Wear/app paths independently rebuild the same briefing | Confirmed duplicated path |
| PERF-02 | P1 | Seven-night briefings enrich and parse products that Week discards | Partly addressed: Tonight-only enrichment and one comet parse per brief; step 3 (lean outlook planner) left open |
| PERF-03 | P1 | Loading and Events calculate ephemeris during composition | Addressed: sky reads run off composition with loading/error states |
| PERF-04 | P1 | Darker-sky search does full candidate planning and unnecessary serial work | Addressed: lean cancellable pipeline on `Dispatchers.Default` with parallel naming; duration not benchmarked end-to-end |
| PKG-01 | P2 | Unsigned Wear release is 30.92 MiB, mostly unminified DEX | Measured build result |
| PERF-08 | P3 | Retained hidden tabs are still measured and composed | Cost present; benefit of change unproven |
| PERF-09 | P3 | Tonight eagerly composes its expanded timeline below the viewport | Cost present; benefit of change unproven |

---

# UI and UX findings

## UI-01 — Replace the no-op verdict chip with a non-interactive badge

**Priority:** P1  
**Status:** Addressed in [#130](https://github.com/kchen52/NightBrief/pull/130). The verdict is a
non-interactive `Surface` that keeps the chip's layout and paints. `NightDetailQuietUiTest` asserts it
has no click action while the window reminder still does.

**Evidence:** `app/src/main/kotlin/app/nightbrief/app/ui/tonight/NightDetail.kt:399-406`

`HeroCopy` renders the verdict/band in an `AssistChip(onClick = {})`. It has a border, tint, click ripple, and
click semantics, suggesting it performs an action, but tapping it does nothing. This is not the same as the
imaging-mode text underneath it, which is correctly non-interactive.

**Recommendation:** render a non-interactive `Surface`/badge with the same visual treatment. Do not invent a new
screen or a new imaging-mode behavior merely to give the chip a purpose. Remove its click action from semantics.

**Acceptance:** the verdict has no `hasClickAction()` semantics and no ripple; the text, colours, and goldens are
otherwise preserved. Add a regression test that distinguishes this badge from the real window-reminder chip.

## UI-02 — Carry the event's site ID into the planner

**Priority:** P1  
**Status:** Addressed in [#130](https://github.com/kchen52/NightBrief/pull/130). `EventsScreen` passes
the producing primary site's id through `onOpenNight`, and `EventsNavigationTest` selects a different
site to prove an event still opens the primary site's planner.

**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/ui/events/EventsScreen.kt:59-64, 77`
- `app/src/main/kotlin/app/nightbrief/app/ui/NightBriefNavHost.kt:279-288`

`EventsScreen` computes events using `book.primary`. When an event is opened, however, the nav callback uses
`vm.selectedSiteId.value ?: primaryId`. A user can select a non-primary site in Week, open Events, see the
primary site's events, then tap one and land on the other site's planner.

**Recommendation:** pass `(siteId, date)` from `EventsScreen` through `onOpenNight`, using the exact `site` that
produced the events. Do not clear the global site selection as a workaround.

**Acceptance:** with primary A and selected B, Events displays A's time zone/events and an event tap opens A's
planner for that exact date. Add a UI test; the existing tests only exercise `EventsList`, not this navigation.

## UI-03 — Normalize touch targets and expose meaningful state/value semantics

**Status:** Addressed in this change.

**Priority:** P1  
**Evidence:**

- Collapse header: `app/src/main/kotlin/app/nightbrief/app/ui/common/Components.kt:103-145` (`heightIn(min = 32.dp)`).
- Why control: `app/src/main/kotlin/app/nightbrief/app/ui/tonight/NightDetail.kt:703-729` (20 dp icon and only
  a small top padding).
- Moon long-press target: `app/src/main/kotlin/app/nightbrief/app/ui/tonight/whimsy/AliveHeroCard.kt:112-124`
  (36 dp).
- Session rating targets: `app/src/main/kotlin/app/nightbrief/app/ui/tonight/SessionCard.kt:55-66` (44 dp).
- Glass action container: `app/src/main/kotlin/app/nightbrief/app/ui/common/Glass.kt:370-379` (44 dp).
- Site selection: `app/src/main/kotlin/app/nightbrief/app/ui/common/Components.kt:263-315` uses `clickable`
  without selection semantics.
- Week progress: `app/src/main/kotlin/app/nightbrief/app/ui/week/WeekScreen.kt:278-284, 291-305` maps a null
  score to `0f` while the adjacent score pill is `—`.
- Generic score pill: `app/src/main/kotlin/app/nightbrief/app/ui/common/Components.kt:217-227` exposes only a
  bare number, not what that number means.

Several custom targets are below the usual 48 dp minimum. The site switcher also gives no semantic selected
state, and Week's decorative bar can expose 0% progress for an unavailable score rather than no score.

**Recommendation:**

1. Give interactive rows/containers at least 48 dp touch bounds without enlarging every visual glyph.
2. Use `selectable(selected = …, role = Role.Tab)` or equivalent selected semantics for site chips.
3. Make decorative progress indicators clear their semantics; put a meaningful night label on the clickable row.
4. Let `ScorePill` accept an accessible label/caption, such as score out of 100 or unavailable. Do not rely on
   colour to convey the band.
5. Keep the existing TalkBack content descriptions required by current tests.

**Acceptance:** inspect actual touch bounds, not just visible icons. Assert chip selection with `isSelected()`,
assert unavailable nights never announce a zero score/progress, and run TalkBack through a populated Week and
Tonight. Preserve horizontal-drag behavior in the site and hour strips.

## UI-04 — Add a real retry path when Week cannot provide an outlook

**Priority:** P1  
**Status:** Addressed in [#130](https://github.com/kchen52/NightBrief/pull/130). Week renders error,
loading, and unavailable states with a Retry that calls `vm.refresh(force = true)`, keeps cached rows
visible, and disables Retry while loading. `WeekRetryTest` covers a failed first load, a missing
outlook, retry success, and cached data after a failed refresh.

**Evidence:** `app/src/main/kotlin/app/nightbrief/app/ui/week/WeekScreen.kt:76-92, 112-200, 222-250`; compare
Tonight's refresh action at `app/src/main/kotlin/app/nightbrief/app/ui/tonight/TonightScreen.kt:116-120` and its
error banner at `:165`.

Week has no manual refresh action. A non-null `ui.error` is not rendered there, and a missing/null outlook produces
little beyond the site switcher and loading state. A per-site forecast failure can instead leave a headline with
no next action. The user must know to switch to Tonight and tap Refresh.

**Recommendation:** give Week a common loading/error/empty state with a Retry action calling the existing
`vm.refresh(force = true)`. Do not add new error text to core scoring and do not fetch from the composable.
Disable/debounce a forced refresh already in progress to avoid cancelling/restarting it on repeated taps.

**Acceptance:** test cold-start failure, offline-with-cache, missing outlook, and retry success. Cached scores
should remain visible while refreshing; a refresh failure must not turn a usable cached report into a blank page.

## UI-05 — Make narrow-window and large-font layouts intentional

**Status:** Addressed in this change.

**Priority:** P2  
**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/ui/WindowForm.kt:126-135` uses `Configuration.screenWidthDp/HeightDp`;
  lint flags both (`ConfigurationScreenWidthHeight`). These can describe more than the app's actual multi-window
  container.
- `app/src/main/kotlin/app/nightbrief/app/ui/common/Glass.kt:229-255, 331-348` assumes four fixed 80 dp tabs
  (plus padding), exceeding a roughly 328 dp content width before large-font considerations.
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/DarkerSky.kt:163-185` puts three access chips in a
  non-wrapping `Row`.
- `app/src/main/kotlin/app/nightbrief/app/ui/settings/SettingsScreen.kt:678-709` fixes pairs of custom-target
  fields in horizontal rows.

**Recommendation:** use the actual window/container size for `WindowForm`; size tab items to the available bar
width and derive the droplet from measured positions rather than the constant; use `FlowRow` or a vertical
fallback for chips and forms. Keep fold/hinge behavior and the existing landscape-Lead layout. Any bottom-bar
change needs new goldens, but must not introduce a tab crossfade.

**Acceptance:** at minimum cover 280–320 dp app widths, portrait and landscape, font scales 1.0/1.5/2.0, and one
unfolded foldable. Check no clipped tabs, overlapping fields, hidden actions, or accidental whole-tab swipes.
Current golden tests largely use default font scale, and the connected 1.5 test checks only a small set of labels.

## UI-06 — Show share/print progress and failure feedback; close the bitmap failure leak

**Status:** Addressed in this change.

**Priority:** P2  
**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/ShareNightPlanButton.kt:28-70`
- `app/src/main/kotlin/app/nightbrief/app/share/NightPlanSharer.kt:52-75, 83-94`
- `app/src/main/kotlin/app/nightbrief/app/share/NightPlanPrinter.kt:57-72`

The first two paths already render off the main thread, which is good. Once Share or Print is chosen, the dialog
closes and only the disabled icon signals work. A failed PNG write or print start has no user feedback. In
`NightPlanSharer.writePng`, an `IOException` between render and line 62 bypasses `bitmap.recycle()`.

**Recommendation:** show a labelled preparing indicator, prevent duplicate actions, return a small success/failure
result to the UI, and present a snackbar on failure. Wrap ownership of each bitmap in `try/finally`. Streamlining
print's intermediate RGB/PDF buffers is a separate allocation-profile task, not a required part of this UX fix.

**Acceptance:** use a fake/failed writer to test preparing, success, failure, and repeated taps. Confirm the bitmap
is recycled on success and every failure after acquisition. Verify the system share sheet and print preview on a
physical device.

## UI-07 — Do not cache the old locale forever; move remaining UI copy to resources

**Priority:** P2  
**Status:** Addressed in [#130](https://github.com/kchen52/NightBrief/pull/130). `Format` caches
formatters by locale, and the Moon, session, and custom-target copy moved to `strings.xml`.
`FormatLocaleTest` verifies `en-US` and `de-DE` output without restarting the process.

**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/ui/common/Format.kt:15-24, 40-50`; lint reports `ConstantLocale` at
  line 17.
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/whimsy/AliveHeroCard.kt:81, 118` hardcodes TalkBack and
  long-click English copy.
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/SessionCard.kt:48-51, 124-127` concatenates `"scored …"`.
- `app/src/main/kotlin/app/nightbrief/app/ui/settings/SettingsScreen.kt:737-739` creates a visible custom-target tip
  from a hardcoded sentence.

The formatter object is initialized once and can keep the old locale after a configuration/locale change. The
hardcoded examples bypass the documented string-resource policy, including accessibility copy.

**Recommendation:** resolve/cache formatters by the current locale (or create them at use sites), and move app-owned
copy to `strings.xml` with placeholders and suitable plurals. Do not try to move core-only target/interest labels
into Android resources. This is not a proposal to translate the entire app in the same change.

**Acceptance:** change locale without killing the process and verify date/time/month text updates. Unit-test at
least two locales, including a 12/24-hour distinction and a language with different grammar.

## UI-08 — Replace inferential loading indicators with accurate states

**Priority:** P2  
**Status:** Addressed.

**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/ui/sites/MapPicker.kt` initializes `tilesLoading = true` and clears it
  only on a scroll or zoom. That is a gesture signal, not tile completion; an untouched loaded map can retain the
  message while a first gesture can dismiss it too early.
- `wear/src/main/kotlin/app/nightbrief/wear/WearActivity.kt` shows an empty dark box while the phone read and
  possible full local briefing are in flight.
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/DarkerSky.kt` has searching UI but no cancel action;
  `AppViewModel.clearDarkerSky` had no production caller.

**Recommendation:** either observe a real osmdroid loading/completion signal or remove the misleading map message;
show a small Wear loading state alongside the existing dark background; expose Cancel for darker-sky search and
actually cancel its coroutine. Do not replace the map failure behavior that still permits a manual pin.

**Resolution:** The darker-sky searching row now exposes Cancel and `clearDarkerSky` is wired from Tonight; a
cancelled or replaced search does not publish stale results (implemented and tested as part of PERF-04). The
site-picker map no longer shows the gesture-based "Loading map tiles…" message, avoiding a stuck or premature
label when there is no reliable completion signal. The watch shows a small loading state over the dark
background while it reads the phone snapshot or falls back to a local brief.

**Verification:** `DarkerSkyUiTest` covers the Cancel control and finished/no-callback states. `:app` and `:wear`
unit suites pass. Manual device checks are still needed for map ready-before-touch/slow or offline tiles and for a
slow Wear local load. Map failure behavior that permits a manual pin is unchanged.

## UI-09 — Handle an imported zero-site library with a useful empty state

**Status:** Addressed in this change (rejected with a specific error).

**Priority:** P2  
**Evidence:**

- `data/src/main/kotlin/app/nightbrief/data/LibraryTransfer.kt:30-52` accepts a valid empty `SiteBook` without
  resetting onboarding.
- `app/src/main/kotlin/app/nightbrief/app/ui/week/WeekScreen.kt:80-81` returns before its Scaffold when no
  primary exists.
- `app/src/main/kotlin/app/nightbrief/app/ui/events/EventsScreen.kt:59-61` likewise returns early.

Normal Sites UI prevents deleting the last site (`SitesScreen.kt:172`), so that alone does not create this state.
The supported import path does: onboarding remains complete with zero sites, and Week/Events become blank.

**Recommendation:** reject an empty library with a specific validation error **or** support it consistently with a
branded empty state and an Add site action. The latter should route to Sites/editor rather than leaving blank
content. Decide this behavior explicitly rather than partially handling it per screen.

**Acceptance:** if rejecting, show a specific import error and leave the current library intact. If accepting,
rotate and navigate Week/Events/Tonight/Sites with an imported empty library. No background worker should fetch,
and the previous site's cached report must not appear under a new empty/null site.

## UI-10 — Complete small Wear setup/discovery details

**Priority:** P3  
**Evidence:** Wear lint reports `TilePreviews` and `WearRecents` for `wear/src/main/AndroidManifest.xml:22-38`.

The tile service has no preview metadata/assets, and the watch activity has no task affinity for correct Recents
presentation. These are bounded platform polish gaps, not tile-rendering defects.

**Recommendation:** add the current AndroidX tile preview declaration and representative preview asset, and follow
the Wear Recents `taskAffinity` guidance. Keep standalone-phone data behavior unchanged.

**Acceptance:** run Wear lint and inspect tile addition/preview plus Recents on a watch or emulator. Add/update the
preview only; do not record an unrelated screenshot golden just to satisfy metadata.

---

# Performance findings

## PERF-01 — Publish one briefing result to secondary surfaces instead of rebuilding it

**Priority:** P1  
**Evidence:**

- App startup publishes Wear immediately: `app/src/main/kotlin/app/nightbrief/app/NightBriefApp.kt:13-16`.
- The ViewModel independently computes the saved-site briefing:
  `app/src/main/kotlin/app/nightbrief/app/AppViewModel.kt:94-122`.
- Wear computes another primary briefing: `app/src/main/kotlin/app/nightbrief/app/wear/WearPublisher.kt:50-68`.
- Widget updates compute a primary briefing again:
  `app/src/main/kotlin/app/nightbrief/app/widget/NightBriefWidget.kt:29-59`.
- A widget request then enqueues Wear again:
  `app/src/main/kotlin/app/nightbrief/app/widget/NightBriefWidgetReceiver.kt:18-29`.
- Digest and prefetch request those widget refreshes:
  `work/src/main/kotlin/app/nightbrief/work/Workers.kt:108-113, 199-200`.

This differs from the documented boundary in `AGENTS.md:62`, which says the widget reads the briefing cache and
does not score on its own. Forecast HTTP calls already have caching/single-flight, so duplication does **not**
always imply three network calls. Ephemeris, scoring, week/plan setup, parsing, and publication work can still be
repeated. A cold app start can run the Wear primary brief alongside the ViewModel brief; a worker/widget/Wear chain
can rescore the same primary again.

**Recommendation:** make the result of a successful briefing available as a small current snapshot. App startup,
ViewModel, and workers should publish/update it; widget and Wear should render from it. A cold process with no
snapshot may compute a fallback, but should not do duplicate foreground work once another owner is loading the
same input. Keep widget and Wear copy generation in shared core logic.

Implementation cautions:

- Include site identity/content, kit, custom targets, forecast generation, and date/night key in cache validity.
- Invalidating a settings change must not surface the previous site's name/score under the new key.
- A forced refresh must not accidentally reuse an older result as fresh.
- If adding concurrent request sharing, do not let one UI caller's cancellation cancel an independent worker's
  shared load. `ForecastRepository.kt:126-138` currently executes the owner load in its caller's coroutine.
- Preserve offline/stale labels and the rule that Wear failure cannot prevent widget refresh.

**Acceptance:** fake-source call-count tests demonstrate one computation with warm secondary consumers and one
fallback only when cold. Trace cold app start, three-hour prefetch, morning digest, site edit, and Wear failure.
Assert score/band/verdict and widget/Wear fixtures are unchanged.

## PERF-02 — Skip optional enrichment for Week-only nights and parse comets once

**Priority:** P1  
**Status:** Partly addressed.

**Evidence:** `core-score/src/main/kotlin/app/nightbrief/score/BriefingService.kt`.

Every one of seven nights for every site receives `.withAurora`, `.withIss`, `.withSmoke`, and `.withComet`.
For every such night, `withComet` reparses the same MPC text. Week reduces these reports to `OutlookNight`, which
uses none of those four optional products. `NightNames.clearSkies` reads only score/cloud (`NightNames.kt:86-90`);
other Week naming fields are Moon phase, meteor, and sky events (`NightNames.kt:36-45`). Thus this discard is
established by the current data model, not inferred from visuals.

`NightPlanner.plan` also builds a full timeline, dew outlook, and cloud-layer explanation for future nights
(`NightPlanner.kt`) even though the `OutlookNight` reducer does not retain them. This second layer needs a
more careful API change than the first.

**Recommendation:**

1. Apply Tonight-only aurora/ISS/smoke/comet enrichment only to the report placed in `Briefing.tonight`.
2. Parse the non-null comet file once per `brief` call and reuse the parsed orbits for each site's Tonight report.
3. If traces remain material, introduce a shared reduced-night/outlook planner that avoids future timelines,
   dew, and cloud-layer copy without duplicating scoring rules.

Do not use Settings' ISS visibility switch to change the fetch contract; the current specification intentionally
keeps ISS data in briefings even when the card is hidden.

**Resolution:** Steps 1 and 2 are implemented. `BriefingService` enriches only the Tonight (offset 0) report;
future nights are planned but not given aurora, ISS, smoke, or comet data because `OutlookNight` discards them.
A non-null comet file is parsed once per `brief` (and once per `plan`) through an injectable parser, and the
parsed orbits are reused for Tonight.

**Verification:** `BriefingEnrichmentTest` proves one comet parse per multi-site, multi-night brief, identical
outlook scores/titles/flags with and without side products, and that a comet parse failure drops the comet card
without failing the briefing or score. The existing `:core-score` suite and app/work/data/wear unit suites pass.
On a SM-F731W (Z Flip 5), a saved-site refresh logged `brief done sites=1 force=false took=1015ms` and the Week
outlook rendered normally.

**Still open:** Step 3 (a reduced outlook planner that avoids future timelines, dew, and cloud-layer work) was not
implemented. PERF-01 remains open. No score weights, gates, bands, verdicts, or fixtures changed.

## PERF-03 — Move remaining ephemeris calculation out of composition

**Priority:** P1  
**Status:** Addressed.

**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/TonightScreen.kt` runs `NightEphemeris.compute` in a
  `remember` block during the initial loading composition.
- `app/src/main/kotlin/app/nightbrief/app/ui/events/EventsScreen.kt` runs `SkyEvents.yearAhead` before its
  Scaffold can render, also inside `remember`.

`remember` avoids repetition, not the first main-thread computation. The year-ahead path is particularly likely
to delay feedback after the Events action, but no frame-time claim can be made without a trace.

**Recommendation:** load these through the ViewModel or `produceState` on `Dispatchers.Default`, with explicit
loading/error/empty states. Reuse the same ephemeris/briefing output when it is already available instead of
calculating a second model in the UI. Inject a `Clock` where practical so loading logic is testable across dates.

**Resolution:** `LoadingSky` now computes tonight's ephemeris through `produceState` on `Dispatchers.Default`
(via `AppViewModel.loadingEphemeris`, clocked) and shows a loading line until it returns. `EventsScreen` loads
the year ahead through `produceState` off composition, with loading, error/retry, and empty states; the load is
cancellable on exit and restartable on retry.

**Verification:** `LoadingSkyTest` proves the sunset/darkness lines appear only after the off-frame read returns.
`EventsLoadingTest` proves the first frame renders without waiting for the scan, a failed scan offers Retry and
recovers, and leaving the screen cancels the in-flight load. On a SM-F731W, Week opened the Sky events screen and
rendered the year-ahead list. Time-to-interactive was not macrobenchmarked.

## PERF-04 — Give darker-sky search a cancellable, off-main, lean pipeline

**Priority:** P1  
**Status:** Addressed.

**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/AppViewModel.kt` launches in `viewModelScope` and invokes
  `DarkSkyFinder.search` without the `Dispatchers.Default` wrapper used by normal refresh.
- `core-score/src/main/kotlin/app/nightbrief/score/DarkSkyFinder.kt` performs up to five full
  `BriefingSource.plan` calls.
- Those plans fetch/enrich all side products and calculate `TargetAdvisor` suggestions, but candidate rows use the
  score/delta/access/name, not target suggestions or those cards.
- Up to five reverse-geocode calls then run serially.
- `clearDarkerSky` has no caller; see UI-08.

HTTP uses OkHttp enqueue and is not synchronous on the main thread. The issue is that scoring, comet parsing, JSON
processing, and other continuations can resume on the view-model/Main caller, plus the extra candidate work and
serial naming phase.

**Recommendation:**

1. Put the compute pipeline on `Dispatchers.Default`, retaining `Dispatchers.IO` for streamed grid reads.
2. Add a shared lean planning path for candidate scores that disables suggestions and unused optional products;
   do not copy the score engine into `DarkSkyFinder`.
3. Parallelize reverse geocoding with failure isolation.
4. Provide cancellation and ensure old jobs cannot publish into a new site's state.
5. Trace the stages (32 grid samples, candidate planning, road checks, naming) before considering additional API
   changes or concurrency increases.

**Resolution:** Darker-sky search now runs on `Dispatchers.Default`, with the streamed grid reads kept on
`Dispatchers.IO` inside `DarkSkyFinder`. Candidates are scored through `planLean` (forecast-only, no aurora, ISS,
smoke, comet, or target suggestions) instead of a full plan. Reverse geocoding runs in parallel with per-candidate
failure isolation, the searching row exposes Cancel, and a cancelled or replaced search does not publish into a
new site's state.

**Verification:** `DarkSkyFinderTest` proves the finder uses `planLean` and never the full `plan` path.
`DarkerSkyUiTest` covers the searching Cancel control and the finished/no-callback states alongside the existing
filter, save, and map behavior. On a SM-F731W, a search showed the Cancel control, completed with scored
candidates and access filters, and left the distance/Bortle labels intact when reverse geocoding failed (404).
Cold/warm duration and a main-thread trace were not measured; stage tracing remains follow-up work. Failure
isolation per candidate and the streamed Bortle grids are preserved.

## PERF-05 — Remove import/export and backup file I/O from the UI caller

**Priority:** P2  
**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/ui/settings/SettingsScreen.kt:101-115, 446-457` reads a whole document,
  decodes/exports JSON, and handles the resulting state on the UI callback path without a size bound.
- `app/src/main/kotlin/app/nightbrief/app/AppViewModel.kt:379-383, 400-407` invokes those operations from the
  ViewModel/Compose caller.
- `data/src/main/kotlin/app/nightbrief/data/SettingsRepository.kt:50-65, 76-85` performs `.good` backup encoding,
  temporary-file writes, and rename synchronously after `store.updateData` returns. `AppViewModel.mutate` starts
  in `viewModelScope`, so every switch/card collapse/nickname update can resume and do that backup on Main.

**Recommendation:** move bounded import reading/parsing and library export behind suspend ViewModel methods on
IO/Default, add an input-size limit and busy state, and serialize backup publication on IO without reordering
writes. Keep DataStore as the source of truth; do not open a second settings graph. Preserve restore semantics and
atomic temp-file behavior.

**Acceptance:** debug StrictMode (or an equivalent instrumented assertion) shows no main-thread reads/writes in the
covered actions. Test rapid updates, large/rejected document, process death during backup, corrupt-state restore,
and the nickname race described in `GearScreen.kt:365-377`.

## PERF-06 — Reduce motion detection's hidden API/reflection and production stack walking

**Priority:** P2  
**Evidence:** `app/src/main/kotlin/app/nightbrief/app/ui/tonight/whimsy/Motion.kt:20-58`.

`stillFrame()` remembers the result of `Throwable().stackTrace.any { … Paparazzi … }` on production devices too.
`motionAllowed()` also reflectively resolves `ValueAnimator.getDurationScale` on calls; lint reports
`DiscouragedPrivateApi`. Catching failure and allowing motion means an unavailable hidden method can defeat the
user's reduce-motion intent.

**Recommendation:** use supported animator-enabled APIs where available plus a settings fallback appropriate to
`minSdk 26`, and replace Paparazzi stack inspection with an explicit test/preview composition local or helper.
Preserve zero-duration behavior for screenshots and ensure changing animation scale takes effect without relying
on repeated hidden reflection.

**Acceptance:** lint no longer reports the private method; test normal scale, zero scale, Paparazzi still frames,
and API 26/current Android. Verify one-shot score, meteor, and aurora effects do not run in reduce-motion mode and
that production startup does not capture stack traces.

## PERF-07 — Avoid synchronous full-brief fallback in the Wear request path

**Priority:** P2  
**Evidence:**

- `wear/src/main/kotlin/app/nightbrief/wear/WearGlanceLoader.kt:14-41`
- `wear/src/main/kotlin/app/nightbrief/wear/NightBriefTileService.kt:14-37`
- `wear/src/main/kotlin/app/nightbrief/wear/WearData.kt:13-21`

The tile request correctly runs on a single background executor, so `runBlocking` here is **not evidence of a
phone main-thread violation**. Still, after a three-second phone-data wait/miss, local fallback can block that
executor on a complete network-backed briefing. The activity shows no interim content. This can delay tile/screen
feedback and repeats PERF-01-style work on devices where no phone snapshot is available.

**Recommendation:** return the latest local glance immediately where available and refresh asynchronously, or use
a coroutine-capable loader/future rather than nested `runBlocking`. Reuse the published snapshot whenever valid;
a standalone cold watch may need a fallback, but it should have clear loading/stale/error semantics.

**Acceptance:** measure phone-connected, phone-disconnected with local cache, cold standalone, and offline tile
requests. Ensure the single executor is shut down, DataItem buffers close, and stale text is never presented as
fresh. No blank multi-second Activity loading frame.

## PERF-08 — Profile retained hidden-tab measure/composition before changing navigation

**Priority:** P3 — **do not remove retained tabs by default**  
**Evidence:** `app/src/main/kotlin/app/nightbrief/app/ui/NightBriefNavHost.kt:361-455`.

Every seen page is measured in `measurables.map { it.measure(...) }`, including when a pushed screen makes
`active == null` and the outer layout becomes 0×0. Hidden pages remain composed offscreen, with semantics and
drawing suppressed. This is intentional warm-switch behavior and the comments explain earlier warm-switch work;
it is not an automatic defect.

**Question to answer:** at 1/2/4 visited tabs, how much time and memory do full-tab switches, opening Settings,
and global `AppState` updates spend measuring or recomposing hidden pages? Haze work is already gated by page
visibility, which is good.

**Measurement/acceptance:** capture Compose sections/layout passes and memory before any change. If material,
experiment with skipping hidden measurement only when lifecycle correctness requires it, while retaining local
page state and instant returns. Tests must cover pushes, predictive back, TalkBack isolation, touch hit testing,
fold/rail bounds, and Haze source ownership. No crossfade between retained tabs.

## PERF-09 — Measure Tonight's eager expanded timeline before introducing lazy layouts

**Priority:** P3  
**Evidence:**

- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/TonightScreen.kt:138-150` uses one eager vertical `Column`.
- `app/src/main/kotlin/app/nightbrief/app/ui/tonight/NightDetail.kt:901-1006` eagerly creates the fixed label
  column and every hour × every metric row when Timeline is expanded.
- `NightDetail.kt:229-235` eagerly emits all suggestion rows when Targets is expanded.

Collapsed content already leaves composition after animation, which limits the default cost. An expanded Timeline
can still allocate hundreds of cell composables below the viewport on every initial build, and all retained tab
content remains alive.

**Recommendation:** first capture cold/warm Tonight composition with all cards collapsed (user can persist this),
Timeline expanded, and many suggestions/custom targets. Only if material, lazify at section level or build the
hour grid incrementally with stable keys and frozen row labels. Do not undertake a blanket conversion of every
small Settings/Gear `Column` without an item-count problem.

**Acceptance:** compare first composition, scroll frame times, allocation/memory, semantics order, horizontal hour
state, fold transitions, and screenshots. Collapsed cards must remain gone from composition after exit.

## PERF-10 — Index air-quality samples once per series (small, bounded cleanup)

**Status:** Addressed in this change.

**Priority:** P3  
**Evidence:** `core-weather/src/main/kotlin/app/nightbrief/weather/AirQualityClient.kt:48-64` sorts and allocates an
epoch list on every `AirQualitySeries.at` call. `Smoke.assess` joins each dark hour
(`core-score/src/main/kotlin/app/nightbrief/score/Smoke.kt:39-52`). Current client results arrive sorted at
`AirQualityClient.kt:192`.

**Recommendation:** cache ordered samples/epochs once per immutable series, with a unit test for unsorted input and
the ±90-minute/tie behavior. PERF-02 removes most Week-only calls; this is a smaller follow-up, not a headline
optimization.

**Acceptance:** all existing `AirQualitySeries` and `SmokeTest` cases retain identical readings and diagnostics.

---

# Packaging finding

## PKG-01 — Enable Wear shrinking and evaluate phone resource shrinking

**Priority:** P2, measured build result  
**Evidence:** `app/build.gradle.kts:23-27` enables phone R8 but not resource shrinking;
`wear/build.gradle.kts:20-23` disables release minification. The app lacks `isShrinkResources = true`, also
reported by lint as `NotShrinkingResources`.

Local unsigned release artifacts from this exact `main` revision:

| Artifact | APK size | DEX detail |
| --- | ---: | --- |
| `app-release-unsigned.apk` | **5.35 MiB** | One minified `classes.dex`, about 4.22 MiB uncompressed |
| `wear-release-unsigned.apk` | **30.92 MiB** | Three stored DEX files, **28.76 MiB** |

The Wear number is a package-size fact, not a measured startup, memory, or battery claim.

**Recommendation:** enable release minification for Wear with the necessary serialization/tile/Play-services keep
rules, and enable resource shrinking in release builds after checking the relevant AGP behavior. Keep the app and
watch versions independent. Test an actual R8-minified watch artifact, not only Debug.

**Acceptance:** record before/after signed APK/AAB sizes; verify state JSON restore, tile rendering, local phone
fallback, Data Layer messages, and watch Activity launch. On phone, verify Bortle/water-mask assets, accessibility
strings, widget resources, map, sharing, and night-vision resources are not stripped. No bulk dependency upgrade is
needed for this task.

---

# Measure first: proposed performance baseline

NightBrief already has useful logs, so the answer is **not** “add generic logging before doing anything.”

Existing coverage:

- `data/src/main/kotlin/app/nightbrief/data/AppGraph.kt:50-58` logs HTTP host/path, rounded location, duration,
  and status under `NightBriefNet`.
- `app/src/main/kotlin/app/nightbrief/app/AppViewModel.kt:103-121, 138-146` logs total brief/plan duration and
  forecast status.

What those logs cannot explain is cache-versus-network time, CPU enrichment, duplicate surface callers, or frame
cost. Add a thin measurement layer around the existing structure.

1. **Macrobenchmark module with deterministic journeys.** Cover cold app startup, warm startup, Tonight scroll
   with glass, tab switching after all four tabs have been visited, Week → planner/date stepping, Events open,
   darker-sky search, and share rendering. Use local fixture/cache setup rather than live-provider variability for
   regression comparisons. Record startup and frame metrics; do not assert an arbitrary universal duration.
2. **Correlation and phase timing.** Add a short request/build identifier and caller (`ui`, `widget`, `wear`,
   `digest`, `prefetch`, `darker-sky`) to existing summaries. Measure cache hit/miss, forecast merge, per-night
   planning, optional enrichment, publish, and tile load. Preserve coarse/no exact coordinates in logs.
3. **Perfetto slices.** Instrument `brief`, `plan`, `outlook enrichment`, `darker-sky`, `settings backup/import`,
   `yearAhead`, share/print render, and widget/Wear load. Keep tracing cheap and avoid sensitive text.
4. **UI evidence.** Use Macrobenchmark/FrameTiming (and optionally JankStats during manual runs) on a physical
   device. Capture release-like/R8 behavior, not just Robolectric or an emulator with software Haze rendering.
   Compose compiler reports are available as a diagnostic if unstable/skipped inputs appear in the trace; they
   are not a KPI by themselves.
5. **Background evidence.** For PERF-01/PERF-02, compare fake-source call counts and WorkManager/Perfetto traces.
   Use Battery Historian only after a repeatable worker scenario exists; a one-off battery percentage is not useful.
6. **Reporting discipline.** For each optimization record device, API, build type, site count, warm/cold cache,
   animation/night-vision state, at five repeated runs and a median plus spread. Keep correctness tests separate
   from timing thresholds to avoid flaky CI.

An app/library baseline profile is present in the built artifacts, but there is no app-owned macrobenchmark
suite. Generating or expanding baseline profiles should follow journey measurements, not substitute for them.

# Test matrix for implementation agents

At minimum:

- **Phone states:** onboarding; one site and many sites; empty imported library; no forecast; cached/stale;
  partial/beyond forecast; no-darkness/polar timeline; all cards collapsed; every optional card expanded; long
  site/target names; zero and maximum plausible custom items.
- **Accessibility:** TalkBack order and selected/state/value labels; actual touch bounds; Switch Access/keyboard
  where practical; font scales 1.0, 1.5, 2.0; reduce motion; standard and night-vision palettes.
- **Window forms:** 280–320 dp narrow window, normal phone, landscape phone, unfolded foldable/hinge, and both
  bottom bar and rail. Preserve existing foldable screenshots and tab-swipe tests.
- **Concurrency:** rapid refresh taps, settings changes during refresh, site change during darker-sky search,
  cancel at each search stage, widget update while Wear unavailable, and one caller cancelling a shared load.
- **Surfaces:** phone app, Glance widget, Wear Activity and tile, Digest/Prefetch/Aurora workers. Confirm one
  primary deep-sky verdict everywhere; no planetary score leakage.
- **Release:** minified phone and Wear packages, serialization recovery, tile fallback, map, and sharing/printing.

# Guardrails and rejected easy “fixes”

- Do not change score weights, gates, bands, verdicts, fixtures, ephemeris precision, or the published planetary
  behavior. PERF-02 is about dropping unused work, not changing numbers.
- Do not globally disable Haze, delete the custom glass work, or add retained-tab crossfades. The implementation
  already releases blur while scrolling and keeps hidden glass readers inactive. Measure at-rest costs first.
- Do not remove retained tabs purely as an architectural preference; `RetainedTabs` exists for warm-switch latency.
- Do not cache Bortle grids in memory, moved off their deliberate streaming design, or change the one-time inflated
  water mask. Keep blocking Bortle I/O on IO.
- Do not gate ISS network content on the display switch, or add another alarm slot. Do not change scheduler/Doze
  policies as part of polish.
- Do not combine this work with dependency upgrades. Lint has many version notices, and the repo explicitly treats
  catalog updates as separate tasks.
- `DigestWorker` processing all sites when only one is due is already listed as known debt in `ROADMAP.md:33`; it
  is not a new finding and should not be mixed into the focused fixes.
- Night vision is intentionally very red and dim, and the roadmap says to keep it. Evaluate physical-screen
  readability separately before changing its palette; do not merely apply the standard-theme contrast choices.
- Paparazzi `forkEvery = 1` addresses a known test-process idle problem. It is not evidence of an app recomposition
  defect and should remain.

# What already looks good

- Forecast HTTP calls are asynchronous/cancellable, shared per forecast cell, cached, parallelized across sites,
  and isolated by product/site failure.
- Normal ViewModel refresh/planning is on `Dispatchers.Default`, while streamed Bortle work is on IO.
- Week/Sites consume precomputed reports; they do not rescore in their rows.
- Site/forecast caches survive offline use and clearly label stale data. `updatePeriodMillis="0"` avoids arbitrary
  widget polling.
- Glass releases whole-viewport capture while scrolling, and hidden retained pages skip visibility-sensitive glass
  work, drawing, and semantics. Existing tests cover swipe ownership and fold boundaries.
- Expanded/collapsed content behavior, fold-state retention, localization resources, and many TalkBack labels have
  deliberate tests; the findings above target holes rather than replacing those components.
- Share/print renders away from Main, reuses one small share filename, avoids exact coordinates, and aims for an
  R8-minified phone release.
- The Wear tile reads phone-published data first and moves tile work onto its own background executor.

# Validation performed for this review

Successful commands on the reviewed `main` revision:

```text
.\gradlew.bat :app:lintDebug :wear:lintDebug :app:assembleDebugAndroidTest --stacktrace
.\gradlew.bat :app:assembleRelease :wear:assembleRelease --stacktrace
```

- App lint generated 62 results, mostly dependency/version notices. Relevant results are cited above.
- Wear lint generated 4 results; tile previews and Recents are cited above.
- Debug connected-test APK compilation passed; no connected tests ran because no Android device/emulator was
  attached.
- Both unsigned release APKs built successfully, which enabled the PKG-01 measurements.
- `:wear:verifyPaparazziDebug` passed as part of the attempted combined verification.

The combined `:app:verifyPaparazziDebug :wear:verifyPaparazziDebug` command was not a clean full suite on this
Windows host. It reported seven golden-image assertion failures (Foldable/Glass/Readme), one
`NightPlanSharerTest` `FileProvider` path failure using a Windows backslash path, then exceeded the five-minute
command timeout. Repo guidance specifies Linux CI for matching renderer goldens. These host results are disclosed
as limitations, **not diagnosed as app regressions or used as UI findings**. No goldens were recorded or changed.

No physical-device trace, battery measurement, network reproduction, Macrobenchmark run, or TalkBack session was
available. Runtime performance statements above therefore distinguish code-confirmed work from measured gains.
