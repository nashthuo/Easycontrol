package top.saymzx.easycontrol.server.helper;

import android.content.ComponentName;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

import top.saymzx.easycontrol.server.ServerText;
import top.saymzx.easycontrol.server.entity.Device;
import top.saymzx.easycontrol.server.entity.Options;
import top.saymzx.easycontrol.server.wrappers.DisplayManager;
import top.saymzx.easycontrol.server.wrappers.InputManager;
import top.saymzx.easycontrol.server.wrappers.WindowManager;

final class AppDisplay {
  private static final String TAG = "EasycontrolServer";

  static VirtualDisplay start(Surface surface) throws InterruptedException {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      Log.w(TAG, ServerText.get("app_display_unsupported"));
      return null;
    }

    VirtualDisplay display = null;
    boolean launched = false;
    try {
      InputManager.requireDisplayIdSupport();
      ComponentName activity = resolveLauncherActivity();
      int videoLongSide = Math.max(Device.videoSize.first, Device.videoSize.second);
      int phoneLongSide = Math.max(Device.deviceSize.first, Device.deviceSize.second);
      int density = Math.max(1, Math.round((float) DisplayManager.getMainDensity() * videoLongSide / phoneLongSide));
      display = DisplayManager.createAppDisplay(Device.videoSize.first, Device.videoSize.second, density, surface);
      if (display == null) throw new IOException(ServerText.get("app_display_create_failed"));
      int id = display.getDisplay().getDisplayId();
      if (id <= 0) throw new IOException(ServerText.get("app_display_create_failed"));
      String displayId = Integer.toString(id);
      // Keep the virtual display's logical size equal to the encoder size for touch mapping.
      String rotationCommand = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
        ? "set-fix-to-user-rotation" : "fixed-to-user-rotation";
      Command.exec("wm", rotationCommand, "-d", displayId, "enabled");
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) WindowManager.destroyContentOnRemoval(id);
      String result = Command.execReadOutput(true, "am", "start", "--display", displayId,
        "--activity-multiple-task", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-n", activity.flattenToString());
      for (String line : result.split("\n")) {
        if (line.startsWith("Error") || line.startsWith("Warning") || line.startsWith("Exception")
            || line.startsWith("Security exception")) {
          throw new IOException(line);
        }
      }
      launched = true;
      Log.i(TAG, "App display " + id + " for " + Options.appPackage);
      return display;
    } catch (IOException | ReflectiveOperationException | RuntimeException error) {
      Log.w(TAG, ServerText.get("app_display_fallback"), error);
      return null;
    } finally {
      if (!launched && display != null) release(display);
    }
  }

  private static ComponentName resolveLauncherActivity() throws IOException, InterruptedException {
    String output = Command.execReadOutput("cmd", "package", "resolve-activity", "--brief",
      "--user", "current", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
      Options.appPackage).trim();
    ComponentName activity = ComponentName.unflattenFromString(output.substring(output.lastIndexOf('\n') + 1).trim());
    if (activity == null || !Options.appPackage.equals(activity.getPackageName())) {
      throw new IOException(ServerText.get("app_display_no_launcher"));
    }
    return activity;
  }

  static void release(VirtualDisplay display) {
    try {
      display.release();
    } catch (RuntimeException error) {
      Log.w(TAG, ServerText.get("app_display_release_failed"), error);
    }
  }
}
