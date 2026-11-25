package top.saymzx.easycontrol.app.entity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.hardware.usb.UsbManager;

import java.io.File;

import top.saymzx.easycontrol.adb.AdbKeyPair;
import top.saymzx.easycontrol.app.BuildConfig;
import top.saymzx.easycontrol.app.helper.DbHelper;
import top.saymzx.easycontrol.app.helper.PublicTools;

public class AppData {
  @SuppressLint("StaticFieldLeak")
  public static Activity main;

  public static PublicTools publicTools = new PublicTools();

  public static DbHelper dbHelper;

  public static AdbKeyPair keyPair;

  public static ClipboardManager clipBoard;

  // USB
  public static UsbManager usbManager;

  public static Setting setting;

  public static String serverName = "easycontrol_server_" + BuildConfig.VERSION_CODE + ".jar";

  public static void init(Context context) {
    Context app = context.getApplicationContext();
    if (setting == null) {
      dbHelper = new DbHelper(app);
      clipBoard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
      usbManager = (UsbManager) app.getSystemService(Context.USB_SERVICE);
      setting = new Setting(app);
    }
    if (keyPair != null) return;
    try {
      File privateKey = new File(app.getFilesDir(), "private.key");
      File publicKey = new File(app.getFilesDir(), "public.key");
      if (!privateKey.isFile() || !publicKey.isFile()) {
        AdbKeyPair.generate(privateKey, publicKey);
      }
      keyPair = AdbKeyPair.read(privateKey, publicKey);
    } catch (Exception ignored) {
    }
  }
}
