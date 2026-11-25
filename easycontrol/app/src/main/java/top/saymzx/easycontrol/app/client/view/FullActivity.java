package top.saymzx.easycontrol.app.client.view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import top.saymzx.easycontrol.app.EnglishActivity;
import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.Controller;
import top.saymzx.easycontrol.app.databinding.ActivityFullBinding;
import top.saymzx.easycontrol.app.entity.AppData;

public class FullActivity extends EnglishActivity {
  @SuppressLint("StaticFieldLeak")
  private static FullActivity currentActivity;
  @SuppressLint("StaticFieldLeak")
  private static ClientView requestedView;
  private static Controller requestedController;
  private static boolean isPortrait;

  private ActivityFullBinding fullActivity;
  private ClientView clientView;
  private Controller controller;
  private final Runnable hideInitialControls = () -> ClientView.resetBar(fullActivity.barView, false);

  public static boolean isShow = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!isShow || requestedView == null) {
      finish();
      return;
    }
    currentActivity = this;
    clientView = requestedView;
    controller = requestedController;
    fullActivity = ActivityFullBinding.inflate(getLayoutInflater());
    setContentView(fullActivity.getRoot());
    AppData.publicTools.setFullScreen(this);
    fullActivity.buttonClose.setOnClickListener(v -> clientView.hide(true));
    int rotation = getOrientation();
    if (rotation != getRequestedOrientation()) {
      setRequestedOrientation(rotation);
      return;
    }
    fullActivity.textureViewLayout.addView(clientView.textureView, 0);
    Pair<Integer, Integer> maxSize = getScreenSizeWithoutDock();
    clientView.updateTextureViewSize(isPortrait ? maxSize : new Pair<>(maxSize.second, maxSize.first));
    setButtonListeners();
    ClientView.resetBar(fullActivity.barView, true);
    fullActivity.barView.postDelayed(hideInitialControls, ClientView.INITIAL_CONTROLS_DURATION_MS);
  }

  @Override
  protected void onPause() {
    if (!isChangingConfigurations() && isShow && currentActivity == this && requestedView == clientView)
      clientView.changeToSmall();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    detachView();
    if (currentActivity == this) currentActivity = null;
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      Toast.makeText(this, R.string.toast_fullscreen_back, Toast.LENGTH_SHORT).show();
      return true;
    }
    // Forward common typing keys and keep the tablet's system keys local.
    if (controller != null && ((keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT) ||
      (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) ||
      (keyCode >= KeyEvent.KEYCODE_COMMA && keyCode <= KeyEvent.KEYCODE_PLUS))) {
      controller.sendKeyEvent(keyCode);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  public static void show(ClientView view, Controller controller, boolean portrait) {
    if (isShow) return;
    isShow = true;
    requestedView = view;
    requestedController = controller;
    isPortrait = portrait;
    AppData.main.startActivity(new Intent(AppData.main, FullActivity.class));
  }

  public static void hide(ClientView view) {
    if (requestedView != view) return;
    isShow = false;
    requestedView = null;
    requestedController = null;
    if (currentActivity != null) {
      FullActivity activity = currentActivity;
      currentActivity = null;
      activity.detachView();
      activity.finish();
    }
  }

  public static void changeRotation(ClientView view) {
    if (requestedView != view) return;
    isPortrait = !isPortrait;
    if (currentActivity != null) {
      currentActivity.detachView();
      currentActivity.setRequestedOrientation(getOrientation());
    }
  }

  private static int getOrientation() {
    return isPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
  }

  private void detachView() {
    if (fullActivity != null) {
      fullActivity.barView.removeCallbacks(hideInitialControls);
      ClientView.resetBar(fullActivity.barView, false);
      fullActivity.textureViewLayout.removeView(clientView.textureView);
    }
  }

  public static Pair<Integer, Integer> getScreenSizeWithoutDock() {
    Pair<Integer, Integer> screenSize = AppData.publicTools.getScreenSize();
    if (screenSize.first > screenSize.second)
      screenSize = new Pair<>(screenSize.second, screenSize.first);
    return new Pair<>(screenSize.first, screenSize.second - AppData.publicTools.dp2px(35f));
  }

  private void setButtonListeners() {
    fullActivity.buttonBack.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_BACK));
    fullActivity.buttonHome.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_HOME));
    fullActivity.buttonSwitch.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_APP_SWITCH));
    fullActivity.buttonMini.setOnClickListener(v -> clientView.changeToMini());
    fullActivity.buttonFullExit.setOnClickListener(v -> clientView.changeToSmall());
    fullActivity.buttonDisplay.setOnClickListener(v -> controller.sendPowerEvent());
    fullActivity.buttonToLine.setOnClickListener(v -> setNavBarHide());
    fullActivity.buttonMore.setOnClickListener(v -> toggleBar());
    fullActivity.line.setOnClickListener(v -> setNavBarHide());
    MirrorKeys.bind(fullActivity.keyControls, controller);
  }

  private void toggleBar() {
    fullActivity.barView.removeCallbacks(hideInitialControls);
    ClientView.changeBarViewAnim(fullActivity.barView, AppData.publicTools.dp2px(40f));
  }

  private void setNavBarHide() {
    toggleBar();
    boolean showNav = fullActivity.navBar.getVisibility() == View.GONE;
    fullActivity.line.setVisibility(showNav ? View.GONE : View.VISIBLE);
    fullActivity.navBar.setVisibility(showNav ? View.VISIBLE : View.GONE);
  }
}
