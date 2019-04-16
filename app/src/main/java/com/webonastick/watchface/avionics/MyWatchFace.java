package com.webonastick.watchface.avionics;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MyWatchFace extends CanvasWatchFaceService {

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1) / 5;

    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
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
        private static final String TAG = "MyWatchFace";

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

        private static final float HAND_SHADOW_RADIUS   = 0.75f;
        private static final float HAND_SHADOW_OFFSET_X = 0f;
        private static final float HAND_SHADOW_OFFSET_Y = -1.5f;

        private static final int TICK_NUMBER_SHADOW = 2;

        private static final float HOUR_HAND_LENGTH    = 0.5f;
        private static final float MINUTE_HAND_LENGTH  = 0.75f;
        private static final float SECOND_HAND_LENGTH  = 0.875f;
        private static final float BATTERY_HAND_LENGTH = 0.875f;

        private static final float HOUR_HAND_WIDTH    = 0.04f;
        private static final float MINUTE_HAND_WIDTH  = 0.04f;
        private static final float SECOND_HAND_WIDTH  = 0.015f;
        private static final float BATTERY_HAND_WIDTH = 0.02f;

        private static final float HOUR_HAND_STROKE_WIDTH    = 4f;
        private static final float MINUTE_HAND_STROKE_WIDTH  = 4f;
        private static final float SECOND_HAND_STROKE_WIDTH  = 2f;
        private static final float BATTERY_HAND_STROKE_WIDTH = 3f;

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
        
        private Paint mHourHandFillPaint;
        private Paint mMinuteHandFillPaint;
        private Paint mSecondHandFillPaint;
        private Paint mBatteryHandFillPaint;
        
        private Paint mHourHandStrokePaint;
        private Paint mMinuteHandStrokePaint;
        private Paint mSecondHandStrokePaint;
        private Paint mBatteryHandStrokePaint;

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

        private int mHourHandFillColor;
        private int mMinuteHandFillColor;
        private int mSecondHandFillColor;
        private int mBatteryHandFillColor;

        private int mHourHandStrokeColor;
        private int mMinuteHandStrokeColor;
        private int mSecondHandStrokeColor;
        private int mBatteryHandStrokeColor;

        private Path mHourHandPath;
        private Path mMinuteHandPath;
        private Path mSecondHandPath;
        private Path mBatteryHandPath;

        private final boolean demoTimeMode = false;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            mTypeface = Typeface.createFromAsset(
                    MyWatchFace.this.getResources().getAssets(),
                    "fonts/routed-gothic.ttf"
            );

            mBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.background_color);
            mShadowColor     = ContextCompat.getColor(getApplicationContext(), R.color.shadow_color);
            mTextColor       = ContextCompat.getColor(getApplicationContext(), R.color.text_color);

            mHourTickColor    = ContextCompat.getColor(getApplicationContext(), R.color.hour_tick_color);
            mMinuteTickColor  = ContextCompat.getColor(getApplicationContext(), R.color.minute_tick_color);
            mBatteryTickColor = ContextCompat.getColor(getApplicationContext(), R.color.battery_tick_color);

            mHourHandFillColor    = ContextCompat.getColor(getApplicationContext(), R.color.hour_hand_fill_color);
            mMinuteHandFillColor  = ContextCompat.getColor(getApplicationContext(), R.color.minute_hand_fill_color);
            mSecondHandFillColor  = ContextCompat.getColor(getApplicationContext(), R.color.second_hand_fill_color);
            mBatteryHandFillColor = ContextCompat.getColor(getApplicationContext(), R.color.battery_hand_fill_color);

            mHourHandStrokeColor    = ContextCompat.getColor(getApplicationContext(), R.color.hour_hand_stroke_color);
            mMinuteHandStrokeColor  = ContextCompat.getColor(getApplicationContext(), R.color.minute_hand_stroke_color);
            mSecondHandStrokeColor  = ContextCompat.getColor(getApplicationContext(), R.color.second_hand_stroke_color);
            mBatteryHandStrokeColor = ContextCompat.getColor(getApplicationContext(), R.color.battery_hand_stroke_color);
            
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundColor);

            mGrayBackgroundPaint = new Paint();
            mGrayBackgroundPaint.setColor(Color.BLACK);

            initializeWatchFace();
        }

        private void initializeWatchFace() {
            mHourHandFillPaint = new Paint();
            mHourHandFillPaint.setColor(mHourHandFillColor);
            mHourHandFillPaint.setStrokeWidth(Math.max(0, HOUR_HAND_STROKE_WIDTH   - 1));
            mHourHandFillPaint.setAntiAlias(true);
            mHourHandFillPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourHandFillPaint.setStrokeJoin(Paint.Join.ROUND);
            mHourHandFillPaint.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
            mHourHandFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mMinuteHandFillPaint = new Paint();
            mMinuteHandFillPaint.setColor(mMinuteHandFillColor);
            mMinuteHandFillPaint.setStrokeWidth(Math.max(0, MINUTE_HAND_STROKE_WIDTH - 1));
            mMinuteHandFillPaint.setAntiAlias(true);
            mMinuteHandFillPaint.setStrokeCap(Paint.Cap.ROUND);
            mMinuteHandFillPaint.setStrokeJoin(Paint.Join.ROUND);
            mMinuteHandFillPaint.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
            mMinuteHandFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mSecondHandFillPaint = new Paint();
            mSecondHandFillPaint.setColor(mSecondHandFillColor);
            mSecondHandFillPaint.setStrokeWidth(Math.max(0, SECOND_HAND_STROKE_WIDTH - 1));
            mSecondHandFillPaint.setAntiAlias(true);
            mSecondHandFillPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondHandFillPaint.setStrokeJoin(Paint.Join.ROUND);
            mSecondHandFillPaint.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
            mSecondHandFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mBatteryHandFillPaint = new Paint();
            mBatteryHandFillPaint.setColor(mBatteryHandFillColor);
            mBatteryHandFillPaint.setStrokeWidth(Math.max(0, BATTERY_HAND_STROKE_WIDTH - 1));
            mBatteryHandFillPaint.setAntiAlias(true);
            mBatteryHandFillPaint.setStrokeCap(Paint.Cap.ROUND);
            mBatteryHandFillPaint.setStrokeJoin(Paint.Join.ROUND);
            mBatteryHandFillPaint.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
            mBatteryHandFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mHourHandStrokePaint = new Paint();
            mHourHandStrokePaint.setColor(mHourHandStrokeColor);
            mHourHandStrokePaint.setStrokeWidth(HOUR_HAND_STROKE_WIDTH);
            mHourHandStrokePaint.setAntiAlias(true);
            mHourHandStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            mHourHandStrokePaint.setStrokeJoin(Paint.Join.ROUND);
            mHourHandStrokePaint.setStyle(Paint.Style.STROKE);

            mMinuteHandStrokePaint = new Paint();
            mMinuteHandStrokePaint.setColor(mMinuteHandStrokeColor);
            mMinuteHandStrokePaint.setStrokeWidth(MINUTE_HAND_STROKE_WIDTH);
            mMinuteHandStrokePaint.setAntiAlias(true);
            mMinuteHandStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinuteHandStrokePaint.setStrokeJoin(Paint.Join.ROUND);
            mMinuteHandStrokePaint.setStyle(Paint.Style.STROKE);

            mSecondHandStrokePaint = new Paint();
            mSecondHandStrokePaint.setColor(mSecondHandStrokeColor);
            mSecondHandStrokePaint.setStrokeWidth(SECOND_HAND_STROKE_WIDTH);
            mSecondHandStrokePaint.setAntiAlias(true);
            mSecondHandStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondHandStrokePaint.setStrokeJoin(Paint.Join.ROUND);
            mSecondHandStrokePaint.setStyle(Paint.Style.STROKE);
            
            mBatteryHandStrokePaint = new Paint();
            mBatteryHandStrokePaint.setColor(mBatteryHandStrokeColor);
            mBatteryHandStrokePaint.setStrokeWidth(BATTERY_HAND_STROKE_WIDTH);
            mBatteryHandStrokePaint.setAntiAlias(true);
            mBatteryHandStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            mBatteryHandStrokePaint.setStrokeJoin(Paint.Join.ROUND);
            mBatteryHandStrokePaint.setStyle(Paint.Style.STROKE);

            mHourTickPaint = new Paint();
            mHourTickPaint.setColor(mHourTickColor);
            mHourTickPaint.setStrokeWidth(HOUR_TICK_STROKE_WIDTH);
            mHourTickPaint.setAntiAlias(true);
            mHourTickPaint.setStyle(Paint.Style.STROKE);
            mHourTickPaint.setStrokeCap(Paint.Cap.BUTT);

            mMinuteTickPaint = new Paint();
            mMinuteTickPaint.setColor(mMinuteTickColor);
            mMinuteTickPaint.setStrokeWidth(MINUTE_TICK_STROKE_WIDTH);
            mMinuteTickPaint.setAntiAlias(true);
            mMinuteTickPaint.setStyle(Paint.Style.STROKE);
            mMinuteTickPaint.setStrokeCap(Paint.Cap.BUTT);

            mBatteryTickPaint = new Paint();
            mBatteryTickPaint.setColor(mBatteryTickColor);
            mBatteryTickPaint.setStrokeWidth(BATTERY_TICK_STROKE_WIDTH);
            mBatteryTickPaint.setAntiAlias(true);
            mBatteryTickPaint.setStyle(Paint.Style.STROKE);
            mBatteryTickPaint.setStrokeCap(Paint.Cap.BUTT);

            mHourTextPaint = new Paint();
            mHourTextPaint.setColor(mTextColor);
            mHourTextPaint.setAntiAlias(true);
            mHourTextPaint.setTypeface(mTypeface);
            mHourTextPaint.setTextAlign(Paint.Align.CENTER);
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

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourHandFillPaint.setColor(Color.BLACK);
                mMinuteHandFillPaint.setColor(Color.BLACK);
                mSecondHandFillPaint.setColor(Color.BLACK);
                mBatteryHandFillPaint.setColor(Color.BLACK);
                mHourHandStrokePaint.setColor(Color.WHITE);
                mMinuteHandStrokePaint.setColor(Color.WHITE);
                mSecondHandStrokePaint.setColor(Color.WHITE);
                mBatteryHandStrokePaint.setColor(Color.WHITE);
                mHourHandFillPaint.clearShadowLayer();
                mMinuteHandFillPaint.clearShadowLayer();
                mSecondHandFillPaint.clearShadowLayer();
                mBatteryHandFillPaint.clearShadowLayer();
            } else {
                mHourHandFillPaint.setColor(mHourHandFillColor);
                mMinuteHandFillPaint.setColor(mMinuteHandFillColor);
                mSecondHandFillPaint.setColor(mSecondHandFillColor);
                mBatteryHandFillPaint.setColor(mBatteryHandFillColor);
                mHourHandStrokePaint.setColor(mHourHandStrokeColor);
                mMinuteHandStrokePaint.setColor(mMinuteHandStrokeColor);
                mSecondHandStrokePaint.setColor(mSecondHandStrokeColor);
                mBatteryHandStrokePaint.setColor(mBatteryHandStrokeColor);
                mHourHandFillPaint.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
                mMinuteHandFillPaint.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
                mSecondHandFillPaint.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
                mBatteryHandFillPaint.setShadowLayer(HAND_SHADOW_RADIUS, HAND_SHADOW_OFFSET_X, HAND_SHADOW_OFFSET_Y, mShadowColor);
            }
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                mHourHandFillPaint.setAntiAlias(false);
                mMinuteHandFillPaint.setAntiAlias(false);
                mSecondHandFillPaint.setAntiAlias(false);
                mBatteryHandFillPaint.setAntiAlias(false);
                mHourTickPaint.setAntiAlias(false);
                mMinuteTickPaint.setAntiAlias(false);
                mBatteryTickPaint.setAntiAlias(false);
            } else {
                mHourHandFillPaint.setAntiAlias(true);
                mMinuteHandFillPaint.setAntiAlias(true);
                mSecondHandFillPaint.setAntiAlias(true);
                mBatteryHandFillPaint.setAntiAlias(true);
                mHourTickPaint.setAntiAlias(true);
                mMinuteTickPaint.setAntiAlias(true);
                mBatteryTickPaint.setAntiAlias(true);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourHandFillPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinuteHandFillPaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondHandFillPaint.setAlpha(inMuteMode ? 80 : 255);
                mBatteryHandFillPaint.setAlpha(inMuteMode ? 100 : 255);
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
            mWidth = width;
            mHeight = height;
            mRadius   = Math.min(width / 2f, height / 2f);
            mDiameter = Math.min(width, height);
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            mBatteryCenterX    = width / 2f;
            mBatteryCenterY    = height * 0.44f;
            mBatteryRadius     = height * 0.16f;

            mHourHandLength = mRadius * HOUR_HAND_LENGTH;
            mMinuteHandLength = mRadius * MINUTE_HAND_LENGTH;
            mSecondHandLength = mRadius * SECOND_HAND_LENGTH;
            mBatteryHandLength = mBatteryRadius * BATTERY_HAND_LENGTH;

            mHourHandWidth = mDiameter * HOUR_HAND_WIDTH;
            mMinuteHandWidth = mDiameter * MINUTE_HAND_WIDTH;
            mSecondHandWidth = mDiameter * SECOND_HAND_WIDTH;
            mBatteryHandWidth = mDiameter * BATTERY_HAND_WIDTH;

            mBackgroundBitmap = null;
            mGrayBackgroundBitmap = null;

            initHandPaths();
            initBackgroundBitmap(width, height);
            initGrayBackgroundBitmap(width, height);
        }

        private void initHandPaths() {
            mHourHandPath = new Path();
            mHourHandPath.moveTo(mCenterX - mHourHandWidth / 3, mCenterY);
            mHourHandPath.lineTo(mCenterX - mHourHandWidth / 2, mCenterY - mHourHandLength * 0.75f);
            mHourHandPath.lineTo(mCenterX, mCenterY - mHourHandLength);
            mHourHandPath.lineTo(mCenterX + mHourHandWidth / 2, mCenterY - mHourHandLength * 0.75f);
            mHourHandPath.lineTo(mCenterX + mHourHandWidth / 3, mCenterY);
            mHourHandPath.lineTo(mCenterX - mHourHandWidth / 3, mCenterY);

            Path hourCirclePath = new Path();
            hourCirclePath.addCircle(mCenterX, mCenterY, mHourHandWidth / 1.5f, Path.Direction.CW);

            mHourHandPath.op(hourCirclePath, Path.Op.UNION);

            mMinuteHandPath = new Path();
            mMinuteHandPath.moveTo(mCenterX - mMinuteHandWidth / 3, mCenterY);
            mMinuteHandPath.lineTo(mCenterX - mMinuteHandWidth / 2, mCenterY - mMinuteHandLength * 0.75f);
            mMinuteHandPath.lineTo(mCenterX, mCenterY - mMinuteHandLength);
            mMinuteHandPath.lineTo(mCenterX + mMinuteHandWidth / 2, mCenterY - mMinuteHandLength * 0.75f);
            mMinuteHandPath.lineTo(mCenterX + mMinuteHandWidth / 3, mCenterY);
            mMinuteHandPath.lineTo(mCenterX - mMinuteHandWidth / 3, mCenterY);

            Path minuteCirclePath = new Path();
            minuteCirclePath.addCircle(mCenterX, mCenterY, mMinuteHandWidth / 1.5f, Path.Direction.CW);

            mMinuteHandPath.op(minuteCirclePath, Path.Op.UNION);

            mSecondHandPath = new Path();
            mSecondHandPath.moveTo(mCenterX - mSecondHandWidth / 3, mCenterY);
            mSecondHandPath.lineTo(mCenterX - mSecondHandWidth / 2, mCenterY - mSecondHandLength * 0.75f);
            mSecondHandPath.lineTo(mCenterX, mCenterY - mSecondHandLength);
            mSecondHandPath.lineTo(mCenterX + mSecondHandWidth / 2, mCenterY - mSecondHandLength * 0.75f);
            mSecondHandPath.lineTo(mCenterX + mSecondHandWidth / 3, mCenterY);
            mSecondHandPath.lineTo(mCenterX - mSecondHandWidth / 3, mCenterY);

            Path secondCirclePath = new Path();
            secondCirclePath.addCircle(mCenterX, mCenterY, mSecondHandWidth / 1.5f, Path.Direction.CW);

            mSecondHandPath.op(secondCirclePath, Path.Op.UNION);

            mBatteryHandPath = new Path();
            mBatteryHandPath.moveTo(mBatteryCenterX - mBatteryHandWidth / 3, mBatteryCenterY);
            mBatteryHandPath.lineTo(mBatteryCenterX - mBatteryHandWidth / 2, mBatteryCenterY - mBatteryHandLength * 0.75f);
            mBatteryHandPath.lineTo(mBatteryCenterX, mBatteryCenterY - mBatteryHandLength);
            mBatteryHandPath.lineTo(mBatteryCenterX + mBatteryHandWidth / 2, mBatteryCenterY - mBatteryHandLength * 0.75f);
            mBatteryHandPath.lineTo(mBatteryCenterX + mBatteryHandWidth / 3, mBatteryCenterY);
            mBatteryHandPath.lineTo(mBatteryCenterX - mBatteryHandWidth / 3, mBatteryCenterY);

            Path batteryCirclePath = new Path();
            batteryCirclePath.addCircle(mBatteryCenterX, mBatteryCenterY, mBatteryHandWidth / 1.5f, Path.Direction.CW);

            mBatteryHandPath.op(batteryCirclePath, Path.Op.UNION);
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

            float centerX = mBatteryCenterX;
            float centerY = mBatteryCenterY + (shadow ? (dy * 1f) : 0f);

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

            canvas.save();
            canvas.rotate(-90f, centerX, centerY);
            float rotation = -90f;
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
                            tickCenterY + textBounds.height() / 2f,
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

                canvas.rotate(180f / 10, centerX, centerY);
                rotation += 180f / 10;
            }
            canvas.restore();

            canvas.drawText(
                    "BATTERY",
                    mBatteryCenterX,
                    mBatteryCenterY - mBatteryRadius / 3 + 0.5f * mDiameter * BATTERY_TEXT_SIZE_PERCENT / 100,
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
//            switch (tapType) {
//                case TAP_TYPE_TOUCH:
//                    // The user has started touching the screen.
//                    break;
//                case TAP_TYPE_TOUCH_CANCEL:
//                    // The user has started a different gesture or otherwise cancelled the tap.
//                    break;
//                case TAP_TYPE_TAP:
//                    // The user has completed the tap gesture.
//                    // TODO: Add code to handle the tap gesture.
//                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
//                            .show();
//                    break;
//            }
            invalidate();
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
                Intent batteryStatus = MyWatchFace.this.registerReceiver(null, intentFilter);
                if (batteryStatus != null) {
                    int batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    batteryPercentage = batteryLevel * 100f / batteryScale;
                } else {
                    batteryPercentage = -1f;
                }
            }
            if (batteryPercentage >= 0f && batteryPercentage <= 100f) {
                float batteryRotation = -90f + 180f * batteryPercentage / 100f;
                canvas.save();
                canvas.rotate(batteryRotation, mBatteryCenterX, mBatteryCenterY);
                canvas.drawPath(mBatteryHandPath, mBatteryHandFillPaint);
                canvas.drawPath(mBatteryHandPath, mBatteryHandStrokePaint);
                canvas.restore();
            }
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
            canvas.drawPath(mHourHandPath, mHourHandFillPaint);
            canvas.drawPath(mHourHandPath, mHourHandStrokePaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawPath(mMinuteHandPath, mMinuteHandFillPaint);
            canvas.drawPath(mMinuteHandPath, mMinuteHandStrokePaint);

            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawPath(mSecondHandPath, mSecondHandFillPaint);
                canvas.drawPath(mSecondHandPath, mSecondHandStrokePaint);
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
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
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
}
