package com.webonastick.watchface.avionics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /*
     * Updates rate in milliseconds for interactive mode. We update five times a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1) / 5;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
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

        private static final float HOUR_STROKE_WIDTH   = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_STROKE_WIDTH = 2f;
        
        private static final float TICK_OUTER_RADIUS           = 0.97f;
        private static final float HOUR_TICK_INNER_RADIUS      = 0.89f;
        private static final float MINUTE_TICK_INNER_RADIUS    = 0.92f;
        private static final float HOUR_TICK_STROKE_WIDTH      = 3f;
        private static final float MINUTE_TICK_STROKE_WIDTH    = 3f;
        private static final float TEXT_OUTER_RADIUS           = 0.84f;
        private static final float HOUR_TEXT_SIZE_PERCENT      = 12f;
        private static final float HOUR24_TEXT_SIZE_PERCENT    = 4f;

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
        private float mCenterX;
        private float mCenterY;
        private float sSecondHandLength;
        private float sSecondHandWidth;
        private float sMinuteHandLength;
        private float sMinuteHandWidth;
        private float sHourHandLength;
        private float sHourHandWidth;
        private float sHourTickLength;
        private float sMinuteTickLength;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mHourTickPaint;
        private Paint mMinuteTickPaint;
        private Paint mBackgroundPaint;
        private Paint mGrayBackgroundPaint;
        private Paint mHourTextPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private Typeface mTypeface;
        private Bitmap mCanvasBitmap = null;

        private int mBackgroundColor;
        private int mShadowColor;
        private int mTextColor;
        private int mHourTickColor;
        private int mMinuteTickColor;
        private int mHourHandColor;
        private int mMinuteHandColor;
        private int mSecondHandColor;

        private Path mHourHandPath;
        private Path mMinuteHandPath;
        private Path mSecondHandPath;

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

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.background_color));

            mGrayBackgroundPaint = new Paint();
            mGrayBackgroundPaint.setColor(Color.BLACK);

            mBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.background_color);
            mShadowColor     = ContextCompat.getColor(getApplicationContext(), R.color.shadow_color);
            mTextColor       = ContextCompat.getColor(getApplicationContext(), R.color.text_color);
            mHourTickColor   = ContextCompat.getColor(getApplicationContext(), R.color.hour_tick_color);
            mMinuteTickColor = ContextCompat.getColor(getApplicationContext(), R.color.minute_tick_color);
            mHourHandColor   = ContextCompat.getColor(getApplicationContext(), R.color.hour_hand_color);
            mMinuteHandColor = ContextCompat.getColor(getApplicationContext(), R.color.minute_hand_color);
            mSecondHandColor = ContextCompat.getColor(getApplicationContext(), R.color.second_hand_color);

            initializeBackground(holder);
            initializeWatchFace();
        }

        private void initializeBackground(SurfaceHolder holder) {
//            /* Extracts colors from background image to improve watchface style. */
//            Palette.from(mBackgroundBitmap).generate(new Palette.PaletteAsyncListener() {
//                @Override
//                public void onGenerated(Palette palette) {
//                    if (palette != null) {
//                        mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
//                        mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
//                        mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
//                        updateWatchHandStyle();
//                    }
//                }
//            });
        }

        private static final float SHADOW_RADIUS = 0.75f;
        private static final float SHADOW_OFFSET_X = 0f;
        private static final float SHADOW_OFFSET_Y = -0.5f;

        private void initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.WHITE;
            mWatchHandShadowColor = Color.BLACK;

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, SHADOW_OFFSET_X, SHADOW_OFFSET_Y, mShadowColor);
            mHourPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, SHADOW_OFFSET_X, SHADOW_OFFSET_Y, mShadowColor);
            mMinutePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, SHADOW_OFFSET_X, SHADOW_OFFSET_Y, mShadowColor);
            mSecondPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mHourTickPaint = new Paint();
            mHourTickPaint.setColor(mHourTickColor);
            mHourTickPaint.setStrokeWidth(HOUR_TICK_STROKE_WIDTH);
            mHourTickPaint.setAntiAlias(true);
            mHourTickPaint.setStyle(Paint.Style.STROKE);
            mHourTickPaint.setStrokeCap(Paint.Cap.BUTT);
            mHourTickPaint.setShadowLayer(SHADOW_RADIUS, SHADOW_OFFSET_X, SHADOW_OFFSET_Y, mShadowColor);

            mMinuteTickPaint = new Paint();
            mMinuteTickPaint.setColor(mMinuteTickColor);
            mMinuteTickPaint.setStrokeWidth(MINUTE_TICK_STROKE_WIDTH);
            mMinuteTickPaint.setAntiAlias(true);
            mMinuteTickPaint.setStyle(Paint.Style.STROKE);
            mMinuteTickPaint.setStrokeCap(Paint.Cap.BUTT);
            mMinuteTickPaint.setShadowLayer(SHADOW_RADIUS, SHADOW_OFFSET_X, SHADOW_OFFSET_Y, mShadowColor);

            mHourTextPaint = new Paint();
            mHourTextPaint.setColor(mTextColor);
            mHourTextPaint.setAntiAlias(true);
            mHourTextPaint.setTypeface(mTypeface);
            mHourTextPaint.setTextAlign(Paint.Align.CENTER);
            mHourTextPaint.setShadowLayer(SHADOW_RADIUS, SHADOW_OFFSET_X, SHADOW_OFFSET_Y, mShadowColor);
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
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
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
            mRadius = Math.min(width / 2f, height / 2f);
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            sSecondHandLength = (float) (mRadius * 0.875);
            sMinuteHandLength = (float) (mRadius * 0.75);
            sHourHandLength   = (float) (mRadius * 0.5);

            sHourHandWidth    = (float) (mRadius * 0.07);
            sMinuteHandWidth  = (float) (mRadius * 0.05);
            sSecondHandWidth  = (float) (mRadius * 0.03);

            sHourTickLength   = (float) (mRadius * 0.1);
            sMinuteTickLength = (float) (mRadius * 0.06);

            mCanvasBitmap = null;
            mBackgroundBitmap = null;
            mGrayBackgroundBitmap = null;


            mHourHandPath = new Path();
            mHourHandPath.moveTo(mCenterX - sHourHandWidth / 3, mCenterY);
            mHourHandPath.lineTo(mCenterX - sHourHandWidth / 2, mCenterY - sHourHandLength * 0.75f);
            mHourHandPath.lineTo(mCenterX, mCenterY - sHourHandLength);
            mHourHandPath.lineTo(mCenterX + sHourHandWidth / 2, mCenterY - sHourHandLength * 0.75f);
            mHourHandPath.lineTo(mCenterX + sHourHandWidth / 3, mCenterY);
            mHourHandPath.lineTo(mCenterX - sHourHandWidth / 3, mCenterY);
            mHourHandPath.addCircle(mCenterX, mCenterY, sHourHandWidth / 1.5f, Path.Direction.CW);
            
            mMinuteHandPath = new Path();
            mMinuteHandPath.moveTo(mCenterX - sMinuteHandWidth / 3, mCenterY);
            mMinuteHandPath.lineTo(mCenterX - sMinuteHandWidth / 2, mCenterY - sMinuteHandLength * 0.75f);
            mMinuteHandPath.lineTo(mCenterX, mCenterY - sMinuteHandLength);
            mMinuteHandPath.lineTo(mCenterX + sMinuteHandWidth / 2, mCenterY - sMinuteHandLength * 0.75f);
            mMinuteHandPath.lineTo(mCenterX + sMinuteHandWidth / 3, mCenterY);
            mMinuteHandPath.lineTo(mCenterX - sMinuteHandWidth / 3, mCenterY);
            mMinuteHandPath.addCircle(mCenterX, mCenterY, sMinuteHandWidth / 1.5f, Path.Direction.CW);

            mSecondHandPath = new Path();
            mSecondHandPath.moveTo(mCenterX - sSecondHandWidth / 3, mCenterY);
            mSecondHandPath.lineTo(mCenterX - sSecondHandWidth / 2, mCenterY - sSecondHandLength * 0.75f);
            mSecondHandPath.lineTo(mCenterX, mCenterY - sSecondHandLength);
            mSecondHandPath.lineTo(mCenterX + sSecondHandWidth / 2, mCenterY - sSecondHandLength * 0.75f);
            mSecondHandPath.lineTo(mCenterX + sSecondHandWidth / 3, mCenterY);
            mSecondHandPath.lineTo(mCenterX - sSecondHandWidth / 3, mCenterY);
            mSecondHandPath.addCircle(mCenterX, mCenterY, sSecondHandWidth / 1.5f, Path.Direction.CW);
        }

        private void drawTicks(Canvas canvas, int modulo) {
            canvas.save();
            for (int tick = 0; tick < 60; tick += modulo) {
                boolean isHourTick = tick % 5 == 0;
                if (isHourTick) {
                    canvas.drawLine(
                            mCenterX, mCenterY - mRadius * TICK_OUTER_RADIUS,
                            mCenterX, mCenterY - mRadius * HOUR_TICK_INNER_RADIUS,
                            mHourTickPaint
                    );
                } else {
                    canvas.drawLine(
                            mCenterX, mCenterY - mRadius * TICK_OUTER_RADIUS,
                            mCenterX, mCenterY - mRadius * MINUTE_TICK_INNER_RADIUS,
                            mMinuteTickPaint
                    );
                }
                canvas.rotate(6f * modulo, mCenterX, mCenterY);
            }
            canvas.restore();
        }

        private void initBackgroundBitmap(Canvas canvas) {
            if (mBackgroundBitmap != null) {
                return;
            }

            int width = canvas.getWidth();
            int height = canvas.getHeight();

            Canvas backgroundCanvas = new Canvas();
            mBackgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            backgroundCanvas.setBitmap(mBackgroundBitmap);
            backgroundCanvas.drawRect(0, 0, width, height, mBackgroundPaint);

            drawTicks(backgroundCanvas, 1);

            for (int hour = 1; hour <= 12; hour += 1) {
                mHourTextPaint.setTextSize(mRadius * 2 / 100 * HOUR_TEXT_SIZE_PERCENT);
                int deg = 30 * (hour % 12);
                String sHour = Integer.toString(hour);
                Rect textBounds = new Rect();
                mHourTextPaint.getTextBounds(sHour, 0, sHour.length(), textBounds);
                float x = mCenterX + (float)Math.sin(Math.PI * deg / 180f) * TEXT_OUTER_RADIUS * mRadius;
                float y = mCenterY - (float)Math.cos(Math.PI * deg / 180f) * TEXT_OUTER_RADIUS * mRadius;
                x = x - (float)Math.sin(Math.PI * deg / 180f) * textBounds.width() / 2;
                y = y + (float)Math.cos(Math.PI * deg / 180f) * textBounds.height() / 2;
                backgroundCanvas.drawText(sHour, x, y + textBounds.height() / 2f, mHourTextPaint);

                if (hour % 3 == 0) {
                    mHourTextPaint.setTextSize(mRadius * 2 / 100 * HOUR24_TEXT_SIZE_PERCENT);
                    sHour = Integer.toString(hour + 12);
                    Rect textBounds24 = new Rect();
                    mHourTextPaint.getTextBounds(sHour, 0, sHour.length(), textBounds24);
                    x = x - (float)Math.sin(Math.PI * deg / 180f) * textBounds.width() / 2;
                    y = y + (float)Math.cos(Math.PI * deg / 180f) * textBounds.height() / 2;
                    x = x - (float)Math.sin(Math.PI * deg / 180f) * mRadius * 0.02f;
                    y = y + (float)Math.cos(Math.PI * deg / 180f) * mRadius * 0.02f;
                    x = x - (float)Math.sin(Math.PI * deg / 180f) * textBounds24.width() / 2;
                    y = y + (float)Math.cos(Math.PI * deg / 180f) * textBounds24.height() / 2;
                    backgroundCanvas.drawText(sHour, x, y + textBounds24.height() / 2f, mHourTextPaint);
                }
            }

            mCanvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas.setBitmap(mCanvasBitmap);
        }

        private void initGrayBackgroundBitmap(Canvas canvas) {
            if (mGrayBackgroundBitmap != null) {
                return;
            }

            int width = canvas.getWidth();
            int height = canvas.getHeight();

            Canvas backgroundCanvas = new Canvas();
            mGrayBackgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            backgroundCanvas.setBitmap(mGrayBackgroundBitmap);
            backgroundCanvas.drawRect(0, 0, width, height, mBackgroundPaint);

            drawTicks(backgroundCanvas, 5);

            mCanvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            backgroundCanvas.setBitmap(mCanvasBitmap);

            mCanvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas.setBitmap(mCanvasBitmap);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
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

            drawBackground(canvas, bounds);
            drawWatchFace(canvas, bounds);
        }

        private void drawBackground(Canvas canvas, Rect bounds) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                initGrayBackgroundBitmap(canvas);
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, null);
            } else {
                initBackgroundBitmap(canvas);
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
            }
        }

        private void drawWatchFace(Canvas canvas, Rect bounds) {
            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds = (float)mCalendar.get(Calendar.SECOND) + (float)mCalendar.get(Calendar.MILLISECOND) / 1000f; /* 0 to 60 */
            final float minutes = (float)mCalendar.get(Calendar.MINUTE) + (float)seconds / 60f; /* 0 to 60 */
            final float hours   = (float)mCalendar.get(Calendar.HOUR)   + (float)minutes / 60f; /* 0 to 12 */

            Log.d(TAG, "> " + seconds + " " + minutes + " " + hours);

            final float secondsRotation = seconds * 6f;
            final float minutesRotation = minutes * 6f;
            final float hoursRotation   = hours * 30f;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawPath(mHourHandPath, mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawPath(mMinuteHandPath, mMinutePaint);

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawPath(mSecondHandPath, mSecondPaint);
            }

            /* Restore the canvas' original orientation. */
            canvas.restore();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
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

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
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
