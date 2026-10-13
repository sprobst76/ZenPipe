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
- `.github/workflows/fdroid-repo.yml`: rebuilds the F-Droid repo on GitHub Pages; called by
  `release.yml` after each release and manually runnable (see "F-Droid repository").
- `.github/workflows/ci.yml`: build + unit tests + emulator tests on push/PR to `zen`; artifact
  `ZenPipe-continuous`.
- Removed upstream files: `FUNDING.yml` (TeamNewPipe donations), `build-release-apk.yml`
  (named the APK `NewPipe_v…`), `pr-labeler.yml` + `changed-lines-count-labeler.yml`
  (`pull_request_target`), `no-response.yml` (cron), `image-minimizer.*`, `backport-pr.yml`,
  `DISCUSSION_TEMPLATE/`. Issue templates, PR template and CONTRIBUTING point to
  `sprobst76/ZenPipe`. On upstream merges these paths can show modify/delete conflicts: keep the
  ZenPipe version (`git rm` the upstream files again).

## F-Droid repository (own repo on GitHub Pages)

URL: `https://sprobst76.github.io/ZenPipe/repo` (plus `?fingerprint=<repo key SHA-256>`).
A plain binary repo: the APKs are the signed release APKs from the GitHub releases, nothing is
rebuilt or re-signed. Only the index is signed, with a **separate repo key** (not the app key).

- Files: `fdroid/config.yml` (repo name/URL, `archive_older: 3`, key from environment variables),
  `fdroid/config/{categories,antiFeatures}.yml`, `fdroid/metadata/io.github.sprobst76.zenpipe.yml`
  (license, links, categories, AntiFeature `NonFreeNet`, `AllowedAPKSigningKeys` = release cert
  SHA-256, so `fdroid update` rejects any APK not signed with the ZenPipe key). Name, summary,
  description, icon and changelogs come from `fastlane/metadata/android/` (single source).
- `fdroid/build-repo.sh <apk-dir> <out-dir>`: checks the `.sha256` files, runs `fdroid lint` and
  `fdroid update --use-date-from-apk`, fails if an APK is missing from the index, writes the web
  root (`repo/`, `archive/`, `index.html`, `.nojekyll`; never `config.yml` or the key).
- `.github/workflows/fdroid-repo.yml` (environment `fdroid-repo`, fdroidserver 2.4.5 via pipx):
  downloads the APKs of the newest 10 releases (file date = release date = "added" date), builds
  the repo, checks the repo key fingerprint against the variable `FDROID_REPO_FINGERPRINT`,
  uploads the result as artifact and force-pushes it as a single orphan commit to `gh-pages`
  (no history of old APKs). Manual run with `publish: false` = dry run (artifact only).
  GitHub lists `workflow_dispatch` workflows only from the default branch; the repo default is
  still `dev`, so either switch the default to `zen` or rely on the call from `release.yml`.
- Secrets (environment `fdroid-repo`): `FDROID_KEYSTORE_BASE64`, `FDROID_KEYSTORE_PASSWORD`
  (store = key password), `FDROID_KEY_ALIAS` (`zenpipe-repo`). Variable:
  `FDROID_REPO_FINGERPRINT` (public). **Passwords ASCII only** (the JVM/keytool decode
  non-ASCII environment values depending on the locale).

### One-time setup (by Stefan)

1. `bash zen-docs/tools/create-fdroid-repo-keystore.sh` -> `~/development/keystores/zenpipe-fdroid-repo.p12`
   (PKCS12, RSA 4096, SHA256withRSA, 10000 days, interactive ASCII password, prints the fingerprint).
2. Back it up like the release key: password manager + offline copy.
3. `bash zen-docs/tools/set-fdroid-repo-secrets.sh` (creates the environment, sets the three
   secrets and the fingerprint variable via stdin).
4. First run: `gh workflow run fdroid-repo.yml -R sprobst76/ZenPipe --ref zen` (needs the file on
   the default branch, see above) or re-run the release; then enable Pages from branch
   `gh-pages`, folder `/`:
   `gh api -X POST repos/sprobst76/ZenPipe/pages -f 'source[branch]=gh-pages' -f 'source[path]=/'`
5. Put the repo fingerprint into README.md ("Own F-Droid repository").

### Per release

Tag `v<versionName>` -> `release.yml` builds, signs and publishes the GitHub release -> job
`fdroid-repo` rebuilds the index from all release APKs and pushes `gh-pages` -> Pages serves it
after ~1 min; clients see the update on their next repo refresh. Obtainium sees the GitHub
release directly. If the repo job failed (e.g. secret problem), fix it and re-run only that job.

### Local dry run

fdroidserver in a throwaway venv, a throwaway key, the APK from the release:
```
uv venv /tmp/fd && VIRTUAL_ENV=/tmp/fd uv pip install fdroidserver==2.4.5
gh release download v0.1.0-zen.1 -R sprobst76/ZenPipe -D /tmp/apks
keytool -genkeypair -storetype PKCS12 -keystore /tmp/test.p12 -alias test -keyalg RSA -keysize 4096 \
  -validity 10000 -dname CN=test -storepass:env FDROID_KEYSTORE_PASSWORD
FDROID_KEYSTORE=/tmp/test.p12 FDROID_KEY_ALIAS=test FDROID_CMD=/tmp/fd/bin/fdroid \
  ANDROID_HOME=~/Android fdroid/build-repo.sh /tmp/apks /tmp/site
```
(`FDROID_KEYSTORE_PASSWORD` exported beforehand.) Verified on 2026-10-13 with v0.1.0-zen.1: lint
clean, index-v1/v2 + entry.jar signed, package with versionCode 1015001, en-US/de-DE texts, icon,
`NonFreeNet`, signer `6e25f0fa…9518`; a client-side download (`fdroidserver.index.download_repo_index_v2`)
accepted the right fingerprint and rejected a wrong one; an APK re-signed with another key was
rejected by `AllowedAPKSigningKeys`. Remaining harmless warning: the empty archive reports
"Category ... defined but not used".

### Repo key: backup and rotation

- Loss of the repo key does **not** affect app updates (the APK signature is the release key).
  Users only have to remove and re-add the repo with the new fingerprint.
- Rotation (lost/compromised): create a new key with the script (after moving the old file),
  set the secrets again (`set-fdroid-repo-secrets.sh` also updates `FDROID_REPO_FINGERPRINT`),
  run the workflow, update the fingerprint in README, announce it in the release notes.
  A compromised repo key can only serve a forged index; Android still refuses APK updates with a
  different signature, but a first install via that repo would be at risk -> rotate promptly.
- The workflow fails if the key in the secret does not match `FDROID_REPO_FINGERPRINT`
  (protects against accidentally uploading the release key or a test key).

## Obtainium

Source: GitHub releases of `https://github.com/sprobst76/ZenPipe` (Obtainium source "GitHub").
APK filter (regex): `ZenPipe_v.*\.apk$` (the release also has a `.sha256` file). Deeplink for a
link/QR code (text only, nothing uploaded): `obtainium://add/https://github.com/sprobst76/ZenPipe`.
Alternative: source type "F-Droid Third-Party Repo" with the repo URL above. Obtainium itself does
not verify signers: check the first install against the certificate SHA-256 in README (e.g.
AppVerifier or `apksigner verify --print-certs`); afterwards Android refuses updates with another key.

## Later channels: IzzyOnDroid and official F-Droid

- **IzzyOnDroid** (planned from a stable 1.0): picks up the signed APKs from the GitHub releases
  and keeps the ZenPipe signature, so users can switch between own repo, GitHub/Obtainium and
  IzzyOnDroid without reinstalling. Inclusion policy is met (own name/icon/texts, release
  signature, < 30 MB); open: screenshots, request issue.
- **Official F-Droid** (optional, later): F-Droid builds from source and normally signs with
  **its own** key. Android only installs updates signed with the same key, so users who switch
  from the own repo to official F-Droid would have to uninstall (export data first) and
  reinstall. Exception: a **reproducible build** - F-Droid rebuilds, verifies that its APK is
  bit-identical to ours and then ships our signed APK (developer signature, like NewPipe), so
  all channels share one signature. Needs build-tools 34 apksigner, no `baseline.prof`
  differences, builds from a clean tag (see `research-name-und-offene-fragen.md` §3).

## Release checklist

1. Bump `ZENPIPE_BUILD` / `ZENPIPE_VERSION_NAME`, add changelogs (en-US, de-DE).
2. `assembleDebug runCheckstyle testDebugUnitTest` green, device check.
3. `android-release-preflight.sh`, then `git tag v<versionName>` on the release commit and
   `git push origin v<versionName>` -> `release.yml` publishes.
4. Check the release page (APK, SHA-256, source link). `release.yml` then runs the job
   `fdroid-repo` (below); check its run summary and that F-Droid shows the new version.

## GPL notices

- §5a dated modification notice: README section "Modifications" and the About > License text
  (`app_license`, app + shared, en + de).
- §6 corresponding source: every GitHub release links the tag's source tree; GitHub attaches the
  source archives automatically.
