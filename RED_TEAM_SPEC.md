# NightBrief Red-Team Review Spec

## 1. Purpose

Use this document to assign focused security and reliability reviews of NightBrief to subagents. The app helps astrophotographers choose when and where to shoot by combining saved locations, weather forecasts, astronomy calculations, gear, and local notifications. Reviewers should look for vulnerabilities, privacy problems, unsafe Android behavior, data corruption, and user-visible failures caused by bad inputs or partial failure.

The reviewer’s job is to find and explain problems. Do not patch the app, change product behavior, or silently edit tests. The human maintainer will pass confirmed findings to the implementation team. A later reviewer can use the same finding and reproduction steps to check the fix.

This is an authorized review of this repository and, only when explicitly arranged, a local emulator or test device running a build of this repository. It is not permission to probe third-party services, other apps, accounts, devices, or networks.

## 2. Read before starting

1. Read `AGENTS.md` completely. It defines constraints that are part of the product contract.
2. Read the relevant sections of `README.md` for the behavior under review.
3. Read the matching module and existing tests before deciding that behavior is wrong. `ROADMAP.md` records known debt and proposed work; proposed items are not bugs merely because they are unfinished.
4. Check `git status --short --branch`. Start from an up-to-date `main` when the maintainer has arranged that. Do not switch branches, fetch, reset, or discard user changes without explicit authorization. If branch state is wrong or the tree has local changes, report that and stop before any action that might disturb them.
5. Use the current source as evidence. Do not assume that README, roadmap, comments, or a previous finding still match the code.

Useful project map:

| Area | Module / location | Main review themes |
| --- | --- | --- |
| Astronomy, forecast parsing, Bortle, gear and score | `core-*` | Input validation, numerical bounds, parser resilience, incorrect confidence or score claims |
| Saved state and process graph | `data` | JSON recovery, import/export validation, persistence, privacy and defaults |
| Alarms, workers and notifications | `work` | Scheduling, duplicate work, stale data, cancellation and failure isolation |
| Phone UI, map, widget and Wear publishing | `app` | Permissions, intents, UI input handling, accessibility, data exposure and rendering |
| Watch UI and tile | `wear` | Data-layer input, fallback behavior, privacy and stale data |

The app has no account or app-owned backend. It does call public forecast, space-weather and orbital-data services; uses OpenStreetMap tiles; stores user state and forecast caches locally; and can publish a small briefing to Wear OS. Treat those facts as the initial threat model, and verify current code.

## 3. Rules of engagement

- Make no production-code, configuration, dependency, documentation, or test changes during an audit. Do not commit, push, publish, install a release, or upload data.
- Do not use real personal locations, credentials, private datasets, or another person’s device. Use synthetic coordinates and disposable app state. Never include secrets or a precise personal location in a report.
- Do not send crafted traffic to live providers, scan the internet, fuzz public endpoints, evade rate limits, or test provider infrastructure. Network-backed tests must use existing mocks/fixtures or an explicitly approved local test endpoint. Avoid live-network tests; the repository already identifies one existing test as live-network and potentially offline-failing.
- Do not attempt persistence, privilege escalation, exploit chaining against the host, destructive filesystem actions, or attacks on other apps. A local proof of concept must stay inside the app/repository test environment and be reversible.
- Do not request broader permissions or turn off device protections except on a disposable emulator under an explicitly assigned test plan.
- Never report a suspicion as a confirmed vulnerability. State what evidence is missing and how to confirm it safely.
- Respect cancellation behavior, licensing, module boundaries, score fixtures, and other invariants in `AGENTS.md`. A deliberate product constraint is not a vulnerability merely because a reviewer would design it differently.
- Do not run tests or builds unless the task assignment asks for validation or reproduction. If asked, use repository-prescribed Gradle tasks and JDK 21. Report commands and outcomes, including environmental failures.

## 4. How to conduct a focused audit

Each reviewer gets one narrow surface. Do not ask a weaker model to audit the whole app at once. Use this sequence:

1. **Define the boundary.** Name the module, files, entry points, data entering them, and the expected output. Ask the coordinator before expanding scope.
2. **Trace data flow.** Follow data from source to sink: UI or intent → validation → domain model → persistence/network/notification/widget/Wear. Record trust boundaries and conversions (JSON, URI, coordinates, epoch seconds, units, time zones, file names, IDs).
3. **Compare contract to implementation.** Check `AGENTS.md`, the relevant README behavior, model `init` requirements/defaults, and neighboring tests. Distinguish intentional behavior from a defect.
4. **Look for concrete failure paths.** Prefer a small input or state sequence that demonstrates a real consequence. Consider malformed, missing, extreme, stale, duplicated, reordered, and interrupted inputs where relevant.
5. **Validate safely.** First reason from source. If a reproduction is needed and authorized, use an existing unit/Robolectric test harness or a local emulator. Do not add files to the repository. If a throwaway test is essential, request that the coordinator create/manage it; otherwise describe the proposed test without editing.
6. **Check impact and reachability.** Identify who can trigger it (ordinary user, imported file, external intent, provider response, OS event, nearby device) and what the app/device actually does. Do not infer exploitability from a dangerous-looking API alone.
7. **Write a finding, or a concise clean-scope note.** Include exact source locations and reproduction details. Make no remediation edits.

For weak models, ask one explicit question at a time, such as: “Can an untrusted JSON file make the app overwrite or expose saved sites? Trace import from picker to persistence. Do not edit code. Return only evidence-backed findings using the template.” Avoid vague asks like “find all security issues.”

## 5. Suggested work packages

Assign packages independently, with file lists and a single outcome. These are review areas, not assumptions that bugs exist.

### A. External entry points and Android platform boundaries

Inspect phone and watch manifests, activities, receivers, services, providers, navigation/deep links, and notification intents. Check component `exported` status, intent action/data validation, URI grants, mutable/immutable `PendingIntent` use, task/back-stack behavior, and whether external callers can select another site or trigger sensitive work. Check that receiver actions are constrained to intended in-app or system senders. Do not use adb against a user device; use an assigned emulator only.

Starting points: `app/src/main/AndroidManifest.xml`, `work/src/main/AndroidManifest.xml`, `wear/src/main/AndroidManifest.xml`, `app/.../MainActivity.kt`, `work/.../Receivers.kt`, `work/.../DigestNotifier.kt`, `work/.../DigestScheduler.kt`.

### B. Local data, imports/exports and recovery

Trace settings serialization and recovery, library transfer, onboarding drafts, forecast caches, and any share/picker flow. Check malformed/oversized/deep JSON, unknown fields, invalid coordinates/classes/times, duplicate IDs, path or URI handling, partial import, accidental overwrite, sensitive data in exports/backups/logs, and recovery atomicity. Check old serialized state compatibility and defaults. Use synthetic data and never open another app’s private files.

Starting points: `data/.../AppState.kt`, `SettingsRepository.kt`, `LibraryTransfer.kt`, `AppGraph.kt`, serializers and tests; forecast cache implementation in `core-weather`.

### C. Network clients and untrusted provider responses

Review HTTP clients, URL construction, timeouts, response size/shape handling, numeric/date parsing, redirects, error handling and cache writes. Verify that malformed or partial provider data cannot crash unrelated briefings or create implausible user-facing certainty. Check hardcoded credentials (do not print any found secret; report file/line and redact value), transport configuration, and whether user coordinates are sent only to the expected providers. Never probe the providers themselves.

Starting points: `core-weather/.../Http.kt`, `OpenMeteoClient.kt`, `SevenTimerClient.kt`, `SwpcKpClient.kt`, `CelestrakClient.kt`, `ForecastRepository.kt`, `TimeZoneLookup.kt`; fixture tests beside them.

### D. Location, map and privacy

Trace GPS permission requests, coordinate entry, site edits, geocoding/time-zone lookups, map tiles, network requests, logs, crash output, backups, widgets, notifications and Wear publication. Establish when exact coordinates leave the device and whether this is disclosed or necessary. Check permission denial, approximate-only access, rapid location changes, invalid ranges, stale zone data, and whether lock-screen surfaces reveal more site detail than intended. Do not use real coordinates.

Starting points: location helpers, `SiteForm`, `MapPicker.kt`, `TimeZoneLookup.kt`, `WearPublisher.kt`, widget code, notification copy and Settings attribution/privacy copy.

### E. Workers, alarms and notifications

Trace onboarding/site guards, retry policy, cancellation, stale-cache fallback, notification permissions/channels, exact-alarm fallback, boot/time/time-zone rescheduling, duplicate alarm/work behavior, notification IDs and notification-to-site navigation. Look for a side-product failure that prevents the main score/widget/digest, unexpected network use, or notifications for deleted/disabled sites. Respect the known single-alarm design and existing collision/roadmap notes; verify whether any listed debt remains before raising it.

Starting points: `work/.../Workers.kt`, `DigestTimes.kt`, `DigestScheduler.kt`, `Receivers.kt`, `DigestNotifier.kt`, `WidgetRefresh.kt`, plus worker tests.

### F. Domain correctness and numerical robustness

For one assigned engine or parser, test reasoning around bounds, units, time zones, daylight saving transitions, polar/no-darkness cases, missing estimates, NaN/infinity, overflow, duplicated timestamps, and ordering. Compare results against documented product rules and fixture ranges. Do not retune score weights or flag documented estimates as bugs. Report a correctness issue only when an input can produce a wrong or misleading result users can act on.

Starting points: one named core module and its existing tests. Keep the audit to that engine/client; do not combine all astrophysics and weather in one assignment.

### G. Build, dependency and release configuration

Inspect Gradle/version catalog, manifest/build variants, R8 rules, asset packaging and release-only behavior. Look for unpinned or unexpected dependencies, debug-only security assumptions, accidentally packaged secrets/test endpoints, unsafe cleartext networking, exported debug surfaces, or sensitive data in backups. Check actual configured policy before relying on a generic Android recommendation. Do not update dependencies as part of the audit.

Starting points: `gradle/libs.versions.toml`, root and module Gradle files, manifests, `app/proguard-rules.pro`, backup/data-extraction rules and network security config if present.

### H. UI and device-side misuse resistance

Review user-controlled text and values, accessibility labels for sensitive actions, state restoration/process death, selection/deletion flows, settings toggles and widget/Wear rendering. Look for crashes, data loss, misleading stale values, unintended actions from repeated taps, or values presented with the wrong unit/time/site. UI polish and preferences are not security findings unless there is concrete impact.

Starting points: Compose screens and view model in `app`, existing Compose/Robolectric tests, and the watch screen/tile in `wear`.

## 6. Severity and confidence

Use severity to describe demonstrated user impact, not how alarming the code looks.

| Level | Meaning | NightBrief examples (only if demonstrated) |
| --- | --- | --- |
| Critical | Broad, remotely triggerable compromise of user/device data or execution with no meaningful user action | A remotely controllable path executes attacker code or exposes arbitrary private app data |
| High | Serious compromise or repeatable loss/exposure of sensitive data affecting users under plausible conditions | Untrusted import or external entry point overwrites/exports private library data; coordinates are disclosed to an unintended party |
| Medium | Bounded but material security, privacy, availability or integrity impact; interaction or narrow conditions may be required | A crafted response crashes core briefing repeatedly; stale/incorrect site association causes materially wrong alerts; exported component triggers unintended work |
| Low | Limited impact, difficult to exploit, or mostly defense-in-depth with a specific risk | Sensitive detail appears in a low-visibility surface; weak validation with no demonstrated harmful consequence yet |
| Informational | Hardening or product concern without demonstrated vulnerability | No timeout where current transport bounds the request another way; optional hardening suggestion |

Confidence:

- **High:** directly reproduced, or source path and behavior are unambiguous.
- **Medium:** source indicates a credible issue, but an environmental condition or runtime behavior is unverified.
- **Low:** hypothesis needing more evidence. Normally do not submit as a finding; ask the coordinator for the missing evidence or report under “questions to validate.”

Do not elevate severity because a tool labels a pattern dangerous. Explain required attacker access, user interaction, affected data/function, and realistic consequences. If scope or a precondition is unknown, say so.

## 7. Required finding format

Return each finding using this exact structure. Keep the title short and state the impact.

```text
[SEVERITY] Short impact-focused title
Confidence: High | Medium | Low
Area: module / feature
Affected code: path/to/File.kt:line (one or more exact locations)
Summary: What is wrong and why it matters to a NightBrief user.
Preconditions: Who can trigger it and what setup or permission is needed.
Evidence: The source behavior, test output, or observed device behavior. Separate facts from inference.
Reproduction: Numbered steps using synthetic inputs and a local build/test environment. Include exact command/test name if used.
Expected: What should happen according to the project contract.
Actual: What happened.
Impact: Data, privacy, availability, safety, or user decision affected; explain scope.
Fix direction: A short, non-prescriptive pointer for the good-guys team. Do not implement it.
Regression check: A focused test scenario the team should add or run.
``` 

If there are no findings, use:

```text
Scope reviewed: ...
Files reviewed: ...
Checks performed: ...
Findings: None identified in this scope.
Limitations: ...
Questions to validate: ...
```

Do not say “secure,” “no vulnerabilities,” or “passed red team” based on a limited package. Say “no findings identified in this scope.”

## 8. Evidence and triage quality bar

A report is ready to pass to the good-guys team only if a maintainer can understand the problem without repeating the audit. It should:

- Point to current file paths and line numbers (refresh line numbers immediately before submission).
- Provide a deterministic reproduction with synthetic inputs, or clearly state why reproduction was impossible.
- Show the expected behavior from an applicable README/AGENTS rule or an established test/product contract.
- Demonstrate an actual consequence, not just a style issue or hypothetical chain.
- Avoid bundling unrelated issues into one report. Split independent root causes; combine symptoms with the same root cause.
- Include environment details only when material: Android API level, build variant, emulator/device, offline/online state, locale/time zone, and command.
- Redact personal data, tokens, keys, and any precise location. Do not attach screenshots containing real user information.
- Mention duplicate/related findings if known. Do not claim a fix is verified; leave that for the follow-up review.

## 9. Coordinator prompt template

Copy and fill this in when assigning a subagent:

```text
You are reviewing NightBrief under RED_TEAM_SPEC.md. Read AGENTS.md first and the relevant README section. Review only: [single module/feature and file list]. Goal: [one concrete question].

Rules: do not edit, commit, install, publish, or contact live providers. Use only source inspection and existing local fixtures/tests unless I explicitly authorize a local runtime reproduction. Use synthetic data. Respect documented invariants and distinguish known roadmap debt from new findings.

Trace input to effect, check reachability and actual impact, and cite exact current file lines. Submit only evidence-backed findings using the required format. If nothing is demonstrated, report “no findings identified in this scope,” checks, limitations, and open questions. Do not propose a patch diff.
```

## 10. Follow-up fix verification

When a finding returns for verification, review only the original finding and its stated regression scenario first:

1. Confirm the fix is present on the supplied branch/commit and inspect its diff.
2. Re-run the original reproduction under the same relevant conditions, using synthetic data and local tests only.
3. Check neighboring paths for the same root cause and ensure the fix did not break the documented contract or failure isolation.
4. Report **Fixed**, **Partially fixed**, **Not fixed**, or **Unable to verify**. Include commit/diff evidence, exact command or steps, actual result, and any remaining scope.
5. Do not close an issue solely because a test was added; verify the behavior the report described. Do not expand into a general audit unless assigned.

## 11. Current baseline

The product and repository constraints in `AGENTS.md` are authoritative. In particular, Kp, meteors, ISS, dew, and cloud layers are not Night Score inputs; missing forecast fields may use marked estimates; optional network products must fail independently; caches have documented age limits; unknown Bortle is class 5 for scoring; and digest scheduling uses one alarm slot. Several roadmap entries are intentional known debt. Verify current `ROADMAP.md` status and source before filing a report based on any item.

Security review is not a request to redesign the scoring product. A wrong score, misleading estimate, location mix-up, corrupt settings recovery, or sensitive disclosure can still be a valid finding when the effect is demonstrated and compared with the documented contract.
