package top.saymzx.easycontrol.app.client.view;

import android.annotation.SuppressLint;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import top.saymzx.easycontrol.app.client.Controller;
import top.saymzx.easycontrol.app.databinding.ModuleSmallViewBinding;
import top.saymzx.easycontrol.app.entity.AppData;

// Floating mirror window. The control bar is docked on the side with free space
// (right in landscape, bottom in portrait) and never hides.
public class SmallView {
  private final ClientView clientView;

  private boolean isShow = false;
  private boolean reconnecting;
  private boolean landscape;

  private final ModuleSmallViewBinding smallView = ModuleSmallViewBinding.inflate(AppData.main.getLayoutInflater());
  private final int videoMarginX = AppData.publicTools.dp2px(10f);
  private final int videoMarginY = AppData.publicTools.dp2px(20f);
  private final int minButton = AppData.publicTools.dp2px(24f);
  private final int maxButton = AppData.publicTools.dp2px(40f);
  private final int dockPadding = AppData.publicTools.dp2px(4f);
  private final int screenGap = AppData.publicTools.dp2px(8f);
  private int buttonSize = AppData.publicTools.dp2px(36f);

  private final WindowManager.LayoutParams smallViewParams =
    new WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      200,
      200,
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
      LayoutParamsFlagFocus,
      PixelFormat.TRANSLUCENT
    );

  private static final int baseLayoutParamsFlag = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
  private static final int LayoutParamsFlagFocus = baseLayoutParamsFlag | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
  private static final int LayoutParamsFlagNoFocus = baseLayoutParamsFlag | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

  // Follows the tablet rotation: move the dock and restore the window box for the new orientation.
  private final ComponentCallbacks configCallbacks = new ComponentCallbacks() {
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
      smallView.getRoot().post(() -> {
        if (!isShow || reconnecting) return;
        if (isLandscape() == landscape) return;
        placeWindow();
        AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
      });
    }

    @Override
    public void onLowMemory() {
    }
  };

  public SmallView(ClientView clientView) {
    this.clientView = clientView;
    smallViewParams.gravity = Gravity.START | Gravity.TOP;
    setFloatVideoListener();
    setReSizeListener();
    setBarListener();
    smallView.buttonMini.setOnClickListener(v -> clientView.changeToMini());
    smallView.buttonFull.setOnClickListener(v -> clientView.changeToFull());
    smallView.buttonFit.setOnClickListener(v -> fitToScreen());
  }

  public void show(Controller controller) {
    if (isShow) return;
    smallView.textureViewLayout.addView(clientView.textureView, 0);
    resumeMirroring(controller);
    placeWindow();
    AppData.main.getWindowManager().addView(smallView.getRoot(), smallViewParams);
    AppData.main.getApplicationContext().registerComponentCallbacks(configCallbacks);
    isShow = true;
    showSmallViewAnim();
  }

  public void showReconnecting(Runnable cancel) {
    reconnecting = true;
    setDockKeysVisible(false);
    smallView.bar.setVisibility(View.INVISIBLE);
    smallView.reSize.setVisibility(View.INVISIBLE);
    smallView.textureViewLayout.setVisibility(View.INVISIBLE);
    smallView.reconnectStatus.setVisibility(View.VISIBLE);
    smallView.reconnectStatus.setOnClickListener(v -> cancel.run());
    smallView.buttonClose.setOnClickListener(v -> cancel.run());
    if (!isShow) {
      int width = AppData.publicTools.dp2px(232f);
      int height = AppData.publicTools.dp2px(104f);
      smallView.videoColumn.setMinimumWidth(width);
      smallView.videoColumn.setMinimumHeight(height);
      applyOrientation();
      sizeDock(landscape ? height : width);
      Pair<Integer, Integer> screen = AppData.publicTools.getScreenSize();
      smallViewParams.x = (screen.first - width) / 2;
      smallViewParams.y = (screen.second - height) / 2;
      AppData.main.getWindowManager().addView(smallView.getRoot(), smallViewParams);
      AppData.main.getApplicationContext().registerComponentCallbacks(configCallbacks);
      isShow = true;
    }
  }

  public void resumeMirroring(Controller controller) {
    boolean wasReconnecting = reconnecting;
    reconnecting = false;
    smallView.reconnectStatus.setVisibility(View.GONE);
    smallView.reconnectStatus.setOnClickListener(null);
    smallView.buttonClose.setOnClickListener(v -> clientView.hide(true));
    smallView.videoColumn.setMinimumWidth(0);
    smallView.videoColumn.setMinimumHeight(0);
    smallView.textureViewLayout.setVisibility(View.VISIBLE);
    smallView.bar.setVisibility(View.VISIBLE);
    smallView.reSize.setVisibility(View.VISIBLE);
    setDockKeysVisible(true);
    setButtonListeners(controller);
    if (wasReconnecting && isShow) {
      placeWindow();
      AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
    }
  }

  public void hide() {
    smallView.getRoot().animate().cancel();
    smallView.getRoot().setTranslationY(0);
    smallView.getRoot().setAlpha(1);
    smallView.textureViewLayout.removeView(clientView.textureView);
    if (!isShow) return;
    isShow = false;
    reconnecting = false;
    smallView.videoColumn.setMinimumWidth(0);
    smallView.videoColumn.setMinimumHeight(0);
    smallView.reconnectStatus.setOnClickListener(null);
    try {
      AppData.main.getApplicationContext().unregisterComponentCallbacks(configCallbacks);
    } catch (RuntimeException ignored) {
    }
    AppData.main.getWindowManager().removeViewImmediate(smallView.getRoot());
  }

  // The phone turned: keep the window where it is and swap the box sides.
  public void changeRotation() {
    if (!isShow || reconnecting) return;
    applyOrientation();
    ViewGroup.LayoutParams lp = clientView.textureView.getLayoutParams();
    Pair<Integer, Integer> limit = availableBox();
    clientView.updateTextureViewSize(new Pair<>(Math.min(lp.height, limit.first), Math.min(lp.width, limit.second)));
    afterVideoResize();
    clampToScreen();
    AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
  }

  // Restore the saved box for this tablet orientation, or use the default (3/4 of the screen, centred).
  private void placeWindow() {
    applyOrientation();
    int[] box = AppData.setting.getWindowBox(landscape);
    Pair<Integer, Integer> limit = availableBox();
    if (box != null) {
      clientView.updateTextureViewSize(new Pair<>(Math.min(box[2], limit.first), Math.min(box[3], limit.second)));
      afterVideoResize();
      smallViewParams.x = box[0];
      smallViewParams.y = box[1];
      clampToScreen();
    } else {
      Pair<Integer, Integer> screen = AppData.publicTools.getScreenSize();
      int width = screen.first * 3 / 4 - (landscape ? dockThickness() : 0);
      int height = screen.second * 3 / 4 - (landscape ? 0 : dockThickness());
      clientView.updateTextureViewSize(new Pair<>(Math.min(width, limit.first), Math.min(height, limit.second)));
      afterVideoResize();
      centerWindow();
    }
  }

  // Largest video that fits the screen next to the dock, centred.
  private void fitToScreen() {
    if (!isShow || reconnecting) return;
    applyOrientation();
    clientView.updateTextureViewSize(availableBox());
    afterVideoResize();
    centerWindow();
    AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
    saveBox();
  }

  // The screen area free of the status bar and the navigation bar.
  private Rect usableArea() {
    Rect area = new Rect();
    AppData.main.getWindow().getDecorView().getWindowVisibleDisplayFrame(area);
    Pair<Integer, Integer> screen = AppData.publicTools.getScreenSize();
    if (area.width() <= 0 || area.height() <= 0) area.set(0, 0, screen.first, screen.second);
    return area;
  }

  private Pair<Integer, Integer> availableBox() {
    Rect area = usableArea();
    int dock = maxButton + dockPadding;
    int width = area.width() - videoMarginX - screenGap - (landscape ? dock : 0);
    int height = area.height() - videoMarginY - screenGap - (landscape ? 0 : dock);
    int min = AppData.publicTools.dp2px(150f);
    return new Pair<>(Math.max(min, width), Math.max(min, height));
  }

  private boolean isLandscape() {
    Pair<Integer, Integer> screen = AppData.publicTools.getScreenSize();
    return screen.first > screen.second;
  }

  private void applyOrientation() {
    landscape = isLandscape();
    LinearLayout root = smallView.getRoot();
    root.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    smallView.dock.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) smallView.dock.getLayoutParams();
    lp.gravity = Gravity.CENTER;
    smallView.dock.setLayoutParams(lp);
  }

  private void afterVideoResize() {
    ViewGroup.LayoutParams lp = clientView.textureView.getLayoutParams();
    sizeDock(landscape ? lp.height + videoMarginY : lp.width + videoMarginX);
  }

  // Fit the dock buttons along the video side, between 24 dp and 40 dp each.
  private void sizeDock(int length) {
    int count = 0;
    for (int i = 0; i < smallView.dock.getChildCount(); i++)
      if (smallView.dock.getChildAt(i).getVisibility() != View.GONE) count++;
    if (count == 0) count = 1;
    buttonSize = Math.max(minButton, Math.min(maxButton, (length - dockPadding) / count));
    int padding = buttonSize * 2 / 9;
    for (int i = 0; i < smallView.dock.getChildCount(); i++) {
      View child = smallView.dock.getChildAt(i);
      ViewGroup.LayoutParams lp = child.getLayoutParams();
      lp.width = buttonSize;
      lp.height = buttonSize;
      child.setLayoutParams(lp);
      child.setPadding(padding, padding, padding, padding);
    }
  }

  private int dockThickness() {
    return buttonSize + dockPadding;
  }

  private Pair<Integer, Integer> windowSize() {
    ViewGroup.LayoutParams lp = clientView.textureView.getLayoutParams();
    int count = 0;
    for (int i = 0; i < smallView.dock.getChildCount(); i++)
      if (smallView.dock.getChildAt(i).getVisibility() != View.GONE) count++;
    int dockLength = count * buttonSize + dockPadding;
    int videoWidth = lp.width + videoMarginX;
    int videoHeight = lp.height + videoMarginY;
    if (landscape) return new Pair<>(videoWidth + dockThickness(), Math.max(videoHeight, dockLength));
    return new Pair<>(Math.max(videoWidth, dockLength), videoHeight + dockThickness());
  }

  private void centerWindow() {
    Rect area = usableArea();
    Pair<Integer, Integer> size = windowSize();
    smallViewParams.x = area.left + Math.max(0, (area.width() - size.first) / 2);
    smallViewParams.y = area.top + Math.max(0, (area.height() - size.second) / 2);
  }

  private void clampToScreen() {
    Rect area = usableArea();
    Pair<Integer, Integer> size = windowSize();
    smallViewParams.x = Math.max(area.left, Math.min(smallViewParams.x, area.right - size.first));
    smallViewParams.y = Math.max(area.top, Math.min(smallViewParams.y, area.bottom - size.second));
  }

  private void saveBox() {
    ViewGroup.LayoutParams lp = clientView.textureView.getLayoutParams();
    AppData.setting.setWindowBox(landscape, smallViewParams.x, smallViewParams.y, lp.width, lp.height);
  }

  private void setDockKeysVisible(boolean visible) {
    for (int i = 0; i < smallView.dock.getChildCount(); i++) {
      View child = smallView.dock.getChildAt(i);
      if (child != smallView.buttonClose) child.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  private void showSmallViewAnim() {
    smallView.getRoot().setTranslationY(400);
    smallView.getRoot().setAlpha(0f);
    ViewPropertyAnimator animator = smallView.getRoot().animate()
      .translationY(0)
      .alpha(1)
      .setDuration(400)
      .setInterpolator(new OvershootInterpolator());

    animator.start();
  }

  private boolean viewFocus = true;

  @SuppressLint("ClickableViewAccessibility")
  private void setFloatVideoListener() {
    smallView.getRoot().setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_OUTSIDE) setViewFocus(false);
      return false;
    });
  }

  public void setViewFocus(boolean toFocus) {
    if (!isShow || reconnecting) return;
    if (!toFocus && viewFocus) {
      smallViewParams.flags = LayoutParamsFlagNoFocus;
      AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
      viewFocus = false;
    } else if (toFocus && !viewFocus) {
      smallViewParams.flags = LayoutParamsFlagFocus;
      AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
      viewFocus = true;
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private void setBarListener() {
    AtomicBoolean dragged = new AtomicBoolean(false);
    AtomicInteger xx = new AtomicInteger();
    AtomicInteger yy = new AtomicInteger();
    AtomicInteger paramsX = new AtomicInteger();
    AtomicInteger paramsY = new AtomicInteger();
    AtomicReference<Pair<Integer, Integer>> screenSize = new AtomicReference<>(new Pair<>(0, 0));
    smallView.bar.setOnTouchListener((v, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          xx.set((int) event.getRawX());
          yy.set((int) event.getRawY());
          paramsX.set(smallViewParams.x);
          paramsY.set(smallViewParams.y);
          dragged.set(false);
          screenSize.set(AppData.publicTools.getScreenSize());
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          int x = (int) event.getRawX();
          int y = (int) event.getRawY();
          int flipX = x - xx.get();
          int flipY = y - yy.get();
          // Ignore small finger movements during a tap.
          if (!dragged.get()) {
            if (flipX * flipX + flipY * flipY < 16) return true;
            dragged.set(true);
          }
          if (x < 100 || x > screenSize.get().first - 100 || y < 50 || y > screenSize.get().second - 100)
            return true;
          smallViewParams.x = paramsX.get() + flipX;
          smallViewParams.y = paramsY.get() + flipY;
          AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
          break;
        }
        case MotionEvent.ACTION_UP:
          if (dragged.get()) saveBox();
          else v.performClick();
          break;
      }
      return true;
    });
  }

  private void setButtonListeners(Controller controller) {
    smallView.buttonBack.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_BACK));
    smallView.buttonHome.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_HOME));
    smallView.buttonSwitch.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_APP_SWITCH));
    smallView.buttonVolumeUp.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_VOLUME_UP));
    smallView.buttonVolumeDown.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN));
    smallView.buttonPower.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_POWER));
    smallView.buttonWake.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_WAKEUP));
    smallView.buttonRotate.setOnClickListener(v -> controller.sendRotate());
  }

  @SuppressLint("ClickableViewAccessibility")
  private void setReSizeListener() {
    int minSize = AppData.publicTools.dp2px(150f);
    smallView.reSize.setOnTouchListener((v, event) -> {
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_MOVE) {
        int sizeX = (int) (event.getRawX() - smallViewParams.x) - videoMarginX / 2;
        int sizeY = (int) (event.getRawY() - smallViewParams.y) - videoMarginY / 2;
        if (sizeX < minSize || sizeY < minSize) return true;
        clientView.updateTextureViewSize(new Pair<>(sizeX, sizeY));
        afterVideoResize();
      } else if (action == MotionEvent.ACTION_UP) {
        saveBox();
      }
      return true;
    });
  }

}
