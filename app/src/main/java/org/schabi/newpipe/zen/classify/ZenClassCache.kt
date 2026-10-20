/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.Locale

/** One cached classification. Categories/tone are what the rules returned (no category = empty map). */
data class ClassEntry(
    /** `serviceId:url` (see [ZenClassCache.keyOf]). */
    val key: String,
    val title: String,
    val channel: String,
    /** "T" = title + channel only, "D" = with details (description/tags/category). */
    val level: String,
    val model: String,
    val cats: Map<String, Double>,
    val tone: String?,
    /** Strongest themes/formats for the "why" line. */
    val top: List<Pair<String, Double>>,
    val weight: Double,
    val tokens: Int,
    /** Computation time (tokenizer + encoder + heads) in ms. */
    val ms: Long,
    val createdMillis: Long,
    val accessedMillis: Long,
    /**
     * The title-only (level T) result this entry replaced when it was computed again with details
     * ([encodeSnapshot]); null if there was none. Only for the review/export (T and D side by side).
     */
    val previousT: String? = null,
    /**
     * The model's values for the five practice themes (D61, [org.schabi.newpipe.zen.ZenPraxisThemes]),
     * only those >= 0.05; null = not recorded (entry of an older version), empty = none.
     */
    val praxisThemes: Map<String, Double>? = null
) {
    /** A stored earlier result: tone, weight, categories and top themes/formats. */
    data class Snapshot(val tone: String?, val weight: Double, val cats: Map<String, Double>, val top: List<Pair<String, Double>>)

    companion object {
        fun encodeSnapshot(e: ClassEntry) = "${e.tone.orEmpty()};${fmt(e.weight)};${encodeMap(e.cats)};${encodePairs(e.top)}"

        fun decodeSnapshot(s: String?): Snapshot? {
            val p = s?.split(';') ?: return null
            if (p.size != 4) return null
            return Snapshot(p[0].ifEmpty { null }, p[1].toDoubleOrNull() ?: return null, decodeMap(p[2]), decodePairs(p[3]))
        }

        fun encodeMap(m: Map<String, Double>) = m.entries.joinToString(",") { "${it.key}=${fmt(it.value)}" }

        fun encodePairs(p: List<Pair<String, Double>>) = p.joinToString(",") { "${it.first}=${fmt(it.second)}" }

        fun decodePairs(s: String?): List<Pair<String, Double>> = s.orEmpty().split(',').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to (it.substring(i + 1).toDoubleOrNull() ?: return@mapNotNull null)
        }

        fun decodeMap(s: String?): Map<String, Double> = LinkedHashMap<String, Double>().apply { decodePairs(s).forEach { put(it.first, it.second) } }

        private fun fmt(v: Double) = String.format(Locale.ROOT, "%.3f", v)
    }
}

/**
 * The user's own judgement of a classification (review list, "correct" / "not correct" with the
 * right categories). Only stored and exported for training elsewhere; it never changes the feed,
 * a weight or a lock. [cats] = the categories the user chose ("correct": the model's categories at
 * that moment; empty = "none").
 */
data class UserLabel(
    val key: String,
    val correct: Boolean,
    val cats: List<String>,
    val title: String,
    val channel: String,
    val atMillis: Long
) {
    companion object {
        /** The seven categories of the rules (order of the review dialog). */
        @JvmField
        val CATEGORIES = listOf("praxis", "ruhe", "psyche", "bildung", "koerper", "aktuelles", "zerstreuung")
    }
}

/**
 * The user's own practice themes of a video (correction in the review list, D61): [mask] of
 * [org.schabi.newpipe.zen.ZenPraxisTheme] bits, 0 = "none of them". It only groups the practice
 * tab; it never changes a lock, a limit or a pause. [src] "U" = user. Kept without expiry, exported.
 */
data class PraxisThemeLabel(
    val key: String,
    val mask: Int,
    val title: String,
    val channel: String,
    val atMillis: Long,
    val src: String = SRC_USER
) {
    companion object {
        const val SRC_USER = "U"
    }
}

/**
 * Detected language of a video (feed language filter). Kept separately from [ClassEntry] because
 * it works without the classifier model. [channel] = channel key of the feed (uploader URL or name).
 * [guess] = the stored result; [signals] = raw detail signals (if the details were loaded), from
 * which the current rule re-evaluates the language without a new request.
 */
data class LangEntry(
    val key: String,
    val channel: String,
    val title: String,
    val channelName: String,
    val guess: org.schabi.newpipe.zen.ZenLangGuess,
    val atMillis: Long,
    val signals: org.schabi.newpipe.zen.ZenLangSignals? = null
)

/** Storage of [ClassEntry]s (SQLite on the device, in memory in tests). */
interface ClassStore {
    fun get(key: String): ClassEntry?
    fun put(e: ClassEntry)
    fun touch(keys: Collection<String>, nowMillis: Long)
    fun purgeAccessedBefore(millis: Long): Int
    fun clear()
    fun recent(limit: Int): List<ClassEntry>
    fun count(): Int

    fun label(key: String): UserLabel?
    fun putLabel(l: UserLabel)
    fun removeLabel(key: String)

    /** All labels, newest first. */
    fun labels(): List<UserLabel>

    fun praxisTheme(key: String): PraxisThemeLabel?
    fun praxisThemes(keys: Collection<String>): Map<String, PraxisThemeLabel>
    fun putPraxisTheme(l: PraxisThemeLabel)
    fun removePraxisTheme(key: String)

    /** All practice theme corrections, newest first. */
    fun allPraxisThemes(): List<PraxisThemeLabel>

    /** Entries for [keys] (one query per chunk, e.g. the practice tab). */
    fun getAll(keys: Collection<String>): Map<String, ClassEntry>

    fun langs(keys: Collection<String>): Map<String, LangEntry>
    fun putLangs(list: Collection<LangEntry>)

    /** The newest [perChannel] language entries of each channel. */
    fun langsByChannel(channels: Collection<String>, perChannel: Int): Map<String, List<LangEntry>>
    fun recentLangs(limit: Int): List<LangEntry>
    fun purgeLangsBefore(millis: Long): Int
}

class MemoryClassStore : ClassStore {
    private val map = LinkedHashMap<String, ClassEntry>()

    @Synchronized override fun get(key: String) = map[key]

    @Synchronized override fun put(e: ClassEntry) {
        map[e.key] = e
    }

    @Synchronized override fun touch(keys: Collection<String>, nowMillis: Long) {
        keys.forEach { k -> map[k]?.let { map[k] = it.copy(accessedMillis = nowMillis) } }
    }

    @Synchronized override fun purgeAccessedBefore(millis: Long): Int {
        val old = map.values.filter { it.accessedMillis < millis }.map { it.key }
        old.forEach { map.remove(it) }
        return old.size
    }

    @Synchronized override fun clear() = map.clear()

    @Synchronized override fun recent(limit: Int) = map.values.sortedByDescending { it.createdMillis }.take(limit)

    @Synchronized override fun count() = map.size

    private val lbl = LinkedHashMap<String, UserLabel>()

    @Synchronized override fun label(key: String) = lbl[key]

    @Synchronized override fun putLabel(l: UserLabel) {
        lbl[l.key] = l
    }

    @Synchronized override fun removeLabel(key: String) {
        lbl.remove(key)
    }

    @Synchronized override fun labels() = lbl.values.sortedByDescending { it.atMillis }

    private val pxt = LinkedHashMap<String, PraxisThemeLabel>()

    @Synchronized override fun praxisTheme(key: String) = pxt[key]

    @Synchronized override fun praxisThemes(keys: Collection<String>) = keys.mapNotNull { k -> pxt[k]?.let { k to it } }.toMap()

    @Synchronized override fun putPraxisTheme(l: PraxisThemeLabel) {
        pxt[l.key] = l
    }

    @Synchronized override fun removePraxisTheme(key: String) {
        pxt.remove(key)
    }

    @Synchronized override fun allPraxisThemes() = pxt.values.sortedByDescending { it.atMillis }

    @Synchronized override fun getAll(keys: Collection<String>) = keys.mapNotNull { k -> map[k]?.let { k to it } }.toMap()

    private val lng = LinkedHashMap<String, LangEntry>()

    @Synchronized override fun langs(keys: Collection<String>) = keys.mapNotNull { k -> lng[k]?.let { k to it } }.toMap()

    @Synchronized override fun putLangs(list: Collection<LangEntry>) {
        list.forEach { lng[it.key] = it }
    }

    @Synchronized override fun langsByChannel(channels: Collection<String>, perChannel: Int) = channels.toSet().associateWith { ch ->
        lng.values.filter { it.channel == ch }.sortedByDescending { it.atMillis }.take(perChannel)
    }

    @Synchronized override fun recentLangs(limit: Int) = lng.values.sortedByDescending { it.atMillis }.take(limit)

    @Synchronized override fun purgeLangsBefore(millis: Long): Int {
        val old = lng.values.filter { it.atMillis < millis }.map { it.key }
        old.forEach { lng.remove(it) }
        return old.size
    }
}

/**
 * Classification cache: separate SQLite file under `noBackupFilesDir` (not in the NewPipe
 * database, so no upstream migration; no auto-backup, not in export/import). Entries are kept
 * [RETENTION_MS] after the last access.
 */
class ZenClassCache(private val store: ClassStore) : ClassStore by store {
    /**
     * True if [existing] must be (re)computed for input [level] with model [model]: missing,
     * other model version, or title-only while details are available now.
     */
    fun needsWork(existing: ClassEntry?, level: String, model: String): Boolean = needsWork(existing, level, model, false)

    /**
     * [wantPraxis]: the practice tab needs the practice theme values; an entry of an older version
     * without them is computed again at the same level (never D downgraded to T).
     */
    fun needsWork(existing: ClassEntry?, level: String, model: String, wantPraxis: Boolean): Boolean = existing == null ||
        existing.model != model ||
        (existing.level == "T" && level == "D") ||
        (wantPraxis && existing.praxisThemes == null && !(existing.level == "D" && level == "T"))

    companion object {
        /**
         * The earlier title-only result to keep when [old] is computed again at [level] with model
         * [model]: T -> D of the same model keeps the T result, D -> D keeps what D had kept.
         */
        fun previousTFor(old: ClassEntry?, level: String, model: String): String? = when {
            old == null || old.model != model -> null
            old.level == "T" && level == "D" -> ClassEntry.encodeSnapshot(old)
            old.level == "D" && level == "D" -> old.previousT
            else -> null
        }

        /**
         * Schema additions made in `onOpen` (no version bump, older versions keep reading the file):
         * the statements for a database whose `cls` table has [clsColumns]. Every existing entry
         * stays valid; `pxt` (practice theme values) is NULL for old entries = not recorded.
         */
        fun additions(clsColumns: Set<String>): List<String> = listOfNotNull(
            if ("tprev" !in clsColumns) "ALTER TABLE cls ADD COLUMN tprev TEXT" else null,
            if ("pxt" !in clsColumns) "ALTER TABLE cls ADD COLUMN pxt TEXT" else null,
            "CREATE TABLE IF NOT EXISTS lbl (k TEXT PRIMARY KEY, verdict TEXT, cats TEXT, title TEXT, channel TEXT, at INTEGER)",
            "CREATE TABLE IF NOT EXISTS lang (k TEXT PRIMARY KEY, channel TEXT, title TEXT, chname TEXT, lang TEXT, conf REAL, " +
                "src TEXT, scores TEXT, at INTEGER)",
            "CREATE INDEX IF NOT EXISTS lang_channel ON lang(channel, at)",
            "CREATE TABLE IF NOT EXISTS praxis_theme (k TEXT PRIMARY KEY, themes INTEGER, src TEXT, title TEXT, channel TEXT, ts INTEGER)"
        )

        /** Practice theme values of a cached row: NULL column = not recorded. */
        fun decodePraxisThemes(s: String?): Map<String, Double>? = s?.let { ClassEntry.decodeMap(it) }

        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val DB_FILE = "zen_class_cache.db"

        fun keyOf(serviceId: Int, url: String) = "$serviceId:$url"

        @Volatile
        private var instance: ZenClassCache? = null

        @JvmStatic
        fun get(context: Context): ZenClassCache = instance ?: synchronized(this) {
            instance ?: ZenClassCache(SqliteClassStore(context.applicationContext)).also { instance = it }
        }
    }
}

private class SqliteClassStore(context: Context) :
    SQLiteOpenHelper(context, File(context.noBackupFilesDir, ZenClassCache.DB_FILE).path, null, 1),
    ClassStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE cls (k TEXT PRIMARY KEY, title TEXT, channel TEXT, level TEXT, model TEXT, cats TEXT, " +
                "tone TEXT, top TEXT, weight REAL, tokens INTEGER, ms INTEGER, created INTEGER, accessed INTEGER, tprev TEXT, pxt TEXT)"
        )
        db.execSQL("CREATE INDEX cls_created ON cls(created)")
    }

    /**
     * Additions after schema version 1 are made here instead of a version bump, so the file stays
     * readable for older ZenPipe versions (they ignore the extra column/table) and every existing
     * entry stays valid: column `tprev` (earlier title-only result), table `lbl` (user labels), table
     * `lang` (feed languages) and its column `sig` (raw detail signals). When `sig` is added, the older
     * detail languages (`src` "D") are dropped: they did not record whether they came from the audio
     * track or only from the automatic captions; the feed re-detects them from the title.
     */
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (db.isReadOnly) return
        val cols = db.rawQuery("PRAGMA table_info(cls)", null).use { c ->
            val out = HashSet<String>()
            while (c.moveToNext()) out.add(c.getString(1))
            out
        }
        ZenClassCache.additions(cols).forEach { db.execSQL(it) }
        val langTable = db.rawQuery("PRAGMA table_info(lang)", null).use { c ->
            val out = HashSet<String>()
            while (c.moveToNext()) out.add(c.getString(1))
            out
        }
        if ("sig" !in langTable) {
            db.beginTransaction()
            try {
                db.execSQL("ALTER TABLE lang ADD COLUMN sig TEXT")
                db.execSQL("DELETE FROM lang WHERE src = 'D'")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS cls")
        onCreate(db)
    }

    private fun row(c: Cursor) = ClassEntry(
        key = c.getString(0),
        title = c.getString(1) ?: "",
        channel = c.getString(2) ?: "",
        level = c.getString(3) ?: "T",
        model = c.getString(4) ?: "",
        cats = ClassEntry.decodeMap(c.getString(5)),
        tone = c.getString(6),
        top = ClassEntry.decodePairs(c.getString(7)),
        weight = c.getDouble(8),
        tokens = c.getInt(9),
        ms = c.getLong(10),
        createdMillis = c.getLong(11),
        accessedMillis = c.getLong(12),
        previousT = c.getString(13),
        praxisThemes = ZenClassCache.decodePraxisThemes(c.getString(14))
    )

    private val cols = "k, title, channel, level, model, cats, tone, top, weight, tokens, ms, created, accessed, tprev, pxt"

    override fun get(key: String): ClassEntry? = readableDatabase.rawQuery("SELECT $cols FROM cls WHERE k = ?", arrayOf(key)).use {
        if (it.moveToFirst()) row(it) else null
    }

    override fun put(e: ClassEntry) {
        val v = ContentValues().apply {
            put("k", e.key)
            put("title", e.title)
            put("channel", e.channel)
            put("level", e.level)
            put("model", e.model)
            put("cats", ClassEntry.encodeMap(e.cats))
            put("tone", e.tone)
            put("top", ClassEntry.encodePairs(e.top))
            put("weight", e.weight)
            put("tokens", e.tokens)
            put("ms", e.ms)
            put("created", e.createdMillis)
            put("accessed", e.accessedMillis)
            put("tprev", e.previousT)
            put("pxt", e.praxisThemes?.let { ClassEntry.encodeMap(it) })
        }
        writableDatabase.insertWithOnConflict("cls", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun touch(keys: Collection<String>, nowMillis: Long) {
        if (keys.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            keys.forEach { db.execSQL("UPDATE cls SET accessed = ? WHERE k = ?", arrayOf<Any>(nowMillis, it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun purgeAccessedBefore(millis: Long): Int = writableDatabase.delete("cls", "accessed < ?", arrayOf(millis.toString()))

    override fun clear() {
        writableDatabase.delete("cls", null, null)
    }

    override fun recent(limit: Int): List<ClassEntry> = readableDatabase.rawQuery("SELECT $cols FROM cls ORDER BY created DESC LIMIT $limit", null).use {
        val out = ArrayList<ClassEntry>()
        while (it.moveToNext()) out.add(row(it))
        out
    }

    override fun getAll(keys: Collection<String>): Map<String, ClassEntry> {
        val out = HashMap<String, ClassEntry>()
        keys.distinct().chunked(SQL_CHUNK).forEach { part ->
            val q = part.joinToString(",") { "?" }
            readableDatabase.rawQuery("SELECT $cols FROM cls WHERE k IN ($q)", part.toTypedArray()).use {
                while (it.moveToNext()) row(it).let { e -> out[e.key] = e }
            }
        }
        return out
    }

    private val pxtCols = "k, themes, src, title, channel, ts"

    private fun pxtRow(c: Cursor) = PraxisThemeLabel(
        key = c.getString(0),
        mask = c.getInt(1),
        src = c.getString(2) ?: PraxisThemeLabel.SRC_USER,
        title = c.getString(3) ?: "",
        channel = c.getString(4) ?: "",
        atMillis = c.getLong(5)
    )

    private fun pxtQuery(sql: String, args: Array<String>): List<PraxisThemeLabel> = readableDatabase.rawQuery(sql, args).use {
        val out = ArrayList<PraxisThemeLabel>()
        while (it.moveToNext()) out.add(pxtRow(it))
        out
    }

    override fun praxisTheme(key: String): PraxisThemeLabel? = pxtQuery("SELECT $pxtCols FROM praxis_theme WHERE k = ?", arrayOf(key)).firstOrNull()

    override fun praxisThemes(keys: Collection<String>): Map<String, PraxisThemeLabel> {
        val out = HashMap<String, PraxisThemeLabel>()
        keys.distinct().chunked(SQL_CHUNK).forEach { part ->
            val q = part.joinToString(",") { "?" }
            pxtQuery("SELECT $pxtCols FROM praxis_theme WHERE k IN ($q)", part.toTypedArray()).forEach { out[it.key] = it }
        }
        return out
    }

    override fun putPraxisTheme(l: PraxisThemeLabel) {
        val v = ContentValues().apply {
            put("k", l.key)
            put("themes", l.mask)
            put("src", l.src)
            put("title", l.title)
            put("channel", l.channel)
            put("ts", l.atMillis)
        }
        writableDatabase.insertWithOnConflict("praxis_theme", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun removePraxisTheme(key: String) {
        writableDatabase.delete("praxis_theme", "k = ?", arrayOf(key))
    }

    override fun allPraxisThemes(): List<PraxisThemeLabel> = pxtQuery("SELECT $pxtCols FROM praxis_theme ORDER BY ts DESC", emptyArray())

    override fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM cls", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private val labelCols = "k, verdict, cats, title, channel, at"

    private fun labelRow(c: Cursor) = UserLabel(
        key = c.getString(0),
        correct = c.getString(1) == VERDICT_CORRECT,
        cats = c.getString(2).orEmpty().split(',').filter { it.isNotEmpty() },
        title = c.getString(3) ?: "",
        channel = c.getString(4) ?: "",
        atMillis = c.getLong(5)
    )

    override fun label(key: String): UserLabel? = readableDatabase.rawQuery("SELECT $labelCols FROM lbl WHERE k = ?", arrayOf(key)).use {
        if (it.moveToFirst()) labelRow(it) else null
    }

    override fun putLabel(l: UserLabel) {
        val v = ContentValues().apply {
            put("k", l.key)
            put("verdict", if (l.correct) VERDICT_CORRECT else VERDICT_WRONG)
            put("cats", l.cats.joinToString(","))
            put("title", l.title)
            put("channel", l.channel)
            put("at", l.atMillis)
        }
        writableDatabase.insertWithOnConflict("lbl", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun removeLabel(key: String) {
        writableDatabase.delete("lbl", "k = ?", arrayOf(key))
    }

    override fun labels(): List<UserLabel> = readableDatabase.rawQuery("SELECT $labelCols FROM lbl ORDER BY at DESC", null).use {
        val out = ArrayList<UserLabel>()
        while (it.moveToNext()) out.add(labelRow(it))
        out
    }

    private val langCols = "k, channel, title, chname, lang, conf, src, scores, at, sig"

    private fun langRow(c: Cursor) = LangEntry(
        key = c.getString(0),
        channel = c.getString(1) ?: "",
        title = c.getString(2) ?: "",
        channelName = c.getString(3) ?: "",
        guess = org.schabi.newpipe.zen.ZenLangGuess(
            c.getString(4),
            c.getDouble(5),
            org.schabi.newpipe.zen.ZenLangGuess.decodeScores(c.getString(7)),
            c.getString(6) ?: "T"
        ),
        atMillis = c.getLong(8),
        signals = org.schabi.newpipe.zen.ZenLangSignals.decode(c.getString(9))
    )

    private fun langQuery(sql: String, args: Array<String>): List<LangEntry> = readableDatabase.rawQuery(sql, args).use {
        val out = ArrayList<LangEntry>()
        while (it.moveToNext()) out.add(langRow(it))
        out
    }

    override fun langs(keys: Collection<String>): Map<String, LangEntry> {
        val out = HashMap<String, LangEntry>()
        keys.distinct().chunked(SQL_CHUNK).forEach { part ->
            val q = part.joinToString(",") { "?" }
            langQuery("SELECT $langCols FROM lang WHERE k IN ($q)", part.toTypedArray()).forEach { out[it.key] = it }
        }
        return out
    }

    override fun putLangs(list: Collection<LangEntry>) {
        if (list.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            list.forEach { e ->
                val v = ContentValues().apply {
                    put("k", e.key)
                    put("channel", e.channel)
                    put("title", e.title)
                    put("chname", e.channelName)
                    put("lang", e.guess.lang)
                    put("conf", e.guess.confidence)
                    put("src", e.guess.source)
                    put("scores", org.schabi.newpipe.zen.ZenLangGuess.encodeScores(e.guess.scores))
                    put("at", e.atMillis)
                    put("sig", e.signals?.encode())
                }
                db.insertWithOnConflict("lang", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun langsByChannel(channels: Collection<String>, perChannel: Int): Map<String, List<LangEntry>> {
        val out = HashMap<String, MutableList<LangEntry>>()
        channels.distinct().chunked(SQL_CHUNK).forEach { part ->
            val q = part.joinToString(",") { "?" }
            langQuery("SELECT $langCols FROM lang WHERE channel IN ($q) ORDER BY at DESC", part.toTypedArray()).forEach {
                val l = out.getOrPut(it.channel) { ArrayList() }
                if (l.size < perChannel) l.add(it)
            }
        }
        return out
    }

    override fun recentLangs(limit: Int): List<LangEntry> = langQuery("SELECT $langCols FROM lang ORDER BY at DESC LIMIT $limit", emptyArray())

    override fun purgeLangsBefore(millis: Long): Int = writableDatabase.delete("lang", "at < ?", arrayOf(millis.toString()))

    private companion object {
        const val SQL_CHUNK = 400
        const val VERDICT_CORRECT = "correct"
        const val VERDICT_WRONG = "wrong"
    }
}
