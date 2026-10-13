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
    val accessedMillis: Long
) {
    companion object {
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

/** Storage of [ClassEntry]s (SQLite on the device, in memory in tests). */
interface ClassStore {
    fun get(key: String): ClassEntry?
    fun put(e: ClassEntry)
    fun touch(keys: Collection<String>, nowMillis: Long)
    fun purgeAccessedBefore(millis: Long): Int
    fun clear()
    fun recent(limit: Int): List<ClassEntry>
    fun count(): Int
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
    fun needsWork(existing: ClassEntry?, level: String, model: String): Boolean = existing == null || existing.model != model || (existing.level == "T" && level == "D")

    companion object {
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
                "tone TEXT, top TEXT, weight REAL, tokens INTEGER, ms INTEGER, created INTEGER, accessed INTEGER)"
        )
        db.execSQL("CREATE INDEX cls_created ON cls(created)")
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
        accessedMillis = c.getLong(12)
    )

    private val cols = "k, title, channel, level, model, cats, tone, top, weight, tokens, ms, created, accessed"

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

    override fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM cls", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
