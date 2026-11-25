/*
 * This project draws on the open source scrcpy project.
 */
package top.saymzx.easycontrol.server.helper;

import android.os.SystemClock;
import android.view.KeyEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import top.saymzx.easycontrol.server.Server;
import top.saymzx.easycontrol.server.entity.Device;
import top.saymzx.easycontrol.server.entity.Options;

public final class Controller {

  public static void handleIn() throws IOException {
    boolean hasData = true;
    while (hasData) {
      switch (Server.streamIn.readByte()) {
        case 1:
          handleTouchEvent();
          break;
        case 2:
          handleKeyEvent();
          break;
        case 3:
          handleClipboardEvent();
          break;
        case 4:
          handleKeepAlive();
          break;
        case 5:
          handlePower();
          break;
      }
      hasData = Server.streamIn.available() > 0;
    }
  }

  private static void handleTouchEvent() throws IOException {
    int action = Server.streamIn.readByte();
    int pointerId = Server.streamIn.readByte();
    float x = Server.streamIn.readFloat();
    float y = Server.streamIn.readFloat();
    Device.touchEvent(action, x, y, pointerId);
  }

  private static void handleKeyEvent() throws IOException {
    int keyCode = Server.streamIn.readInt();
    sendKey(keyCode);
  }

  private static void handleClipboardEvent() throws IOException {
    int size = Server.streamIn.readInt();
    byte[] textBytes = new byte[size];
    Server.streamIn.readFully(textBytes);
    String text = new String(textBytes, StandardCharsets.UTF_8);
    Device.setClipboardText(text);
  }

  public static volatile long lastKeepAliveTime = SystemClock.elapsedRealtime();

  private static void handleKeepAlive() {
    lastKeepAliveTime = SystemClock.elapsedRealtime();
  }

  private static void handlePower() {
    sendKey(KeyEvent.KEYCODE_POWER);
  }

  private static boolean manualPowerControl;
  private static boolean blankingPaused;

  private static synchronized void sendKey(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_POWER || keyCode == KeyEvent.KEYCODE_WAKEUP) {
      manualPowerControl = keyCode == KeyEvent.KEYCODE_POWER;
      blankingPaused = true;
      Server.setDisplayBlanked(false);
    }
    Device.keyEvent(keyCode);
  }

  public static synchronized void maintainScreen() {
    wakeForCapture();
    if (Options.turnOffScreen && !blankingPaused) Server.setDisplayBlanked(true);
  }

  public static void wakeForCapture() {
    if (Options.autoControlScreen && !manualPowerControl) Device.keyEvent(KeyEvent.KEYCODE_WAKEUP);
  }
}
