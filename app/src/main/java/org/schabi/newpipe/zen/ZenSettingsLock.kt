package org.schabi.newpipe.zen

/** Timing constants of the settings lock (see zen-docs/rules.md and spec-einstellungs-sperre.md). */
object ZenSettingsRules {
    /** Lock after a loosening, and delay until a loosening takes effect (device uptime). */
    const val LOCK_MS = 24 * 60 * 60 * 1000L

    /** Window after a confirmed change in which it can be reverted exactly. */
    const val UNDO_MS = 5 * 60 * 1000L

    /** A first-time setup that is never finished counts as finished after this much uptime. */
    const val SETUP_MAX_MS = 24 * 60 * 60 * 1000L

    /** Wait time of the confirmation dialog. */
    const val CONFIRM_WAIT_S = 3
}

/**
 * The 24 hour lock on the Zen settings. All time keeping uses [ZenCountdown] (device uptime only,
 * independent of the wall clock, switched-off time does not count), so setting the clock or
 * rebooting never shortens anything (fail-closed).
 *
 * Rules (per setting, see [ZenSettingDef.isStricter]):
 *  - Stricter: effective immediately, always allowed, never starts or extends the lock.
 *  - Looser: only while no lock runs. Becomes a PENDING change that takes effect when the lock
 *    ends 24 h later; the same confirmation starts that lock. Until then it can be cancelled
 *    ([cancelPending]) and stricter changes remain possible.
 *  - Setting a value back to the effective one cancels a pending change of that setting.
 *  - Undo window after every confirmed change: [undo] restores the exact previous state (values,
 *    pending changes, and the lock if this change started it); it never starts a lock itself.
 *  - First-time setup: changes are free (no lock, no undo) until [completeSetup] ("Done"), the
 *    first playback, or [ZenSettingsRules.SETUP_MAX_MS] of uptime; completing starts the lock.
 */
class ZenSettingsLock(
    private val store: ZenKeyValueStore,
    private val lockCd: ZenCountdown,
    private val undoCd: ZenCountdown,
    private val setupCd: ZenCountdown,
    private val log: ((String, String) -> Unit)? = null,
    private val lockMs: Long = ZenSettingsRules.LOCK_MS,
    private val undoMs: Long = ZenSettingsRules.UNDO_MS,
    private val setupMaxMs: Long = ZenSettingsRules.SETUP_MAX_MS
) {
    enum class Reason { LOCKED, OUT_OF_RANGE, UNKNOWN_KEY, INVALID_COMBINATION }

    sealed class Result {
        object NoChange : Result()
        data class Rejected(val reason: Reason, val detail: String = "") : Result()
        data class Applied(
            val immediate: List<String>,
            val pending: List<String>,
            val lockStarted: Boolean,
            val setupMode: Boolean
        ) : Result()
    }

    data class Snapshot(
        val setupMode: Boolean,
        val lockRemainingMs: Long,
        val undoRemainingMs: Long,
        val pending: Map<String, Int>
    )

    private fun kEff(k: String) = "zs_$k"
    private fun kPend(k: String) = "zp_$k"

    @Synchronized
    fun effective(key: String): Int {
        val def = ZenSettingDefs.get(key) ?: throw IllegalArgumentException(key)
        applyDue()
        val v = store.getInt(kEff(key), def.default)
        return if (def.inRange(v)) v else def.default
    }

    @Synchronized
    fun effectiveAll(): Map<String, Int> {
        applyDue()
        return ZenSettingDefs.all.associate { it.key to readEff(it) }
    }

    private fun readEff(def: ZenSettingDef): Int {
        val v = store.getInt(kEff(def.key), def.default)
        return if (def.inRange(v)) v else def.default
    }

    private fun readPending(): Map<String, Int> {
        val m = LinkedHashMap<String, Int>()
        ZenSettingDefs.all.forEach { d ->
            val v = store.getInt(kPend(d.key), Int.MIN_VALUE)
            if (v != Int.MIN_VALUE && d.inRange(v)) m[d.key] = v
        }
        return m
    }

    @Synchronized
    fun pending(): Map<String, Int> {
        applyDue()
        return readPending()
    }

    /** Pending changes take effect once the lock countdown has run out. */
    private fun applyDue() {
        val pend = readPending()
        if (pend.isEmpty()) return
        if (lockCd.remainingMs() > 0) return
        pend.forEach { (k, v) -> store.put(kEff(k) to v, kPend(k) to null) }
        clearUndo()
        log?.invoke("settings_pending_applied", pend.entries.joinToString(",") { "${it.key}=${it.value}" })
    }

    @Synchronized
    fun isSetupMode(): Boolean {
        if (store.getInt(K_SETUP_DONE, 0) == 1) return false
        if (store.getInt(K_SETUP_STARTED, 0) == 0) {
            setupCd.start(setupMaxMs)
            store.put(K_SETUP_STARTED to 1)
            return true
        }
        if (setupCd.remainingMs() <= 0) {
            finishSetup("timeout")
            return false
        }
        return true
    }

    /** "Done" in the first-time setup. Starts the lock. No-op when the setup is over already. */
    @Synchronized
    fun completeSetup() {
        if (isSetupMode()) finishSetup("done")
    }

    /** Called when the first video starts: a setup that was never finished ends here. */
    @Synchronized
    fun onPlaybackStarted() {
        if (isSetupMode()) finishSetup("playback")
    }

    private fun finishSetup(reason: String) {
        store.put(K_SETUP_DONE to 1)
        setupCd.clear()
        lockCd.start(lockMs)
        log?.invoke("settings_setup_done", "reason=$reason")
    }

    @Synchronized
    fun snapshot(): Snapshot {
        applyDue()
        val setup = isSetupMode()
        return Snapshot(setup, lockCd.remainingMs(), undoCd.remainingMs(), readPending())
    }

    @Synchronized
    fun isLocked(): Boolean = !isSetupMode() && lockCd.remainingMs() > 0

    /** Classification of one requested value relative to the effective one. */
    enum class Kind { UNCHANGED, STRICTER, LOOSER, CANCEL_PENDING }

    @Synchronized
    fun classify(key: String, value: Int): Kind {
        val def = ZenSettingDefs.get(key) ?: return Kind.UNCHANGED
        applyDue()
        val eff = readEff(def)
        return when {
            value == eff -> if (store.getInt(kPend(key), Int.MIN_VALUE) != Int.MIN_VALUE) Kind.CANCEL_PENDING else Kind.UNCHANGED
            def.isStricter(eff, value) -> Kind.STRICTER
            else -> Kind.LOOSER
        }
    }

    /**
     * Applies a group of changes (one confirmation). Settings not contained stay untouched.
     * Check with [classify] first to show the summary.
     */
    @Synchronized
    fun apply(changes: Map<String, Int>): Result {
        applyDue()
        for ((k, v) in changes) {
            val d = ZenSettingDefs.get(k) ?: return Result.Rejected(Reason.UNKNOWN_KEY, k)
            if (!d.inRange(v)) return Result.Rejected(Reason.OUT_OF_RANGE, k)
        }
        val eff = ZenSettingDefs.all.associate { it.key to readEff(it) }
        val setup = isSetupMode()
        val kinds = changes.mapValues { (k, v) -> classify(k, v) }.filterValues { it != Kind.UNCHANGED }
        if (kinds.isEmpty()) return Result.NoChange
        val pendOld = readPending()
        // in the first-time setup a looser value is not pending but applied at once
        val loosen = if (setup) emptySet() else kinds.filterValues { it == Kind.LOOSER }.keys
        val now = changes.filterKeys { it in kinds && it !in loosen }
        val effAfter = eff + now
        val pendAfter = if (setup) emptyMap() else pendOld.filterKeys { it !in changes } + changes.filterKeys { it in loosen }
        if (loosen.isNotEmpty() && lockCd.remainingMs() > 0) {
            return Result.Rejected(Reason.LOCKED, loosen.first())
        }
        ZenSettingDefs.combinationError(effAfter)?.let { return Result.Rejected(Reason.INVALID_COMBINATION, it) }
        ZenSettingDefs.combinationError(effAfter + pendAfter)?.let { return Result.Rejected(Reason.INVALID_COMBINATION, it) }

        val startsLock = loosen.isNotEmpty()
        if (!setup) saveUndo(eff, pendOld, startsLock)
        val entries = ArrayList<Pair<String, Any?>>()
        effAfter.forEach { (k, v) -> if (eff[k] != v) entries.add(kEff(k) to v) }
        // pending: removed for every key touched here, set for the loosened ones
        changes.keys.forEach { entries.add(kPend(it) to null) }
        loosen.forEach { entries.add(kPend(it) to changes.getValue(it)) }
        store.put(*entries.toTypedArray())
        if (startsLock) lockCd.start(lockMs)
        if (!setup) undoCd.start(undoMs)
        val immediate = kinds.filterValues { it != Kind.CANCEL_PENDING && it != Kind.UNCHANGED }.keys.filter { it !in loosen }
        val cancelled = kinds.filterValues { it == Kind.CANCEL_PENDING }.keys.toList()
        log?.invoke(
            "settings_change",
            "immediate=${immediate.joinToString(",") { "$it=${changes[it]}" }} " +
                "pending=${loosen.joinToString(",") { "$it=${changes[it]}" }} " +
                "cancelled=${cancelled.joinToString(",")} setup=$setup lock=$startsLock"
        )
        return Result.Applied(immediate + cancelled, loosen.toList(), startsLock, setup)
    }

    /** Drops every pending loosening. The lock keeps running (cancelling never shortens it). */
    @Synchronized
    fun cancelPending(): Boolean {
        applyDue()
        val pend = readPending()
        if (pend.isEmpty()) return false
        store.put(*pend.keys.map { kPend(it) to null }.toTypedArray())
        clearUndo()
        log?.invoke("settings_pending_cancelled", pend.entries.joinToString(",") { "${it.key}=${it.value}" })
        return true
    }

    private fun saveUndo(eff: Map<String, Int>, pend: Map<String, Int>, startedLock: Boolean) {
        val e = eff.entries.joinToString(",") { "${it.key}=${it.value}" }
        val p = pend.entries.joinToString(",") { "${it.key}=${it.value}" }
        store.put(K_UNDO to "${if (startedLock) 1 else 0}|$e|$p")
    }

    private fun clearUndo() {
        undoCd.clear()
        store.put(K_UNDO to null)
    }

    private fun parse(s: String): Map<String, Int> = s.split(',').filter { '=' in it }.associate {
        val (k, v) = it.split('=', limit = 2)
        k to (v.toIntOrNull() ?: Int.MIN_VALUE)
    }

    @Synchronized
    fun undoRemainingMs(): Long {
        val r = undoCd.remainingMs()
        if (r <= 0 && store.getString(K_UNDO, null) != null) store.put(K_UNDO to null)
        return r
    }

    /** Reverts the last confirmed change exactly. False if the window is over. */
    @Synchronized
    fun undo(): Boolean {
        applyDue()
        if (undoRemainingMs() <= 0) return false
        val raw = store.getString(K_UNDO, null) ?: return false
        val parts = raw.split('|')
        if (parts.size < 3) return false
        val startedLock = parts[0] == "1"
        val eff = parse(parts[1])
        val pend = parse(parts[2])
        val entries = ArrayList<Pair<String, Any?>>()
        ZenSettingDefs.all.forEach { d ->
            eff[d.key]?.takeIf { d.inRange(it) }?.let { entries.add(kEff(d.key) to it) }
            entries.add(kPend(d.key) to pend[d.key]?.takeIf { d.inRange(it) })
        }
        store.put(*entries.toTypedArray())
        if (startedLock) lockCd.clear()
        clearUndo()
        log?.invoke("settings_undo", "restoredLockCleared=$startedLock")
        return true
    }

    companion object {
        private const val K_SETUP_DONE = "zs_setup_done"
        private const val K_SETUP_STARTED = "zs_setup_started"
        private const val K_UNDO = "zs_undo"
    }
}
