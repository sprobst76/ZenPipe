package org.schabi.newpipe.zen

import android.content.Context
import android.content.SharedPreferences

/** Minimal key-value storage so the Zen logic can be unit-tested without Android. */
interface ZenKeyValueStore {
    fun getString(key: String, default: String?): String?
    fun getLong(key: String, default: Long): Long
    fun getInt(key: String, default: Int): Int
    fun put(vararg entries: Pair<String, Any?>)
}

/** In-memory implementation (tests). */
class MemoryZenStore : ZenKeyValueStore {
    private val map = HashMap<String, Any?>()
    override fun getString(key: String, default: String?) = map[key] as? String ?: default
    override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
    override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
    override fun put(vararg entries: Pair<String, Any?>) {
        entries.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
    }
}

/**
 * ZenPipe state lives in its own SharedPreferences file ([FILE_NAME]), separate from NewPipe's
 * default preferences. Therefore NewPipe's data export/import and "reset settings" (which only
 * touch the default preferences) do not affect it, and it is excluded from Android auto-backup
 * (see res/xml/zen_backup_rules.xml and zen_data_extraction_rules.xml).
 */
class SharedPrefsZenStore(private val prefs: SharedPreferences) : ZenKeyValueStore {
    override fun getString(key: String, default: String?) = prefs.getString(key, default)
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun put(vararg entries: Pair<String, Any?>) {
        val e = prefs.edit()
        entries.forEach { (k, v) ->
            when (v) {
                null -> e.remove(k)
                is String -> e.putString(k, v)
                is Long -> e.putLong(k, v)
                is Int -> e.putInt(k, v)
                else -> throw IllegalArgumentException("Unsupported type for $k")
            }
        }
        e.apply()
    }
}

object Zen {
    const val FILE_NAME = "zen_state"

    @Volatile
    private var store: ZenKeyValueStore? = null

    @JvmStatic
    fun store(context: Context): ZenKeyValueStore {
        return store ?: synchronized(this) {
            store ?: SharedPrefsZenStore(
                context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            ).also { store = it }
        }
    }

    @JvmStatic
    fun clock(context: Context) = ZenClock(store(context), bootId = { bootCount(context) })

    /** Device reboot counter (API 24+), -1 if unavailable. */
    @JvmStatic
    fun bootCount(context: Context): Int = try {
        android.provider.Settings.Global.getInt(context.contentResolver, "boot_count", -1)
    } catch (e: Exception) {
        -1
    }

    @JvmStatic
    fun countdown(context: Context, name: String) = ZenCountdown(store(context), name, bootId = { bootCount(context) })

    @JvmStatic
    fun log(context: Context) = ZenLog(store(context), clock(context))
}
