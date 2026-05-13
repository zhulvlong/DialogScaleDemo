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

/**
 * 可自由缩放、拖拽移动的 Dialog 实现
 * 核心机制：Window 尺寸缩放 + View 层级缩放（双管齐下）
 */
public class ScaleableDialog extends Dialog {

    // 默认缩放比例（1.0 = 原始大小）
    private static final float DEFAULT_SCALE_FACTOR = 1.0f;
    // 最小缩放比例（防止缩得过小导致内容无法辨识）
    private static final float MIN_SCALE = 0.6f;
    // 最大缩放比例（防止超出屏幕边界）
    private static final float MAX_SCALE = 1.4f;
    // Dialog 默认宽度（dp）
    private static final int DEFAULT_WIDTH_DP = 400;
    // Dialog 默认高度（dp）
    private static final int DEFAULT_HEIGHT_DP = 300;

    // 当前缩放比例因子
    private float mScaleFactor = DEFAULT_SCALE_FACTOR;
    // 默认宽度（px，根据屏幕密度转换后的像素值）
    private int mDefaultWidth;
    // 默认高度（px，根据屏幕密度转换后的像素值）
    private int mDefaultHeight;
    // Dialog 根布局，用于 View 层级缩放
    private RelativeLayout mLlRoot;
    // 双指缩放手势检测器
    private ScaleGestureDetector mScaleGestureDetector;
    // Dialog 窗口的布局参数，控制窗口位置、大小等
    private WindowManager.LayoutParams mWinParam;

    // 标题栏拖拽：手指按下时的窗口位置
    private int mMoveDownX, mMoveDownY, mMoveX, mMoveY;
    // 右下角拖拽：手指按下时的坐标和窗口尺寸
    private float mResizeDownX, mResizeDownY;
    private int mResizeStartWidth, mResizeStartHeight;

    /**
     * 构造函数
     * @param context 上下文
     */
    public ScaleableDialog(Context context) {
        // 使用透明无标题栏主题，让 Dialog 背景完全由自定义布局控制
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        // 将 dp 转换为 px，适配不同屏幕密度
        mDefaultWidth = dp2px(context, DEFAULT_WIDTH_DP);
        mDefaultHeight = dp2px(context, DEFAULT_HEIGHT_DP);
    }

    /**
     * Dialog 创建时的初始化逻辑
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 加载 Dialog 布局文件
        View root = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_scale_demo, null, false);
        setContentView(root);

        // 获取 Dialog 的 Window 对象，用于控制窗口属性
        Window window = getWindow();
        if (window != null) {
            // 设置窗口背景透明，让自定义布局的背景显示出来
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            // 获取当前窗口属性并复制一份，避免直接修改系统默认属性
            mWinParam = window.getAttributes();
            mWinParam.copyFrom(window.getAttributes());
            // 设置像素格式为 RGBA_8888，支持透明通道
            mWinParam.format = PixelFormat.RGBA_8888;
            // 设置窗口初始宽高
            mWinParam.width = mDefaultWidth;
            mWinParam.height = mDefaultHeight;
            // 设置窗口对齐方式为左上角对齐（配合 x/y 坐标定位）
            mWinParam.gravity = Gravity.START | Gravity.TOP;
            // 将窗口居中显示在屏幕上
            centerWindow();
            // 应用窗口属性
            window.setAttributes(mWinParam);
        }

        // 获取根布局，这是 View 层级缩放的核心对象
        mLlRoot = root.findViewById(R.id.ll_root);
        // 设置根布局的宽高与窗口一致
        ViewGroup.LayoutParams rootLayoutParams = mLlRoot.getLayoutParams();
        rootLayoutParams.width = mDefaultWidth;
        rootLayoutParams.height = mDefaultHeight;
        mLlRoot.setLayoutParams(rootLayoutParams);
        // 设置缩放锚点为左上角 (0, 0)，缩放时以左上角为基准向右下角扩展
        mLlRoot.setPivotX(0f);
        mLlRoot.setPivotY(0f);
        // 初始化缩放比例为 1.0（原始大小）
        mLlRoot.setScaleX(DEFAULT_SCALE_FACTOR);
        mLlRoot.setScaleY(DEFAULT_SCALE_FACTOR);

        // 获取布局中的控件引用
        FrameLayout flTitle = root.findViewById(R.id.fl_title);         // 标题栏
        FrameLayout flResizeHandle = root.findViewById(R.id.fl_touch_scale); // 缩放手柄
        ImageView ivClose = root.findViewById(R.id.iv_close);           // 关闭按钮

        // 关闭按钮点击事件
        ivClose.setOnClickListener(v -> dismiss());
        // 初始化标题栏拖拽功能
        initTitleDrag(flTitle);
        // 初始化右下角拖拽缩放功能
        initResizeHandle(flResizeHandle);

        // 初始化双指捏合缩放手势检测器
        mScaleGestureDetector = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    /**
                     * 双指缩放回调
                     * @param detector 手势检测器，包含缩放比例信息
                     */
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        // 计算新的缩放比例：当前比例 × 手势比例因子
                        float newScale = mScaleFactor * detector.getScaleFactor();
                        // 应用缩放
                        applyScale(newScale);
                        return true;
                    }
                });
    }

    /**
     * 初始化标题栏拖拽功能
     * 通过监听标题栏的触摸事件，实现窗口拖拽移动
     * @param flTitle 标题栏 FrameLayout
     */
    private void initTitleDrag(FrameLayout flTitle) {
        if (flTitle == null) return;
        flTitle.setOnTouchListener((v, event) -> {
            // 获取手指在屏幕上的绝对坐标（非相对坐标）
            int x = (int) event.getRawX();
            int y = (int) event.getRawY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 记录手指按下时的窗口位置
                    mMoveX = mWinParam.x;
                    mMoveY = mWinParam.y;
                    // 记录手指按下时的坐标
                    mMoveDownX = x;
                    mMoveDownY = y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    // 计算新的窗口位置：原位置 + 手指移动距离
                    mWinParam.x = mMoveX + (x - mMoveDownX);
                    mWinParam.y = mMoveY + (y - mMoveDownY);
                    // 确保窗口不超出屏幕边界
                    ensureWindowInScreen();
                    // 更新窗口布局
                    updateLayout();
                    break;
            }
            // 返回 true 表示消费触摸事件，不再向下传递
            return true;
        });
    }

    /**
     * 初始化右下角拖拽缩放功能
     * 通过监听右下角手柄的触摸事件，实现窗口缩放
     * @param flResizeHandle 缩放手柄 FrameLayout
     */
    private void initResizeHandle(FrameLayout flResizeHandle) {
        if (flResizeHandle == null) return;
        flResizeHandle.setOnTouchListener((v, event) -> {
            // 获取手指在屏幕上的绝对坐标
            int x = (int) event.getRawX();
            int y = (int) event.getRawY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 记录手指按下时的坐标
                    mResizeDownX = x;
                    mResizeDownY = y;
                    // 记录手指按下时的窗口宽高（作为缩放基准）
                    mResizeStartWidth = mWinParam.width;
                    mResizeStartHeight = mWinParam.height;
                    break;
                case MotionEvent.ACTION_MOVE:
                    // 计算手指在 X 和 Y 方向的移动距离
                    float deltaX = x - mResizeDownX;
                    float deltaY = y - mResizeDownY;
                    // 取较大值保持等比缩放，避免窗口变形
                    float delta = Math.max(deltaX, deltaY);
                    // 计算新的缩放比例：(基准宽度 + 位移) / 默认宽度
                    float newScale = (mResizeStartWidth + delta) / (float) mDefaultWidth;
                    // 应用缩放
                    applyScale(newScale);
                    break;
            }
            return true;
        });
    }

    /**
     * 统一缩放入口
     * 同时更新 Window 层级和 View 层级的缩放
     * @param scale 目标缩放比例
     */
    private void applyScale(float scale) {
        // 限制缩放范围在 [MIN_SCALE, MAX_SCALE] 之间
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        // 防抖动：如果缩放变化小于 0.001，跳过刷新避免频繁重绘
        if (Math.abs(scale - mScaleFactor) < 0.001f) {
            return;
        }
        // 更新当前缩放比例
        mScaleFactor = scale;
        // 同步 View 层级缩放（关键：让所有子控件按比例缩放）
        syncRootScale();
        // 同步 Window 层级尺寸缩放（关键：让触摸事件区域与视觉大小一致）
        mWinParam.width = (int) (mDefaultWidth * mScaleFactor);
        mWinParam.height = (int) (mDefaultHeight * mScaleFactor);
        // 确保缩放后窗口不超出屏幕
        ensureWindowInScreen();
        // 刷新布局使更改生效
        updateLayout();
    }

    /**
     * 同步 View 层级缩放
     * 对根布局应用 setScaleX/Y，让所有子 View 自动按比例缩放
     *  - 如果只改变 Window 尺寸，内部控件不会自动按比例缩放，会导致布局错乱、文字大小不变、图片变形等问题。
     *  - 通过对根布局设置 `setScaleX/ScaleY`，Android 的 View 系统会自动将所有子 View 按相同比例缩放，包括：
     *  - RecyclerView 及其列表项
     *  - TextView 的文字和图标
     *  - ImageView 的图片
     *  - WebView 的内容
     *  - 所有间距、边距、圆角等
     *  - 将 Pivot 设置为 `(0, 0)`（左上角），意味着缩放以左上角为锚点，向右下角扩展。这与拖拽调整大小的视觉体验一致。
     */
    private void syncRootScale() {
        if (mLlRoot == null) return;
        // 设置缩放锚点为左上角，缩放时以左上角为基准向右下角扩展
        mLlRoot.setPivotX(0f);
        mLlRoot.setPivotY(0f);
        // 应用当前缩放比例到 X 和 Y 方向
        mLlRoot.setScaleX(mScaleFactor);
        mLlRoot.setScaleY(mScaleFactor);
    }

    /**
     * 将窗口居中显示在屏幕上
     */
    private void centerWindow() {
        // 获取屏幕宽高
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        // 计算居中坐标：屏幕中心 - 窗口一半宽高
        mWinParam.x = Math.max(0, (screenWidth - mWinParam.width) / 2);
        mWinParam.y = Math.max(0, (screenHeight - mWinParam.height) / 2);
    }

    /**
     * 窗口边界限制
     * 确保窗口在缩放和移动后始终不超出屏幕边界
     */
    private void ensureWindowInScreen() {
        // 获取屏幕宽高
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        // 如果窗口右边缘超出屏幕，将其拉回
        if (mWinParam.x + mWinParam.width > screenWidth) {
            mWinParam.x = screenWidth - mWinParam.width;
        }
        // 如果窗口底边缘超出屏幕，将其拉回
        if (mWinParam.y + mWinParam.height > screenHeight) {
            mWinParam.y = screenHeight - mWinParam.height;
        }
        // 如果窗口左边缘超出屏幕（x < 0），将其设为 0
        if (mWinParam.x < 0) mWinParam.x = 0;
        // 如果窗口上边缘超出屏幕（y < 0），将其设为 0
        if (mWinParam.y < 0) mWinParam.y = 0;
    }

    /**
     * 更新窗口布局
     * 将最新的 WindowManager.LayoutParams 应用到窗口
     */
    private void updateLayout() {
        Window window = getWindow();
        if (window == null) return;
        // 应用最新的窗口属性
        window.setAttributes(mWinParam);
        // 获取 DecorView 并强制刷新布局
        View decorView = window.getDecorView();
        if (decorView != null) {
            decorView.requestLayout();  // 请求重新测量和布局
            decorView.invalidate();     // 请求重绘
        }
    }

    /**
     * 分发触摸事件
     * 将触摸事件同时分发给双指缩放手势检测器
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 将触摸事件传递给 ScaleGestureDetector 处理双指手势
        if (mScaleGestureDetector != null) {
            mScaleGestureDetector.onTouchEvent(ev);
        }
        // 继续传递给父类处理其他触摸事件
        return super.dispatchTouchEvent(ev);
    }

    /**
     * dp 转 px 工具方法
     * 将密度无关像素转换为实际像素，适配不同屏幕密度
     * @param context 上下文
     * @param dp dp 值
     * @return px 值
     */
    private int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
