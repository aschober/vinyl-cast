package tech.schober.vinylcast.ui.main;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

import tech.schober.vinylcast.R;

/**
 * Custom view that graphs data in the data queue
 */
public class BarGraphView extends View {

    private final Paint paint = new Paint();

    private float minValue;
    private float maxValue;
    private int length;

    private final Queue<Float> dataQueue = new ArrayDeque<>();

    public BarGraphView(Context context) {
        this(context, null);
    }

    public BarGraphView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.BarGraphView);

        minValue = array.getFloat(R.styleable.BarGraphView_minValue, 0);
        maxValue = array.getFloat(R.styleable.BarGraphView_maxValue, 1);
        length = array.getInteger(R.styleable.BarGraphView_length, 1);

        int color = array.getColor(R.styleable.BarGraphView_color, ContextCompat.getColor(context, R.color.colorPrimary));
        paint.setColor(color);

        array.recycle();
    }

    public void setMaxValue(float maxValue) {
        this.maxValue = maxValue;
    }

    public void setMinValue(float minValue) {
        this.minValue = minValue;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void setColor(@ColorRes int color) {
        paint.setColor(ContextCompat.getColor(getContext(), color));
    }

    public void clearData() {
        dataQueue.clear();
        invalidate();
    }

    public void addData(float data) {
        if (dataQueue.size() >= length) {
            dataQueue.poll();
        }

        dataQueue.offer(data);
        invalidate();
    }

    public void setData(double[] data) {
        length = data.length;
        dataQueue.clear();

        for (double item : data) {
            dataQueue.offer((float) item);
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        float unitLength = (float) getWidth() / ((length - 1) * 3 + 1);
        float ratio = (float) getHeight() / (maxValue - minValue);
        float x = 0;

        Iterator<Float> iterator = dataQueue.iterator();

        while (iterator.hasNext()) {
            float value = iterator.next() - minValue;

            if (value < minValue) {
                value = minValue;
            }

            if (value > maxValue - minValue) {
                value = maxValue - minValue;
            }

            canvas.drawRect(x, (float) getHeight() - ratio * value, x + unitLength, (float) getHeight(), paint);

            x += unitLength * 3;
        }

        super.onDraw(canvas);
    }
}
