package top.saymzx.easycontrol.server.helper;

import android.util.Log;

import top.saymzx.easycontrol.server.entity.Device;

// Turns the phone between portrait and landscape on request, and puts the
// rotation settings back as they were when the session ends.
public final class Rotation {
  private static final String TAG = "EasycontrolRotation";
  private static String savedAuto;
  private static String savedUser;

  private Rotation() {
  }

  public static synchronized void toggle() {
    try {
      if (savedAuto == null) {
        savedAuto = read("accelerometer_rotation");
        savedUser = read("user_rotation");
      }
      int target = Device.deviceRotation % 2 == 0 ? 1 : 0;
      Command.exec("settings", "put", "system", "accelerometer_rotation", "0");
      Command.exec("settings", "put", "system", "user_rotation", String.valueOf(target));
    } catch (Exception error) {
      Log.w(TAG, "Rotate failed", error);
    }
  }

  public static synchronized void restore() {
    if (savedAuto == null) return;
    try {
      if (isNumber(savedUser)) Command.exec("settings", "put", "system", "user_rotation", savedUser);
      Command.exec("settings", "put", "system", "accelerometer_rotation", isNumber(savedAuto) ? savedAuto : "1");
    } catch (Exception error) {
      Log.w(TAG, "Rotation restore failed", error);
    }
    savedAuto = null;
    savedUser = null;
  }

  private static String read(String key) throws Exception {
    String value = Command.execReadLine("settings", "get", "system", key);
    return value == null ? "" : value.trim();
  }

  private static boolean isNumber(String value) {
    return value != null && value.matches("[0-9]{1,2}");
  }
}
