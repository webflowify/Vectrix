package com.tansoft.ps1emulator.ads;

import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tansoft.ps1emulator.R;

public class RewardedUnlockDialog {

    /**
     * Show a rewarded-unlock bottom sheet.
     *
     * @param activity    host activity
     * @param featureName human-readable feature name (e.g. "Fast Forward")
     * @param description prompt text shown to the user
     * @param onUnlocked  called when the reward is granted (ad completed successfully)
     * @param onDismissed called when the overlay is fully gone — either the user
     *                    tapped Cancel, or the rewarded ad was dismissed.  This is
     *                    the right place to resume emulation that was paused for the
     *                    overlay.  May be {@code null}.
     */
    public static void show(@NonNull FragmentActivity activity,
                            @NonNull String featureName,
                            @NonNull String description,
                            @NonNull Runnable onUnlocked,
                            @Nullable Runnable onDismissed) {

        if (!AdsConfig.ENABLE_ADS) {
            onUnlocked.run();
            return;
        }

        if (!AdManager.getInstance().canShowRewarded()) {
            Toast.makeText(activity, "Ad not ready. Please try again.", Toast.LENGTH_SHORT).show();
            if (onDismissed != null) onDismissed.run();
            return;
        }

        if (!RewardedUnlockManager.getInstance().canShowSessionRewarded()) {
            Toast.makeText(activity, "Session limit reached. Finish your current game to unlock more.", Toast.LENGTH_SHORT).show();
            if (onDismissed != null) onDismissed.run();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(R.layout.dialog_rewarded_unlock);

        TextView textTitle = dialog.findViewById(R.id.textTitle);
        TextView textDescription = dialog.findViewById(R.id.textDescription);

        if (textTitle != null) textTitle.setText("Unlock " + featureName);
        if (textDescription != null) textDescription.setText(description);

        // Track whether the user chose to watch the ad so the dialog dismiss
        // listener can distinguish "Cancel" from "Watch Ad → ad shown".
        final boolean[] adShowing = {false};

        dialog.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.btnWatchAd).setOnClickListener(v -> {
            adShowing[0] = true;
            dialog.dismiss();
            RewardedUnlockManager.getInstance().incrementSessionRewarded();
            AdManager.getInstance().showRewarded(activity,
                () -> onUnlocked.run(),
                () -> {
                    if (onDismissed != null) onDismissed.run();
                });
        });

        // Fire onDismissed when the dialog is cancelled (user tapped outside /
        // back / Cancel) — but NOT when the dialog is dismissed to show the ad
        // (the ad's own dismiss callback handles that case).
        dialog.setOnDismissListener(d -> {
            if (!adShowing[0] && onDismissed != null) {
                onDismissed.run();
            }
        });

        dialog.show();
    }
}
