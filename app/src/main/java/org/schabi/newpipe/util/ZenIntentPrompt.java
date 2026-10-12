package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.CountDownTimer;
import android.os.SystemClock;

import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;

/**
 * ZenPipe: asks "What do you want to watch?" on app start, when an external/shared link is
 * opened and when the app returns from a long time in the background. At most once per
 * {@link #MIN_INTERVAL_MS} and never while media is playing. The "Continue" button only
 * becomes enabled after a short delay, "Not now" closes the app.
 */
public final class ZenIntentPrompt {
    private static final int WAIT_SECONDS = 5;
    private static final long MIN_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long BACKGROUND_THRESHOLD_MS = 5 * 60 * 1000L;

    private static long lastAskedAt = -1;
    private static long backgroundedAt = -1;
    private static boolean dialogShowing = false;
    // ZenPipe: true once the user confirmed the latest prompt of this process. The feed edition
    // is only built then, so "Never mind" never uses up any of the daily cap.
    private static boolean confirmed = false;
    private static Runnable onConfirmedListener = null;

    public static boolean isConfirmed() {
        return confirmed && !dialogShowing;
    }

    // Called (main thread) when the user confirmed a prompt; used by the feed to build its edition.
    public static void setOnConfirmedListener(final Runnable listener) {
        onConfirmedListener = listener;
    }

    private ZenIntentPrompt() { }

    // App start: always ask (once per process).
    public static void showIfNeeded(final Activity activity) {
        if (lastAskedAt < 0) {
            show(activity);
        }
    }

    // Call from Activity.onStop().
    public static void onActivityStopped() {
        backgroundedAt = SystemClock.elapsedRealtime();
    }

    // Call from Activity.onStart(): ask again after a long time in the background.
    public static void onActivityStarted(final Activity activity) {
        final long stoppedAt = backgroundedAt;
        backgroundedAt = -1;
        if (stoppedAt >= 0
                && SystemClock.elapsedRealtime() - stoppedAt >= BACKGROUND_THRESHOLD_MS) {
            showIfAllowed(activity);
        }
    }

    // Call when an external/shared link is opened in the app.
    public static void onExternalLink(final Activity activity) {
        showIfAllowed(activity);
    }

    private static void showIfAllowed(final Activity activity) {
        if (dialogShowing || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (lastAskedAt >= 0
                && SystemClock.elapsedRealtime() - lastAskedAt < MIN_INTERVAL_MS) {
            return;
        }
        final AudioManager audio = (AudioManager) activity.getSystemService(
                Activity.AUDIO_SERVICE);
        if (audio != null && audio.isMusicActive()) {
            return; // never interrupt running playback (incl. background audio)
        }
        show(activity);
    }

    private static void show(final Activity activity) {
        lastAskedAt = SystemClock.elapsedRealtime();
        dialogShowing = true;
        confirmed = false;

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.zen_intent_title)
                .setMessage(R.string.zen_intent_message)
                .setCancelable(false)
                .setPositiveButton(R.string.zen_intent_continue, (d, which) -> confirmed = true)
                .setNegativeButton(R.string.zen_intent_not_now,
                        (d, which) -> activity.finishAffinity())
                .create();
        dialog.setOnDismissListener(d -> {
            dialogShowing = false;
            lastAskedAt = SystemClock.elapsedRealtime();
            if (confirmed && onConfirmedListener != null) {
                onConfirmedListener.run();
            }
        });
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
