package com.tansoft.ps1emulator;

import android.app.Application;
import android.util.Log;

import com.tansoft.ps1emulator.ads.AdManager;
import com.tansoft.ps1emulator.ads.AdsConfig;
import com.tansoft.ps1emulator.ads.AppOpenAdManager;
import com.tansoft.ps1emulator.ads.NetworkHelper;
import com.tansoft.ps1emulator.ads.RewardedUnlockManager;
import com.tansoft.ps1emulator.ads.ClickCounter;
import com.tansoft.ps1emulator.util.ThumbnailExtractor;
import com.tansoft.ps1emulator.data.AppDatabase;
import com.tansoft.ps1emulator.data.GameEntity;

import java.io.File;
import java.util.List;

public class PS1EmulatorApp extends Application {

    private static PS1EmulatorApp instance;
    private static final String TAG = "PS1EmulatorApp";

    public static PS1EmulatorApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        ThumbnailExtractor.migrateOldThumbnails(this);
        migrateStaleThumbnailPaths();

        NetworkHelper.startMonitoring(this);

        RewardedUnlockManager.getInstance().init(this);
        ClickCounter.getInstance(this);

        if (AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "AdMob enabled — initializing SDK");
            AppOpenAdManager.getInstance().init(this);
            AdManager.getInstance().init(this, () -> {
                AppOpenAdManager.getInstance().markSdkInitialized();
                AppOpenAdManager.getInstance().loadAd(this);
            });
        } else {
            Log.d(TAG, "AdMob disabled — skipping SDK initialization");
        }
    }

    private void migrateStaleThumbnailPaths() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                List<GameEntity> allGames = db.gameDao().getAllGamesSync();
                if (allGames == null || allGames.isEmpty()) {
                    return;
                }

                int updated = 0;
                for (GameEntity game : allGames) {
                    if (game.thumbnailPath == null || game.thumbnailPath.isEmpty()) {
                        continue;
                    }
                    File thumbFile = new File(game.thumbnailPath);
                    if (!thumbFile.exists()) {
                        String legacyPath = ThumbnailExtractor.getLegacyPngPath(game.thumbnailPath);
                        File legacyFile = new File(legacyPath);
                        if (!legacyFile.exists()) {
                            game.thumbnailPath = null;
                            db.gameDao().update(game);
                            updated++;
                        }
                    }
                }

                if (updated > 0) {
                    Log.i(TAG, "Thumbnail migration: cleared stale paths for " + updated + " games");
                }
            } catch (Exception e) {
                Log.w(TAG, "Thumbnail migration failed: " + e.getMessage());
            }
        }).start();
    }
}
