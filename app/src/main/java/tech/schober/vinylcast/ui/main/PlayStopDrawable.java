package tech.schober.vinylcast.ui.main;
/*
 *
 * The MIT License (MIT)
 *
 * Copyright 2016 OHoussein
 *
 * Copyright (c) 2015 Alex Lockwood
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Based on MaterialPlayPauseView
 * https://github.com/OHoussein/android-material-play-pause-view
 *
 * Modifications Copyright 2020 Allen Schober
 */

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Property;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class PlayStopDrawable extends Drawable {

    private static final Property<PlayStopDrawable, Float> PROGRESS =
            new Property<PlayStopDrawable, Float>(Float.class, "progress") {
                @Override
                public Float get(PlayStopDrawable d) {
                    return d.getProgress();
                }

                @Override
                public void set(PlayStopDrawable d, Float value) {
                    d.setProgress(value);
                }
            };

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PLAYSTOP_ROTATION_CW, PLAYSTOP_ROTATION_CCW})
    public @interface PlayStopRotationDirection {}
    public static final int PLAYSTOP_ROTATION_CW = 1;
    public static final int PLAYSTOP_ROTATION_CCW = 2;

    private final Path mLeftPauseBar = new Path();
    private final Path mRightPauseBar = new Path();
    private final Paint mPaint = new Paint();
    private final RectF mBounds = new RectF();
    private float mPauseBarWidth;
    private float mPauseBarHeight;
    private float mPauseBarDistance;

    private float mWidth;
    private float mHeight;
    private @PlayStopRotationDirection int mDirection = PLAYSTOP_ROTATION_CCW;

    private float mProgress = 1;
    private boolean mIsPlay = true;

    public PlayStopDrawable(@ColorInt int fillColor) {
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(fillColor);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mBounds.set(bounds);
        mWidth = mBounds.width();
        mHeight = mBounds.height();

        mPauseBarHeight = mHeight / 2.5f;
        mPauseBarWidth = mPauseBarHeight / 2f;
        mPauseBarDistance = 0;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        mLeftPauseBar.rewind();
        mRightPauseBar.rewind();

        // The current distance between the two pause bars.
        final float barDist = lerp(mPauseBarDistance, 0, mProgress) - 1;
        // The current width of each pause bar.
        final float barWidth = lerp(mPauseBarWidth, mPauseBarHeight / 2f, mProgress);
        // The current position of the left pause bar's top left coordinate.
        final float firstBarTopLeft = lerp(0, barWidth, mProgress);
        // The current position of the right pause bar's top right coordinate.
        final float secondBarTopRight = lerp(2 * barWidth + barDist, barWidth + barDist, mProgress);


        // Draw the left pause bar. The left pause bar transforms into the
        // top half of the play button triangle by animating the position of the
        // rectangle's top left coordinate and expanding its bottom width.
        mLeftPauseBar.moveTo(0, 0);
        mLeftPauseBar.lineTo(firstBarTopLeft, -mPauseBarHeight);
        mLeftPauseBar.lineTo(barWidth, -mPauseBarHeight);
        mLeftPauseBar.lineTo(barWidth, 0);
        mLeftPauseBar.close();

        // Draw the right pause bar. The right pause bar transforms into the
        // bottom half of the play button triangle by animating the position of the
        // rectangle's top right coordinate and expanding its bottom width.
        mRightPauseBar.moveTo(barWidth + barDist, 0);
        mRightPauseBar.lineTo(barWidth + barDist, -mPauseBarHeight);
        mRightPauseBar.lineTo(secondBarTopRight, -mPauseBarHeight);
        mRightPauseBar.lineTo(2 * barWidth + barDist, 0);
        mRightPauseBar.close();

        canvas.save();

        // Translate the play button a tiny bit to the right so it looks more centered.
        canvas.translate(lerp(0, mPauseBarHeight / 8f, mProgress), 0);

        final float rotationProgress = mIsPlay ? 1 - mProgress : mProgress;
        if (mDirection == PLAYSTOP_ROTATION_CW) {
            // (1) Pause --> Play: rotate 90 to 0 degrees clockwise.
            // (2) Play --> Pause: rotate 180 to 90 degrees clockwise.
            final float startingRotation = mIsPlay ? 0 : 90;
            canvas.rotate(lerp(startingRotation + 90, startingRotation, rotationProgress), mWidth / 2f, mHeight / 2f);
        } else {
            // (1) Pause --> Play: rotate 0 to 90 degrees clockwise.
            // (2) Play --> Pause: rotate 90 to 180 degrees clockwise.
            final float startingRotation = mIsPlay ? 90 : 0;
            canvas.rotate(lerp(startingRotation, startingRotation + 90, rotationProgress), mWidth / 2f, mHeight / 2f);
        }

        // Position the pause/play button in the center of the drawable's bounds.
        canvas.translate(mWidth / 2f - ((2 * barWidth + barDist) / 2f), mHeight / 2f + (mPauseBarHeight / 2f));

        // Draw the two bars that form the animated pause/play button.
        canvas.drawPath(mLeftPauseBar, mPaint);
        canvas.drawPath(mRightPauseBar, mPaint);

        canvas.restore();
    }

    public void setPlay() {
        mIsPlay = true;
        mProgress = 1;
    }

    public void setPause() {
        mIsPlay = false;
        mProgress = 0;
    }

    public Animator getPausePlayAnimator() {
        final Animator anim = ObjectAnimator.ofFloat(this, PROGRESS, mIsPlay ? 1 : 0, mIsPlay ? 0 : 1);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsPlay = !mIsPlay;
            }
        });
        return anim;
    }

    public boolean isPlay() {
        return mIsPlay;
    }

    private void setProgress(float progress) {
        mProgress = progress;
        invalidateSelf();
    }

    private float getProgress() {
        return mProgress;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void setDirection(@PlayStopRotationDirection int direction) {
        mDirection = direction;
        invalidateSelf();
    }

    public int getDirection() {
        return mDirection;
    }

    /**
     * Linear interpolate between a and b with parameter t.
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
