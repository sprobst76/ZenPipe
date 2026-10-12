package org.schabi.newpipe.util

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ReleaseVersionUtil {
    /**
     * ZenPipe: never an "official NewPipe release", so the update checker (which would query
     * NewPipe's release API and offer NewPipe APKs) and its settings are permanently disabled.
     * Updates come through the ZenPipe F-Droid repo / GitHub releases instead.
     */
    val isReleaseApk: Boolean = false

    fun isLastUpdateCheckExpired(expiry: Long): Boolean {
        return Instant.ofEpochSecond(expiry) < Instant.now()
    }

    /**
     * Coerce expiry date time in between 6 hours and 72 hours from now
     *
     * @return Epoch second of expiry date time
     */
    fun coerceUpdateCheckExpiry(expiryString: String?): Long {
        val nowPlus6Hours = ZonedDateTime.now().plusHours(6)
        val expiry = expiryString?.let {
            ZonedDateTime.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(it))
                .coerceIn(nowPlus6Hours, nowPlus6Hours.plusHours(66))
        } ?: nowPlus6Hours
        return expiry.toEpochSecond()
    }
}
