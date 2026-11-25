/*
 * This project draws on the open source scrcpy project.
 */
package top.saymzx.easycontrol.server.entity;

import top.saymzx.easycontrol.server.ServerText;

public final class Options {
  public static boolean isAudio;
  public static int maxSize;
  public static int videoBitRate;
  public static int maxFps;
  public static boolean turnOffScreen;
  public static boolean autoControlScreen;
  public static int setWidth = -1;
  public static String socketName = "easycontrol";
  public static int setHeight = -1;
  public static String appPackage = "";

  public static void parse(String... args) {
    for (String arg : args) {
      int equalIndex = arg.indexOf('=');
      if (equalIndex == -1) {
        throw new IllegalArgumentException(ServerText.get("invalid_arg"));
      }
      String key = arg.substring(0, equalIndex);
      String value = arg.substring(equalIndex + 1);
      switch (key) {
        case "socket_name":
          if (!value.matches("[a-zA-Z0-9_-]{1,80}")) {
            throw new IllegalArgumentException(ServerText.get("invalid_arg"));
          }
          socketName = value;
          break;
        case "is_audio":
          isAudio = Integer.parseInt(value) == 1;
          break;
        case "max_size":
          maxSize = Integer.parseInt(value) & ~7;
          break;
        case "max_fps":
          maxFps = Integer.parseInt(value);
          break;
        case "video_bit_rate":
          videoBitRate = Integer.parseInt(value) * 1000000;
          break;
        case "turn_off_screen":
          turnOffScreen = Integer.parseInt(value) == 1;
          break;
        case "auto_control_screen":
          autoControlScreen = Integer.parseInt(value) == 1;
          break;
        case "set_width":
          setWidth = Integer.parseInt(value);
          break;
        case "set_height":
          setHeight = Integer.parseInt(value);
          break;
        case "app_package":
          if (!value.matches("[A-Za-z0-9._]{1,200}"))
            throw new IllegalArgumentException(ServerText.get("invalid_arg"));
          appPackage = value;
          break;
      }
    }
  }
}

