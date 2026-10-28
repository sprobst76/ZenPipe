package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.CountDownTimer;
import android.os.SystemClock;

import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;
import org.schabi.newpipe.zen.Zen;
import org.schabi.newpipe.zen.ZenIntentGate;
import org.schabi.newpipe.zen.ZenIntentResult;
import org.schabi.newpipe.zen.ZenIntentStats;
import org.schabi.newpipe.zen.ZenPlayback;
import org.schabi.newpipe.zen.ZenSettings;

/**
 * ZenPipe: asks "What do you want to watch?" on app start, when an external/shared link is
 * opened and when the app returns from a long time in the background. After a confirmed
 * prompt it is not repeated for {@link #MIN_INTERVAL_MS}; it is never shown while ZenPipe's OWN
 * player is playing (audio of other apps does not count). The "Continue" button only becomes
 * enabled after a short delay, "Not now" closes the app and the very next start asks again.
 * Background/popup playback started from a shared link goes through {@link #gate}.
 */
public final class ZenIntentPrompt {
    // confirmed state + 5 min cooldown (elapsedRealtime of the last CONFIRMED prompt)
    private static final ZenIntentGate GATE = new ZenIntentGate(SystemClock::elapsedRealtime);
    private static long backgroundedAt = -1;
    private static AlertDialog currentDialog = null;
    private static CountDownTimer countdown = null;
    private static Activity dialogActivity = null;
    // The feed edition is only built once the user confirmed the latest prompt of this process
    // (GATE.getConfirmed()), so "Close app" never uses up any of the daily cap.
    private static Runnable onConfirmedListener = null;
    // a shared-link playback that arrived while a prompt was already open waits for its answer
    private static Runnable pendingProceed = null;
    private static Runnable pendingDeclined = null;

    public static boolean isConfirmed() {
        return GATE.getConfirmed() && !isDialogShowing();
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
    // "Close app" (process still alive) asks again because nothing was confirmed.
    public static void showIfNeeded(final Activity activity) {
        if (!GATE.getConfirmed() && !isDialogShowing()) {
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
                && SystemClock.elapsedRealtime() - stoppedAt >= ZenSettings.intentBackgroundMs()) {
            showIfAllowed(activity);
        }
    }

    // Call when an external/shared link is opened in the app.
    public static void onExternalLink(final Activity activity) {
        showIfAllowed(activity);
    }

    // For playback that starts without MainActivity (shared link -> background/popup/video
    // player via RouterActivity): runs onProceed at once when no prompt is needed, otherwise
    // shows the prompt first. "Close app" runs onDeclined.
    public static void gate(final Activity activity, final Runnable onProceed,
                            final Runnable onDeclined) {
        if (isSkippable()) {
            onProceed.run();
            return;
        }
        if (isDialogShowing()) {
            // another prompt is open: never stack them, the answer to it decides
            pendingProceed = onProceed;
            pendingDeclined = onDeclined;
            return;
        }
        show(activity, onDeclined, onProceed);
    }

    // true: no (new) prompt needed right now
    private static boolean isSkippable() {
        if (ZenPlayback.isPlaying()) {
            return true; // never interrupt ZenPipe's own running playback
        }
        return GATE.isSkippable(false);
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
        GATE.onAsked();

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.zen_intent_title)
                .setMessage(R.string.zen_intent_message)
                .setCancelable(false)
                .setPositiveButton(R.string.zen_intent_continue, (d, which) -> {
                    GATE.decide();
                    record(activity, ZenIntentResult.CONTINUE, null);
                })
                .setNegativeButton(R.string.zen_intent_not_now, (d, which) -> {
                    GATE.decline();
                    record(activity, ZenIntentResult.NOT_NOW, null);
                    onDeclined.run();
                    if (pendingDeclined != null) {
                        final Runnable declined = pendingDeclined;
                        pendingProceed = null;
                        pendingDeclined = null;
                        declined.run();
                    }
                })
                .create();
        dialog.setOnDismissListener(d -> {
            if (countdown != null) {
                countdown.cancel();
            }
            if (GATE.getConfirmed()) {
                GATE.decide();
                if (onProceed != null) {
                    onProceed.run();
                }
                if (pendingProceed != null) {
                    final Runnable proceed = pendingProceed;
                    pendingProceed = null;
                    pendingDeclined = null;
                    proceed.run();
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
        countdown = new CountDownTimer(ZenSettings.intentWaitSeconds() * 1000L, 1000L) {
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

    // Counts the outcome per Zen day and logs it ("intent_prompt result=...").
    private static void record(final Context context, final ZenIntentResult result,
                               final String reason) {
        try {
            final Context app = context.getApplicationContext();
            new ZenIntentStats(Zen.store(app), java.time.ZoneId.systemDefault())
                    .record(result, System.currentTimeMillis());
            Zen.log(app).add("intent_prompt", "result=" + result.getKey()
                    + (reason != null ? " reason=" + reason : ""));
        } catch (final RuntimeException e) {
            android.util.Log.w("ZenIntentPrompt", "record", e);
        }
    }

    /**
     * A conscious decision made elsewhere (channel sheet "Just view") counts like "Continue":
     * sets the cooldown and releases the feed edition, without a prompt of its own.
     *
     * @param context any context (used for the Zen state and log)
     * @param reason  short origin tag for the log, e.g. {@code channel_sheet}
     */
    public static void acknowledge(final Context context, final String reason) {
        GATE.decide();
        record(context, ZenIntentResult.SHEET, reason);
        if (onConfirmedListener != null) {
            onConfirmedListener.run();
        }
    }
}
