package org.schabi.newpipe.zen

import android.content.Context
import org.schabi.newpipe.zen.classify.ClassStore
import org.schabi.newpipe.zen.classify.PraxisThemeLabel
import org.schabi.newpipe.zen.classify.ZenClassCache
import org.schabi.newpipe.zen.classify.ZenClassifierModel

/**
 * Reads and writes the practice themes of videos (D61) on the device: the user's corrections
 * (table `praxis_theme` in `zen_class_cache.db`) and the cached model values; the decision itself
 * is [ZenPraxisThemes.resolve]. Never touches a lock, a limit or a pause.
 */
object ZenPraxisThemeData {
    /** One video to group: [key] = [ZenClassCache.keyOf], [defaultMask] = default of its source entry. */
    data class Video(val key: String, val title: String?, val defaultMask: Int)

    /**
     * The themes of [videos] from [store]; model values only count if [modelReady] (without an
     * installed, working model only corrections, title rules and defaults work).
     */
    @JvmStatic
    fun resolveAll(store: ClassStore, videos: List<Video>, modelReady: Boolean): Map<String, ZenPraxisThemes.Resolved> {
        if (videos.isEmpty()) return emptyMap()
        val keys = videos.map { it.key }
        val user = store.praxisThemes(keys)
        val cached = if (modelReady) store.getAll(keys) else emptyMap()
        return videos.associate { v ->
            val e = cached[v.key]
            v.key to ZenPraxisThemes.resolve(user[v.key]?.mask, v.title, e?.praxisThemes, e?.level, v.defaultMask)
        }
    }

    @JvmStatic
    fun resolveAll(context: Context, videos: List<Video>): Map<String, ZenPraxisThemes.Resolved> = resolveAll(ZenClassCache.get(context), videos, ZenClassifierModel.isReady(context))

    /** The user's correction ([mask] 0 = none of them); local only, exported with the debug data. */
    @JvmStatic
    fun setCorrection(context: Context, key: String, mask: Int, title: String, channel: String) {
        val m = ZenPraxisThemes.sanitize(mask)
        ZenClassCache.get(context).putPraxisTheme(PraxisThemeLabel(key, m, title, channel, System.currentTimeMillis()))
        Zen.log(context).add("praxis_theme_label", "key=$key themes=${ZenPraxisThemes.keys(m)}")
    }

    @JvmStatic
    fun removeCorrection(context: Context, key: String) {
        ZenClassCache.get(context).removePraxisTheme(key)
        Zen.log(context).add("praxis_theme_label", "key=$key removed=true")
    }

    @JvmStatic
    fun correction(context: Context, key: String): PraxisThemeLabel? = ZenClassCache.get(context).praxisTheme(key)
}
