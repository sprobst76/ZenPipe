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
