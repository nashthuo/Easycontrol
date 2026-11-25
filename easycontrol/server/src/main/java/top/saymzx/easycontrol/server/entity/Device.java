/*
 * This project draws on the open source scrcpy project.
 */
package top.saymzx.easycontrol.server.entity;

import android.content.IOnPrimaryClipChangedListener;
import android.os.SystemClock;
import android.util.Pair;
import android.view.IRotationWatcher;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import top.saymzx.easycontrol.server.Server;
import top.saymzx.easycontrol.server.helper.VideoEncode;
import top.saymzx.easycontrol.server.wrappers.ClipboardManager;
import top.saymzx.easycontrol.server.wrappers.DisplayManager;
import top.saymzx.easycontrol.server.wrappers.InputManager;
import top.saymzx.easycontrol.server.wrappers.WindowManager;

public final class Device {

  public static Pair<Integer, Integer> deviceSize;
  public static int deviceRotation;
  public static Pair<Integer, Integer> videoSize;

  private static String nowClipboardText = "";

  public static int displayId = 0;
  public static int layerStack;

  public static void init() {
    DisplayInfo displayInfo = DisplayManager.getDisplayInfo(displayId);
    deviceSize = displayInfo.size;
    deviceRotation = displayInfo.rotation;
    // Compute the video size.
    computeVideoSize();
    layerStack = displayInfo.layerStack;
  }

  public static void startListeners() {
    if (displayId == 0) setRotationListener();
    // Watch for clipboard changes.
    setClipBoardListener();
  }

  public static void useAppDisplay(int id) {
    displayId = id;
    deviceSize = videoSize;
  }

  private static void computeVideoSize() {
    boolean isPortrait = deviceSize.first < deviceSize.second;
    int major = isPortrait ? deviceSize.second : deviceSize.first;
    int minor = isPortrait ? deviceSize.first : deviceSize.second;
    if (major > Options.maxSize) {
      minor = minor * Options.maxSize / major;
      major = Options.maxSize;
    }
    // Round the H.264 dimensions to multiples of eight.
    minor = minor + 4 & ~7;
    major = major + 4 & ~7;
    videoSize = isPortrait ? new Pair<>(minor, major) : new Pair<>(major, minor);
  }

  private static void setClipBoardListener() {
    ClipboardManager.addPrimaryClipChangedListener(new IOnPrimaryClipChangedListener.Stub() {
      public void dispatchPrimaryClipChanged() {
        String newClipboardText = ClipboardManager.getText();
        if (newClipboardText == null) return;
        if (!newClipboardText.equals(nowClipboardText)) {
          // Send the update.
          byte[] tmpTextByte = newClipboardText.getBytes(StandardCharsets.UTF_8);
          if (tmpTextByte.length > 5000) return;
          nowClipboardText = newClipboardText;
          ByteBuffer byteBuffer = ByteBuffer.allocate(5 + tmpTextByte.length);
          byteBuffer.put((byte) 3);
          byteBuffer.putInt(tmpTextByte.length);
          byteBuffer.put(tmpTextByte);
          byteBuffer.flip();
          try {
            Server.write(byteBuffer);
          } catch (Exception error) {
            Server.requestStop(error);
          }
        }
      }
    });
  }

  private static void setRotationListener() {
    WindowManager.registerRotationWatcher(new IRotationWatcher.Stub() {
      public void onRotationChanged(int rotation) {
        if ((deviceRotation + rotation) % 2 != 0) {
          deviceSize = new Pair<>(deviceSize.second, deviceSize.first);
          videoSize = new Pair<>(videoSize.second, videoSize.first);
          byte[] bytes = new byte[]{4};
          try {
            Server.write(ByteBuffer.wrap(bytes));
          } catch (Exception error) {
            Server.requestStop(error);
          }
        }
        deviceRotation = rotation;
        VideoEncode.isHasChangeRotation = true;
      }
    }, displayId);
  }

  public static void setClipboardText(String text) {
    nowClipboardText = text;
    ClipboardManager.setText(nowClipboardText);
  }

  private static final PointersState pointersState = new PointersState();

  public static void touchEvent(int action, Float x, Float y, int pointerId) {
    Pointer pointer;
    long now = SystemClock.uptimeMillis();
    if (!pointersState.hasPointer(pointerId)) {
      if (action != MotionEvent.ACTION_DOWN) return;
      pointer = pointersState.newPointer(pointerId, now);
    } else pointer = pointersState.get(pointerId);

    pointer.x = x * deviceSize.first;
    pointer.y = y * deviceSize.second;

    int pointerCount = pointersState.update();

    if (action == MotionEvent.ACTION_UP) pointersState.remove(pointerId);

    if (pointerCount > 1) {
      if (action == MotionEvent.ACTION_UP) {
        action = MotionEvent.ACTION_POINTER_UP | (pointer.id << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
      } else if (action == MotionEvent.ACTION_DOWN) {
        action = MotionEvent.ACTION_POINTER_DOWN | (pointer.id << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
      }
    }

    MotionEvent event = MotionEvent.obtain(pointer.downTime, now, action, pointerCount, pointersState.pointerProperties, pointersState.pointerCoords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    injectEvent(event);

  }

  public static void keyEvent(int keyCode) {
    // Android handles these keys globally even when the event names a display.
    if (displayId != 0 && (keyCode == KeyEvent.KEYCODE_POWER || keyCode == KeyEvent.KEYCODE_WAKEUP)) return;
    long now = SystemClock.uptimeMillis();
    KeyEvent event1 = new KeyEvent(now, now, MotionEvent.ACTION_DOWN, keyCode, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    KeyEvent event2 = new KeyEvent(now, now, MotionEvent.ACTION_UP, keyCode, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    injectEvent(event1);
    injectEvent(event2);
  }

  private static void injectEvent(InputEvent inputEvent) {
    try {
      if (displayId != 0) InputManager.setDisplayId(inputEvent, displayId);
      InputManager.injectInputEvent(inputEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    } catch (Exception ignored) {
    }
  }

}
