package top.saymzx.easycontrol.app.entity;

import android.content.Context;
import android.content.SharedPreferences;

public final class Setting {
  private final SharedPreferences sharedPreferences;

  private final SharedPreferences.Editor editor;

  public boolean getDefaultIsAudio() {
    return sharedPreferences.getBoolean("defaultIsAudio", true);
  }

  public void setDefaultIsAudio(boolean value) {
    editor.putBoolean("defaultIsAudio", value);
    editor.apply();
  }

  public int getDefaultMaxSize() {
    return sharedPreferences.getInt("defaultMaxSize", 1600);
  }

  public void setDefaultMaxSize(int value) {
    editor.putInt("defaultMaxSize", value);
    editor.apply();
  }

  public int getDefaultMaxFps() {
    return sharedPreferences.getInt("defaultMaxFps", 60);
  }

  public void setDefaultMaxFps(int value) {
    editor.putInt("defaultMaxFps", value);
    editor.apply();
  }

  public int getDefaultVideoBit() {
    return sharedPreferences.getInt("defaultVideoBit", 8);
  }

  public void setDefaultVideoBit(int value) {
    editor.putInt("defaultVideoBit", value);
    editor.apply();
  }

  public boolean getDefaultSetResolution() {
    return sharedPreferences.getBoolean("defaultSetResolution", false);
  }

  public void setDefaultSetResolution(boolean value) {
    editor.putBoolean("defaultSetResolution", value);
    editor.apply();
  }

  public boolean getSlaveTurnOffScreen() {
    return sharedPreferences.getBoolean("slaveTurnOffScreen", true);
  }

  public void setSlaveTurnOffScreen(boolean value) {
    editor.putBoolean("slaveTurnOffScreen", value);
    editor.apply();
  }

  public boolean getAutoControlScreen() {
    return sharedPreferences.getBoolean("autoControlScreen", true);
  }

  public void setAutoControlScreen(boolean value) {
    editor.putBoolean("autoControlScreen", value);
    editor.apply();
  }

  public boolean getDefaultFull() {
    return sharedPreferences.getBoolean("defaultFull", false);
  }

  public void setDefaultFull(boolean value) {
    editor.putBoolean("defaultFull", value);
    editor.apply();
  }

  public int getDefaultDevice() {
    return sharedPreferences.getInt("defaultDevice", -1);
  }

  public boolean getAutoOpenDefault() {
    return sharedPreferences.getBoolean("autoOpenDefault", true);
  }

  public void setAutoOpenDefault(boolean value) {
    sharedPreferences.edit().putBoolean("autoOpenDefault", value).apply();
  }

  public boolean getAllowCommands() {
    return sharedPreferences.getBoolean("allowCommands", false);
  }

  public void setAllowCommands(boolean value) {
    sharedPreferences.edit().putBoolean("allowCommands", value).apply();
  }

  public void setDefaultDevice(int value) {
    editor.putInt("defaultDevice", value);
    editor.apply();
  }

  // Floating window box per tablet orientation: {x, y, width, height} of the video area, or null.
  public int[] getWindowBox(boolean landscape) {
    String value = sharedPreferences.getString(landscape ? "windowBoxLand" : "windowBoxPort", "");
    String[] parts = value.split(",");
    if (parts.length != 4) return null;
    try {
      int[] box = new int[4];
      for (int i = 0; i < 4; i++) box[i] = Integer.parseInt(parts[i]);
      return box[2] > 0 && box[3] > 0 ? box : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  public void setWindowBox(boolean landscape, int x, int y, int width, int height) {
    editor.putString(landscape ? "windowBoxLand" : "windowBoxPort", x + "," + y + "," + width + "," + height);
    editor.apply();
  }

  public boolean getSyncClipboard() {
    return sharedPreferences.getBoolean("syncClipboard", true);
  }

  public void setSyncClipboard(boolean value) {
    sharedPreferences.edit().putBoolean("syncClipboard", value).apply();
  }

  public String getVideoDecoder() {
    return sharedPreferences.getString("videoDecoder", "");
  }

  public void setVideoDecoder(String name) {
    sharedPreferences.edit().putString("videoDecoder", name).apply();
  }

  public Setting(Context context) {
    this(context.getSharedPreferences("setting", Context.MODE_PRIVATE));
  }

  public Setting(SharedPreferences sharedPreferences) {
    this.sharedPreferences = sharedPreferences;
    this.editor = sharedPreferences.edit();
  }
}
