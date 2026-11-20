# Release signing

No signing keystore is committed to this repository. Keep release signing keys
private and outside Git.

## Local release builds

Create a private keystore outside the repository (or anywhere ignored by
`.gitignore`) and add a gitignored `keystore.properties` file at the repo root:

```properties
storeFile=/absolute/or/relative/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Release builds use `keystore.properties` when present, or the environment
variables below in CI. Without either, `:app:assembleRelease` produces an
unsigned APK instead of falling back to a debug or committed key.

## GitHub Actions release builds

Configure these repository secrets for `.github/workflows/release-apk.yml`:

- `RELEASE_KEYSTORE_BASE64`: base64-encoded `.jks` keystore file
- `RELEASE_STORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: key alias
- `RELEASE_KEY_PASSWORD`: key password

Keep a separate upload key for Google Play releases.
