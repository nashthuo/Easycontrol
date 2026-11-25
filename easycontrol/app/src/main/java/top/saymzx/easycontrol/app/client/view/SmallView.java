package top.saymzx.easycontrol.app.client.view;

import android.annotation.SuppressLint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import top.saymzx.easycontrol.app.client.Controller;
import top.saymzx.easycontrol.app.databinding.ModuleSmallViewBinding;
import top.saymzx.easycontrol.app.entity.AppData;

public class SmallView {
  private final ClientView clientView;

  private boolean isShow = false;
  private boolean reconnecting;

  private final ModuleSmallViewBinding smallView = ModuleSmallViewBinding.inflate(AppData.main.getLayoutInflater());
  private final int normalMinimumWidth = smallView.getRoot().getMinimumWidth();
  private final Runnable hideInitialControls = () -> ClientView.resetBar(smallView.barView, false);
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

  public SmallView(ClientView clientView) {
    this.clientView = clientView;
    smallViewParams.gravity = Gravity.START | Gravity.TOP;
    setFloatVideoListener();
    setReSizeListener();
    setBarListener();
    smallView.bar.setOnClickListener(v -> {
      smallView.barView.removeCallbacks(hideInitialControls);
      ClientView.changeBarViewAnim(smallView.barView, AppData.publicTools.dp2px(-40f));
    });
    smallView.buttonMini.setOnClickListener(v -> clientView.changeToMini());
    smallView.buttonFull.setOnClickListener(v -> clientView.changeToFull());
  }

  public void show(Controller controller) {
    if (isShow) return;
    smallView.textureViewLayout.addView(clientView.textureView, 0);
    resumeMirroring(controller);
    resizeForScreen();
    centerWindow(clientView.textureView.getLayoutParams().width, clientView.textureView.getLayoutParams().height);
    AppData.main.getWindowManager().addView(smallView.getRoot(), smallViewParams);
    isShow = true;
    showSmallViewAnim();
  }

  public void showReconnecting(Runnable cancel) {
    reconnecting = true;
    smallView.barView.removeCallbacks(hideInitialControls);
    ClientView.resetBar(smallView.barView, false);
    smallView.bar.setVisibility(View.INVISIBLE);
    smallView.reSize.setVisibility(View.INVISIBLE);
    smallView.textureViewLayout.setVisibility(View.INVISIBLE);
    smallView.reconnectStatus.setVisibility(View.VISIBLE);
    smallView.reconnectStatus.setOnClickListener(v -> cancel.run());
    smallView.buttonClose.setOnClickListener(v -> cancel.run());
    if (!isShow) {
      int width = AppData.publicTools.dp2px(232f);
      int height = AppData.publicTools.dp2px(104f);
      smallView.getRoot().setMinimumWidth(width);
      smallView.getRoot().setMinimumHeight(height);
      centerWindow(width, height);
      AppData.main.getWindowManager().addView(smallView.getRoot(), smallViewParams);
      isShow = true;
    }
  }

  public void resumeMirroring(Controller controller) {
    boolean wasReconnecting = reconnecting;
    reconnecting = false;
    smallView.reconnectStatus.setVisibility(View.GONE);
    smallView.reconnectStatus.setOnClickListener(null);
    smallView.buttonClose.setOnClickListener(v -> clientView.hide(true));
    smallView.textureViewLayout.setVisibility(View.VISIBLE);
    smallView.bar.setVisibility(View.VISIBLE);
    smallView.reSize.setVisibility(View.VISIBLE);
    setButtonListeners(controller);
    if (wasReconnecting) {
      clientView.updateTextureViewSize(new Pair<>(clientView.textureView.getLayoutParams().width,
        clientView.textureView.getLayoutParams().height));
    }
    ClientView.resetBar(smallView.barView, true);
    smallView.barView.removeCallbacks(hideInitialControls);
    smallView.barView.postDelayed(hideInitialControls, ClientView.INITIAL_CONTROLS_DURATION_MS);
  }

  public void hide() {
    smallView.barView.removeCallbacks(hideInitialControls);
    ClientView.resetBar(smallView.barView, false);
    smallView.getRoot().animate().cancel();
    smallView.getRoot().setTranslationY(0);
    smallView.getRoot().setAlpha(1);
    smallView.textureViewLayout.removeView(clientView.textureView);
    if (!isShow) return;
    isShow = false;
    reconnecting = false;
    smallView.getRoot().setMinimumWidth(normalMinimumWidth);
    smallView.getRoot().setMinimumHeight(0);
    smallView.reconnectStatus.setOnClickListener(null);
    AppData.main.getWindowManager().removeViewImmediate(smallView.getRoot());
  }

  public void changeRotation() {
    resizeForScreen();
    centerWindow(clientView.textureView.getLayoutParams().width, clientView.textureView.getLayoutParams().height);
    AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
  }

  private void resizeForScreen() {
    Pair<Integer, Integer> screenSize = AppData.publicTools.getScreenSize();
    clientView.updateTextureViewSize(new Pair<>(screenSize.first * 3 / 4, screenSize.second * 3 / 4));
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
          if (x < 200 || x > screenSize.get().first - 200 || y < 200 || y > screenSize.get().second - 200)
            return true;
          smallViewParams.x = paramsX.get() + flipX;
          smallViewParams.y = paramsY.get() + flipY;
          AppData.main.getWindowManager().updateViewLayout(smallView.getRoot(), smallViewParams);
          break;
        }
        case MotionEvent.ACTION_UP:
          if (!dragged.get()) v.performClick();
          break;
      }
      return true;
    });
  }

  private void centerWindow(int width, int height) {
    Pair<Integer, Integer> screenSize = AppData.publicTools.getScreenSize();
    smallViewParams.x = (screenSize.first - Math.max(width, normalMinimumWidth)) / 2;
    smallViewParams.y = (screenSize.second - height) / 2;
  }

  private void setButtonListeners(Controller controller) {
    smallView.buttonBack.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_BACK));
    smallView.buttonHome.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_HOME));
    smallView.buttonSwitch.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_APP_SWITCH));
    smallView.buttonDisplay.setOnClickListener(v -> controller.sendPowerEvent());
    MirrorKeys.bind(smallView.keyControls, controller);
  }

  @SuppressLint("ClickableViewAccessibility")
  private void setReSizeListener() {
    int minSize = AppData.publicTools.dp2px(150f);
    smallView.reSize.setOnTouchListener((v, event) -> {
      if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
        int sizeX = (int) (event.getRawX() - smallViewParams.x);
        int sizeY = (int) (event.getRawY() - smallViewParams.y);
        if (sizeX < minSize || sizeY < minSize) return true;
        clientView.updateTextureViewSize(new Pair<>(sizeX, sizeY));
      }
      return true;
    });
  }

}

