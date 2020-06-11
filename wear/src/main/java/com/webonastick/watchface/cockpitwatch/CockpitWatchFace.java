package com.webonastick.watchface.cockpitwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.core.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class CockpitWatchFace extends CanvasWatchFaceService {

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1) / 5;

    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<CockpitWatchFace.Engine> mWeakReference;

        EngineHandler(CockpitWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            CockpitWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final String TAG = "CockpitWatchFace";

        private static final float TICK_OUTER_RADIUS        = 0.97f;
        private static final float HOUR_TICK_INNER_RADIUS   = 0.89f;
        private static final float MINUTE_TICK_INNER_RADIUS = 0.92f;

        private static final float HOUR_TICK_STROKE_WIDTH    = 3f;
        private static final float MINUTE_TICK_STROKE_WIDTH  = 3f;
        private static final float BATTERY_TICK_STROKE_WIDTH = 3f;

        private static final float TEXT_OUTER_RADIUS = 0.84f;

        private static final float HOUR_TEXT_SIZE_PERCENT          = 12f;
        private static final float HOUR24_TEXT_SIZE_PERCENT        = 4.5f;
        private static final float HOUR24_TEXT_SIZE_OFFSET_PERCENT = 2f;
        private static final float BATTERY_TEXT_SIZE_PERCENT       = 4.5f;

        private static final float HAND_SHADOW_RADIUS   = 3f;
        private static final float HAND_SHADOW_OFFSET_X = 0f;
        private static final float HAND_SHADOW_OFFSET_Y = 3f;

        private static final int TICK_NUMBER_SHADOW = 2;

        private static final float HOUR_HAND_LENGTH    = 0.5f;
        private static final float MINUTE_HAND_LENGTH  = (TICK_OUTER_RADIUS + MINUTE_TICK_INNER_RADIUS) / 2f;
        private static final float SECOND_HAND_LENGTH  = TICK_OUTER_RADIUS;
        private static final float BATTERY_HAND_LENGTH = (1 + HOUR_TICK_INNER_RADIUS) / 2f;

        private static final float HOUR_HAND_WIDTH    = 0.04f;
        private static final float MINUTE_HAND_WIDTH  = 0.04f;
        private static final float SECOND_HAND_WIDTH  = 0.015f;
        private static final float BATTERY_HAND_WIDTH = 0.02f;

        private static final float HOUR_HAND_STROKE_WIDTH    = 2f;
        private static final float MINUTE_HAND_STROKE_WIDTH  = 2f;
        private static final float SECOND_HAND_STROKE_WIDTH  = 2f;
        private static final float BATTERY_HAND_STROKE_WIDTH = 2f;

        /* Handler to update the time five times a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;

        private float mWidth;
        private float mHeight;
        private float mRadius;
        private float mDiameter;
        private float mCenterX;
        private float mCenterY;
        
        private float mBatteryCenterX;
        private float mBatteryCenterY;
        private float mBatteryRadius;

        private float mSecondHandLength;
        private float mSecondHandWidth;
        private float mMinuteHandLength;
        private float mMinuteHandWidth;
        private float mHourHandLength;
        private float mHourHandWidth;
        private float mBatteryHandLength;
        private float mBatteryHandWidth;
        
        private Paint mHourHandPaint1;
        private Paint mMinuteHandPaint1;
        private Paint mSecondHandPaint1;
        private Paint mBatteryHandPaint1;
        
        private Paint mHourHandPaint2;
        private Paint mMinuteHandPaint2;
        private Paint mSecondHandPaint2;
        private Paint mBatteryHandPaint2;

        private Paint mHourTickPaint;
        private Paint mMinuteTickPaint;
        private Paint mBatteryTickPaint;
        
        private Paint mBackgroundPaint;
        private Paint mGrayBackgroundPaint;
        private Paint mHourTextPaint;

        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;

        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private Typeface mTypeface;

        private int mBackgroundColor;
        private int mShadowColor;
        private int mTextColor;
        private int mHourTickColor;
        private int mMinuteTickColor;
        private int mBatteryTickColor;

        private int mHourHandColor1;
        private int mMinuteHandColor1;
        private int mSecondHandColor1;
        private int mBatteryHandColor1;

        private int mHourHandColor2;
        private int mMinuteHandColor2;
        private int mSecondHandColor2;
        private int mBatteryHandColor2;

        private Path mHourHandPath1;
        private Path mMinuteHandPath1;
        private Path mSecondHandPath1;
        private Path mBatteryHandPath1;

        private Path mHourHandPath2;
        private Path mMinuteHandPath2;
        private Path mSecondHandPath2;
        private Path mBatteryHandPath2;

        private boolean demoTimeMode = false;
        private boolean emulatorMode = false;

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Build.MODEL.startsWith("sdk_") || Build.FINGERPRINT.contains("/sdk_")) {
                emulatorMode = true;
            }

            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(CockpitWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .setStatusBarGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
                    .build());

            mCalendar = Calendar.getInstance();

            mTypeface = Typeface.createFromAsset(
                    CockpitWatchFace.this.getResources().getAssets(),
                    "fonts/routed-gothic.ttf"
            );

            mBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.background_color);
            mShadowColor     = ContextCompat.getColor(getApplicationContext(), R.color.shadow_color);
            mTextColor       = ContextCompat.getColor(getApplicationContext(), R.color.text_color);

            mHourTickColor    = ContextCompat.getColor(getApplicationContext(), R.color.hour_tick_color);
            mMinuteTickColor  = ContextCompat.getColor(getApplicationContext(), R.color.minute_tick_color);
            mBatteryTickColor = ContextCompat.getColor(getApplicationContext(), R.color.battery_tick_color);

            mHourHandColor1    = ContextCompat.getColor(getApplicationContext(), R.color.hour_hand_color_1);
            mMinuteHandColor1  = ContextCompat.getColor(getApplicationContext(), R.color.minute_hand_color_1);
            mSecondHandColor1  = ContextCompat.getColor(getApplicationContext(), R.color.second_hand_color_1);
            mBatteryHandColor1 = ContextCompat.getColor(getApplicationContext(), R.color.battery_hand_color_1);

            mHourHandColor2    = ContextCompat.getColor(getApplicationContext(), R.color.hour_hand_color_2);
            mMinuteHandColor2  = ContextCompat.getColor(getApplicationContext(), R.color.minute_hand_color_2);
            mSecondHandColor2  = ContextCompat.getColor(getApplicationContext(), R.color.second_hand_color_2);
            mBatteryHandColor2 = ContextCompat.getColor(getApplicationContext(), R.color.battery_hand_color_2);
            
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundColor);

            mGrayBackgroundPaint = new Paint();
            mGrayBackgroundPaint.setColor(Color.BLACK);

            initializePaintStyles();
        }

        private void initializePaintStyles() {
            mHourHandPaint1 = new Paint();
            mHourHandPaint1.setStrokeWidth(Math.max(0, HOUR_HAND_STROKE_WIDTH   - 1));
            mHourHandPaint1.setStrokeCap(Paint.Cap.ROUND);
            mHourHandPaint1.setStrokeJoin(Paint.Join.ROUND);
            mHourHandPaint1.setStyle(Paint.Style.FILL_AND_STROKE);

            mMinuteHandPaint1 = new Paint();
            mMinuteHandPaint1.setStrokeWidth(Math.max(0, MINUTE_HAND_STROKE_WIDTH - 1));
            mMinuteHandPaint1.setStrokeCap(Paint.Cap.ROUND);
            mMinuteHandPaint1.setStrokeJoin(Paint.Join.ROUND);
            mMinuteHandPaint1.setStyle(Paint.Style.FILL_AND_STROKE);

            mSecondHandPaint1 = new Paint();
            mSecondHandPaint1.setStrokeWidth(Math.max(0, SECOND_HAND_STROKE_WIDTH - 1));
            mSecondHandPaint1.setStrokeCap(Paint.Cap.ROUND);
            mSecondHandPaint1.setStrokeJoin(Paint.Join.ROUND);
            mSecondHandPaint1.setStyle(Paint.Style.FILL_AND_STROKE);

            mBatteryHandPaint1 = new Paint();
            mBatteryHandPaint1.setStrokeWidth(Math.max(0, BATTERY_HAND_STROKE_WIDTH - 1));
            mBatteryHandPaint1.setStrokeCap(Paint.Cap.ROUND);
            mBatteryHandPaint1.setStrokeJoin(Paint.Join.ROUND);
            mBatteryHandPaint1.setStyle(Paint.Style.FILL_AND_STROKE);

            mHourHandPaint2 = new Paint();
            mHourHandPaint2.setStrokeWidth(HOUR_HAND_STROKE_WIDTH);
            mHourHandPaint2.setStrokeCap(Paint.Cap.ROUND);
            mHourHandPaint2.setStrokeJoin(Paint.Join.ROUND);
            mHourHandPaint2.setStyle(Paint.Style.FILL_AND_STROKE);

            mMinuteHandPaint2 = new Paint();
            mMinuteHandPaint2.setStrokeWidth(MINUTE_HAND_STROKE_WIDTH);
            mMinuteHandPaint2.setStrokeCap(Paint.Cap.ROUND);
            mMinuteHandPaint2.setStrokeJoin(Paint.Join.ROUND);
            mMinuteHandPaint2.setStyle(Paint.Style.FILL_AND_STROKE);

            mSecondHandPaint2 = new Paint();
            mSecondHandPaint2.setStrokeWidth(SECOND_HAND_STROKE_WIDTH);
            mSecondHandPaint2.setStrokeCap(Paint.Cap.ROUND);
            mSecondHandPaint2.setStrokeJoin(Paint.Join.ROUND);
            mSecondHandPaint2.setStyle(Paint.Style.FILL_AND_STROKE);
            
            mBatteryHandPaint2 = new Paint();
            mBatteryHandPaint2.setStrokeWidth(BATTERY_HAND_STROKE_WIDTH);
            mBatteryHandPaint2.setStrokeCap(Paint.Cap.ROUND);
            mBatteryHandPaint2.setStrokeJoin(Paint.Join.ROUND);
            mBatteryHandPaint2.setStyle(Paint.Style.FILL_AND_STROKE);

            changePaintColorsAndShadowsForDefault();

            mHourTickPaint = new Paint();
            mHourTickPaint.setColor(mHourTickColor);
            mHourTickPaint.setStrokeWidth(HOUR_TICK_STROKE_WIDTH);
            mHourTickPaint.setStyle(Paint.Style.STROKE);
            mHourTickPaint.setStrokeCap(Paint.Cap.BUTT);

            mMinuteTickPaint = new Paint();
            mMinuteTickPaint.setColor(mMinuteTickColor);
            mMinuteTickPaint.setStrokeWidth(MINUTE_TICK_STROKE_WIDTH);
            mMinuteTickPaint.setStyle(Paint.Style.STROKE);
            mMinuteTickPaint.setStrokeCap(Paint.Cap.BUTT);

            mBatteryTickPaint = new Paint();
            mBatteryTickPaint.setColor(mBatteryTickColor);
            mBatteryTickPaint.setStrokeWidth(BATTERY_TICK_STROKE_WIDTH);
            mBatteryTickPaint.setStyle(Paint.Style.STROKE);
            mBatteryTickPaint.setStrokeCap(Paint.Cap.BUTT);

            mHourTextPaint = new Paint();
            mHourTextPaint.setColor(mTextColor);
            mHourTextPaint.setTypeface(mTypeface);
            mHourTextPaint.setTextAlign(Paint.Align.CENTER);

            changePaintAntiAliasForDefault();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            changePaintColorsAndShadows();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }
        
        private void changePaintColorsAndShadowsForDefault() {
            mHourHandPaint1.setColor(mHourHandColor1);
            mMinuteHandPaint1.setColor(mMinuteHandColor1);
            mSecondHandPaint1.setColor(mSecondHandColor1);
            mBatteryHandPaint1.setColor(mBatteryHandColor1);
            mHourHandPaint2.setColor(mHourHandColor2);
            mMinuteHandPaint2.setColor(mMinuteHandColor2);
            mSecondHandPaint2.setColor(mSecondHandColor2);
            mBatteryHandPaint2.setColor(mBatteryHandColor2);
            mHourHandPaint1.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
            mMinuteHandPaint1.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
            mSecondHandPaint1.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
            mBatteryHandPaint1.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
        }

        private void changePaintColorsAndShadowsForAmbient() {
            mHourHandPaint1.setColor(Color.BLACK);
            mMinuteHandPaint1.setColor(Color.BLACK);
            mSecondHandPaint1.setColor(Color.BLACK);
            mBatteryHandPaint1.setColor(Color.BLACK);
            mHourHandPaint2.setColor(Color.WHITE);
            mMinuteHandPaint2.setColor(Color.WHITE);
            mSecondHandPaint2.setColor(Color.WHITE);
            mBatteryHandPaint2.setColor(Color.WHITE);
            mHourHandPaint1.clearShadowLayer();
            mMinuteHandPaint1.clearShadowLayer();
            mSecondHandPaint1.clearShadowLayer();
            mBatteryHandPaint1.clearShadowLayer();
        }

        private void changePaintAntiAliasForDefault() {
            mHourHandPaint1.setAntiAlias(true);
            mMinuteHandPaint1.setAntiAlias(true);
            mSecondHandPaint1.setAntiAlias(true);
            mBatteryHandPaint1.setAntiAlias(true);
            mHourHandPaint2.setAntiAlias(true);
            mMinuteHandPaint2.setAntiAlias(true);
            mSecondHandPaint2.setAntiAlias(true);
            mBatteryHandPaint2.setAntiAlias(true);
            mHourTickPaint.setAntiAlias(true);
            mMinuteTickPaint.setAntiAlias(true);
            mBatteryTickPaint.setAntiAlias(true);
            mHourTextPaint.setAntiAlias(true);
        }

        private void changePaintAntiAliasForLowBit() {
            mHourHandPaint1.setAntiAlias(false);
            mMinuteHandPaint1.setAntiAlias(false);
            mSecondHandPaint1.setAntiAlias(false);
            mBatteryHandPaint1.setAntiAlias(false);
            mHourHandPaint2.setAntiAlias(false);
            mMinuteHandPaint2.setAntiAlias(false);
            mSecondHandPaint2.setAntiAlias(false);
            mBatteryHandPaint2.setAntiAlias(false);
            mHourTickPaint.setAntiAlias(false);
            mMinuteTickPaint.setAntiAlias(false);
            mBatteryTickPaint.setAntiAlias(false);
            mHourTextPaint.setAntiAlias(false);
        }

        private void changePaintColorsAndShadows() {
            if (mAmbient) {
                changePaintColorsAndShadowsForAmbient();
            } else {
                changePaintColorsAndShadowsForDefault();
            }
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                changePaintAntiAliasForLowBit();
            } else {
                changePaintAntiAliasForDefault();
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourHandPaint1.setAlpha(inMuteMode ? 100 : 255);
                mMinuteHandPaint1.setAlpha(inMuteMode ? 100 : 255);
                mSecondHandPaint1.setAlpha(inMuteMode ? 80 : 255);
                mBatteryHandPaint1.setAlpha(inMuteMode ? 100 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mWidth    = width;
            mHeight   = height;
            mRadius   = Math.min(width / 2f, height / 2f);
            mDiameter = Math.min(width, height);
            mCenterX  = width / 2f;
            mCenterY  = height / 2f;

            mBatteryCenterX    = width / 2f;
            mBatteryCenterY    = height * 0.72f;
            mBatteryRadius     = height * 0.16f;

            mHourHandLength    = mRadius * HOUR_HAND_LENGTH;
            mMinuteHandLength  = mRadius * MINUTE_HAND_LENGTH;
            mSecondHandLength  = mRadius * SECOND_HAND_LENGTH;
            mBatteryHandLength = mBatteryRadius * BATTERY_HAND_LENGTH;

            mHourHandWidth    = mDiameter * HOUR_HAND_WIDTH;
            mMinuteHandWidth  = mDiameter * MINUTE_HAND_WIDTH;
            mSecondHandWidth  = mDiameter * SECOND_HAND_WIDTH;
            mBatteryHandWidth = mDiameter * BATTERY_HAND_WIDTH;

            mBackgroundBitmap     = null;
            mGrayBackgroundBitmap = null;

            initHandPaths();
            initBackgroundBitmap(width, height);
            initGrayBackgroundBitmap(width, height);
        }

        private void initHandPaths() {
            
            /*
             * Base Paths
             */

            /* hour hand */
            
            mHourHandPath1 = new Path();
            mHourHandPath1.moveTo(mCenterX - mHourHandWidth / 3, mCenterY);
            mHourHandPath1.lineTo(mCenterX - mHourHandWidth / 2, mCenterY - mHourHandLength * 0.75f);
            mHourHandPath1.lineTo(mCenterX, mCenterY - mHourHandLength);
            mHourHandPath1.lineTo(mCenterX + mHourHandWidth / 2, mCenterY - mHourHandLength * 0.75f);
            mHourHandPath1.lineTo(mCenterX + mHourHandWidth / 3, mCenterY);
            mHourHandPath1.close();

            Path hourCirclePath = new Path();
            hourCirclePath.addCircle(mCenterX, mCenterY, mHourHandWidth / 1.5f, Path.Direction.CW);

            mHourHandPath1.op(hourCirclePath, Path.Op.UNION);

            /* minute hand */

            mMinuteHandPath1 = new Path();
            mMinuteHandPath1.moveTo(mCenterX - mMinuteHandWidth / 3, mCenterY);
            mMinuteHandPath1.lineTo(mCenterX - mMinuteHandWidth / 2, mCenterY - mMinuteHandLength * 0.75f);
            mMinuteHandPath1.lineTo(mCenterX, mCenterY - mMinuteHandLength);
            mMinuteHandPath1.lineTo(mCenterX + mMinuteHandWidth / 2, mCenterY - mMinuteHandLength * 0.75f);
            mMinuteHandPath1.lineTo(mCenterX + mMinuteHandWidth / 3, mCenterY);
            mMinuteHandPath1.close();

            Path minuteCirclePath = new Path();
            minuteCirclePath.addCircle(mCenterX, mCenterY, mMinuteHandWidth / 1.5f, Path.Direction.CW);

            mMinuteHandPath1.op(minuteCirclePath, Path.Op.UNION);

            /* second hand */
            
            mSecondHandPath1 = new Path();
            mSecondHandPath1.moveTo(mCenterX - mSecondHandWidth / 3, mCenterY);
            mSecondHandPath1.lineTo(mCenterX - mSecondHandWidth / 2, mCenterY - mSecondHandLength * 0.75f);
            mSecondHandPath1.lineTo(mCenterX, mCenterY - mSecondHandLength);
            mSecondHandPath1.lineTo(mCenterX + mSecondHandWidth / 2, mCenterY - mSecondHandLength * 0.75f);
            mSecondHandPath1.lineTo(mCenterX + mSecondHandWidth / 3, mCenterY);
            mSecondHandPath1.close();

            Path secondCirclePath = new Path();
            secondCirclePath.addCircle(mCenterX, mCenterY, mSecondHandWidth / 1.5f, Path.Direction.CW);

            mSecondHandPath1.op(secondCirclePath, Path.Op.UNION);

            /* battery hand */

            mBatteryHandPath1 = new Path();
            mBatteryHandPath1.moveTo(mBatteryCenterX - mBatteryHandWidth / 3, mBatteryCenterY);
            mBatteryHandPath1.lineTo(mBatteryCenterX - mBatteryHandWidth / 2, mBatteryCenterY - mBatteryHandLength * 0.75f);
            mBatteryHandPath1.lineTo(mBatteryCenterX, mBatteryCenterY - mBatteryHandLength);
            mBatteryHandPath1.lineTo(mBatteryCenterX + mBatteryHandWidth / 2, mBatteryCenterY - mBatteryHandLength * 0.75f);
            mBatteryHandPath1.lineTo(mBatteryCenterX + mBatteryHandWidth / 3, mBatteryCenterY);
            mBatteryHandPath1.close();

            Path batteryCirclePath = new Path();
            batteryCirclePath.addCircle(mBatteryCenterX, mBatteryCenterY, mBatteryHandWidth / 1.5f, Path.Direction.CW);

            mBatteryHandPath1.op(batteryCirclePath, Path.Op.UNION);

            /*
             * Second Layer Paths
             */

            float mHourHandY    = mCenterY - mHourHandLength / 3f;
            float mMinuteHandY  = mCenterY - mMinuteHandLength / 4f;
            float mSecondHandY  = mCenterY - mSecondHandLength / 4f;
            float mBatteryHandY = mBatteryCenterY - mBatteryHandLength / 3f;
            
            mHourHandPath2 = new Path();
            mHourHandPath2.moveTo(0, mHourHandY);
            mHourHandPath2.lineTo(mWidth, mHourHandY);
            mHourHandPath2.lineTo(mWidth, 0);
            mHourHandPath2.lineTo(0, 0);
            mHourHandPath2.close();
            mHourHandPath2.op(mHourHandPath1, Path.Op.INTERSECT);

            mMinuteHandPath2 = new Path();
            mMinuteHandPath2.moveTo(0, mMinuteHandY);
            mMinuteHandPath2.lineTo(mWidth, mMinuteHandY);
            mMinuteHandPath2.lineTo(mWidth, 0);
            mMinuteHandPath2.lineTo(0, 0);
            mMinuteHandPath2.close();
            mMinuteHandPath2.op(mMinuteHandPath1, Path.Op.INTERSECT);
            
            mSecondHandPath2 = new Path();
            mSecondHandPath2.moveTo(0, mSecondHandY);
            mSecondHandPath2.lineTo(mWidth, mSecondHandY);
            mSecondHandPath2.lineTo(mWidth, 0);
            mSecondHandPath2.lineTo(0, 0);
            mSecondHandPath2.close();
            mSecondHandPath2.op(mSecondHandPath1, Path.Op.INTERSECT);
            
            mBatteryHandPath2 = new Path();
            mBatteryHandPath2.moveTo(0, mBatteryHandY);
            mBatteryHandPath2.lineTo(mWidth, mBatteryHandY);
            mBatteryHandPath2.lineTo(mWidth, 0);
            mBatteryHandPath2.lineTo(0, 0);
            mBatteryHandPath2.close();
            mBatteryHandPath2.op(mBatteryHandPath1, Path.Op.INTERSECT);
        }

        private void drawTicks(Canvas canvas, boolean shadow) {
            if (shadow) {
                for (int dy = 1; dy <= TICK_NUMBER_SHADOW; dy += 1) {
                    drawTicks(canvas, true, dy);
                }
            } else {
                drawTicks(canvas, false, 0);
            }
        }

        private void drawTicks(Canvas canvas, boolean shadow, int dy) {
            drawClockTicks(canvas, shadow, dy);
            drawBatteryTicks(canvas, shadow, dy);
        }
        
        private void drawClockTicks(Canvas canvas, boolean shadow, int dy) {
            if (shadow) {
                mHourTickPaint.setColor(Color.BLACK);
                mMinuteTickPaint.setColor(Color.BLACK);
            } else {
                mHourTickPaint.setColor(mHourTickColor);
                mMinuteTickPaint.setColor(mMinuteTickColor);
            }

            float centerX = mCenterX;
            float centerY = mCenterY + (shadow ? (dy * 1f) : 0f);

            canvas.save();
            for (int tick = 0; tick < 60; tick += 1) {
                boolean isHourTick = tick % 5 == 0;
                if (isHourTick) {
                    canvas.drawLine(
                            centerX, centerY - mRadius * TICK_OUTER_RADIUS,
                            centerX, centerY - mRadius * HOUR_TICK_INNER_RADIUS,
                            mHourTickPaint
                    );
                } else {
                    canvas.drawLine(
                            centerX, centerY - mRadius * TICK_OUTER_RADIUS,
                            centerX, centerY - mRadius * MINUTE_TICK_INNER_RADIUS,
                            mMinuteTickPaint
                    );
                }
                canvas.rotate(6f, centerX, centerY);
            }
            canvas.restore();
        }
        
        private void drawBatteryTicks(Canvas canvas, boolean shadow, int dy) {
            if (shadow) {
                mBatteryTickPaint.setColor(mShadowColor);
            } else {
                mBatteryTickPaint.setColor(mBatteryTickColor);
            }

            float offsetY = (shadow ? (dy * 1f) : 0f);
            float centerX = mBatteryCenterX;
            float centerY = mBatteryCenterY + offsetY;

            Paint textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setTypeface(mTypeface);
            textPaint.setTextAlign(Paint.Align.CENTER);
            if (mAmbient) {
                textPaint.setColor(Color.WHITE);
            } else {
                if (shadow) {
                    textPaint.setColor(mShadowColor);
                } else {
                    textPaint.setColor(mBatteryTickColor);
                }
            }
            textPaint.setTextSize(mDiameter * BATTERY_TEXT_SIZE_PERCENT / 100);

            float startAngle = -90f;
            float endAngle = 90f;

            canvas.save();
            canvas.rotate(startAngle, centerX, centerY);
            float rotation = startAngle;
            for (int tick = 0; tick <= 100; tick += 10) {
                if (tick == 0 || tick == 50 || tick == 100) {
                    float tickCenterY = centerY - mBatteryRadius * ((1f + HOUR_TICK_INNER_RADIUS) / 2);
                    Rect textBounds = new Rect();
                    String tickString = Integer.toString(tick);
                    textPaint.getTextBounds(tickString, 0, tickString.length(), textBounds);
                    canvas.rotate(
                            -rotation, centerX, tickCenterY
                    );
                    canvas.drawText(
                            tickString,
                            centerX,
                            tickCenterY + textBounds.height() * 0.4f,
                            textPaint
                    );
                    canvas.rotate(
                            rotation, centerX, tickCenterY
                    );
                } else {
                    canvas.drawLine(
                            centerX, centerY - mBatteryRadius,
                            centerX, centerY - mBatteryRadius * HOUR_TICK_INNER_RADIUS,
                            mBatteryTickPaint
                    );
                }
                canvas.rotate((endAngle - startAngle) / 10, centerX, centerY);
                rotation += (endAngle - startAngle) / 10;
            }
            canvas.restore();

            canvas.drawText(
                    "BATTERY",
                    mBatteryCenterX,
                    mBatteryCenterY - mBatteryRadius / 3 + 0.5f * mDiameter * BATTERY_TEXT_SIZE_PERCENT / 100 + offsetY,
                    textPaint);
        }

        private void initBackgroundBitmap(int width, int height) {
            Canvas backgroundCanvas = new Canvas();
            mBackgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            backgroundCanvas.setBitmap(mBackgroundBitmap);
            backgroundCanvas.drawColor(mBackgroundColor);

            drawTicks(backgroundCanvas, true);
            drawTicks(backgroundCanvas, false);
            drawHourNumbers(backgroundCanvas, true);
            drawHourNumbers(backgroundCanvas, false);
        }

        private void initGrayBackgroundBitmap(int width, int height) {
            Canvas backgroundCanvas = new Canvas();
            mGrayBackgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            backgroundCanvas.setBitmap(mGrayBackgroundBitmap);
            backgroundCanvas.drawColor(Color.BLACK);

            drawTicks(backgroundCanvas, false);
            drawHourNumbers(backgroundCanvas, false);
        }

        private void drawHourNumbers(Canvas canvas, boolean shadow) {
            if (shadow) {
                for (int dy = 1; dy <= TICK_NUMBER_SHADOW; dy += 1) {
                    drawHourNumbers(canvas, true, dy);
                }
            } else {
                drawHourNumbers(canvas, false, 0);
            }
        }

        private void drawHourNumbers(Canvas canvas, boolean shadow, int dy) {
            if (shadow) {
                mHourTextPaint.setColor(mShadowColor);
            } else {
                mHourTextPaint.setColor(mTextColor);
            }

            float centerX = mCenterX;
            float centerY = mCenterY + (shadow ? (dy * 1f) : 0f);

            for (int hour = 1; hour <= 12; hour += 1) {
                mHourTextPaint.setTextSize(mDiameter * HOUR_TEXT_SIZE_PERCENT / 100);
                int deg = 30 * (hour % 12);
                String sHour = Integer.toString(hour);
                Rect textBounds = new Rect();
                mHourTextPaint.getTextBounds(sHour, 0, sHour.length(), textBounds);
                float x = centerX + (float)Math.sin(Math.PI * deg / 180f) * TEXT_OUTER_RADIUS * mRadius;
                float y = centerY - (float)Math.cos(Math.PI * deg / 180f) * TEXT_OUTER_RADIUS * mRadius;
                x = x - (float)Math.sin(Math.PI * deg / 180f) * textBounds.width() / 2;
                y = y + (float)Math.cos(Math.PI * deg / 180f) * textBounds.height() / 2;
                canvas.drawText(sHour, x, y + textBounds.height() / 2f, mHourTextPaint);

                if (hour % 3 == 0) {
                    mHourTextPaint.setTextSize(mDiameter * HOUR24_TEXT_SIZE_PERCENT / 100);
                    sHour = Integer.toString(hour + 12);
                    Rect textBounds24 = new Rect();
                    mHourTextPaint.getTextBounds(sHour, 0, sHour.length(), textBounds24);
                    if (hour > 9 || hour < 3) {
                        y = y + textBounds.height() / 2f + mDiameter * HOUR24_TEXT_SIZE_OFFSET_PERCENT / 100 + textBounds24.height() / 2f;
                    } else if (hour == 9 || hour == 3) {
                        x = x - (float)Math.sin(Math.PI * deg / 180f) * textBounds.width() / 2
                                - (float)Math.sin(Math.PI * deg / 180f) * mDiameter * HOUR24_TEXT_SIZE_OFFSET_PERCENT / 100
                                - (float)Math.sin(Math.PI * deg / 180f) * textBounds24.width() / 2;
                    } else {
                        y = y - textBounds.height() / 2f - mDiameter * HOUR24_TEXT_SIZE_OFFSET_PERCENT / 100 - textBounds24.height() / 2f;
                    }
                    canvas.drawText(sHour, x, y + textBounds24.height() / 2f, mHourTextPaint);
                }
            }
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TAP:
                    if (emulatorMode) {
                        float xx = (float)x;
                        float yy = (float)y;
                        if (xx < mWidth / 2 && yy < mHeight / 2) {
                            demoTimeMode = true;
                        } else if (xx >= mWidth / 2 && yy >= mHeight / 2) {
                            demoTimeMode = false;
                        }
                        invalidate();
                    }
                    break;
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawBatteryHand(canvas);
            drawWatchFace(canvas);
        }

        private void drawBackground(Canvas canvas) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, null);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, null);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
            }
        }

        private void drawBatteryHand(Canvas canvas) {
            float batteryPercentage;

            if (demoTimeMode) {
                //noinspection UnusedAssignment
                batteryPercentage = 69f;
            } else {
                IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = CockpitWatchFace.this.registerReceiver(null, intentFilter);
                if (batteryStatus != null) {
                    int batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    batteryPercentage = batteryLevel * 100f / batteryScale;
                } else {
                    batteryPercentage = -1f;
                }
            }

            /* apperance of levels off the odometer range in case they happen */
            if (batteryPercentage < 0f) {
                batteryPercentage = -25f;
            } else if (batteryPercentage > 100f) {
                batteryPercentage = 125f;
            }

            float batteryRotation = -90f + 180f * batteryPercentage / 100f;
            canvas.save();
            canvas.rotate(batteryRotation, mBatteryCenterX, mBatteryCenterY);
            canvas.drawPath(mBatteryHandPath1, mBatteryHandPaint1);
            canvas.drawPath(mBatteryHandPath2, mBatteryHandPaint2);
            canvas.restore();

        }

        private void drawWatchFace(Canvas canvas) {
            int h;
            int m;
            int s;
            int ms;

            if (demoTimeMode) {
                //noinspection UnusedAssignment
                h = 10;
                //noinspection UnusedAssignment
                m = 10;
                //noinspection UnusedAssignment
                s = 32;
                //noinspection UnusedAssignment
                ms = 500;
            } else {
                h  = mCalendar.get(Calendar.HOUR);
                m  = mCalendar.get(Calendar.MINUTE);
                s  = mCalendar.get(Calendar.SECOND);
                ms = mCalendar.get(Calendar.MILLISECOND);
            }

            final float seconds = (float)s + (float) ms / 1000f; /* 0 to 60 */
            final float minutes = (float)m + seconds / 60f; /* 0 to 60 */
            final float hours   = (float)h + minutes / 60f; /* 0 to 12 */

            final float secondsRotation = seconds * 6f;
            final float minutesRotation = minutes * 6f;
            final float hoursRotation   = hours * 30f;

            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawPath(mHourHandPath1, mHourHandPaint1);
            canvas.drawPath(mHourHandPath2, mHourHandPaint2);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawPath(mMinuteHandPath1, mMinuteHandPaint1);
            canvas.drawPath(mMinuteHandPath2, mMinuteHandPaint2);

            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawPath(mSecondHandPath1, mSecondHandPaint1);
                canvas.drawPath(mSecondHandPath2, mSecondHandPaint2);
            }

            canvas.restore();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            CockpitWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            CockpitWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static String getDeviceListing() {
        return "Build.PRODUCT: " + Build.PRODUCT + "\n" +
                "Build.MANUFACTURER: " + Build.MANUFACTURER + "\n" +
                "Build.BRAND: " + Build.BRAND + "\n" +
                "Build.DEVICE: " + Build.DEVICE + "\n" +
                "Build.MODEL: " + Build.MODEL + "\n" +
                "Build.HARDWARE: " + Build.HARDWARE + "\n" +
                "Build.FINGERPRINT: " + Build.FINGERPRINT + "\n" +
                "Build.TAGS: " + android.os.Build.TAGS + "\n" +
                "GL_RENDERER: " +android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER) + "\n" +
                "GL_VENDOR: " +android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR) + "\n" +
                "GL_VERSION: " +android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION) + "\n" +
                "GL_EXTENSIONS: " +android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_EXTENSIONS) + "\n";
    }
}
