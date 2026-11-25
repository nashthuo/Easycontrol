/*
 * This project draws on the open source scrcpy project.
 */
package top.saymzx.easycontrol.server.wrappers;

import android.os.IInterface;
import android.view.IRotationWatcher;

public final class WindowManager {
  private static final int REMOVE_CONTENT_MODE_DESTROY = 2;
  private static IInterface manager;

  public static void init(IInterface m) throws NoSuchMethodException {
    manager = m;
  }

  public static void destroyContentOnRemoval(int displayId) throws ReflectiveOperationException {
    // Android 10's window manager requires this in addition to the virtual display flag.
    Class.forName("android.view.IWindowManager")
      .getMethod("setRemoveContentMode", int.class, int.class)
      .invoke(manager, displayId, REMOVE_CONTENT_MODE_DESTROY);
  }

  public static void registerRotationWatcher(IRotationWatcher rotationWatcher, int displayId) {
    try {
      Class<?> cls = manager.getClass();
      try {
        cls.getMethod("watchRotation", IRotationWatcher.class, int.class).invoke(manager, rotationWatcher, displayId);
      } catch (NoSuchMethodException e) {
        cls.getMethod("watchRotation", IRotationWatcher.class).invoke(manager, rotationWatcher);
      }
    } catch (Exception ignored) {
    }
  }

}
