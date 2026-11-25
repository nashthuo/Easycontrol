package top.saymzx.easycontrol.app.client.view;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.Client;
import top.saymzx.easycontrol.app.client.Controller;
import top.saymzx.easycontrol.app.entity.AppData;

public class ClientView implements TextureView.SurfaceTextureListener {
  static final long INITIAL_CONTROLS_DURATION_MS = 3000;

  private enum Mode { HIDDEN, FULL, SMALL, MINI, RECONNECTING }

  private final Client client;
  final TextureView textureView = new TextureView(AppData.main);
  private SurfaceTexture surfaceTexture;
  private boolean dismissed;
  private Mode mode = Mode.HIDDEN;
  private Mode previousMode = Mode.HIDDEN;
  private final SmallView smallView = new SmallView(this);
  private final MiniView miniView = new MiniView(this);

  public Pair<Integer, Integer> videoSize;
  private Pair<Integer, Integer> surfaceSize;

  public ClientView(Client client) {
    this.client = client;
    setTouchListener();
    textureView.setSurfaceTextureListener(this);
  }

  public void changeToFull() {
    if (dismissed || mode == Mode.FULL) return;
    if (FullActivity.isShow) {
      Toast.makeText(AppData.main, R.string.toast_fullscreen_busy, Toast.LENGTH_SHORT).show();
      if (mode == Mode.HIDDEN || mode == Mode.RECONNECTING) changeToSmall();
      return;
    }
    hideWindow();
    client.isNormalPlay = true;
    mode = Mode.FULL;
    FullActivity.show(this, client.controller, videoSize.second > videoSize.first);
  }

  public void changeToSmall() {
    if (dismissed || mode == Mode.SMALL) return;
    hideWindow();
    client.isNormalPlay = true;
    mode = Mode.SMALL;
    smallView.show(client.controller);
  }

  public void changeToMini() {
    if (dismissed || mode == Mode.MINI) return;
    hideWindow();
    client.isNormalPlay = false;
    mode = Mode.MINI;
    miniView.show();
  }

  public void showReconnecting(Runnable cancel) {
    if (dismissed || mode == Mode.RECONNECTING) return;
    previousMode = mode;
    if (mode != Mode.SMALL) hideWindow();
    mode = Mode.RECONNECTING;
    smallView.showReconnecting(cancel);
  }

  public void restoreAfterReconnect() {
    if (dismissed || mode != Mode.RECONNECTING) return;
    switch (previousMode) {
      case MINI:
        changeToMini();
        break;
      case FULL:
        changeToFull();
        break;
      case SMALL:
        client.isNormalPlay = true;
        mode = Mode.SMALL;
        smallView.resumeMirroring(client.controller);
        break;
      default:
        if (AppData.setting.getDefaultFull()) changeToFull();
        else changeToSmall();
    }
    if (surfaceTexture != null) client.onSurfaceReady(surfaceTexture);
  }

  public void hide(boolean isRelease) {
    if (isRelease) client.release();
    else hideWindow();
  }

  public void dismiss() {
    if (dismissed) return;
    dismissed = true;
    hideWindow();
    if (surfaceTexture != null) {
      surfaceTexture.release();
      surfaceTexture = null;
    }
  }

  private void hideWindow() {
    Mode oldMode = mode;
    mode = Mode.HIDDEN;
    if (oldMode == Mode.FULL) FullActivity.hide(this);
    else if (oldMode == Mode.SMALL || oldMode == Mode.RECONNECTING) smallView.hide();
    else if (oldMode == Mode.MINI) miniView.hide();
  }

  public void changeRotation() {
    if (dismissed || videoSize == null || mode == Mode.RECONNECTING) return;
    videoSize = new Pair<>(videoSize.second, videoSize.first);
    if (mode == Mode.FULL) FullActivity.changeRotation(this);
    else if (mode == Mode.SMALL) smallView.changeRotation();
  }

  void updateTextureViewSize(Pair<Integer, Integer> maxSize) {
    double scale = Math.min((double) maxSize.first / videoSize.first,
      (double) maxSize.second / videoSize.second);
    surfaceSize = new Pair<>(Math.max(1, (int) (videoSize.first * scale)),
      Math.max(1, (int) (videoSize.second * scale)));
    ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
    layoutParams.width = surfaceSize.first;
    layoutParams.height = surfaceSize.second;
    textureView.setLayoutParams(layoutParams);
  }

  @SuppressLint("ClickableViewAccessibility")
  private void setTouchListener() {
    // Android identifies pointers with IDs 0 through 31.
    int[] startX = new int[32];
    int[] startY = new int[32];
    boolean[] moved = new boolean[32];
    textureView.setOnTouchListener((view, event) -> {
      Controller controller = client.controller;
      Pair<Integer, Integer> size = surfaceSize;
      if (controller == null || size == null || mode == Mode.RECONNECTING) return true;

      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_CANCEL) {
        for (int i = 0; i < event.getPointerCount(); i++) {
          int id = event.getPointerId(i);
          float x = event.getX(i);
          float y = event.getY(i);
          if (action == MotionEvent.ACTION_MOVE && !moved[id]) {
            float dx = startX[id] - x;
            float dy = startY[id] - y;
            if (dx * dx + dy * dy < 9) continue;
            moved[id] = true;
          }
          controller.sendTouchEvent(action == MotionEvent.ACTION_CANCEL ? MotionEvent.ACTION_UP
            : MotionEvent.ACTION_MOVE, id, x / size.first, y / size.second);
        }
      } else {
        int i = event.getActionIndex();
        int id = event.getPointerId(i);
        float x = event.getX(i);
        float y = event.getY(i);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
          startX[id] = (int) x;
          startY[id] = (int) y;
          moved[id] = false;
          controller.sendTouchEvent(MotionEvent.ACTION_DOWN, id, x / size.first, y / size.second);
          if (mode == Mode.SMALL) smallView.setViewFocus(true);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
          controller.sendTouchEvent(MotionEvent.ACTION_UP, id, x / size.first, y / size.second);
        }
      }
      return true;
    });
  }

  static void resetBar(View view, boolean visible) {
    view.animate().cancel();
    view.setTranslationY(0);
    view.setScaleY(1);
    view.setAlpha(1);
    view.setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  static void changeBarViewAnim(View view, int translationY) {
    boolean show = view.getVisibility() != View.VISIBLE;
    resetBar(view, true);
    if (show) {
      view.setTranslationY(translationY);
      view.setScaleY(0);
      view.setAlpha(0);
    }
    view.animate()
      .translationY(show ? 0 : translationY)
      .scaleY(show ? 1 : 0)
      .alpha(show ? 1 : 0)
      .setDuration(300)
      .setInterpolator(show ? new OvershootInterpolator() : new DecelerateInterpolator())
      .withEndAction(() -> view.setVisibility(show ? View.VISIBLE : View.GONE))
      .start();
  }

  @Override
  public void onSurfaceTextureAvailable(@NonNull SurfaceTexture available, int width, int height) {
    if (dismissed) return;
    if (surfaceTexture == null) surfaceTexture = available;
    else if (surfaceTexture != available) textureView.setSurfaceTexture(surfaceTexture);
    if (mode != Mode.RECONNECTING) client.onSurfaceReady(surfaceTexture);
  }

  @Override
  public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
  }

  @Override
  public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture destroyed) {
    return destroyed != surfaceTexture;
  }

  @Override
  public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
  }
}
