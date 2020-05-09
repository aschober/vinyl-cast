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
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import tech.schober.vinylcast.R;

public class PlayStopView extends FrameLayout {

    private static final Property<PlayStopView, Integer> COLOR =
            new Property<PlayStopView, Integer>(Integer.class, "color") {
                @Override
                public Integer get(PlayStopView v) {
                    return v.getColor();
                }

                @Override
                public void set(PlayStopView v, Integer value) {
                    v.setColor(value);
                }
            };

    private static final long PLAY_PAUSE_ANIMATION_DURATION = 250;

    private final PlayStopDrawable mDrawable;
    private final Paint mPaint = new Paint();
    private final int mPauseBackgroundColor;
    private final int mPlayBackgroundColor;

    private AnimatorSet mAnimatorSet;
    private int mBackgroundColor;
    private int mWidth;
    private int mHeight;

    public PlayStopView(Context context) {
        super(context);
        mPlayBackgroundColor = Color.BLUE;
        mPauseBackgroundColor = Color.CYAN;
        int fillColor = Color.WHITE;
        mDrawable = new PlayStopDrawable(fillColor);
        init(context);
    }

    public PlayStopView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.PlayStopView,
                0, 0);
        int fillColor;
        try {
            mPlayBackgroundColor = a.getColor(R.styleable.PlayStopView_play_bg, Color.BLUE);
            mPauseBackgroundColor = a.getColor(R.styleable.PlayStopView_pause_bg, Color.CYAN);
            fillColor = a.getColor(R.styleable.PlayStopView_fill_color, Color.WHITE);
        } finally {
            a.recycle();
        }
        mDrawable = new PlayStopDrawable(fillColor);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mDrawable.setCallback(this);

        mBackgroundColor = mPlayBackgroundColor;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.isPlay = mDrawable.isPlay();
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        initStatus(ss.isPlay);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        final int size = Math.min(width, height);
        setMeasuredDimension(
                MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onSizeChanged(final int w, final int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mDrawable.setBounds(0, 0, w, h);
        mWidth = w;
        mHeight = h;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            setClipToOutline(true);
        }
    }

    public void setColor(int color) {
        mBackgroundColor = color;
        invalidate();
    }

    public int getColor() {
        return mBackgroundColor;
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mDrawable || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setColor(mBackgroundColor);
        final float radius = Math.min(mWidth, mHeight) / 2f;
        canvas.drawCircle(mWidth / 2f, mHeight / 2f, radius, mPaint);
        mDrawable.draw(canvas);
    }

    /**
     * Toogle the play/pause status
     */
    public void toggle() {
        toggle(true);
    }

    /**
     * Change status to play or pause
     *
     * @param isPlay for playing, false else
     */
    public void change(boolean isPlay) {
        change(isPlay, true);
    }

    /**
     * Change status to play or pause
     *
     * @param isPlay   for playing, false else
     * @param withAnim false to change status without animation
     */
    public void change(boolean isPlay, boolean withAnim) {
        if (mDrawable.isPlay() == isPlay)
            return;
        toggle(withAnim);
    }

    /**
     * Toogle the play/pause status
     *
     * @param withAnim false to change status without animation
     */
    public void toggle(boolean withAnim) {
        setDirection((isPlay() ? PlayStopDrawable.PLAYSTOP_ROTATION_CW : PlayStopDrawable.PLAYSTOP_ROTATION_CCW));
        if (withAnim) {
            if (mAnimatorSet != null) {
                mAnimatorSet.cancel();
            }

            mAnimatorSet = new AnimatorSet();
            final boolean isPlay = mDrawable.isPlay();
            final ObjectAnimator colorAnim = ObjectAnimator.ofInt(this, COLOR, isPlay ? mPauseBackgroundColor : mPlayBackgroundColor);
            colorAnim.setEvaluator(new ArgbEvaluator());
            final Animator pausePlayAnim = mDrawable.getPausePlayAnimator();
            mAnimatorSet.setInterpolator(new DecelerateInterpolator());
            mAnimatorSet.setDuration(PLAY_PAUSE_ANIMATION_DURATION);
            mAnimatorSet.playTogether(colorAnim, pausePlayAnim);
            mAnimatorSet.start();
        } else {
            final boolean isPlay = mDrawable.isPlay();
            initStatus(!isPlay);
            invalidate();
        }
    }

    private void initStatus(boolean isPlay) {
        if (isPlay) {
            mDrawable.setPlay();
            setColor(mPlayBackgroundColor);
        } else {
            mDrawable.setPause();
            setColor(mPauseBackgroundColor);
        }
    }

    public boolean isPlay() {
        return mDrawable.isPlay();
    }

    public int getDirection() {
        return mDrawable.getDirection();
    }

    public void setDirection(@PlayStopDrawable.PlayStopRotationDirection int direction) {
        mDrawable.setDirection(direction);
    }

    private static class SavedState extends BaseSavedState {
        boolean isPlay;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            isPlay = in.readByte() > 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeByte(isPlay ? (byte) 1 : (byte) 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}