/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 37
const val NEWPIPE_VERSION_SDK_COMPILE_MINOR = 0
const val NEWPIPE_VERSION_SDK_MIN = 23
const val NEWPIPE_VERSION_SDK_TARGET = 35

const val NEWPIPE_VERSION_CODE = 1015
const val NEWPIPE_VERSION_NAME = "0.29.1"

// ZenPipe release identity. versionCode = <upstream NewPipe versionCode> * 1000 + <ZenPipe build n>
// (see zen-docs/release.md). Bump ZENPIPE_BUILD for every release, reset it when merging a new upstream.
const val ZENPIPE_VERSION_NAME = "0.1.0-zen.3"
const val ZENPIPE_BUILD = 3
const val ZENPIPE_VERSION_CODE = NEWPIPE_VERSION_CODE * 1000 + ZENPIPE_BUILD

// ZenPipe: Kotlin/Java namespace stays "org.schabi.newpipe" (keeps upstream merges clean),
// only the installed application id differs from NewPipe.
const val NEWPIPE_APPLICATION_ID_OLD = "org.schabi.newpipe"
const val ZENPIPE_APPLICATION_ID = "io.github.sprobst76.zenpipe"
const val NEWPIPE_APPLICATION_ID_NEW = "net.newpipe.app"
