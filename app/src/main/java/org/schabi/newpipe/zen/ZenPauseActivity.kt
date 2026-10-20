package org.schabi.newpipe.zen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

/**
 * Shown instead of starting a video while the mandatory pause (or the short pause after a short
 * video, D62) runs: explains why, counts down and offers no way to skip. Browsing and search stay available (this screen just closes).
 */
class ZenPauseActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countdown: TextView
    private lateinit var message: TextView
    private lateinit var back: Button

    // TalkBack: what was said last (minutes left, message), so a tick does not repeat it
    private var spokenMinutes = -1
    private var shownMessage: String? = null

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        // calm reference screen (spec-design.md §5): status bar = background, theme text colors
        val attrs = obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowBackground,
                android.R.attr.textColorPrimary,
                android.R.attr.textColorSecondary,
                androidx.appcompat.R.attr.colorAccent
            )
        )
        val background = attrs.getColor(0, 0)
        val textPrimary = attrs.getColorStateList(1)
        val textSecondary = attrs.getColorStateList(2)
        val accent = attrs.getColor(3, 0)
        attrs.recycle()
        window.statusBarColor = background
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            ThemeHelper.isLightThemeSelected(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        val ring = ImageView(this).apply {
            setImageResource(R.drawable.zen_splash_ring)
            setColorFilter(accent)
            alpha = 0.7f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val title = TextView(this).apply {
            setText(R.string.zen_pause_title)
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            setPadding(0, pad, 0, 0)
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        }
        message = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setTextColor(textSecondary)
            setPadding(0, pad, 0, pad)
            // announced when it changes (the end of the pause), never on every tick
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        countdown = TextView(this).apply {
            textSize = 52f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            setTextColor(textPrimary)
            // read as "5 minutes of pause left" (updated per minute in update()), not "4:59"
        }
        back = AppCompatButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            setText(R.string.zen_pause_back)
            setTextColor(accent)
            setOnClickListener { finish() }
        }
        // one quote per pause (zen-docs/sprueche.md), below the countdown; wraps, never pushes the
        // buttons away (the whole screen scrolls if a long quote does not fit)
        val (quoteText, attribution) = ZenQuotes.splitAttribution(
            ZenQuotes.of(this).forPause(ZenPause.pauseSeq(this), ZenQuotes.builtIn(this)).orEmpty()
        )
        val quote = TextView(this).apply {
            textSize = 16f
            setTextColor(textPrimary)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            setLineSpacing(0f, 1.15f)
            setPadding(0, pad, 0, 0)
            text = quoteText
            visibility = if (quoteText.isEmpty()) View.GONE else View.VISIBLE
        }
        // source line: upright, smaller and muted, so it does not read as part of the quote
        val source = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(textSecondary)
            setPadding(0, pad / 2, 0, 0)
            text = attribution?.let { "— $it" }
            visibility = if (attribution == null || quoteText.isEmpty()) View.GONE else View.VISIBLE
        }
        root.addView(ring, LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()))
        root.addView(title)
        root.addView(message)
        root.addView(countdown)
        root.addView(
            quote,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        root.addView(
            source,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        root.addView(
            back,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad * 2 }
        )
        setContentView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(root)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun update() {
        val left = ZenPause.remainingMs(this)
        if (left <= 0) {
            setMessage(getString(R.string.zen_pause_over))
            countdown.text = ""
            countdown.contentDescription = null
            back.setText(R.string.zen_pause_ok)
            return
        }
        if (ZenPause.isShortPause(this)) {
            setMessage(getString(R.string.zen_pause_short_message))
        } else {
            val minutes = (ZenPause.playedMs(this) / 60_000L).toInt()
            setMessage(getString(R.string.zen_pause_message, minutes))
        }
        val secs = (left + 999) / 1000
        countdown.text = String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60)
        val spoken = ZenPauseRules.spokenMinutesLeft(left)
        if (spoken != spokenMinutes) {
            val text = getString(
                R.string.zen_pause_a11y_left,
                resources.getQuantityString(R.plurals.zen_minutes, spoken, spoken)
            )
            countdown.contentDescription = text
            // one announcement per minute (the first value is read with the screen)
            if (spokenMinutes >= 0) countdown.announceForAccessibility(text)
            spokenMinutes = spoken
        }
    }

    /** Sets the message only when it changes (it is a live region). */
    private fun setMessage(text: String) {
        if (text == shownMessage) return
        shownMessage = text
        message.text = text
    }
}
