# DialogScaleDemo - Android Dialog 自由缩放演示工程

## 项目简介

本项目是一个独立的 Android Demo 工程，演示如何实现一个 **可自由缩放、拖拽移动的 Dialog 窗口**。该 Demo专注于展示 Android 中 Dialog 窗口缩放的技术实现。

## 功能特性

| 功能      | 说明                                 |
| ------- | ---------------------------------- |
| 自由放大缩小  | 支持将 Dialog 缩放至 0.6 \~ 1.4 倍        |
| 内容同步缩放  | Dialog 内部所有控件随窗口等比例缩放              |
| 双指捏合缩放  | 使用 `ScaleGestureDetector` 实现双指手势缩放 |
| 右下角拖拽缩放 | 通过拖拽右下角手柄调整窗口大小                    |
| 标题栏拖拽移动 | 拖拽标题栏可移动窗口位置                       |
| 窗口边界限制  | 确保窗口始终不超出屏幕边界                      |

## 项目结构

```
DialogScaleDemo/
├── build.gradle                          # 根项目构建配置
├── settings.gradle                       # 项目设置
├── gradle.properties                     # Gradle 属性配置
└── app/
    ├── build.gradle                      # App 模块构建配置
    ├── proguard-rules.pro                # ProGuard 混淆规则
    └── src/main/
        ├── AndroidManifest.xml           # 应用清单文件
        ├── java/com/demo/dialogscale/
        │   ├── DialogScaleDemoActivity.java   # 入口 Activity
        │   └── ScaleableDialog.java           # 核心可缩放 Dialog 实现
        └── res/
            ├── layout/
            │   └── dialog_scale_demo.xml      # Dialog 布局文件
            ├── drawable/
            │   ├── shape_dialog_bg.xml        # Dialog 圆角背景
            │   ├── ic_close.xml               # 关闭按钮图标
            │   └── ic_resize_handle.xml       # 缩放手柄图标
            ├── mipmap-hdpi/
            │   └── ic_launcher.xml            # 应用启动图标
            └── values/
                ├── colors.xml                 # 颜色定义
                ├── strings.xml                # 字符串资源
                └── styles.xml                 # 主题样式
```

## 核心实现详解

### 1. 双管齐下缩放策略

Dialog 的缩放采用 **Window 层级缩放 + View 层级缩放** 的双重机制，确保窗口和内容完美同步。

#### 1.1 Window 层级缩放

通过修改 `WindowManager.LayoutParams` 的宽高，改变 Dialog 窗口的实际尺寸：

```java
mWinParam.width = (int) (mDefaultWidth * mScaleFactor);
mWinParam.height = (int) (mDefaultHeight * mScaleFactor);
window.setAttributes(mWinParam);
```

**作用**：确保触摸事件区域与窗口视觉大小一致。

#### 1.2 View 层级缩放

对 Dialog 的根布局 `mLlRoot` 应用 `setScaleX()` 和 `setScaleY()`：

```java
mLlRoot.setPivotX(0f);  // 缩放锚点设为左上角
mLlRoot.setPivotY(0f);
mLlRoot.setScaleX(mScaleFactor);
mLlRoot.setScaleY(mScaleFactor);
```

**作用**：让根布局下的所有子 View（TextView、Button、ImageView 等）按相同比例缩放，包括文字、间距、圆角等。

#### 1.3 为什么需要双重缩放？

如果只改变 Window 尺寸，内部控件不会自动按比例缩放，会导致：

- 文字大小不变，显得过大或过小
- 图片变形或保持原大小
- 布局错乱、间距不协调

通过 View 层级缩放，Android 的 View 系统会自动处理所有子元素的缩放，保证视觉效果的一致性。

**1.4 Pivot 点设置保证对齐**

```Java
mLlRoot.setPivotX(0f);  // 左上角为缩放原点
mLlRoot.setPivotY(0f);
```

将缩放锚点设为左上角 (0, 0) ，意味着：

- View 层级的缩放是从左上角向右下角扩展
- 而Window 层级的 x/y 位置不变，宽高从左上角开始增长
- View 层级和Window 层级两者的扩展方向完全一致， 左上角始终对齐 。

### 2. 缩放范围限制

```java
private static final float MIN_SCALE = 0.6f;   // 最小缩放至 60%
private static final float MAX_SCALE = 1.4f;   // 最大缩放至 140%

private void applyScale(float scale) {
    scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    // ...
}
```

- **最小 0.6 倍**：防止缩得过小导致内容无法辨识
- **最大 1.4 倍**：防止超出屏幕边界

### 3. 交互方式

#### 3.1 双指捏合缩放

使用 Android 标准 `ScaleGestureDetector` 检测双指手势：

```java
mScaleGestureDetector = new ScaleGestureDetector(getContext(),
    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float newScale = mScaleFactor * detector.getScaleFactor();
            applyScale(newScale);
            return true;
        }
    });
```

在 `dispatchTouchEvent()` 中分发触摸事件给手势检测器。

#### 3.2 右下角拖拽缩放

在布局右下角放置一个透明的大点击区域（44dp x 44dp），内部显示一个小手柄（16dp x 16dp）：

```xml
<FrameLayout
    android:id="@+id/fl_touch_scale"
    android:layout_width="44dp"
    android:layout_height="44dp">

    <View
        android:layout_width="16dp"
        android:layout_height="16dp"
        android:background="@drawable/ic_resize_handle" />
</FrameLayout>
```

拖拽逻辑：

- 记录按下时的窗口宽高作为基准
- 移动时计算位移，取 X/Y 方向的较大值保持等比缩放
- 新缩放比例 = `(基准宽度 + 位移) / 默认宽度`

#### 3.3 标题栏拖拽移动

通过监听标题栏的 `OnTouchListener`，计算手指移动距离并更新窗口位置：

```java
mWinParam.x = mMoveX + (x - mMoveDownX);
mWinParam.y = mMoveY + (y - mMoveDownY);
```

### 4. 窗口边界限制

```java
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
```

在缩放和移动时都会调用此方法，确保窗口始终可见。

### 5. 防抖动优化

```java
if (Math.abs(scale - mScaleFactor) < 0.001f) {
    return;  // 缩放变化小于 0.001 时跳过刷新
}
```

避免微小的缩放变化触发频繁的重绘，提升性能。

### 6. 防裁剪处理

布局文件中设置 `clipChildren="false"` 和 `clipToPadding="false"`：

```xml
<FrameLayout
    android:clipChildren="false"
    android:clipToPadding="false">
```

确保缩放后的内容不会被父布局裁剪。

## 技术点总结

| 技术点    | 实现方式                                      | 作用         |
| ------ | ----------------------------------------- | ---------- |
| 缩放范围限制 | `Math.max/min` 限制在 `[0.6, 1.4]`           | 防止缩放过小或过大  |
| 内容同步缩放 | `View.setScaleX/Y()` + Pivot 设置           | 所有子控件等比例缩放 |
| 双指缩放   | `ScaleGestureDetector`                    | 标准手势识别     |
| 拖拽缩放   | `OnTouchListener` + 位移计算                  | 单指拖拽调整大小   |
| 等比缩放   | `Math.max(deltaX, deltaY)`                | 保持宽高比不变    |
| 边界限制   | `ensureWindowInScreen()`                  | 窗口不超出屏幕    |
| 防裁剪    | `clipChildren="false"`                    | 缩放内容不被裁剪   |
| 防抖动    | `Math.abs(scale - mScaleFactor) < 0.001f` | 避免频繁无效刷新   |

## 运行环境

- **minSdk**: 24
- **targetSdk**: 34
- **compileSdk**: 34
- **Java 版本**: 1.8

## 如何运行

1. 用 Android Studio 打开 `DialogScaleDemo` 目录
2. 等待 Gradle 同步完成
3. 点击运行按钮，编译并安装到设备
4. 点击"打开可缩放 Dialog"按钮
5. 体验以下交互：
   - **双指捏合**：放大/缩小 Dialog
   - **拖拽右下角蓝色手柄**：调整 Dialog 大小
   - **拖拽标题栏**：移动 Dialog 位置

## 核心代码文件

- [ScaleableDialog.java](app/src/main/java/com/demo/dialogscale/ScaleableDialog.java) — 可缩放 Dialog 核心实现
- [DialogScaleDemoActivity.java](app/src/main/java/com/demo/dialogscale/DialogScaleDemoActivity.java) — Demo 入口
- [dialog\_scale\_demo.xml](app/src/main/res/layout/dialog_scale_demo.xml) — Dialog 布局

## 来源

本 Demo 的技术用于学习和演示 Android Dialog 窗口缩放技术。
