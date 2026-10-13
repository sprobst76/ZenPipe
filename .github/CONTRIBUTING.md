ZenPipe contribution notes
==========================

ZenPipe is a fork of [NewPipe](https://github.com/TeamNewPipe/NewPipe) (GPLv3) and is not
affiliated with TeamNewPipe. Please report ZenPipe problems only here:
https://github.com/sprobst76/ZenPipe/issues

## How the fork is kept

* Branch `dev` mirrors upstream NewPipe unchanged; branch `zen` carries the ZenPipe patches.
* Keep changes small and local (one clear commit per change, prefix `zen:`), so upstream merges
  stay easy. NewPipeExtractor is used unchanged.
* The rules of the app (feed size, pause, intent prompt, disabled features, ...) are documented
  in `zen-docs/rules.md` (German). Update it together with the code.

## Building

* JDK 21 and the Android SDK are required. `./gradlew assembleDebug runCheckstyle testDebugUnitTest`
  must pass before a change is merged.
* Code style: the inherited checkstyle and ktlint configuration of NewPipe applies.

## Crash reports

The error report screen offers "Copy formatted report" and "Open GitHub issue" (ZenPipe
repository). Nothing is sent automatically.

For general NewPipe development documentation see the
[upstream contribution guidelines](https://github.com/TeamNewPipe/NewPipe/blob/dev/.github/CONTRIBUTING.md).
