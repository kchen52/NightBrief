# Changelog

Notable changes to NightBrief. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0/) from `0.1.1` onward.
While the app is pre-1.0, a change that breaks saved data or the score model may bump the minor version.

## [Unreleased]

### Added

- A second imaging-mode readout on Tonight and the planner. When the planetary weighting lands in a
  different band from the published score, the hero shows `62 overall · 78 for planets`. It uses the
  same per-hour qualities and gates with a documented alternate weight table. The deep-sky score stays
  the published verdict in the digest, widget, Wear tile, Sites list, and alerts.

## [0.1.1] - 2026-11-13

First changelog entry. It consolidates everything shipped before semantic versioning started:
`versionCode` 2 and `versionName` 0.1.1. The roadmap now tracks only open work.

### Added

- Night Score for the dark hours: cloud, moon and twilight, transparency, wind, seeing, and light
  pollution, with cloud and moon/twilight gates, a 50/50 blend of all dark hours and the best 3-hour
  window, bands, and the Go / Maybe / No-go verdict.
- A seven-night outlook, per-night `est.` markers when a factor used a proxy, and a week view that
  names the best night.
- Sites: GPS, an OpenStreetMap picker with place search, typed coordinates, Bortle lookup, time-zone
  lookup, an optional local horizon, and a darker-sky finder that filters open water and annotates
  road access. Sites and gear can be exported and imported as JSON.
- Gear: bodies, lenses, NPF exposure suggestions, a star tracker, filters, target-interest chips, and
  custom targets. The catalog includes southern-sky objects.
- Conditions beyond the score: dew and frost risk, the overnight low, cloud layers, wildfire smoke
  (PM2.5), and a forecast-confidence card from a second weather model.
- Aurora: the NOAA SWPC Kp forecast on Tonight and in the digest, plus an optional night-time nowcast
  push when observed Kp crosses the saved threshold.
- Meteors, ISS passes (opt-in), and comets as their own cards and digest lines.
- Planner: date stepping through the forecast horizon, per-target windows, treeline crossings,
  moon-free sub-windows, and a shareable or printable night plan with the location rounded to 0.1°.
- Notifications: the morning digest with an offline fallback to the last saved forecast, Big Night
  alerts, night-collapse alerts, and a one-shot window-start reminder. Alarms survive reboot, package
  replace, and time-zone changes.
- Home-screen widget and optional Wear OS tile and screen for the primary site.
- Tonight presentation: the twilight/Moon/Milky Way hero, collapsible cards, red night-vision mode,
  metric/imperial units, whimsical copy and night names, the sky passport, session log, score-bias
  readout, rotating refresh lines, and gear nicknames.
- A sky-events calendar for oppositions, elongations, conjunctions, appulses, eclipses, equinoxes,
  solstices, and shower peaks.
- Recovery for corrupt settings from the last good copy, plus per-site notification slots so two
  sites cannot replace each other's digest or Big Night notification.

### Changed

- Exposure hints show the NPF shutter alone; the 500 rule is no longer printed.
- A failed forecast fetch serves the last saved file at any age and labels it with when it was saved.
- The morning digest and the planner use the same saved-forecast wording as the app.

### Fixed

- Corrupt settings no longer wipe every site.
- A site deletion no longer hands its notification id to the next site.
- The digest path and the cached briefing keep Settings from reopening over Tonight.
- A malformed settings import is rejected without touching the phone's library.

### Notes

- The Bortle atlas is CC BY-NC 4.0, so builds that ship it stay non-commercial.
- Ephemeris and seeing/transparency beyond the 7Timer horizon are planning-grade and mark estimated
  factors.
