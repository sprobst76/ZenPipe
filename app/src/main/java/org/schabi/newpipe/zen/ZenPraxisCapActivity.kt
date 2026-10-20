package org.schabi.newpipe.zen

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.notification.NotificationConstants

/**
 * The calm question of the soft practice cap ([ZenPraxisCap]): a small dialog over the player.
 * "Keep practising" closes it; "Pause" pauses the player (pausing is never an abort, so no
 * mandatory pause follows). Nothing is stopped by itself. Wording to be agreed on the device.
 */
class ZenPraxisCapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val minutes = ZenPraxisCap.markShown(this)
        val d = resources.displayMetrics.density
        val pad = (24 * d).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
        }
        box.addView(
            TextView(this).apply {
                setText(R.string.zen_praxis_cap_title)
                textSize = 20f
            }
        )
        box.addView(
            TextView(this).apply {
                text = getString(R.string.zen_praxis_cap_message, minutes)
                textSize = 16f
                setPadding(0, pad / 2, 0, pad / 2)
            }
        )
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        buttons.addView(
            Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                setText(R.string.zen_praxis_cap_pause)
                setOnClickListener {
                    ZenPraxisCap.onAnswer(this@ZenPraxisCapActivity, true)
                    // only pause a playing player (the broadcast toggles)
                    if (PlayerHolder.getInstance().isPlaying) {
                        sendBroadcast(Intent(NotificationConstants.ACTION_PLAY_PAUSE).setPackage(packageName))
                    }
                    finish()
                }
            }
        )
        buttons.addView(
            Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                setText(R.string.zen_praxis_cap_continue)
                setOnClickListener {
                    ZenPraxisCap.onAnswer(this@ZenPraxisCapActivity, false)
                    finish()
                }
            }
        )
        box.addView(buttons)
        setContentView(box)
        setFinishOnTouchOutside(false)
    }
}
