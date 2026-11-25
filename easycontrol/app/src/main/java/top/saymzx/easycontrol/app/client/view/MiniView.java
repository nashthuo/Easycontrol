package top.saymzx.easycontrol.app.client.view;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;

import java.util.concurrent.atomic.AtomicInteger;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.databinding.ModuleMiniViewBinding;
import top.saymzx.easycontrol.app.entity.AppData;

public class MiniView {
  private static final int[] BAR_COLORS = {R.color.bar1, R.color.bar2, R.color.bar3, R.color.bar4};
  private static int nextColor;

  private final ModuleMiniViewBinding miniView = ModuleMiniViewBinding.inflate(AppData.main.getLayoutInflater());
  private final WindowManager.LayoutParams miniViewParams = new WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT
  );
  private boolean isShow;

  public MiniView(ClientView clientView) {
    miniViewParams.gravity = Gravity.START | Gravity.TOP;
    int colorIndex = nextColor++ % BAR_COLORS.length;
    miniView.bar.setBackgroundTintList(ColorStateList.valueOf(AppData.main.getColor(BAR_COLORS[colorIndex])));
    miniViewParams.x = 0;
    miniViewParams.y = 300 * (colorIndex + 1);
    miniView.getRoot().setOnClickListener(v -> clientView.changeToSmall());
    miniView.buttonClose.setOnClickListener(v -> clientView.hide(true));
    setBarListener();
  }

  public void show() {
    if (isShow) return;
    AppData.main.getWindowManager().addView(miniView.getRoot(), miniViewParams);
    isShow = true;
    miniView.getRoot().setTranslationX(AppData.publicTools.dp2px(-30f));
    miniView.getRoot().setAlpha(0);
    miniView.getRoot().animate()
      .translationX(0)
      .alpha(1)
      .setDuration(400)
      .setInterpolator(new LinearInterpolator())
      .start();
  }

  public void hide() {
    if (!isShow) return;
    isShow = false;
    miniView.getRoot().animate().cancel();
    AppData.main.getWindowManager().removeViewImmediate(miniView.getRoot());
  }

  @SuppressLint("ClickableViewAccessibility")
  private void setBarListener() {
    AtomicInteger downY = new AtomicInteger();
    AtomicInteger windowY = new AtomicInteger();
    miniView.getRoot().setOnTouchListener((v, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downY.set((int) event.getRawY());
          windowY.set(miniViewParams.y);
          break;
        case MotionEvent.ACTION_MOVE:
          int maxY = AppData.publicTools.getScreenSize().second - miniView.getRoot().getHeight();
          miniViewParams.y = Math.max(0, Math.min(maxY, windowY.get() + (int) event.getRawY() - downY.get()));
          AppData.main.getWindowManager().updateViewLayout(miniView.getRoot(), miniViewParams);
          break;
        case MotionEvent.ACTION_UP:
          if (Math.abs(downY.get() - event.getRawY()) < 4) v.performClick();
          break;
      }
      return true;
    });
  }
}
