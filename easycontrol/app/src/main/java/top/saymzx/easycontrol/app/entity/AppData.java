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

  public static void init(Activity m) {
    main = m;
    dbHelper = new DbHelper(main);
    clipBoard = (ClipboardManager) main.getSystemService(Context.CLIPBOARD_SERVICE);
    usbManager = (UsbManager) main.getSystemService(Context.USB_SERVICE);
    setting = new Setting(main.getSharedPreferences("setting", Context.MODE_PRIVATE));
    try {
      File privateKey = new File(main.getApplicationContext().getFilesDir(), "private.key");
      File publicKey = new File(main.getApplicationContext().getFilesDir(), "public.key");
      if (!privateKey.isFile() || !publicKey.isFile()) {
        AdbKeyPair.generate(privateKey, publicKey);
      }
      keyPair = AdbKeyPair.read(privateKey, publicKey);
    } catch (Exception ignored) {
    }
  }
}
