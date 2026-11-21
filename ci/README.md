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

When `keystore.properties` is present, debug and release builds both use it, so
a later build from this machine can update an install made here. The
environment variables below are the fallback when that file is absent, which is
how CI signs a release build. Without either, debug stays on the Android debug
key and `:app:assembleRelease` stays unsigned. Do not restore a keystore that
was ever committed.

Example, stored outside the repo:

```bash
mkdir -p ~/.nightbrief
keytool -genkeypair \
  -keystore ~/.nightbrief/nightbrief-sideload.jks \
  -storetype PKCS12 \
  -alias nightbrief \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=NightBrief, OU=Local, O=NightBrief"
```

## GitHub Actions release builds

Configure these repository secrets for `.github/workflows/release-apk.yml`:

- `RELEASE_KEYSTORE_BASE64`: base64-encoded `.jks` keystore file
- `RELEASE_STORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: key alias
- `RELEASE_KEY_PASSWORD`: key password

Keep a separate upload key for Google Play releases.
