package org.schabi.newpipe.zen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

/**
 * Shown instead of starting a video while the mandatory pause runs: explains why, counts down and
 * offers no way to skip. Browsing and search stay available (this screen just closes).
 */
class ZenPauseActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countdown: TextView
    private lateinit var message: TextView
    private lateinit var back: Button

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

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            setText(R.string.zen_pause_title)
            textSize = 26f
            gravity = Gravity.CENTER
        }
        message = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        countdown = TextView(this).apply {
            textSize = 56f
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.zen_pause_title)
        }
        back = Button(this).apply {
            setText(R.string.zen_pause_back)
            setOnClickListener { finish() }
        }
        // one quote per pause (zen-docs/sprueche.md), below the countdown; wraps, never pushes the
        // buttons away (the whole screen scrolls if a long quote does not fit)
        val quote = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            setLineSpacing(0f, 1.15f)
            setPadding(0, pad, 0, 0)
            text = ZenQuotes.of(this@ZenPauseActivity)
                .forPause(ZenPause.pauseSeq(this@ZenPauseActivity), ZenQuotes.builtIn(this@ZenPauseActivity))
            visibility = if (text.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
        root.addView(title)
        root.addView(message)
        root.addView(countdown)
        root.addView(
            quote,
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
            message.setText(R.string.zen_pause_over)
            countdown.text = ""
            back.setText(R.string.zen_pause_ok)
            return
        }
        val minutes = (ZenPause.playedMs(this) / 60_000L).toInt()
        message.text = getString(R.string.zen_pause_message, minutes)
        val secs = (left + 999) / 1000
        countdown.text = String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60)
    }
}
