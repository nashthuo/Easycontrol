/*
 * This project draws on the open source scrcpy project.
 */
package top.saymzx.easycontrol.server.wrappers;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;

import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.Display;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.saymzx.easycontrol.server.entity.DisplayInfo;
import top.saymzx.easycontrol.server.helper.Command;
import top.saymzx.easycontrol.server.helper.FakeContext;

public final class DisplayManager {
  // Hidden display flags available to the shell on Android 10 and 11.
  private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
  private static final int VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
  private static Object manager;

  public static void init(Object m) {
    manager = m;
  }

  public static VirtualDisplay createAppDisplay(int width, int height, int density, Surface surface)
      throws ReflectiveOperationException {
    Constructor<android.hardware.display.DisplayManager> constructor =
      android.hardware.display.DisplayManager.class.getDeclaredConstructor(Context.class);
    constructor.setAccessible(true);
    android.hardware.display.DisplayManager shellManager = constructor.newInstance(FakeContext.get());
    int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_PRESENTATION | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
      | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH | VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;
    return shellManager.createVirtualDisplay("Easycontrol App", width, height, density, surface, flags);
  }

  public static int getMainDensity() {
    try {
      Object displayInfo = manager.getClass().getMethod("getDisplayInfo", int.class).invoke(manager, Display.DEFAULT_DISPLAY);
      return displayInfo.getClass().getDeclaredField("logicalDensityDpi").getInt(displayInfo);
    } catch (ReflectiveOperationException | RuntimeException error) {
      return DisplayMetrics.DENSITY_DEFAULT;
    }
  }

  public static DisplayInfo parseDisplayInfo(String dumpsysDisplayOutput, int displayId) {
    Pattern regex = Pattern.compile(
        "^    mOverrideDisplayInfo=DisplayInfo\\{\".*?, displayId " + displayId + ".*?(, FLAG_.*)?, real ([0-9]+) x ([0-9]+).*?, "
            + "rotation ([0-9]+).*?, layerStack ([0-9]+)",
        Pattern.MULTILINE);
    Matcher m = regex.matcher(dumpsysDisplayOutput);
    if (!m.find()) {
      return null;
    }
    int flags = parseDisplayFlags(m.group(1));
    int width = Integer.parseInt(Objects.requireNonNull(m.group(2)));
    int height = Integer.parseInt(Objects.requireNonNull(m.group(3)));
    int rotation = Integer.parseInt(Objects.requireNonNull(m.group(4)));
    int layerStack = Integer.parseInt(Objects.requireNonNull(m.group(5)));

    return new DisplayInfo(displayId, new Pair<>(width, height), rotation, layerStack, flags);
  }

  private static DisplayInfo getDisplayInfoFromDumpsysDisplay(int displayId) {
    try {
      String dumpsysDisplayOutput = Command.execReadOutput("dumpsys", "display");
      return parseDisplayInfo(dumpsysDisplayOutput, displayId);
    } catch (Exception e) {
      return null;
    }
  }

  private static int parseDisplayFlags(String text) {
    Pattern regex = Pattern.compile("FLAG_[A-Z_]+");
    if (text == null) {
      return 0;
    }

    int flags = 0;
    Matcher m = regex.matcher(text);
    while (m.find()) {
      String flagString = m.group();
      try {
        Field filed = Display.class.getDeclaredField(flagString);
        flags |= filed.getInt(null);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        // Silently ignore, some flags reported by "dumpsys display" are @TestApi
      }
    }
    return flags;
  }

  public static DisplayInfo getDisplayInfo(int displayId) {
    try {
      Object displayInfo = manager.getClass().getMethod("getDisplayInfo", int.class).invoke(manager, displayId);
      if (displayInfo == null) {
        // fallback when displayInfo is null
        return getDisplayInfoFromDumpsysDisplay(displayId);
      }
      Class<?> cls = displayInfo.getClass();
      // width and height already take the rotation into account
      int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
      int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
      int rotation = cls.getDeclaredField("rotation").getInt(displayInfo);
      int layerStack = cls.getDeclaredField("layerStack").getInt(displayInfo);
      int flags = cls.getDeclaredField("flags").getInt(displayInfo);
      return new DisplayInfo(displayId, new Pair<>(width, height), rotation, layerStack, flags);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
