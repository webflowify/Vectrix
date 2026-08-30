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
package com.tansoft.ps1emulator.input;

import android.content.Context;
import android.os.SystemClock;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;

import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.preference.PreferenceManager;

import com.tansoft.ps1emulator.input.InputDispatcher;
import com.tansoft.ps1emulator.util.ObjectPool;

import java.util.ArrayList;

public class VirtualGamepadView extends View {

    private static final int BUTTON_COLOR = 0x66000000;
    private static final int BUTTON_PRESSED_COLOR = 0x99FFFFFF;
    private static final int LABEL_COLOR = 0xCCFFFFFF;
    private static final int STROKE_COLOR = 0x44FFFFFF;

    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Each (non-D-pad) button's hit-test rect and its PS1 bitmask
    private static final int NUM_BUTTONS = 10;
    private final Rect[] hitTestRects = new Rect[NUM_BUTTONS];
    private final int[] hitTestKeys = new int[NUM_BUTTONS];

    // The D-pad is an 8-direction radial pad (a disc) instead of four separate
    // arms. A touch anywhere inside the disc is converted to the nearest one or
    // two orthogonal directions, so diagonals are expressed exactly like the
    // physical controller path does: UP|LEFT, UP|RIGHT, DOWN|LEFT, DOWN|RIGHT.
    // This keeps full compatibility with the PS1 bitmask (no new bits) and with
    // EmulationActivity's hat-axis handling.
    private int dpadCx = 0;
    private int dpadCy = 0;
    private int dpadR = 0;
    private static final float DPAD_DEADZONE_FRAC = 0.16f;

    // Joystick mode: when enabled the D-pad disc is replaced by a touch joystick.
    // It drives the PS1 left analog stick (analogX/analogY) and, for games that
    // only read the D-pad, also emulates the D-pad as digital directions derived
    // from the stick deflection (matching how mobile PS1 emulators such as
    // DuckStation/FPse behave). Geometry reuses the D-pad centre/radius.
    private boolean joystickMode = false;
    private boolean joyActive = false;
    private int joyPointerId = -1;
    private float knobX = 0;
    private float knobY = 0;
    private float joyX = 0;
    private float joyY = 0;
    private int joyDpadBits = 0;
    private static final float JOY_DPAD_DEADZONE = 0.35f;
    private static final float JOY_GRAB_RADIUS_FRAC = 1.4f;

    // Safe insets from the system display cutout (notch / camera cut), applied so
    // controls are never drawn under the cut. Populated in onApplyWindowInsets.
    private int safeInsetLeft = 0;
    private int safeInsetRight = 0;
    private int safeInsetTop = 0;
    private int safeInsetBottom = 0;

    private final Rect btnTriangle = new Rect();
    private final Rect btnCircle = new Rect();
    private final Rect btnCross = new Rect();
    private final Rect btnSquare = new Rect();
    private final Rect btnL1 = new Rect();
    private final Rect btnL2 = new Rect();
    private final Rect btnR1 = new Rect();
    private final Rect btnR2 = new Rect();
    private final Rect btnStart = new Rect();
    private final Rect btnSelect = new Rect();

    // Pre-allocated scratch objects for allocation-free event handling
    private final Point tmpPoint = new Point();
    private final Rect tmpRect = new Rect();
    private final ArrayList<Integer> touchedButtons = new ArrayList<>(NUM_BUTTONS);

    // Pooled per-pointer touch state to reduce GC pressure
    private static class TouchState {
        float x;
        float y;
        int pointerId;
        int buttonMask;

        void reset() {
            x = 0;
            y = 0;
            pointerId = -1;
            buttonMask = 0;
        }
    }

    private final ObjectPool<TouchState> touchStatePool = new ObjectPool<TouchState>(16) {
        @Override
        protected TouchState createInstance() {
            return new TouchState();
        }
    };

    private int currentBitmask = 0;

    private SharedPreferences preferences;
    private boolean autoHideEnabled = true;
    private long lastTouchTime = 0;
    private static final long AUTO_HIDE_DELAY_MS = 3000;

    public VirtualGamepadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        buttonPaint.setColor(BUTTON_COLOR);
        buttonPaint.setStyle(Paint.Style.FILL);

        pressedPaint.setColor(BUTTON_PRESSED_COLOR);
        pressedPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(LABEL_COLOR);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);

        strokePaint.setColor(STROKE_COLOR);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.5f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutButtons(w, h);
    }

    private boolean verticalMode = false;

    private void layoutButtons(int w, int h) {
        float density = getResources().getDisplayMetrics().density;
        // Decide portrait vs landscape from the SCREEN orientation, not the
        // gamepad view's own size. In portrait the gamepad view only covers the
        // bottom half of the screen (wide & short), so using its w/h would
        // wrongly pick the landscape layout and push the controls into the
        // settings (bottom-left) and fast-forward (bottom-right) buttons.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        verticalMode = dm.heightPixels > dm.widthPixels * 1.2;

        if (verticalMode) {
            layoutVerticalMode(w, h, density);
        } else {
            layoutLandscapeMode(w, h, density);
        }

        // Populate hit-test arrays (preserves insertion order for priority).
        // The D-pad is handled separately (radial region), so it is not part
        // of this list.
        hitTestRects[0] = btnTriangle;  hitTestKeys[0] = Ps1Buttons.BUTTON_TRIANGLE;
        hitTestRects[1] = btnCircle;    hitTestKeys[1] = Ps1Buttons.BUTTON_CIRCLE;
        hitTestRects[2] = btnCross;     hitTestKeys[2] = Ps1Buttons.BUTTON_CROSS;
        hitTestRects[3] = btnSquare;    hitTestKeys[3] = Ps1Buttons.BUTTON_SQUARE;
        hitTestRects[4] = btnL1;        hitTestKeys[4] = Ps1Buttons.BUTTON_L1;
        hitTestRects[5] = btnL2;        hitTestKeys[5] = Ps1Buttons.BUTTON_L2;
        hitTestRects[6] = btnR1;        hitTestKeys[6] = Ps1Buttons.BUTTON_R1;
        hitTestRects[7] = btnR2;        hitTestKeys[7] = Ps1Buttons.BUTTON_R2;
        hitTestRects[8] = btnStart;     hitTestKeys[8] = Ps1Buttons.BUTTON_START;
        hitTestRects[9] = btnSelect;    hitTestKeys[9] = Ps1Buttons.BUTTON_SELECT;
    }

    private void layoutVerticalMode(int w, int h, float density) {
        // In portrait the gamepad view only covers the BOTTOM HALF of the
        // screen. The settings (bottom-left) and fast-forward (bottom-right)
        // buttons overlap this view's bottom corners, so the D-pad and action
        // cluster must be sized and positioned to stay clear of them:
        //   shoulders at the very top -> cluster band -> Start/Select -> corners.
        // All sizes scale with the smaller screen dimension so the controls stay
        // usable across phone/tablet sizes.
        float scale = responsiveScale(w, h, density);
        int pad        = (int) (16 * density * scale);
        int shoulderW  = (int) (130 * density * scale);
        int shoulderH  = (int) (56 * density * scale);
        int shGap      = (int) (12 * density * scale);

        // Shoulder buttons pinned to the top of this view
        int shTop = shGap + safeInsetTop;
        btnL1.set(pad, shTop, pad + shoulderW, shTop + shoulderH);
        btnL2.set(pad, shTop + shoulderH + shGap,
                  pad + shoulderW, shTop + shoulderH + shGap + shoulderH);
        btnR1.set(w - pad - shoulderW, shTop, w - pad, shTop + shoulderH);
        btnR2.set(w - pad - shoulderW, shTop + shoulderH + shGap,
                  w - pad, shTop + shoulderH + shGap + shoulderH);

        // Vertical band available for the D-pad / action cluster:
        // top  = just below the shoulders
        // bottom = just above Start/Select (which sit at the very bottom)
        int topBound    = shTop + 2 * shoulderH + shGap + (int) (8 * density * scale);
        int ssH         = (int) (40 * density * scale);
        int ssGap       = (int) (32 * density * scale);
        int ssBottomMargin = (int) (56 * density * scale);  // aligns with corner buttons
        int ssY         = h - ssBottomMargin - ssH - safeInsetBottom;
        int clusterBottom = ssY - (int) (12 * density * scale);
        int available   = Math.max(0, clusterBottom - topBound);
        int dpadCenterX = w / 4;
        int maxW = dpadCenterX - pad; // keep the disc within the left quarter

        // D-pad sized to fit the band and biased toward the top (under shoulders).
        // It is an 8-direction radial disc; the radius is allowed to grow larger
        // than before while staying clear of the screen edge and face buttons.
        int armLen = Math.max((int) (48 * density * scale),
                              Math.min((int) (100 * density * scale),
                                       Math.min(available / 2 - (int) (4 * density * scale), maxW)));
        int dpadCenterY = topBound + armLen + (int) (4 * density * scale);
        this.dpadCx = dpadCenterX;
        this.dpadCy = dpadCenterY;
        this.dpadR = armLen;

        // Face buttons - right side, same vertical centre, sized to the band
        int gap = (int) (12 * density * scale);
        int fbSize = (int) Math.min(64 * density * scale, available / 2 - (int) (6 * density * scale));
        fbSize = Math.max((int) (40 * density * scale), fbSize);
        int fbOff = fbSize / 2 + gap / 2;
        int faceCenterY = dpadCenterY;
        int faceLeftBound  = dpadCenterX + armLen + pad;
        int faceRightBound = w - pad;
        int faceCenterX = (faceLeftBound + faceRightBound) / 2;
        if (faceCenterX + fbOff + fbSize > faceRightBound) {
            faceCenterX = faceRightBound - fbOff - fbSize;
        }

        btnTriangle.set(faceCenterX - fbSize / 2, faceCenterY - fbSize - gap / 2,
                         faceCenterX + fbSize / 2, faceCenterY - gap / 2);
        btnCircle.set(faceCenterX + fbOff, faceCenterY - fbSize / 2,
                      faceCenterX + fbOff + fbSize, faceCenterY + fbSize / 2);
        btnCross.set(faceCenterX - fbSize / 2, faceCenterY + gap / 2,
                     faceCenterX + fbSize / 2, faceCenterY + fbSize + gap / 2);
        btnSquare.set(faceCenterX - fbOff - fbSize, faceCenterY - fbSize / 2,
                      faceCenterX - fbOff, faceCenterY + fbSize / 2);

        // Start / Select - small, bottom-centre, between the settings
        // (bottom-left) and fast-forward (bottom-right) corner buttons
        int ssW = (int) (80 * density * scale);
        btnSelect.set(w / 2 - ssGap - ssW, ssY, w / 2 - ssGap, ssY + ssH);
        btnStart.set(w / 2 + ssGap, ssY, w / 2 + ssGap + ssW, ssY + ssH);
    }

    /**
     * Derives a layout scale factor from the smaller screen dimension so the
     * on-screen controls grow on tablets and shrink on small phones while
     * staying within sane bounds.
     */
    private static float responsiveScale(int w, int h, float density) {
        float minDimDp = Math.min(w, h) / density;
        return Math.max(0.7f, Math.min(1.8f, minDimDp / 420f));
    }

    private void layoutLandscapeMode(int w, int h, float density) {
        // Landscape: classic layout — D-pad on the LEFT, action buttons on the
        // RIGHT, both bottom-aligned above the corner buttons. The D-pad is
        // pushed in by the left display-cutout safe inset so it never sits under
        // a camera cut / notch. All sizes scale with the smaller screen dimension.
        float scale = responsiveScale(w, h, density);
        int gap = (int) (12 * density * scale);
        int shoulderW = (int) (130 * density * scale);
        int shoulderH = (int) (56 * density * scale);
        int edgeMargin = (int) (16 * density * scale);
        int smallBtn = (int) (72 * density * scale);

        int shTop = gap + safeInsetTop;
        // Shoulders: L1/L2 on the left (past the cut), R1/R2 on the right.
        int leftEdge = edgeMargin + safeInsetLeft;
        btnL1.set(leftEdge, shTop, leftEdge + shoulderW, shTop + shoulderH);
        btnL2.set(leftEdge, shTop + shoulderH + gap / 2,
                  leftEdge + shoulderW, shTop + shoulderH + gap / 2 + shoulderH);
        int rightEdge = w - edgeMargin - safeInsetRight;
        btnR1.set(rightEdge - shoulderW, shTop, rightEdge, shTop + shoulderH);
        btnR2.set(rightEdge - shoulderW, shTop + shoulderH + gap / 2,
                  rightEdge, shTop + shoulderH + gap / 2 + shoulderH);

        // Bottom corner buttons (menu at bottom-left, fast-forward at bottom-right)
        // are ~48dp tall with a 12dp margin; add the bottom cutout inset too.
        int cornerBtn = (int) (48 * density * scale);
        int cornerMargin = (int) (12 * density * scale);
        int bottomClear = cornerBtn + cornerMargin + gap + safeInsetBottom;
        int shouldersBottom = shTop + 2 * shoulderH + gap / 2;
        int heightBudget = (h - bottomClear) - shouldersBottom;
        // Keep the D-pad within the left ~45% so it never reaches the action cluster.
        int maxW = (int) ((w * 0.45f - leftEdge) / 2f);

        // D-pad — pinned to the LEFT edge (past the camera cut), bottom-aligned.
        // 8-direction radial disc; the radius may grow while staying clear.
        int armLen = Math.max((int) (48 * density * scale),
                              Math.min((int) (100 * density * scale),
                                       Math.min(heightBudget / 2 - (int) (4 * density * scale), maxW)));
        int clusterExtent = Math.max(armLen, smallBtn + gap / 2);
        int bottomAlignedY = h - bottomClear - clusterExtent;

        int dpadCenterY = bottomAlignedY;
        int dpadCenterX = leftEdge + armLen;
        this.dpadCx = dpadCenterX;
        this.dpadCy = dpadCenterY;
        this.dpadR = armLen;

        // Face buttons — pinned to the RIGHT edge.
        int faceCenterY = dpadCenterY;
        int fbSize = smallBtn;
        int fbOff = fbSize / 2 + gap / 2;
        int faceCenterX = rightEdge - fbOff - fbSize;

        btnTriangle.set(faceCenterX - fbSize / 2, faceCenterY - fbSize - gap / 2,
                        faceCenterX + fbSize / 2, faceCenterY - gap / 2);
        btnCircle.set(faceCenterX + fbOff, faceCenterY - fbSize / 2,
                      faceCenterX + fbOff + fbSize, faceCenterY + fbSize / 2);
        btnCross.set(faceCenterX - fbSize / 2, faceCenterY + gap / 2,
                     faceCenterX + fbSize / 2, faceCenterY + fbSize + gap / 2);
        btnSquare.set(faceCenterX - fbOff - fbSize, faceCenterY - fbSize / 2,
                      faceCenterX - fbOff, faceCenterY + fbSize / 2);

        // Start / Select — bottom center (over the game, as in portrait layout)
        int ssW = (int) (80 * density * scale);
        int ssH = (int) (40 * density * scale);
        int ssGap = (int) (32 * density * scale);
        int ssY = h - ssGap - ssH - safeInsetBottom;
        btnSelect.set(w / 2 - ssGap - ssW, ssY, w / 2 - ssGap, ssY + ssH);
        btnStart.set(w / 2 + ssGap, ssY, w / 2 + ssGap + ssW, ssY + ssH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw each (non-D-pad) button as a rounded rect
        float density = getResources().getDisplayMetrics().density;
        boolean isVertical = verticalMode;
        float r = isVertical ? 12f * density : 8f * density;
        for (int i = 0; i < NUM_BUTTONS; i++) {
            Rect rect = hitTestRects[i];
            canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom,
                                 r, r, buttonPaint);
            canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom,
                                 r, r, strokePaint);
        }

        // Draw the 8-direction D-pad disc (with pressed-direction highlighting)
        // — or the touch joystick when joystick mode is enabled.
        if (joystickMode) {
            drawJoystick(canvas);
        } else {
            drawDpad(canvas);
        }

        // Draw labels on top of each button
        float labelSize = isVertical ? 30f * density : 18f * density;
        float smallLabelSize = isVertical ? 20f * density : 18f * density;
        labelPaint.setTextSize(labelSize);

        drawLabel(canvas, btnTriangle, "\u25B3");
        drawLabel(canvas, btnCircle, "\u25CB");
        drawLabel(canvas, btnCross, "\u2715");
        drawLabel(canvas, btnSquare, "\u25A1");
        // Smaller labels for the narrow shoulder / start / select keys
        labelPaint.setTextSize(smallLabelSize);
        drawLabel(canvas, btnL1, "L1");
        drawLabel(canvas, btnL2, "L2");
        drawLabel(canvas, btnR1, "R1");
        drawLabel(canvas, btnR2, "R2");
        drawLabel(canvas, btnStart, "START");
        drawLabel(canvas, btnSelect, "SELECT");
    }

    /**
     * Draws the D-pad as a classic round cross with eight directions: a central
     * hub plus eight rounded "arms" (the four cardinals and four diagonals),
     * matching the 8-way hit detection in {@link #dpadMask}. Pressed arms are
     * highlighted, including diagonal combos (e.g. UP|LEFT).
     */
    private void drawDpad(Canvas canvas) {
        if (dpadR <= 0) {
            return;
        }
        // Bit combos per direction, in clockwise screen order starting at RIGHT.
        final int[] sectorBits = {
                Ps1Buttons.BUTTON_RIGHT,
                Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_RIGHT,
                Ps1Buttons.BUTTON_DOWN,
                Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_LEFT,
                Ps1Buttons.BUTTON_LEFT,
                Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_LEFT,
                Ps1Buttons.BUTTON_UP,
                Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_RIGHT
        };
        // Canvas angles (degrees, +x = right, +y = down): up = 270, right = 0 ...
        final double[] canvasAngle = {0, 45, 90, 135, 180, 225, 270, 315};
        final String[] arrow = {
                "\u25B6", // right
                "\u2198", // down-right
                "\u25BC", // down
                "\u2199", // down-left
                "\u25C0", // left
                "\u2196", // up-left
                "\u25B2", // up
                "\u2197"  // up-right
        };

        float hubR = dpadR * 0.46f;
        float armW = dpadR * 0.52f;

        // Central hub
        canvas.drawCircle(dpadCx, dpadCy, hubR, buttonPaint);
        canvas.drawCircle(dpadCx, dpadCy, hubR, strokePaint);

        // Eight radiating arms (capsules) from the hub to the rim. Each is drawn
        // pointing up, then rotated to its direction.
        for (int i = 0; i < 8; i++) {
            boolean pressed = (currentBitmask & sectorBits[i]) == sectorBits[i];
            float rot = (float) (canvasAngle[i] + 90.0);
            canvas.save();
            canvas.rotate(rot, dpadCx, dpadCy);
            float left = dpadCx - armW / 2f;
            float top = dpadCy - dpadR;
            float right = dpadCx + armW / 2f;
            float bottom = dpadCy - hubR * 0.15f;
            float cr = armW / 2f;
            canvas.drawRoundRect(left, top, right, bottom, cr, cr,
                    pressed ? pressedPaint : buttonPaint);
            canvas.drawRoundRect(left, top, right, bottom, cr, cr, strokePaint);
            canvas.restore();
        }

        // Direction arrows at the tips, kept upright for readability.
        float tipR = dpadR * 0.72f;
        labelPaint.setTextSize(dpadR * 0.30f);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(canvasAngle[i]);
            float tx = dpadCx + (float) Math.cos(a) * tipR;
            float ty = dpadCy + (float) Math.sin(a) * tipR;
            drawLabelAt(canvas, tx, ty, arrow[i]);
        }
    }

    /**
     * Draws the touch joystick: a fixed translucent base ring with a centre
     * guide, plus a draggable knob that follows the finger (and springs back to
     * centre when released). The knob position reflects the current analog value.
     */
    private void drawJoystick(Canvas canvas) {
        if (dpadR <= 0) {
            return;
        }
        float baseR = dpadR;
        // Base ring
        canvas.drawCircle(dpadCx, dpadCy, baseR, buttonPaint);
        canvas.drawCircle(dpadCx, dpadCy, baseR, strokePaint);
        // Inner guide circle
        canvas.drawCircle(dpadCx, dpadCy, baseR * 0.5f, strokePaint);
        // Knob (rests at centre when idle)
        float kx = joyActive ? knobX : dpadCx;
        float ky = joyActive ? knobY : dpadCy;
        float knobR = baseR * 0.42f;
        canvas.drawCircle(kx, ky, knobR, pressedPaint);
        canvas.drawCircle(kx, ky, knobR, strokePaint);
    }

    private void drawLabel(Canvas canvas, Rect rect, String label) {
        drawLabelAt(canvas, rect.exactCenterX(), rect.exactCenterY(), label);
    }

    private void drawLabelAt(Canvas canvas, float cx, float cy, String label) {
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, cx, baseline, labelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        lastTouchTime = SystemClock.uptimeMillis();
        show();
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                // In joystick mode, claim the pointer if it lands on the stick base.
                if (joystickMode && joyPointerId < 0
                        && isInsideJoystickBase(event.getX(pointerIndex), event.getY(pointerIndex))) {
                    joyPointerId = pointerId;
                    joyActive = true;
                    updateJoystick(event.getX(pointerIndex), event.getY(pointerIndex));
                }
                recomputeButtons(event);
                dispatchState();
                invalidate();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (joyActive && joyPointerId >= 0) {
                    int idx = event.findPointerIndex(joyPointerId);
                    if (idx >= 0) {
                        updateJoystick(event.getX(idx), event.getY(idx));
                    }
                }
                recomputeButtons(event);
                dispatchState();
                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (joyActive && joyPointerId == pointerId) {
                    joyPointerId = -1;
                    joyActive = false;
                    resetJoystick();
                }
                recomputeButtons(event);
                dispatchState();
                invalidate();
                break;
            }
        }
        return true;
    }

    /**
     * Recomputes {@link #currentBitmask} from every active touch pointer,
     * excluding (a) the pointer currently driving the joystick in joystick mode,
     * and (b) the pointer that is lifting (for UP/POINTER_UP/CANCEL) so its
     * release does not latch a stray button.
     */
    private void recomputeButtons(MotionEvent event) {
        int mask = 0;
        int count = event.getPointerCount();
        int action = event.getActionMasked();
        boolean skipLifting = (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL);
        int liftingId = skipLifting ? event.getPointerId(event.getActionIndex()) : -1;
        for (int i = 0; i < count; i++) {
            int pid = event.getPointerId(i);
            if (skipLifting && pid == liftingId) {
                continue;
            }
            if (joystickMode && pid == joyPointerId) {
                continue;
            }
            mask |= hitTest(event.getX(i), event.getY(i));
        }
        currentBitmask = mask;
    }

    private void dispatchState() {
        int bits = currentBitmask;
        if (joystickMode) {
            bits |= joyDpadBits;
        }
        InputDispatcher.getInstance().setVirtualButtons(bits);
    }

    private int hitTest(float x, float y) {
        int ix = (int) x;
        int iy = (int) y;
        // The D-pad is a radial disc. A touch inside it resolves to one or two
        // orthogonal directions (8-way), including diagonal combinations.
        // In joystick mode the disc is owned by the joystick (handled separately
        // in onTouchEvent), so it must not also report D-pad button bits here.
        if (!joystickMode) {
            int dpad = dpadMask(ix, iy);
            if (dpad != 0) {
                return dpad;
            }
        }
        for (int i = 0; i < NUM_BUTTONS; i++) {
            if (hitTestRects[i].contains(ix, iy)) {
                return hitTestKeys[i];
            }
        }
        return 0;
    }

    /**
     * Resolves a point inside the D-pad disc to the nearest direction(s).
     *
     * <p>The disc is split into eight 45-degree sectors. The four cardinal
     * sectors produce a single direction bit; the four diagonal sectors produce
     * the corresponding two orthogonal bits (e.g. up+left). A small dead-zone in
     * the very centre is treated as no press so a finger resting dead-centre does
     * not latch a direction. Touches outside the disc return 0.
     *
     * <p>This mirrors how a real controller's hat axes report diagonals
     * (see {@code EmulationActivity#onGenericMotionEvent}), keeping the PS1
     * bitmask fully compatible.
     */
    int dpadMask(int x, int y) {
        if (dpadR <= 0) {
            return 0;
        }
        int dx = x - dpadCx;
        int dy = y - dpadCy;
        double dist = Math.hypot(dx, dy);
        if (dist > dpadR) {
            return 0; // outside the D-pad disc
        }
        if (dist < dpadR * DPAD_DEADZONE_FRAC) {
            return 0; // dead-centre: neutral
        }
        // atan2 with screen coordinates: right = 0, down = +90, left = 180,
        // up = 270 (or -90). Normalise to [0, 360).
        double deg = Math.toDegrees(Math.atan2(dy, dx));
        if (deg < 0) {
            deg += 360.0;
        }
        // Snap to one of 8 sectors of 45 degrees; round() maps the 45-degree band
        // centred on each direction to that direction (cardinals and diagonals).
        int sector = ((int) Math.round(deg / 45.0)) % 8;
        switch (sector) {
            case 0:  return Ps1Buttons.BUTTON_RIGHT;
            case 1:  return Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_RIGHT;
            case 2:  return Ps1Buttons.BUTTON_DOWN;
            case 3:  return Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_LEFT;
            case 4:  return Ps1Buttons.BUTTON_LEFT;
            case 5:  return Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_LEFT;
            case 6:  return Ps1Buttons.BUTTON_UP;
            case 7:  return Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_RIGHT;
            default: return 0;
        }
    }

    /**
     * Updates the joystick knob position, the analog stick value, and the
     * emulated D-pad bitmask from a touch point. The analog vector (joyX, joyY)
     * is the clamped, normalized deflection of the knob from the base centre;
     * the D-pad bits are derived from it using a radial dead-zone so the stick
     * doubles as a D-pad replacement for games that only read the D-pad.
     */
    private void updateJoystick(float x, float y) {
        float maxR = dpadR > 0 ? dpadR : 1f;
        float nx = (x - dpadCx) / maxR;
        float ny = (y - dpadCy) / maxR;
        float mag = (float) Math.hypot(nx, ny);
        if (mag > 1f) {
            nx /= mag;
            ny /= mag;
            knobX = dpadCx + nx * maxR;
            knobY = dpadCy + ny * maxR;
        } else {
            knobX = x;
            knobY = y;
        }
        joyX = nx;
        joyY = ny;
        joyDpadBits = computeJoystickDpadBits(nx, ny);
        InputDispatcher.getInstance().setAnalog(joyX, joyY);
    }

    private void resetJoystick() {
        knobX = dpadCx;
        knobY = dpadCy;
        joyX = 0f;
        joyY = 0f;
        joyDpadBits = 0;
        InputDispatcher.getInstance().setAnalog(0f, 0f);
    }

    /** Derives D-pad bitmask from a normalized stick vector (package-private for tests). */
    int computeJoystickDpadBits(float nx, float ny) {
        int bits = 0;
        if (ny < -JOY_DPAD_DEADZONE) bits |= Ps1Buttons.BUTTON_UP;
        if (ny >  JOY_DPAD_DEADZONE) bits |= Ps1Buttons.BUTTON_DOWN;
        if (nx < -JOY_DPAD_DEADZONE) bits |= Ps1Buttons.BUTTON_LEFT;
        if (nx >  JOY_DPAD_DEADZONE) bits |= Ps1Buttons.BUTTON_RIGHT;
        return bits;
    }

    private boolean isInsideJoystickBase(float x, float y) {
        if (dpadR <= 0) {
            return false;
        }
        float dx = x - dpadCx;
        float dy = y - dpadCy;
        float grab = dpadR * JOY_GRAB_RADIUS_FRAC;
        return (dx * dx + dy * dy) <= grab * grab;
    }

    public int getCurrentBitmask() {
        return currentBitmask;
    }

    public int getDpadCenterX() {
        return dpadCx;
    }

    public int getDpadCenterY() {
        return dpadCy;
    }

    public int getDpadRadius() {
        return dpadR;
    }

    public Rect getL2Bounds() {
        return new Rect(btnL2);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loadPreferences(getContext());
    }

    @Override
    public android.view.WindowInsets onApplyWindowInsets(android.view.WindowInsets insets) {
        int l = 0, r = 0, t = 0, b = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.view.DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                l = cutout.getSafeInsetLeft();
                r = cutout.getSafeInsetRight();
                t = cutout.getSafeInsetTop();
                b = cutout.getSafeInsetBottom();
            }
        }
        if (l != safeInsetLeft || r != safeInsetRight
                || t != safeInsetTop || b != safeInsetBottom) {
            safeInsetLeft = l;
            safeInsetRight = r;
            safeInsetTop = t;
            safeInsetBottom = b;
            requestLayout();
        }
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(hideRunnable);
        animate().cancel();
    }

    public void loadPreferences(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        float opacity = preferences.getInt("vpad_opacity", 70) / 100f;
        boolean visible = preferences.getBoolean("vpad_visible", true);
        joystickMode = preferences.getBoolean("enable_joystick", false);
        setOpacity(opacity);
        setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * Re-reads the "enable_joystick" preference and switches the D-pad/joystick
     * rendering + input mode accordingly. Safe to call at any time (e.g. when the
     * Settings activity returns to the emulation screen).
     */
    public void applyJoystickModeFromPrefs(Context context) {
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("enable_joystick", false);
        setJoystickMode(enabled);
    }

    public void setJoystickMode(boolean enabled) {
        if (joystickMode == enabled) {
            return;
        }
        joystickMode = enabled;
        resetJoystick();
        invalidate();
    }

    public boolean isJoystickMode() {
        return joystickMode;
    }

    public void setOpacity(float alpha) {
        alpha = Math.max(0.3f, Math.min(1.0f, alpha));
        setAlpha(alpha);
        if (preferences != null) {
            preferences.edit().putInt("vpad_opacity", (int) (alpha * 100)).apply();
        }
    }

    public void show() {
        setVisibility(VISIBLE);
        float targetAlpha = preferences != null
                ? preferences.getInt("vpad_opacity", 70) / 100f : 0.7f;
        animate().alpha(targetAlpha).setDuration(200).start();
        removeCallbacks(hideRunnable);
        if (autoHideEnabled) {
            postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS);
        }
    }

    private final Runnable hideRunnable = () -> {
        animate().alpha(0.15f).setDuration(500).start();
    };

    public void setAutoHideEnabled(boolean enabled) {
        autoHideEnabled = enabled;
        if (enabled) {
            postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS);
        } else {
            removeCallbacks(hideRunnable);
            show();
        }
    }

    public boolean isAutoHideEnabled() {
        return autoHideEnabled;
    }
}
