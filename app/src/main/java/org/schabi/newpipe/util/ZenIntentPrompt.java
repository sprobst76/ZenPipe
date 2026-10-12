package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.CountDownTimer;

import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;

/**
 * ZenPipe: asks "What do you want to watch?" once per app start. The "Continue" button
 * only becomes enabled after a short delay, "Not now" closes the app.
 */
public final class ZenIntentPrompt {
    private static final int WAIT_SECONDS = 5;
    private static boolean shownInThisProcess = false;

    private ZenIntentPrompt() { }

    public static void showIfNeeded(final Activity activity) {
        if (shownInThisProcess) {
            return;
        }
        shownInThisProcess = true;

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.zen_intent_title)
                .setMessage(R.string.zen_intent_message)
                .setCancelable(false)
                .setPositiveButton(R.string.zen_intent_continue, null)
                .setNegativeButton(R.string.zen_intent_not_now,
                        (d, which) -> activity.finishAffinity())
                .create();
        dialog.show();

        final android.widget.Button continueButton =
                dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        continueButton.setEnabled(false);
        new CountDownTimer(WAIT_SECONDS * 1000L, 1000L) {
            @Override
            public void onTick(final long millisUntilFinished) {
                continueButton.setText(activity.getString(R.string.zen_intent_continue_wait,
                        (millisUntilFinished + 999) / 1000));
            }

            @Override
            public void onFinish() {
                continueButton.setText(R.string.zen_intent_continue);
                continueButton.setEnabled(true);
            }
        }.start();
    }
}
