package org.schabi.newpipe.zen

import android.content.Context

/**
 * Definition of one Zen setting: allowed range and which direction is "stricter" (see
 * zen-docs/rules.md, section "Zen-Einstellungen"). Booleans are stored as 0/1 with `true` = the
 * feature is on. The default is the former hard-coded constant.
 */
data class ZenSettingDef(
    val key: String,
    val default: Int,
    val min: Int,
    val max: Int,
    /** true: a HIGHER value is stricter (longer pause, longer minimum length); false: a lower one. */
    val strictHigher: Boolean,
    val step: Int = 1,
    val isBool: Boolean = false
) {
    fun inRange(v: Int) = v in min..max && (v - min) % step == 0

    /** [new] is stricter than [old]. */
    fun isStricter(old: Int, new: Int) = if (strictHigher) new > old else new < old
}

object ZenSettingKeys {
    const val FEED_BUDGET = "feed_budget"
    const val DAILY_CAP = "daily_cap"
    const val MIN_LENGTH_MIN = "min_length_min"
    const val PAUSE_ENABLED = "pause_enabled"
    const val PAUSE_PERCENT = "pause_percent"
    const val PAUSE_MIN_MIN = "pause_min_min"
    const val PAUSE_MAX_MIN = "pause_max_min"
    const val PAUSE_TRIGGER_MIN = "pause_trigger_min"
    const val INTENT_WAIT_S = "intent_wait_s"
    const val INTENT_BACKGROUND_MIN = "intent_background_min"
}

object ZenSettingDefs {
    // The default is the loosest value for budget and cap: more than the defaults is never offered.
    val all: List<ZenSettingDef> = listOf(
        ZenSettingDef(ZenSettingKeys.FEED_BUDGET, default = 15, min = 5, max = 15, strictHigher = false),
        ZenSettingDef(ZenSettingKeys.DAILY_CAP, default = 30, min = 10, max = 30, strictHigher = false),
        ZenSettingDef(ZenSettingKeys.MIN_LENGTH_MIN, default = 5, min = 1, max = 30, strictHigher = true),
        ZenSettingDef(ZenSettingKeys.PAUSE_ENABLED, default = 1, min = 0, max = 1, strictHigher = true, isBool = true),
        ZenSettingDef(ZenSettingKeys.PAUSE_PERCENT, default = 20, min = 10, max = 50, strictHigher = true, step = 5),
        // ranges of min (1-5) and max (5-30) pause cannot cross, so no cross rule is needed
        ZenSettingDef(ZenSettingKeys.PAUSE_MIN_MIN, default = 1, min = 1, max = 5, strictHigher = true),
        ZenSettingDef(ZenSettingKeys.PAUSE_MAX_MIN, default = 15, min = 5, max = 30, strictHigher = true),
        ZenSettingDef(ZenSettingKeys.PAUSE_TRIGGER_MIN, default = 5, min = 1, max = 15, strictHigher = false),
        ZenSettingDef(ZenSettingKeys.INTENT_WAIT_S, default = 5, min = 3, max = 30, strictHigher = true),
        ZenSettingDef(ZenSettingKeys.INTENT_BACKGROUND_MIN, default = 5, min = 1, max = 60, strictHigher = false)
    )

    private val byKey = all.associateBy { it.key }

    @JvmStatic
    fun get(key: String): ZenSettingDef? = byKey[key]

    /** Cross-setting rules; null = valid, otherwise a short reason. */
    @JvmStatic
    fun combinationError(v: Map<String, Int>): String? {
        fun g(k: String) = v[k] ?: byKey.getValue(k).default
        return when {
            g(ZenSettingKeys.DAILY_CAP) < g(ZenSettingKeys.FEED_BUDGET) -> "cap_below_budget"
            else -> null
        }
    }
}

/**
 * Process-wide access to the EFFECTIVE Zen settings (the ones the rules run with). Before
 * [init] (and in unit tests without [install]) the defaults apply, i.e. the former constants.
 * Changes go through [ZenSettingsLock] only.
 */
object ZenSettings {
    @Volatile
    private var lock: ZenSettingsLock? = null

    @JvmStatic
    fun init(context: Context) {
        lock = Zen.settingsLock(context)
    }

    /** For tests: use [lock] (or the defaults for null). */
    @JvmStatic
    fun install(lock: ZenSettingsLock?) {
        this.lock = lock
    }

    @JvmStatic
    fun lock(): ZenSettingsLock? = lock

    @JvmStatic
    fun get(key: String): Int {
        val l = lock
        return l?.effective(key) ?: ZenSettingDefs.get(key)!!.default
    }

    @JvmStatic
    fun feedBudget() = get(ZenSettingKeys.FEED_BUDGET)

    @JvmStatic
    fun dailyCap() = get(ZenSettingKeys.DAILY_CAP)

    @JvmStatic
    fun minLengthSec() = get(ZenSettingKeys.MIN_LENGTH_MIN) * 60

    @JvmStatic
    fun pauseEnabled() = get(ZenSettingKeys.PAUSE_ENABLED) == 1

    @JvmStatic
    fun pauseFraction() = get(ZenSettingKeys.PAUSE_PERCENT) / 100.0

    @JvmStatic
    fun minPauseMs() = get(ZenSettingKeys.PAUSE_MIN_MIN) * 60_000L

    @JvmStatic
    fun maxPauseMs() = get(ZenSettingKeys.PAUSE_MAX_MIN) * 60_000L

    @JvmStatic
    fun pauseTriggerMs() = get(ZenSettingKeys.PAUSE_TRIGGER_MIN) * 60_000L

    @JvmStatic
    fun intentWaitSeconds() = get(ZenSettingKeys.INTENT_WAIT_S)

    @JvmStatic
    fun intentBackgroundMs() = get(ZenSettingKeys.INTENT_BACKGROUND_MIN) * 60_000L
}
