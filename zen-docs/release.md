# ZenPipe release identity

## Package names

| Variant | applicationId | Label |
|---|---|---|
| release | `io.github.sprobst76.zenpipe` | ZenPipe |
| debug | `io.github.sprobst76.zenpipe.debug` | ZenPipe Debug |
| continuous (CI) | `io.github.sprobst76.zenpipe.continuous[.<branch>]` | ZenPipe Continuous |

The Kotlin/Java namespace stays `org.schabi.newpipe` (keeps upstream merges clean); only the
installed `applicationId` differs, so ZenPipe installs in parallel to NewPipe.
Defined in `buildSrc/src/main/kotlin/ProjectConfig.kt` (`ZENPIPE_APPLICATION_ID`).

## Version scheme

- `versionName`: `<zen version>-zen.<n>`, e.g. `0.1.0-zen.1` (`ZENPIPE_VERSION_NAME`).
- `versionCode = <upstream NewPipe versionCode> * 1000 + <ZenPipe build n>`
  (`ZENPIPE_VERSION_CODE = NEWPIPE_VERSION_CODE * 1000 + ZENPIPE_BUILD`).
  Upstream NewPipe 0.29.1 has versionCode 1015, so the first release is **1015001**; the next
  ZenPipe build on the same upstream is 1015002. After merging a new upstream (e.g. 1016) reset
  `ZENPIPE_BUILD` to 1 -> 1016001. The code therefore always rises monotonically.
- Per release: bump `ZENPIPE_BUILD` (and `ZENPIPE_VERSION_NAME` if needed), add
  `fastlane/metadata/android/<lang>/changelogs/<versionCode>.txt`, tag `v<versionName>`.
- CI can override with `-DversionCodeOverride=...`.

## No NewPipe backend contact

- Crash reports: no e-mail (the NewPipe address is removed). The report screen offers copy,
  share (JSON) and "open GitHub issue" -> `https://github.com/sprobst76/ZenPipe/issues`.
- Update checker: `ReleaseVersionUtil.isReleaseApk` is hard-wired to `false`, so the settings
  entry is hidden and `NewVersionWorker` never requests `newpipe.net/api/data.json`.
  Updates are delivered via the ZenPipe F-Droid repo / GitHub releases.
- About screen: GitHub + privacy link -> ZenPipe; donate/website/FAQ links stay on NewPipe as
  the upstream project and are labelled as such. License and credits state that ZenPipe is a
  fork of NewPipe (GPLv3) and not affiliated with TeamNewPipe.

## Translations

Only English (default) and German are shipped (`localeFilters` + `ignoreAssetsPatterns` in
`app/build.gradle.kts`). The ~100 inherited NewPipe translations stay untouched in the source
tree (merge-friendly) but are not packaged because they still say "NewPipe". To add a language,
review its strings for "NewPipe" and extend `localeFilters`.

## Fastlane / F-Droid metadata

`fastlane/metadata/android/{en-US,de-DE}`: title, short/full description, changelogs, icon.
The inherited NewPipe language folders, changelogs, feature graphic and screenshots were removed
from the fork (they say "NewPipe" - trademark policy). Upstream merges that touch those paths show
up as modify/delete conflicts: resolve with `git rm -r fastlane/metadata/android/<folder>`
(keep only `en-US` and `de-DE`). TODO: add own phone screenshots to `en-US/images/phoneScreenshots/`.

## Icons

`python3 zen-docs/tools/gen_icons.py` regenerates all launcher/notification/TV-banner PNGs and
the store icon (open ring on calm green `#2E4A45`, ring `#F2EFE6`).

## Signing

- Release signing is read **only from environment variables** in `app/build.gradle.kts`
  (`signingConfigs.zenRelease`): `ZENPIPE_KEYSTORE` (path to the PKCS12 file),
  `ZENPIPE_KEYSTORE_PASSWORD`, `ZENPIPE_KEY_ALIAS`, `ZENPIPE_KEY_PASSWORD`. Schemes v1+v2+v3.
- Without `ZENPIPE_KEYSTORE` `assembleRelease` builds an **unsigned** APK
  (`app-release-unsigned.apk`), e.g. for local tests or F-Droid. If `ZENPIPE_KEYSTORE` is set but
  one of the other three variables is missing, the build fails with a clear message.
- `.gitignore` blocks `*.jks *.keystore *.p12 *.pem keystore.properties`.
- Key (not created yet, see `research-name-und-offene-fragen.md` §3):
  `keytool -genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 -alias zenpipe -keystore zenpipe-release.p12`
  Store keystore + passwords in the password manager and two offline copies. Publish the
  SHA-256 fingerprint in the README (`apksigner verify --print-certs ZenPipe_v….apk`).
- Local signed build:
  `ZENPIPE_KEYSTORE=… ZENPIPE_KEYSTORE_PASSWORD=… ZENPIPE_KEY_ALIAS=zenpipe ZENPIPE_KEY_PASSWORD=… ./gradlew assembleRelease`
  (load the values from a non-committed file into the environment, never as command-line
  arguments in the shell history).

## GitHub Actions

- `.github/workflows/release.yml`: runs on tag `v*` (environment `release`). Restores the
  keystore from secret `ZENPIPE_KEYSTORE_BASE64` (`base64 -w0 zenpipe-release.p12`), plus secrets
  `ZENPIPE_KEYSTORE_PASSWORD`, `ZENPIPE_KEY_ALIAS`, `ZENPIPE_KEY_PASSWORD`; builds
  `assembleRelease`, fails if the tag is not `v<versionName>`, the APK is unsigned or the
  changelog `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` is missing; renames to
  `ZenPipe_v<versionName>.apk`, writes `.sha256`, creates the GitHub release with the changelog,
  a link to the source tree of the tag (GPLv3 §6), the SHA-256 and the certificate digest.
- `.github/workflows/ci.yml`: build + unit tests + emulator tests on push/PR to `zen`; artifact
  `ZenPipe-continuous`.
- Removed upstream files: `FUNDING.yml` (TeamNewPipe donations), `build-release-apk.yml`
  (named the APK `NewPipe_v…`), `pr-labeler.yml` + `changed-lines-count-labeler.yml`
  (`pull_request_target`), `no-response.yml` (cron), `image-minimizer.*`, `backport-pr.yml`,
  `DISCUSSION_TEMPLATE/`. Issue templates, PR template and CONTRIBUTING point to
  `sprobst76/ZenPipe`. On upstream merges these paths can show modify/delete conflicts: keep the
  ZenPipe version (`git rm` the upstream files again).

## Release checklist

1. Bump `ZENPIPE_BUILD` / `ZENPIPE_VERSION_NAME`, add changelogs (en-US, de-DE).
2. `assembleDebug runCheckstyle testDebugUnitTest` green, device check.
3. `android-release-preflight.sh`, then `git tag v<versionName>` on the release commit and
   `git push origin v<versionName>` -> `release.yml` publishes.
4. Check the release page (APK, SHA-256, source link), then update the F-Droid repo / Obtainium.

## GPL notices

- §5a dated modification notice: README section "Modifications" and the About > License text
  (`app_license`, app + shared, en + de).
- §6 corresponding source: every GitHub release links the tag's source tree; GitHub attaches the
  source archives automatically.
