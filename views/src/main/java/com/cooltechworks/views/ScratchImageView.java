/*
 * Copyright 2016 Harish Sridharan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cooltechworks.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.cooltechworks.utils.BitmapUtils;

/**
 * Scratch card ImageView. Optional custom overlay via app:customScrach (PR #4).
 */
public class ScratchImageView extends AppCompatImageView {

    public interface IRevealListener {
        void onRevealed(ScratchImageView iv);
        void onRevealPercentChangedListener(ScratchImageView siv, float percent);
    }

    public static final float STROKE_WIDTH = 12f;

    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private Bitmap mScratchBitmap;
    private Canvas mCanvas;
    private Path mErasePath;
    private Path mTouchPath;
    private Paint mBitmapPaint;
    private Paint mErasePaint;
    private Paint mGradientBgPaint;
    private BitmapDrawable mDrawable;
    private IRevealListener mRevealListener;
    private float mRevealPercent;
    private int mThreadCount = 0;

    /** Optional custom scratch overlay (e.g. app:customScrach). */
    private Drawable mCustomScrachView;

    public ScratchImageView(Context context) {
        super(context);
        init();
    }

    public ScratchImageView(Context context, AttributeSet set) {
        super(context, set);
        loadCustomScrach(context, set, 0);
        init();
    }

    public ScratchImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        loadCustomScrach(context, attrs, defStyleAttr);
        init();
    }

    private void loadCustomScrach(Context context, AttributeSet attrs, int defStyleAttr) {
        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ScratchImageView, defStyleAttr, 0);
        try {
            mCustomScrachView = a.getDrawable(R.styleable.ScratchImageView_customScrach);
        } finally {
            a.recycle();
        }
    }

    public void setStrokeWidth(int multiplier) {
        mErasePaint.setStrokeWidth(multiplier * STROKE_WIDTH);
    }

    private void init() {
        mTouchPath = new Path();
        mErasePaint = new Paint();
        mErasePaint.setAntiAlias(true);
        mErasePaint.setDither(true);
        mErasePaint.setColor(0xFFFF0000);
        mErasePaint.setStyle(Paint.Style.STROKE);
        mErasePaint.setStrokeJoin(Paint.Join.BEVEL);
        mErasePaint.setStrokeCap(Paint.Cap.ROUND);
        setStrokeWidth(6);
        mGradientBgPaint = new Paint();
        mErasePath = new Path();
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);

        Bitmap scratchBitmap;
        if (mCustomScrachView != null) {
            scratchBitmap = drawableToBitmap(mCustomScrachView);
            if (scratchBitmap == null) {
                scratchBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_scratch_pattern);
            }
        } else {
            scratchBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_scratch_pattern);
        }
        mDrawable = new BitmapDrawable(getResources(), scratchBitmap);
        mDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        setEraserMode();
    }

    /** Converts any drawable (including VectorDrawable) to a Bitmap for use as scratch overlay. */
    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0) w = 300;
        if (h <= 0) h = 300;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mScratchBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mScratchBitmap);
        Rect rect = new Rect(0, 0, mScratchBitmap.getWidth(), mScratchBitmap.getHeight());
        mDrawable.setBounds(rect);
        int startGradientColor = ContextCompat.getColor(getContext(), R.color.scratch_start_gradient);
        int endGradientColor = ContextCompat.getColor(getContext(), R.color.scratch_end_gradient);
        mGradientBgPaint.setShader(new LinearGradient(0, 0, 0, getHeight(), startGradientColor, endGradientColor, Shader.TileMode.MIRROR));
        mCanvas.drawRect(rect, mGradientBgPaint);
        mDrawable.draw(mCanvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(mScratchBitmap, 0, 0, mBitmapPaint);
        canvas.drawPath(mErasePath, mErasePaint);
    }

    private void touch_start(float x, float y) {
        mErasePath.reset();
        mErasePath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    public void clear() {
        int[] bounds = getImageBounds();
        int left = bounds[0];
        int top = bounds[1];
        int right = bounds[2];
        int bottom = bounds[3];
        int width = right - left;
        int height = bottom - top;
        int centerX = left + width / 2;
        int centerY = top + height / 2;
        left = centerX - width / 2;
        top = centerY - height / 2;
        right = left + width;
        bottom = top + height;
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mCanvas.drawRect(left, top, right, bottom, paint);
        checkRevealed();
        invalidate();
    }

    private void touch_move(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mErasePath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
            drawPath();
        }
        mTouchPath.reset();
        mTouchPath.addCircle(mX, mY, 30, Path.Direction.CW);
    }

    private void drawPath() {
        mErasePath.lineTo(mX, mY);
        mCanvas.drawPath(mErasePath, mErasePaint);
        mTouchPath.reset();
        mErasePath.reset();
        mErasePath.moveTo(mX, mY);
        checkRevealed();
    }

    public void reveal() {
        clear();
    }

    private void touch_up() {
        drawPath();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touch_start(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touch_move(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touch_up();
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    public int getColor() {
        return mErasePaint.getColor();
    }

    public Paint getErasePaint() {
        return mErasePaint;
    }

    public void setEraserMode() {
        getErasePaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setRevealListener(IRevealListener listener) {
        this.mRevealListener = listener;
    }

    public boolean isRevealed() {
        return mRevealPercent == 1;
    }

    private void checkRevealed() {
        if (!isRevealed() && mRevealListener != null) {
            int[] bounds = getImageBounds();
            int left = bounds[0];
            int top = bounds[1];
            int width = bounds[2] - left;
            int height = bounds[3] - top;
            if (mThreadCount > 1) {
                Log.d("Captcha", "Count greater than 1");
                return;
            }
            mThreadCount++;
            new AsyncTask<Integer, Void, Float>() {
                @Override
                protected Float doInBackground(Integer... params) {
                    try {
                        int left = params[0];
                        int top = params[1];
                        int width = params[2];
                        int height = params[3];
                        Bitmap croppedBitmap = Bitmap.createBitmap(mScratchBitmap, left, top, width, height);
                        return BitmapUtils.getTransparentPixelPercent(croppedBitmap);
                    } finally {
                        mThreadCount--;
                    }
                }

                @Override
                protected void onPostExecute(Float percentRevealed) {
                    if (!isRevealed()) {
                        float oldValue = mRevealPercent;
                        mRevealPercent = percentRevealed;
                        if (oldValue != percentRevealed) {
                            mRevealListener.onRevealPercentChangedListener(ScratchImageView.this, percentRevealed);
                        }
                        if (isRevealed()) {
                            mRevealListener.onRevealed(ScratchImageView.this);
                        }
                    }
                }
            }.execute(left, top, width, height);
        }
    }

    public int[] getImageBounds() {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();
        int vwidth = getWidth() - paddingLeft - paddingRight;
        int vheight = getHeight() - paddingBottom - paddingTop;
        int centerX = vwidth / 2;
        int centerY = vheight / 2;
        Drawable drawable = getDrawable();
        Rect bounds = drawable.getBounds();
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0) width = bounds.right - bounds.left;
        if (height <= 0) height = bounds.bottom - bounds.top;
        if (height > vheight) height = vheight;
        if (width > vwidth) width = vwidth;
        int left;
        int top;
        ScaleType scaleType = getScaleType();
        switch (scaleType) {
            case FIT_START:
                left = paddingLeft;
                top = centerY - height / 2;
                break;
            case FIT_END:
                left = vwidth - paddingRight - width;
                top = centerY - height / 2;
                break;
            case CENTER:
                left = centerX - width / 2;
                top = centerY - height / 2;
                break;
            default:
                left = paddingLeft;
                top = paddingTop;
                width = vwidth;
                height = vheight;
                break;
        }
        return new int[]{left, top, left + width, top + height};
    }
}
