package com.demo.dialogscale;

import android.app.Dialog;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

public class ScaleableDialog extends Dialog {

    private static final float DEFAULT_SCALE_FACTOR = 1.0f;
    private static final float MIN_SCALE = 0.6f;
    private static final float MAX_SCALE = 1.4f;
    private static final int DEFAULT_WIDTH_DP = 400;
    private static final int DEFAULT_HEIGHT_DP = 300;

    private float mScaleFactor = DEFAULT_SCALE_FACTOR;
    private int mDefaultWidth;
    private int mDefaultHeight;
    private RelativeLayout mLlRoot;
    private ScaleGestureDetector mScaleGestureDetector;
    private WindowManager.LayoutParams mWinParam;

    private int mMoveDownX, mMoveDownY, mMoveX, mMoveY;
    private float mResizeDownX, mResizeDownY;
    private int mResizeStartWidth, mResizeStartHeight;

    public ScaleableDialog(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        mDefaultWidth = dp2px(context, DEFAULT_WIDTH_DP);
        mDefaultHeight = dp2px(context, DEFAULT_HEIGHT_DP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View root = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_scale_demo, null, false);
        setContentView(root);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            mWinParam = window.getAttributes();
            mWinParam.copyFrom(window.getAttributes());
            mWinParam.format = PixelFormat.RGBA_8888;
            mWinParam.width = mDefaultWidth;
            mWinParam.height = mDefaultHeight;
            mWinParam.gravity = Gravity.START | Gravity.TOP;
            centerWindow();
            window.setAttributes(mWinParam);
        }

        mLlRoot = root.findViewById(R.id.ll_root);
        ViewGroup.LayoutParams rootLayoutParams = mLlRoot.getLayoutParams();
        rootLayoutParams.width = mDefaultWidth;
        rootLayoutParams.height = mDefaultHeight;
        mLlRoot.setLayoutParams(rootLayoutParams);
        mLlRoot.setPivotX(0f);
        mLlRoot.setPivotY(0f);
        mLlRoot.setScaleX(DEFAULT_SCALE_FACTOR);
        mLlRoot.setScaleY(DEFAULT_SCALE_FACTOR);

        FrameLayout flTitle = root.findViewById(R.id.fl_title);
        FrameLayout flResizeHandle = root.findViewById(R.id.fl_touch_scale);
        ImageView ivClose = root.findViewById(R.id.iv_close);

        ivClose.setOnClickListener(v -> dismiss());
        initTitleDrag(flTitle);
        initResizeHandle(flResizeHandle);

        mScaleGestureDetector = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float newScale = mScaleFactor * detector.getScaleFactor();
                        applyScale(newScale);
                        return true;
                    }
                });
    }

    private void initTitleDrag(FrameLayout flTitle) {
        if (flTitle == null) return;
        flTitle.setOnTouchListener((v, event) -> {
            int x = (int) event.getRawX();
            int y = (int) event.getRawY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mMoveX = mWinParam.x;
                    mMoveY = mWinParam.y;
                    mMoveDownX = x;
                    mMoveDownY = y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    mWinParam.x = mMoveX + (x - mMoveDownX);
                    mWinParam.y = mMoveY + (y - mMoveDownY);
                    ensureWindowInScreen();
                    updateLayout();
                    break;
            }
            return true;
        });
    }

    private void initResizeHandle(FrameLayout flResizeHandle) {
        if (flResizeHandle == null) return;
        flResizeHandle.setOnTouchListener((v, event) -> {
            int x = (int) event.getRawX();
            int y = (int) event.getRawY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mResizeDownX = x;
                    mResizeDownY = y;
                    mResizeStartWidth = mWinParam.width;
                    mResizeStartHeight = mWinParam.height;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = x - mResizeDownX;
                    float deltaY = y - mResizeDownY;
                    float delta = Math.max(deltaX, deltaY);
                    float newScale = (mResizeStartWidth + delta) / (float) mDefaultWidth;
                    applyScale(newScale);
                    break;
            }
            return true;
        });
    }

    private void applyScale(float scale) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (Math.abs(scale - mScaleFactor) < 0.001f) {
            return;
        }
        mScaleFactor = scale;
        syncRootScale();
        mWinParam.width = (int) (mDefaultWidth * mScaleFactor);
        mWinParam.height = (int) (mDefaultHeight * mScaleFactor);
        ensureWindowInScreen();
        updateLayout();
    }

    private void syncRootScale() {
        if (mLlRoot == null) return;
        mLlRoot.setPivotX(0f);
        mLlRoot.setPivotY(0f);
        mLlRoot.setScaleX(mScaleFactor);
        mLlRoot.setScaleY(mScaleFactor);
    }

    private void centerWindow() {
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        mWinParam.x = Math.max(0, (screenWidth - mWinParam.width) / 2);
        mWinParam.y = Math.max(0, (screenHeight - mWinParam.height) / 2);
    }

    private void ensureWindowInScreen() {
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        if (mWinParam.x + mWinParam.width > screenWidth) {
            mWinParam.x = screenWidth - mWinParam.width;
        }
        if (mWinParam.y + mWinParam.height > screenHeight) {
            mWinParam.y = screenHeight - mWinParam.height;
        }
        if (mWinParam.x < 0) mWinParam.x = 0;
        if (mWinParam.y < 0) mWinParam.y = 0;
    }

    private void updateLayout() {
        Window window = getWindow();
        if (window == null) return;
        window.setAttributes(mWinParam);
        View decorView = window.getDecorView();
        if (decorView != null) {
            decorView.requestLayout();
            decorView.invalidate();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mScaleGestureDetector != null) {
            mScaleGestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
