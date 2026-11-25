package top.saymzx.easycontrol.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import java.util.Locale;

import top.saymzx.easycontrol.app.client.Client;
import top.saymzx.easycontrol.app.client.Controller;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.entity.Setting;

public class CommandReceiver extends BroadcastReceiver {
  private static final String TAG = "EasycontrolCommand";
  private static final String ACTION_OPEN = "io.github.nashthuo.easycontrol.action.OPEN";
  private static final String ACTION_CLOSE = "io.github.nashthuo.easycontrol.action.CLOSE";
  private static final String ACTION_KEY = "io.github.nashthuo.easycontrol.action.KEY";
  static final String EXTRA_OPEN_DEVICE_ID = "open_device_id";

  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (!ACTION_OPEN.equals(action) && !ACTION_CLOSE.equals(action) && !ACTION_KEY.equals(action)) return;
    Setting setting = AppData.setting != null ? AppData.setting : new Setting(context);
    if (!setting.getAllowCommands()) {
      Log.i(TAG, "Ignored an automation command because automation is off");
      return;
    }
    try {
      handleCommand(context, intent, action);
    } catch (RuntimeException error) {
      Log.w(TAG, "Could not handle the automation command", error);
    }
  }

  private static void handleCommand(Context context, Intent intent, String action) {
    boolean hasId = intent.hasExtra("device_id");
    boolean hasName = intent.hasExtra("device");
    if (ACTION_CLOSE.equals(action) && !hasId && !hasName) {
      Client.releaseAll();
      return;
    }

    AppData.init(context);
    int deviceId = resolveDeviceId(intent, hasId, hasName);
    if (deviceId <= 0) {
      Log.i(TAG, "Ignored an automation command without a valid device");
      return;
    }
    switch (action) {
      case ACTION_OPEN:
        open(context, deviceId);
        break;
      case ACTION_CLOSE:
        Client client = Client.find(deviceId);
        if (client != null) client.release();
        break;
      case ACTION_KEY:
        sendKey(intent, deviceId);
        break;
    }
  }

  private static int resolveDeviceId(Intent intent, boolean hasId, boolean hasName) {
    if (hasId) return intent.getIntExtra("device_id", -1);
    if (!hasName) return AppData.setting.getDefaultDevice();

    String name = intent.getStringExtra("device");
    if (name == null || name.isEmpty()) return -1;
    int deviceId = -1;
    for (Device device : AppData.dbHelper.getAll()) {
      if (!name.equalsIgnoreCase(device.name)) continue;
      if (deviceId != -1) {
        Log.i(TAG, "More than one device matches the name. Use device_id.");
        return -1;
      }
      deviceId = device.id;
    }
    return deviceId;
  }

  private static void open(Context context, int deviceId) {
    if (AppData.dbHelper.getById(deviceId) == null) {
      Log.i(TAG, "Ignored OPEN because the saved device does not exist");
      return;
    }
    Activity activity = AppData.main;
    if (activity instanceof MainActivity && !activity.isFinishing() && !activity.isDestroyed()) {
      // Manifest receivers run on the main thread.
      ((MainActivity) activity).openDevice(deviceId);
      return;
    }
    // Overlay permission lets Android start this activity from the background.
    Intent launch = new Intent(context, MainActivity.class)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .putExtra(EXTRA_OPEN_DEVICE_ID, deviceId);
    context.startActivity(launch);
  }

  private static void sendKey(Intent intent, int deviceId) {
    String name = intent.getStringExtra("key");
    int keyCode = intent.getIntExtra("keycode", KeyEvent.KEYCODE_UNKNOWN);
    if (keyCode == KeyEvent.KEYCODE_POWER || "power".equalsIgnoreCase(name)) {
      Log.w(TAG, "Ignored an automation request for the power key");
      return;
    }
    if (name != null) keyCode = getNamedKeyCode(name);
    if (keyCode <= KeyEvent.KEYCODE_UNKNOWN) {
      Log.i(TAG, "Ignored KEY because the key is missing or invalid");
      return;
    }

    Client client = Client.find(deviceId);
    Controller controller = client == null ? null : client.controller;
    if (controller == null) {
      Log.i(TAG, "Ignored KEY because the mirror is not connected");
      return;
    }
    controller.sendKeyEvent(keyCode);
  }

  private static int getNamedKeyCode(String name) {
    switch (name.toLowerCase(Locale.ROOT)) {
      case "home": return KeyEvent.KEYCODE_HOME;
      case "back": return KeyEvent.KEYCODE_BACK;
      case "recents": return KeyEvent.KEYCODE_APP_SWITCH;
      case "volume_up": return KeyEvent.KEYCODE_VOLUME_UP;
      case "volume_down": return KeyEvent.KEYCODE_VOLUME_DOWN;
      case "wake": return KeyEvent.KEYCODE_WAKEUP;
      default: return KeyEvent.KEYCODE_UNKNOWN;
    }
  }
}
