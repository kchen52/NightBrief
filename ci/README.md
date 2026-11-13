# Sideload signing

`sideload.jks` is the shared key for GitHub-distributed NightBrief APKs
(debug/develop builds and tagged releases that do not use a Play Store key).

It is committed on purpose so CI and a laptop `assembleDebug` produce the same
signature. That is what lets the in-app update banner replace an install without
`adb`. It is **not** a Play Store upload key. Do not reuse it for Play.

Password and alias (also in `app/build.gradle.kts`):

- store / key password: `nightbrief-sideload`
- alias: `sideload`

Fingerprint (SHA-256):

```
88:33:8D:FD:32:3C:35:19:C2:A0:20:D5:2C:C7:C4:D0:68:5D:46:7D:A2:0E:0F:BF:F6:B6:BD:73:88:AA:88:2F
```

For a Play release, create a separate keystore, write `keystore.properties` at
the repo root (gitignored), and point `storeFile` at that key. Release builds
prefer `keystore.properties` when it exists.
