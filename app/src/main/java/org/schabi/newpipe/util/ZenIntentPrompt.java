package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.CountDownTimer;
import android.os.SystemClock;

import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;
import org.schabi.newpipe.zen.ZenPlayback;

/**
 * ZenPipe: asks "What do you want to watch?" on app start, when an external/shared link is
 * opened and when the app returns from a long time in the background. After a confirmed
 * prompt it is not repeated for {@link #MIN_INTERVAL_MS}; it is never shown while ZenPipe's OWN
 * player is playing (audio of other apps does not count). The "Continue" button only becomes
 * enabled after a short delay, "Not now" closes the app and the very next start asks again.
 * Background/popup playback started from a shared link goes through {@link #gate}.
 */
public final class ZenIntentPrompt {
    private static final int WAIT_SECONDS = 5;
    private static final long MIN_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long BACKGROUND_THRESHOLD_MS = 5 * 60 * 1000L;

    // elapsedRealtime of the last CONFIRMED prompt, -1 if the latest prompt was not confirmed
    private static long lastAskedAt = -1;
    private static long backgroundedAt = -1;
    private static AlertDialog currentDialog = null;
    private static Activity dialogActivity = null;
    // ZenPipe: true once the user confirmed the latest prompt of this process. The feed edition
    // is only built then, so "Never mind" never uses up any of the daily cap.
    private static boolean confirmed = false;
    private static Runnable onConfirmedListener = null;

    public static boolean isConfirmed() {
        return confirmed && !isDialogShowing();
    }

    // The dialog counts as showing only while its activity is alive (a recreated activity, e.g.
    // after rotation, loses the dialog and must ask again instead of silently skipping).
    private static boolean isDialogShowing() {
        return currentDialog != null && currentDialog.isShowing()
                && dialogActivity != null && !dialogActivity.isDestroyed()
                && !dialogActivity.isFinishing();
    }

    // Called (main thread) when the user confirmed a prompt; used by the feed to build its edition.
    public static void setOnConfirmedListener(final Runnable listener) {
        onConfirmedListener = listener;
    }

    private ZenIntentPrompt() { }

    // App start: ask until the user has confirmed once in this process. A restart after
    // "Never mind" (process still alive) asks again because nothing was confirmed.
    public static void showIfNeeded(final Activity activity) {
        if (!confirmed && !isDialogShowing()) {
            show(activity, activity::finishAffinity, null);
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

    // For playback that starts without MainActivity (shared link -> background/popup/video
    // player via RouterActivity): runs onProceed at once when no prompt is needed, otherwise
    // shows the prompt first. "Never mind" runs onDeclined.
    public static void gate(final Activity activity, final Runnable onProceed,
                            final Runnable onDeclined) {
        if (isSkippable()) {
            onProceed.run();
            return;
        }
        if (isDialogShowing()) {
            onDeclined.run(); // another prompt is open; never stack them
            return;
        }
        show(activity, onDeclined, onProceed);
    }

    // true: no (new) prompt needed right now
    private static boolean isSkippable() {
        if (ZenPlayback.isPlaying()) {
            return true; // never interrupt ZenPipe's own running playback
        }
        return confirmed && lastAskedAt >= 0
                && SystemClock.elapsedRealtime() - lastAskedAt < MIN_INTERVAL_MS;
    }

    private static void showIfAllowed(final Activity activity) {
        if (isDialogShowing() || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (isSkippable()) {
            return;
        }
        show(activity, activity::finishAffinity, null);
    }

    private static void show(final Activity activity, final Runnable onDeclined,
                             final Runnable onProceed) {
        confirmed = false;
        lastAskedAt = -1;

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.zen_intent_title)
                .setMessage(R.string.zen_intent_message)
                .setCancelable(false)
                .setPositiveButton(R.string.zen_intent_continue, (d, which) -> {
                    confirmed = true;
                    lastAskedAt = SystemClock.elapsedRealtime();
                })
                .setNegativeButton(R.string.zen_intent_not_now, (d, which) -> {
                    confirmed = false;
                    lastAskedAt = -1;
                    onDeclined.run();
                })
                .create();
        dialog.setOnDismissListener(d -> {
            if (confirmed) {
                lastAskedAt = SystemClock.elapsedRealtime();
                if (onProceed != null) {
                    onProceed.run();
                }
                if (onConfirmedListener != null) {
                    onConfirmedListener.run();
                }
            }
        });
        currentDialog = dialog;
        dialogActivity = activity;
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
