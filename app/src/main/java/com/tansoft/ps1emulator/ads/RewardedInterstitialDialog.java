package com.tansoft.ps1emulator.ads;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tansoft.ps1emulator.R;

public class RewardedInterstitialDialog {

    /**
     * Show a rewarded-interstitial bottom sheet.
     *
     * @param activity        host activity
     * @param rewardTitle     short title shown to the user (e.g. "Ad-Free Session")
     * @param rewardDescription reward description (e.g. "Enjoy 15 minutes without ads")
     * @param onWatchAd       called when the user taps Watch Ad — caller should invoke
     *                        {@link AdManager#showRewardedInterstitialThen} from here
     * @param onDismissed     called when the overlay is dismissed without watching
     */
    public static void show(@NonNull FragmentActivity activity,
                            @NonNull String rewardTitle,
                            @NonNull String rewardDescription,
                            @NonNull Runnable onWatchAd,
                            @Nullable Runnable onDismissed) {

        if (!AdsConfig.ENABLE_ADS) {
            if (onDismissed != null) onDismissed.run();
            return;
        }

        if (!AdManager.getInstance().canShowRewarded()
                && !AdManager.getInstance().canShowInterstitial()) {
            if (onDismissed != null) onDismissed.run();
            return;
        }

        if (!AdCoordinator.getInstance().canShow()) {
            if (onDismissed != null) onDismissed.run();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(R.layout.dialog_rewarded_interstitial);

        TextView textTitle = dialog.findViewById(R.id.textTitle);
        TextView textDescription = dialog.findViewById(R.id.textDescription);

        if (textTitle != null) textTitle.setText(rewardTitle);
        if (textDescription != null) textDescription.setText(rewardDescription);

        final boolean[] adShowing = {false};

        dialog.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.btnWatchAd).setOnClickListener(v -> {
            adShowing[0] = true;
            dialog.dismiss();
            if (onWatchAd != null) onWatchAd.run();
        });

        dialog.setOnDismissListener(d -> {
            if (!adShowing[0] && onDismissed != null) {
                onDismissed.run();
            }
        });

        dialog.show();
    }
}
