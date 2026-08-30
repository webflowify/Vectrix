/*
 * PS1 Emulator for Android
 * Copyright (C) 2024-2026 MST. ROKIEA SULTANA
 * 2 No. College Gate Bylane, Mymensingh - 2207, Bangladesh (BD)
*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.tansoft.ps1emulator.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThumbnailExtractor {

    private static final int THUMBNAIL_WIDTH = 400;
    private static final int THUMBNAIL_HEIGHT = 300;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final int[] GRADIENT_COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00,
            0xFF8E24AA, 0xFF00ACC1, 0xFF6D4C41, 0xFF546E7A,
            0xFFD81B60, 0xFF3949AB, 0xFF00897B, 0xFFFDD835
    };

    public interface ThumbnailCallback {
        void onThumbnailReady(String thumbnailPath);
        void onError(Exception e);
    }

    public static void extractThumbnailAsync(Context context, String gameTitle, long gameId, ThumbnailCallback callback) {
        executor.execute(() -> {
            try {
                String path = extractOrGenerate(context, gameTitle, gameId);
                callback.onThumbnailReady(path);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public static String extractOrGenerate(Context context, String gameTitle, long gameId) throws IOException {
        File thumbnailFile = getThumbnailFile(context, gameId);

        if (thumbnailFile.exists()) {
            return thumbnailFile.getAbsolutePath();
        }

        Bitmap thumbnail = generatePlaceholder(gameTitle, gameId);
        saveBitmap(thumbnail, thumbnailFile);
        thumbnail.recycle();

        return thumbnailFile.getAbsolutePath();
    }

    private static Bitmap generatePlaceholder(String gameTitle, long gameId) {
        Bitmap bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int colorIndex = (int) (gameId % GRADIENT_COLORS.length);
        int baseColor = GRADIENT_COLORS[colorIndex];
        int darkerColor = darkenColor(baseColor, 0.6f);

        Paint gradientPaint = new Paint();
        gradientPaint.setShader(new LinearGradient(
                0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                baseColor, darkerColor,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, gradientPaint);

        Paint patternPaint = new Paint();
        patternPaint.setColor(Color.argb(30, 255, 255, 255));
        for (int i = 0; i < 8; i++) {
            float x = (float) (Math.random() * THUMBNAIL_WIDTH);
            float y = (float) (Math.random() * THUMBNAIL_HEIGHT);
            float radius = (float) (20 + Math.random() * 40);
            canvas.drawCircle(x, y, radius, patternPaint);
        }

        String displayText = getDisplayText(gameTitle);
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(72f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.argb(100, 0, 0, 0));
        canvas.drawText(displayText, THUMBNAIL_WIDTH / 2f, THUMBNAIL_HEIGHT / 2f + 24f, textPaint);

        return bitmap;
    }

    private static String getDisplayText(String title) {
        if (title == null || title.isEmpty()) return "?";

        String[] words = title.split("[\\s_]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }

        String result = sb.toString();
        if (result.length() > 3) {
            result = result.substring(0, 3);
        }

        return result.isEmpty() ? "?" : result;
    }

    private static int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    private static File getThumbnailFile(Context context, long gameId) {
        File dir = new File(context.getFilesDir(), "thumbnails");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "game_" + gameId + ".jpg");
    }

    private static void saveBitmap(Bitmap bitmap, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        }
    }

    public static void deleteThumbnail(Context context, long gameId) {
        File file = getThumbnailFile(context, gameId);
        if (file.exists()) {
            file.delete();
        }
    }
}
